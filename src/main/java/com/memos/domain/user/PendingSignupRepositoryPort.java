package com.memos.domain.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PendingSignupRepositoryPort {

    /** Saves the sign-up, replacing any earlier pending one for the same {@code emailKey}. */
    PendingSignup replace(PendingSignup signup);

    Optional<PendingSignup> findByEmailKey(String emailKey);

    void recordFailedAttempt(UUID id);

    /** Deletes the sign-up; returns false if it was already gone (e.g. a concurrent request). */
    boolean delete(UUID id);

    void deleteExpired(Instant now);
}
