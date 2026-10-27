package com.memos.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
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

import com.memos.application.limits.RateLimiterPort;
import com.memos.domain.exception.InvalidCredentialsException;
import com.memos.domain.exception.RateLimitExceededException;
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

    @Mock
    private RateLimiterPort rateLimiterPort;

    private AuthApplicationService authApplicationService;

    @BeforeEach
    void setUp() {
        authApplicationService = new AuthApplicationService(userRepositoryPort, passwordHasherPort, tokenIssuerPort, rateLimiterPort);
        lenient().when(rateLimiterPort.tryAcquire(anyString(), anyInt(), any())).thenReturn(RateLimiterPort.Result.allow());
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
    void loginIsRateLimitedPerEmail() {
        when(rateLimiterPort.tryAcquire(startsWith("login:email:"), anyInt(), any()))
                .thenReturn(new RateLimiterPort.Result(false, 600));

        assertThatThrownBy(() -> authApplicationService.login(new LoginCommand("alice@example.com", "guess")))
                .isInstanceOf(RateLimitExceededException.class);
        verify(userRepositoryPort, never()).findByEmail(anyString());
    }

    @Test
    void unknownEmailStillChecksAPasswordHash() {
        when(userRepositoryPort.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(passwordHasherPort.hash(anyString())).thenReturn("dummy-hash");

        assertThatThrownBy(() -> authApplicationService.login(new LoginCommand("ghost@example.com", "pw")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(passwordHasherPort).matches("pw", "dummy-hash");
    }

    @Test
    void logoutEverywhereRevokesAllSessions() {
        UUID userId = UUID.randomUUID();

        authApplicationService.logoutEverywhere(userId);

        verify(tokenIssuerPort).revokeAllForUser(userId);
    }
}
