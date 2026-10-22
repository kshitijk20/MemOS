package com.memos.web.feedback;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.memos.application.conversation.ConversationApplicationService;
import com.memos.domain.common.PageRequest;
import com.memos.domain.conversation.MessageFeedback;
import com.memos.infrastructure.security.CustomUserPrincipal;
import com.memos.web.common.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Rated answers across the user's conversations, so thumbs-down answers can be reviewed and fixed. */
@Tag(name = "Feedback")
@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FeedbackController {

    private final ConversationApplicationService conversationApplicationService;

    @GetMapping
    public ResponseEntity<PageResponse<FeedbackEntryResponse>> list(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(required = false) MessageFeedback rating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(PageResponse.of(
                conversationApplicationService.listFeedback(principal.getId(), rating, PageRequest.of(page, size)),
                FeedbackEntryResponse::from));
    }
}
