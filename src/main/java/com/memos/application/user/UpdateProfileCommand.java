package com.memos.application.user;

public record UpdateProfileCommand(
        String fullName,
        String email,
        String currentPassword,
        String newPassword) {
}
