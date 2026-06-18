package com.memos.domain.user;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class User {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final Role role;
    private final boolean enabled;
    private final Instant createdAt;
    private final Instant updatedAt;
}
