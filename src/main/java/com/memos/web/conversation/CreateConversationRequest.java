package com.memos.web.conversation;

import com.memos.domain.ai.AiProvider;

import jakarta.validation.constraints.Size;

public record CreateConversationRequest(

        @Size(max = 500)
        String title,

        AiProvider provider,

        @Size(max = 100)
        String model) {
}
