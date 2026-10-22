package com.memos.application.auth;

/** {@code inviteCode} is only checked when the server requires one. */
public record RegisterCommand(String email, String rawPassword, String fullName, String inviteCode) {
}
