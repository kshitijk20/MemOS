package com.memos.web.conversation;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.context.SecurityContextHolder;

import com.memos.application.conversation.ConversationApplicationService;
import com.memos.application.limits.UsageLimitService;
import com.memos.application.rag.RagQueryService;
import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.Message;
import com.memos.domain.exception.DomainException;
import com.memos.infrastructure.security.CustomUserPrincipal;
import com.memos.web.common.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Tag(name = "Conversations")
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;
    private final RagQueryService ragQueryService;
    private final UsageLimitService usageLimitService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateConversationRequest request) {

        Conversation conversation = conversationApplicationService.create(
                principal.getId(), request.title(), request.provider(), request.model(), request.documentIds(),
                request.collectionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConversationResponse.from(conversation));
    }

    @GetMapping
    public ResponseEntity<PageResponse<ConversationResponse>> list(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResult<Conversation> result = conversationApplicationService.list(principal.getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.of(result, ConversationResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> get(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {

        Conversation conversation = conversationApplicationService.get(principal.getId(), id);
        return ResponseEntity.ok(ConversationResponse.from(conversation));
    }

    @PatchMapping("/{id}/scope")
    public ResponseEntity<ConversationResponse> updateScope(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody UpdateDocumentScopeRequest request) {

        Conversation conversation = conversationApplicationService.updateScope(
                principal.getId(),
                id,
                request == null ? List.of() : request.documentIds(),
                request == null ? null : request.collectionId());
        return ResponseEntity.ok(ConversationResponse.from(conversation));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {

        conversationApplicationService.delete(principal.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<PageResponse<MessageResponse>> listMessages(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResult<Message> result = conversationApplicationService.listMessages(principal.getId(), id, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.of(result, MessageResponse::from));
    }

    @PutMapping("/{id}/messages/{messageId}/feedback")
    public ResponseEntity<MessageResponse> setFeedback(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID messageId,
            @Valid @RequestBody MessageFeedbackRequest request) {

        Message message = conversationApplicationService.setFeedback(
                principal.getId(), id, messageId, request.feedback(), request.comment());
        return ResponseEntity.ok(MessageResponse.from(message));
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> createMessage(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateMessageRequest request) {

        usageLimitService.checkQuestion(principal.getId());
        Message assistantMessage = ragQueryService.askQuestion(principal.getId(), id, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(assistantMessage));
    }

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateMessageRequest request) {

        // Checked before the stream opens, so an over-limit request gets a plain 429 response.
        usageLimitService.checkQuestion(principal.getId());
        SseEmitter emitter = new SseEmitter(120_000L);
        UUID userId = principal.getId();
        String content = request.content();

        Thread.ofVirtual().start(new DelegatingSecurityContextRunnable(() -> {
            try {
                RagQueryService.StreamResult result = ragQueryService.streamQuestion(
                        userId, id, content,
                        token -> {
                            try {
                                emitter.send(SseEmitter.event()
                                        .data("{\"type\":\"token\",\"content\":" + objectMapper.writeValueAsString(token) + "}"));
                            } catch (IOException e) {
                                emitter.completeWithError(e);
                            }
                        });

                SseDoneEvent done = new SseDoneEvent("done",
                        MessageResponse.from(result.assistantMessage()),
                        result.generatedTitle());
                emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(done)));
                emitter.complete();
            } catch (Exception e) {
                log.error("Streaming answer failed for conversation {}", id, e);
                // Domain exceptions carry user-facing messages; anything else could leak internals.
                String message = e instanceof DomainException
                        ? e.getMessage()
                        : "Something went wrong while answering. Try again.";
                try {
                    emitter.send(SseEmitter.event()
                            .data("{\"type\":\"error\",\"message\":" + objectMapper.writeValueAsString(message) + "}"));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        }, SecurityContextHolder.getContext()));

        return emitter;
    }

    private record SseDoneEvent(String type, MessageResponse message, String conversationTitle) {}
}
