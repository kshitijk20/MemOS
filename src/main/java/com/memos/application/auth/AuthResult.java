package com.memos.application.auth;

import com.memos.domain.user.User;

public record AuthResult(User user, TokenPair tokens) {
}
