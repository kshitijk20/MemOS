package com.memos.application.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.memos.application.limits.RateLimiterPort;
import com.memos.application.notification.EmailMessage;
import com.memos.application.notification.EmailSenderPort;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.user.PasswordResetToken;
import com.memos.domain.user.PasswordResetTokenRepositoryPort;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * "Forgot password" flow: emails a single-use, short-lived link, then sets a new password when the
 * link is used.
 *
 * <p>Requests never reveal whether an account exists: the endpoint always answers the same way and
 * the work (lookup, token, email) runs asynchronously, so response time doesn't leak it either.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_LENGTH = 100;
    static final String INVALID_LINK =
            "This reset link is invalid or has expired. Request a new one from the sign-in page.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordResetTokenRepositoryPort tokenRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final TokenIssuerPort tokenIssuerPort;
    private final EmailSenderPort emailSenderPort;
    private final RateLimiterPort rateLimiterPort;

    /** Base URL of the web app; the emailed link points at {frontendUrl}/reset-password. */
    @Value("${memos.app.frontend-url:http://localhost:3000}")
    private String frontendUrl = "http://localhost:3000";

    /** Reset emails one address can trigger per hour, so nobody can flood someone's inbox. */
    @Value("${memos.mail.reset-emails-per-address-per-hour:3}")
    private int perAddressPerHour = 3;

    /** Reset emails sent per day across all users, kept under the email provider's free quota. */
    @Value("${memos.mail.reset-emails-per-day:250}")
    private int perDay = 250;

    /** Accepts the request and does the work in the background (see class comment). */
    @Async
    public void requestReset(String email) {
        try {
            sendResetLink(email);
        } catch (RuntimeException e) {
            log.error("Password reset request failed", e);
        }
    }

    void sendResetLink(String email) {
        String normalized = email == null ? "" : email.strip();
        Optional<User> user = userRepositoryPort.findByEmail(normalized);
        if (user.isEmpty() || !user.get().isEnabled()) {
            log.info("Password reset requested for an unknown or disabled account");
            return;
        }
        String addressKey = "password-reset:address:" + sha256Hex(normalized.toLowerCase(Locale.ROOT));
        if (!rateLimiterPort.tryAcquire(addressKey, perAddressPerHour, Duration.ofHours(1)).allowed()) {
            log.warn("Password reset emails for one address hit the hourly limit; not sending");
            return;
        }
        if (!rateLimiterPort.tryAcquire("password-reset:global", perDay, Duration.ofDays(1)).allowed()) {
            log.warn("Daily password reset email limit reached; not sending");
            return;
        }

        Instant now = Instant.now();
        // Only the newest link works.
        tokenRepositoryPort.invalidateUnusedForUser(user.get().getId(), now);
        String token = newToken();
        tokenRepositoryPort.save(PasswordResetToken.builder()
                .userId(user.get().getId())
                .tokenHash(sha256Hex(token))
                .expiresAt(now.plus(TOKEN_LIFETIME))
                .createdAt(now)
                .build());

        emailSenderPort.send(resetEmail(user.get(), resetLink(token)));
        log.info("Password reset email sent");
    }

    /** Sets a new password from a reset link and signs the user out of all existing sessions. */
    public void resetPassword(String token, String newPassword) {
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH || newPassword.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidRequestException(
                    "Password must be " + MIN_PASSWORD_LENGTH + " to " + MAX_PASSWORD_LENGTH + " characters.");
        }
        if (token == null || token.isBlank()) {
            throw new InvalidRequestException(INVALID_LINK);
        }

        Instant now = Instant.now();
        PasswordResetToken resetToken = tokenRepositoryPort.findByTokenHash(sha256Hex(token.strip()))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
        User user = userRepositoryPort.findById(resetToken.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new InvalidRequestException(INVALID_LINK));
        // Consume the token first; a concurrent request with the same link loses here.
        if (!tokenRepositoryPort.markUsed(resetToken.getId(), now)) {
            throw new InvalidRequestException(INVALID_LINK);
        }

        userRepositoryPort.save(user.toBuilder()
                .passwordHash(passwordHasherPort.hash(newPassword))
                .updatedAt(now)
                .build());
        tokenIssuerPort.revokeAllForUser(user.getId());
        log.info("Password reset completed for user {}", user.getId());
    }

    private String resetLink(String token) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/reset-password?token=" + token;
    }

    private static EmailMessage resetEmail(User user, String link) {
        String name = user.getFullName() == null || user.getFullName().isBlank() ? "there" : user.getFullName();
        String text = """
                Hi %s,

                We received a request to reset your MemOS password. Open this link to choose a new one:

                %s

                The link expires in %d minutes and can be used once. If you didn't ask for this, you can \
                ignore this email; your password won't change.
                """.formatted(name, link, TOKEN_LIFETIME.toMinutes());
        String html = """
                <div style="font-family:system-ui,-apple-system,Segoe UI,sans-serif;max-width:480px;margin:auto;color:#111">
                  <h2 style="margin:0 0 16px">Reset your MemOS password</h2>
                  <p>Hi %s,</p>
                  <p>We received a request to reset your password. Click the button to choose a new one.</p>
                  <p style="margin:24px 0">
                    <a href="%s" style="background:#111;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none;display:inline-block">Reset password</a>
                  </p>
                  <p style="color:#555;font-size:14px">The link expires in %d minutes and can be used once. If you didn't ask for this, you can ignore this email; your password won't change.</p>
                  <p style="color:#888;font-size:12px;word-break:break-all">If the button doesn't work, paste this into your browser:<br>%s</p>
                </div>
                """.formatted(escapeHtml(name), link, TOKEN_LIFETIME.toMinutes(), link);
        return new EmailMessage(user.getEmail(), name, "Reset your MemOS password", text, html);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
