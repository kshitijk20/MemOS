package com.memos.domain.user;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for user persistence. Implemented by an infrastructure adapter.
 */
public interface UserRepositoryPort {

    User save(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Deletes the user; everything they own is removed with them (database cascades). */
    void deleteById(UUID id);
}
