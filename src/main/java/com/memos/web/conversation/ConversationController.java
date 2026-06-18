package com.memos.web.conversation;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memos.application.conversation.ConversationApplicationService;
import com.memos.application.rag.RagQueryService;
import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;
import com.memos.domain.conversation.Conversation;
import com.memos.domain.conversation.Message;
import com.memos.infrastructure.security.CustomUserPrincipal;
import com.memos.web.common.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Conversations")
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ConversationController {

    private final ConversationApplicationService conversationApplicationService;
    private final RagQueryService ragQueryService;

    @PostMapping
    public ResponseEntity<ConversationResponse> create(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody CreateConversationRequest request) {

        Conversation conversation = conversationApplicationService.create(
                principal.getId(), request.title(), request.provider(), request.model());
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

    @PostMapping("/{id}/messages")
    public ResponseEntity<MessageResponse> createMessage(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateMessageRequest request) {

        Message assistantMessage = ragQueryService.askQuestion(principal.getId(), id, request.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(assistantMessage));
    }
}
