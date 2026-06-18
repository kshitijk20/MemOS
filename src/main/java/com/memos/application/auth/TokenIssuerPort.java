package com.memos.application.auth;

import com.memos.domain.user.User;

/**
 * Issues and manages access/refresh token pairs for authenticated users. Implemented by an
 * infrastructure adapter that handles JWT signing and refresh-token storage.
 */
public interface TokenIssuerPort {

    /**
     * Issues a new access/refresh token pair for the given user, persisting the refresh token.
     */
    TokenPair issueTokens(User user);

    /**
     * Validates and rotates a refresh token, returning a new token pair. The presented refresh
     * token is revoked as part of the rotation.
     *
     * @throws com.memos.domain.exception.InvalidCredentialsException if the refresh token is
     *         missing, expired, or already revoked
     */
    TokenPair refreshTokens(String refreshToken);

    /**
     * Revokes a refresh token so it can no longer be used, if it exists.
     */
    void revokeRefreshToken(String refreshToken);
}
