package com.memos.application.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memos.application.auth.PasswordHasherPort;
import com.memos.application.auth.TokenIssuerPort;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private PasswordHasherPort passwordHasherPort;
    @Mock private TokenIssuerPort tokenIssuerPort;

    @InjectMocks private UserProfileService service;

    private final UUID userId = UUID.randomUUID();
    private final User alice = User.builder()
            .id(userId).email("alice@example.com").fullName("Alice").passwordHash("hash")
            .role(Role.USER).enabled(true).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(alice));
    }

    @Test
    void renamingNeedsNoPassword() {
        when(userRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));

        service.updateProfile(userId, new UpdateProfileCommand("Alice B", "alice@example.com", null, null));

        verify(tokenIssuerPort, never()).revokeAllForUser(any());
    }

    @Test
    void changingPasswordNeedsTheCurrentOneAndSignsOutEverywhere() {
        assertThatThrownBy(() -> service.updateProfile(userId,
                new UpdateProfileCommand("Alice", "alice@example.com", null, "new-password")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.updateProfile(userId,
                new UpdateProfileCommand("Alice", "alice@example.com", "wrong", "new-password")))
                .isInstanceOf(InvalidRequestException.class);
        verify(userRepositoryPort, never()).save(any());

        when(passwordHasherPort.matches("right", "hash")).thenReturn(true);
        when(passwordHasherPort.hash("new-password")).thenReturn("new-hash");
        when(userRepositoryPort.save(any())).thenAnswer(i -> i.getArgument(0));
        service.updateProfile(userId, new UpdateProfileCommand("Alice", "alice@example.com", "right", "new-password"));

        verify(tokenIssuerPort).revokeAllForUser(userId);
    }

    @Test
    void changingEmailNeedsTheCurrentPassword() {
        assertThatThrownBy(() -> service.updateProfile(userId,
                new UpdateProfileCommand("Alice", "attacker@example.com", null, null)))
                .isInstanceOf(InvalidRequestException.class);
        verify(userRepositoryPort, never()).save(any());
    }
}
