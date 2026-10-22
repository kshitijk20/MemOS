package com.memos.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 8, max = 100)
        String password,

        @NotBlank @Size(max = 150)
        String fullName,

        /** Required only when the server is configured with an invite code. */
        @Size(max = 200)
        String inviteCode) {
}
