package com.memos.application.auth;

public record RegisterCommand(String email, String rawPassword, String fullName) {
}
