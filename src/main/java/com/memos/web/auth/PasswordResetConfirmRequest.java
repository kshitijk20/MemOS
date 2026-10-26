package com.memos.web.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank @Size(max = 200)
        String token,

        @NotBlank @Size(min = 8, max = 100)
        String newPassword) {
}
