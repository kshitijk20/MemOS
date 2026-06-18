package com.memos.application.auth;

public record LoginCommand(String email, String rawPassword) {
}
