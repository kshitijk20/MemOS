package com.memos.web.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifySignupRequest(

        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(max = 20)
        String code) {
}
