package com.memos.application.auth;

/**
 * A freshly issued access/refresh token pair.
 *
 * @param accessToken  short-lived JWT used to authenticate API requests
 * @param refreshToken opaque, single-use token used to obtain a new {@link TokenPair}
 * @param expiresIn    access token lifetime, in seconds
 */
public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
}
