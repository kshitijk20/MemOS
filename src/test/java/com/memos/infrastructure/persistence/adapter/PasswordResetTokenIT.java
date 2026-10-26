package com.memos.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.memos.domain.user.PasswordResetToken;
import com.memos.domain.user.PasswordResetTokenRepositoryPort;
import com.memos.domain.user.Role;
import com.memos.infrastructure.persistence.entity.UserEntity;
import com.memos.infrastructure.persistence.repository.UserJpaRepository;

/** Password reset tokens against a real Postgres (V15). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class PasswordResetTokenIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PasswordResetTokenRepositoryPort tokens;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Test
    void tokensAreSingleUseAndOnlyTheNewestStaysValid() {
        UUID userId = userJpaRepository.save(UserEntity.builder()
                .email("reset@example.com").passwordHash("x").fullName("Reset").role(Role.USER).enabled(true)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build()).getId();
        Instant now = Instant.now();

        PasswordResetToken first = tokens.save(token(userId, "hash-1", now));
        tokens.invalidateUnusedForUser(userId, now);
        PasswordResetToken second = tokens.save(token(userId, "hash-2", now));

        assertThat(tokens.findByTokenHash("hash-1")).get().matches(t -> !t.isUsable(now));
        assertThat(tokens.findByTokenHash("hash-2")).get().matches(t -> t.isUsable(now));

        assertThat(tokens.markUsed(second.getId(), now)).isTrue();
        assertThat(tokens.markUsed(second.getId(), now)).isFalse();
        assertThat(tokens.markUsed(first.getId(), now)).isFalse();

        userJpaRepository.deleteById(userId);
        assertThat(tokens.findByTokenHash("hash-2")).isEmpty();
    }

    private static PasswordResetToken token(UUID userId, String hash, Instant now) {
        return PasswordResetToken.builder()
                .userId(userId).tokenHash(hash).expiresAt(now.plusSeconds(1800)).createdAt(now).build();
    }
}
