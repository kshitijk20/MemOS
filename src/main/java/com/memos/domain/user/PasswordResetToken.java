package com.memos.domain.user;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** A single-use password reset link. Only the hash of the emailed token is stored. */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class PasswordResetToken {

    private final UUID id;
    private final UUID userId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final Instant usedAt;
    private final Instant createdAt;

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
