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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.memos.application.limits.RateLimiterPort;
import com.memos.application.notification.EmailMessage;
import com.memos.application.notification.EmailSenderPort;
import com.memos.domain.exception.AccessDeniedException;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.user.PendingSignup;
import com.memos.domain.user.PendingSignupRepositoryPort;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private PendingSignupRepositoryPort pendingSignupRepositoryPort;
    @Mock private PasswordHasherPort passwordHasherPort;
    @Mock private TokenIssuerPort tokenIssuerPort;
    @Mock private EmailSenderPort emailSenderPort;
    @Mock private RateLimiterPort rateLimiterPort;

    @InjectMocks private SignupService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "https://memos.example.com");
        lenient().when(rateLimiterPort.tryAcquire(anyString(), anyInt(), any())).thenReturn(RateLimiterPort.Result.allow());
    }

    @Test
    void emailsACodeAndStoresOnlyItsHash() {
        when(userRepositoryPort.findByEmail("Alice@example.com")).thenReturn(Optional.empty());
        when(passwordHasherPort.hash("super-secret")).thenReturn("hashed-pw");

        service.sendCode(new RegisterCommand(" Alice@example.com ", "super-secret", "Alice"));

        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSenderPort).send(email.capture());
        String code = email.getValue().subject().substring(0, 6);
        assertThat(code).matches("\\d{6}");
        assertThat(email.getValue().textBody()).contains(code);

        ArgumentCaptor<PendingSignup> saved = ArgumentCaptor.forClass(PendingSignup.class);
        verify(pendingSignupRepositoryPort).replace(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("Alice@example.com");
        assertThat(saved.getValue().getEmailKey()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(saved.getValue().getCodeHash()).isEqualTo(PasswordResetService.sha256Hex(code)).doesNotContain(code);
    }

    @Test
    void existingAccountGetsANoticeInsteadOfACode() {
        User alice = user();
        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        service.sendCode(new RegisterCommand("alice@example.com", "super-secret", "Alice"));

        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSenderPort).send(email.capture());
        assertThat(email.getValue().subject()).contains("already have");
        assertThat(email.getValue().textBody()).contains("https://memos.example.com/forgot-password");
        verify(pendingSignupRepositoryPort, never()).replace(any());
    }

    @Test
    void stopsSendingWhenAnAddressHitsItsHourlyLimit() {
        when(rateLimiterPort.tryAcquire(startsWith("signup:address:"), anyInt(), any()))
                .thenReturn(new RateLimiterPort.Result(false, 600));

        service.sendCode(new RegisterCommand("alice@example.com", "super-secret", "Alice"));

        verify(emailSenderPort, never()).send(any());
    }

    @Test
    void correctCodeCreatesTheAccountAndSignsIn() {
        PendingSignup pending = pending("123456", 0);
        when(pendingSignupRepositoryPort.findByEmailKey("alice@example.com")).thenReturn(Optional.of(pending));
        when(pendingSignupRepositoryPort.delete(pending.getId())).thenReturn(true);
        when(userRepositoryPort.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        TokenPair tokens = new TokenPair("access", "refresh", 900L);
        when(tokenIssuerPort.issueTokens(any())).thenReturn(tokens);

        AuthResult result = service.verifySignup(" ALICE@example.com", "123 456");

        assertThat(result.tokens()).isEqualTo(tokens);
        assertThat(result.user().getEmail()).isEqualTo("Alice@example.com");
        assertThat(result.user().getPasswordHash()).isEqualTo("hashed-pw");
        assertThat(result.user().getRole()).isEqualTo(Role.USER);
        assertThat(result.user().isEnabled()).isTrue();
    }

    @Test
    void wrongCodeCountsAnAttemptAndCreatesNothing() {
        PendingSignup pending = pending("123456", 0);
        when(pendingSignupRepositoryPort.findByEmailKey("alice@example.com")).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.verifySignup("alice@example.com", "654321"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("4 tries left");

        verify(pendingSignupRepositoryPort).recordFailedAttempt(pending.getId());
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    void codeStopsWorkingAfterTooManyWrongAttempts() {
        when(pendingSignupRepositoryPort.findByEmailKey("alice@example.com"))
                .thenReturn(Optional.of(pending("123456", SignupService.MAX_ATTEMPTS)));

        assertThatThrownBy(() -> service.verifySignup("alice@example.com", "123456"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(SignupService.INVALID_CODE);
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    void expiredCodeIsRejected() {
        PendingSignup expired = pending("123456", 0).toBuilder().expiresAt(Instant.now().minusSeconds(1)).build();
        when(pendingSignupRepositoryPort.findByEmailKey("alice@example.com")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.verifySignup("alice@example.com", "123456"))
                .isInstanceOf(InvalidRequestException.class);
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    void concurrentVerificationOnlyCreatesOneAccount() {
        PendingSignup pending = pending("123456", 0);
        when(pendingSignupRepositoryPort.findByEmailKey("alice@example.com")).thenReturn(Optional.of(pending));
        when(pendingSignupRepositoryPort.delete(pending.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.verifySignup("alice@example.com", "123456"))
                .isInstanceOf(InvalidRequestException.class);
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    void registrationCanBeClosed() {
        ReflectionTestUtils.setField(service, "registrationEnabled", false);

        assertThat(service.registrationOpen()).isFalse();
        assertThatThrownBy(service::requireRegistrationOpen).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.verifySignup("alice@example.com", "123456"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static PendingSignup pending(String code, int attempts) {
        return PendingSignup.builder()
                .id(UUID.randomUUID())
                .email("Alice@example.com")
                .emailKey("alice@example.com")
                .fullName("Alice")
                .passwordHash("hashed-pw")
                .codeHash(PasswordResetService.sha256Hex(code))
                .attempts(attempts)
                .expiresAt(Instant.now().plus(SignupService.CODE_LIFETIME))
                .createdAt(Instant.now())
                .build();
    }

    private static User user() {
        return User.builder()
                .id(UUID.randomUUID()).email("alice@example.com").fullName("Alice").passwordHash("old")
                .role(Role.USER).enabled(true).createdAt(Instant.now()).updatedAt(Instant.now()).build();
    }
}
