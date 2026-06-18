package com.memos.web.auth;

import com.memos.application.auth.TokenPair;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {

    public static AuthResponse from(TokenPair tokens) {
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken(), "Bearer", tokens.expiresIn());
    }
}
