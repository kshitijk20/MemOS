package com.memos.domain.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepositoryPort {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Marks every unused token of the user as used, so only the newest link works. */
    void invalidateUnusedForUser(UUID userId, Instant now);

    /** Marks the token used; returns false if it was already used (e.g. a concurrent request). */
    boolean markUsed(UUID tokenId, Instant now);
}
