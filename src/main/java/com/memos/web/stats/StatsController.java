package com.memos.web.stats;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.memos.domain.conversation.ConversationRepositoryPort;
import com.memos.domain.conversation.MessageFeedback;
import com.memos.domain.conversation.MessageRepositoryPort;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.document.DocumentStatus;
import com.memos.infrastructure.security.CustomUserPrincipal;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    private final DocumentRepositoryPort documentRepositoryPort;
    private final ConversationRepositoryPort conversationRepositoryPort;
    private final MessageRepositoryPort messageRepositoryPort;

    @GetMapping
    public ResponseEntity<StatsResponse> getStats(@AuthenticationPrincipal CustomUserPrincipal principal) {
        UUID userId = principal.getId();
        long total = documentRepositoryPort.countByOwnerId(userId);
        long ready = documentRepositoryPort.countByOwnerIdAndStatus(userId, DocumentStatus.COMPLETED);
        long processing = documentRepositoryPort.countByOwnerIdAndStatus(userId, DocumentStatus.PROCESSING)
                + documentRepositoryPort.countByOwnerIdAndStatus(userId, DocumentStatus.PENDING);
        long failed = documentRepositoryPort.countByOwnerIdAndStatus(userId, DocumentStatus.FAILED);
        long conversations = conversationRepositoryPort.countByUserId(userId);

        long helpful = messageRepositoryPort.countFeedbackByUserId(userId, MessageFeedback.UP);
        long unhelpful = messageRepositoryPort.countFeedbackByUserId(userId, MessageFeedback.DOWN);
        return ResponseEntity.ok(new StatsResponse(
                total, ready, processing, failed, conversations, helpful, unhelpful));
    }
}
