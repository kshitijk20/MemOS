package com.memos.web.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMessageRequest(

        @NotBlank @Size(max = 8000)
        String content) {
}
