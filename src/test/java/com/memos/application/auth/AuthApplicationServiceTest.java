package com.memos.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.exception.AccessDeniedException;
import com.memos.domain.exception.InvalidCredentialsException;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    @Mock
    private UserRepositoryPort userRepositoryPort;

    @Mock
    private PasswordHasherPort passwordHasherPort;

    @Mock
    private TokenIssuerPort tokenIssuerPort;

    private AuthApplicationService authApplicationService;

    @BeforeEach
    void setUp() {
        authApplicationService = new AuthApplicationService(userRepositoryPort, passwordHasherPort, tokenIssuerPort);
    }

    @Test
    void registerCreatesUserWithHashedPasswordAndDefaultRole() {
        when(userRepositoryPort.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordHasherPort.hash("plaintext-pw")).thenReturn("hashed-pw");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authApplicationService.register(new RegisterCommand("alice@example.com", "plaintext-pw", "Alice", null));

        assertThat(result.getEmail()).isEqualTo("alice@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(result.getFullName()).isEqualTo("Alice");
        assertThat(result.getRole()).isEqualTo(Role.USER);
        assertThat(result.isEnabled()).isTrue();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepositoryPort.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authApplicationService.register(new RegisterCommand("alice@example.com", "pw", "Alice", null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    void loginIssuesTokensForValidCredentials() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .passwordHash("hashed-pw")
                .fullName("Alice")
                .role(Role.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        TokenPair tokenPair = new TokenPair("access-token", "refresh-token", 3600L);

        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches("plaintext-pw", "hashed-pw")).thenReturn(true);
        when(tokenIssuerPort.issueTokens(user)).thenReturn(tokenPair);

        AuthResult result = authApplicationService.login(new LoginCommand("alice@example.com", "plaintext-pw"));

        assertThat(result.user()).isEqualTo(user);
        assertThat(result.tokens()).isEqualTo(tokenPair);
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepositoryPort.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authApplicationService.login(new LoginCommand("ghost@example.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .passwordHash("hashed-pw")
                .fullName("Alice")
                .role(Role.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordHasherPort.matches(anyString(), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authApplicationService.login(new LoginCommand("alice@example.com", "wrong-pw")))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(tokenIssuerPort, never()).issueTokens(any());
    }

    @Test
    void loginRejectsDisabledUser() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alice@example.com")
                .passwordHash("hashed-pw")
                .fullName("Alice")
                .role(Role.USER)
                .enabled(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authApplicationService.login(new LoginCommand("alice@example.com", "plaintext-pw")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshDelegatesToTokenIssuer() {
        TokenPair tokenPair = new TokenPair("new-access", "new-refresh", 3600L);
        when(tokenIssuerPort.refreshTokens("old-refresh")).thenReturn(tokenPair);

        TokenPair result = authApplicationService.refresh("old-refresh");

        assertThat(result).isEqualTo(tokenPair);
    }

    @Test
    void logoutDelegatesToTokenIssuer() {
        authApplicationService.logout("refresh-token");

        verify(tokenIssuerPort).revokeRefreshToken("refresh-token");
    }

    @Test
    void registrationCanBeClosed() {
        ReflectionTestUtils.setField(authApplicationService, "registrationEnabled", false);

        assertThatThrownBy(() -> authApplicationService.register(new RegisterCommand("bob@example.com", "password1", "Bob", null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("closed");
        assertThat(authApplicationService.registrationPolicy().open()).isFalse();
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    void inviteCodeIsRequiredWhenConfigured() {
        ReflectionTestUtils.setField(authApplicationService, "inviteCode", "let-me-in");
        assertThat(authApplicationService.registrationPolicy().inviteCodeRequired()).isTrue();

        assertThatThrownBy(() -> authApplicationService.register(new RegisterCommand("bob@example.com", "password1", "Bob", null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authApplicationService.register(new RegisterCommand("bob@example.com", "password1", "Bob", "wrong")))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepositoryPort, never()).save(any());

        when(userRepositoryPort.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordHasherPort.hash("password1")).thenReturn("hashed");
        when(userRepositoryPort.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(authApplicationService.register(
                new RegisterCommand("bob@example.com", "password1", "Bob", " let-me-in ")).getEmail())
                .isEqualTo("bob@example.com");
    }

    @Test
    void openRegistrationNeedsNoInviteCode() {
        assertThat(authApplicationService.registrationPolicy())
                .isEqualTo(new AuthApplicationService.RegistrationPolicy(true, false));
    }
}
