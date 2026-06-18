package com.memos.web.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

        @NotBlank
        String refreshToken) {
}
