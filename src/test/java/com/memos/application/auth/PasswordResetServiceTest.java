package com.memos.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.user.PasswordResetToken;
import com.memos.domain.user.PasswordResetTokenRepositoryPort;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private PasswordResetTokenRepositoryPort tokenRepositoryPort;
    @Mock private PasswordHasherPort passwordHasherPort;
    @Mock private TokenIssuerPort tokenIssuerPort;
    @Mock private EmailSenderPort emailSenderPort;
    @Mock private RateLimiterPort rateLimiterPort;

    @InjectMocks private PasswordResetService service;

    private final User alice = User.builder()
            .id(UUID.randomUUID()).email("alice@example.com").fullName("Alice").passwordHash("old")
            .role(Role.USER).enabled(true).createdAt(Instant.now()).updatedAt(Instant.now()).build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendUrl", "https://memos.example.com/");
        lenient().when(rateLimiterPort.tryAcquire(anyString(), anyInt(), any())).thenReturn(RateLimiterPort.Result.allow());
    }

    @Test
    void emailsASingleUseLinkAndStoresOnlyItsHash() {
        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        service.sendResetLink(" alice@example.com ");

        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSenderPort).send(email.capture());
        assertThat(email.getValue().toEmail()).isEqualTo("alice@example.com");
        String link = email.getValue().textBody().lines()
                .filter(l -> l.startsWith("https://")).findFirst().orElseThrow();
        assertThat(link).startsWith("https://memos.example.com/reset-password?token=");
        String token = link.substring(link.indexOf("token=") + 6);

        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepositoryPort).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(PasswordResetService.sha256Hex(token)).isNotEqualTo(token);
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now().plusSeconds(25 * 60));
        verify(tokenRepositoryPort).invalidateUnusedForUser(eq(alice.getId()), any());
    }

    @Test
    void unknownEmailSendsNothingAndDoesNotThrow() {
        when(userRepositoryPort.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.sendResetLink("nobody@example.com");

        verify(emailSenderPort, never()).send(any());
        verify(tokenRepositoryPort, never()).save(any());
    }

    @Test
    void perAddressLimitStopsInboxFlooding() {
        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(rateLimiterPort.tryAcquire(org.mockito.ArgumentMatchers.startsWith("password-reset:address:"), anyInt(), any()))
                .thenReturn(new RateLimiterPort.Result(false, 600));

        service.sendResetLink("alice@example.com");

        verify(emailSenderPort, never()).send(any());
    }

    @Test
    void emailFailuresAreSwallowedByTheAsyncEntryPoint() {
        when(userRepositoryPort.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        org.mockito.Mockito.doThrow(new IllegalStateException("Brevo down")).when(emailSenderPort).send(any());

        service.requestReset("alice@example.com");
    }

    @Test
    void validLinkSetsNewPasswordAndSignsOutEverywhere() {
        PasswordResetToken token = token(Instant.now().plusSeconds(600), null);
        when(tokenRepositoryPort.findByTokenHash(PasswordResetService.sha256Hex("tok"))).thenReturn(Optional.of(token));
        when(userRepositoryPort.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(tokenRepositoryPort.markUsed(eq(token.getId()), any())).thenReturn(true);
        when(passwordHasherPort.hash("new-password-1")).thenReturn("new-hash");

        service.resetPassword("tok", "new-password-1");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepositoryPort).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("new-hash");
        verify(tokenIssuerPort).revokeAllForUser(alice.getId());
    }

    @Test
    void expiredUsedOrUnknownLinksAreRejected() {
        when(tokenRepositoryPort.findByTokenHash(PasswordResetService.sha256Hex("expired")))
                .thenReturn(Optional.of(token(Instant.now().minusSeconds(1), null)));
        when(tokenRepositoryPort.findByTokenHash(PasswordResetService.sha256Hex("used")))
                .thenReturn(Optional.of(token(Instant.now().plusSeconds(600), Instant.now())));
        when(tokenRepositoryPort.findByTokenHash(PasswordResetService.sha256Hex("unknown"))).thenReturn(Optional.empty());

        for (String t : new String[] {"expired", "used", "unknown"}) {
            assertThatThrownBy(() -> service.resetPassword(t, "new-password-1"))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage(PasswordResetService.INVALID_LINK);
        }
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    void aLinkUsedConcurrentlyOnlyWorksOnce() {
        PasswordResetToken token = token(Instant.now().plusSeconds(600), null);
        when(tokenRepositoryPort.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepositoryPort.findById(alice.getId())).thenReturn(Optional.of(alice));
        when(tokenRepositoryPort.markUsed(eq(token.getId()), any())).thenReturn(false);

        assertThatThrownBy(() -> service.resetPassword("tok", "new-password-1")).isInstanceOf(InvalidRequestException.class);
        verify(userRepositoryPort, never()).save(any());
    }

    @Test
    void shortPasswordsAreRejectedBeforeTouchingTheToken() {
        assertThatThrownBy(() -> service.resetPassword("tok", "short")).isInstanceOf(InvalidRequestException.class);
        verify(tokenRepositoryPort, never()).findByTokenHash(any());
    }

    private PasswordResetToken token(Instant expiresAt, Instant usedAt) {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID()).userId(alice.getId()).tokenHash("h")
                .expiresAt(expiresAt).usedAt(usedAt).createdAt(Instant.now()).build();
    }
}
