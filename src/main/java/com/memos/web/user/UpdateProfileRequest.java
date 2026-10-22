package com.memos.web.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @NotBlank @Size(max = 150)
        String fullName,

        @NotBlank @Email
        String email,

        String currentPassword,

        @Size(min = 8, max = 100)
        String newPassword) {
}
