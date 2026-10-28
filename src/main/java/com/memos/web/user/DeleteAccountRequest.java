package com.memos.web.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeleteAccountRequest(
        @NotBlank @Size(max = 100)
        String password) {
}
