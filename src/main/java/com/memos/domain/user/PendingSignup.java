package com.memos.domain.user;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * A sign-up waiting for its emailed verification code. {@code emailKey} is the lower-cased email,
 * so one address has at most one pending sign-up. Only the hash of the code is stored.
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class PendingSignup {

    private final UUID id;
    private final String email;
    private final String emailKey;
    private final String fullName;
    private final String passwordHash;
    private final String codeHash;
    private final int attempts;
    private final Instant expiresAt;
    private final Instant createdAt;

    public boolean isUsable(Instant now, int maxAttempts) {
        return attempts < maxAttempts && now.isBefore(expiresAt);
    }
}
