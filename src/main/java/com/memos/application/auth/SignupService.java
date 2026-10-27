package com.memos.application.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sign-up with email verification: the first step emails a 6-digit code, and the account is only
 * created when that code is entered, so every account belongs to someone who controls its inbox.
 *
 * <p>Like password reset, the first step never reveals whether an account exists: it always
 * answers the same way and the work runs asynchronously. An address that already has an account
 * gets a "you already have an account" email instead of a code.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignupService {

    static final Duration CODE_LIFETIME = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 5;
    static final String INVALID_CODE = "This code is invalid or has expired. Request a new one.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepositoryPort userRepositoryPort;
    private final PendingSignupRepositoryPort pendingSignupRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final TokenIssuerPort tokenIssuerPort;
    private final EmailSenderPort emailSenderPort;
    private final RateLimiterPort rateLimiterPort;

    /** Off: nobody can create accounts (existing users can still sign in). */
    @Value("${memos.auth.registration-enabled:true}")
    private boolean registrationEnabled = true;

    @Value("${memos.app.frontend-url:http://localhost:3000}")
    private String frontendUrl = "http://localhost:3000";

    /** Sign-up emails one address can trigger per hour, so nobody can flood someone's inbox. */
    @Value("${memos.mail.signup-emails-per-address-per-hour:3}")
    private int perAddressPerHour = 3;

    /** All emails sent per day (shared with password reset), kept under the provider's free quota. */
    @Value("${memos.mail.emails-per-day:250}")
    private int perDay = 250;

    public boolean registrationOpen() {
        return registrationEnabled;
    }

    /** Fails fast when registration is closed; the rest happens in {@link #sendCode}. */
    public void requireRegistrationOpen() {
        if (!registrationEnabled) {
            throw new AccessDeniedException("Registration is closed on this server.");
        }
    }

    /** Accepts the request and does the work in the background (see class comment). */
    @Async
    public void startSignup(RegisterCommand command) {
        try {
            sendCode(command);
        } catch (RuntimeException e) {
            log.error("Sign-up request failed", e);
        }
    }

    void sendCode(RegisterCommand command) {
        String email = command.email().strip();
        String emailKey = email.toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        pendingSignupRepositoryPort.deleteExpired(now);

        if (!rateLimiterPort.tryAcquire("signup:address:" + PasswordResetService.sha256Hex(emailKey),
                perAddressPerHour, Duration.ofHours(1)).allowed()) {
            log.warn("Sign-up emails for one address hit the hourly limit; not sending");
            return;
        }
        if (!rateLimiterPort.tryAcquire("email:global", perDay, Duration.ofDays(1)).allowed()) {
            log.warn("Daily email limit reached; not sending sign-up email");
            return;
        }

        Optional<User> existing = userRepositoryPort.findByEmail(email);
        if (existing.isPresent()) {
            emailSenderPort.send(alreadyRegisteredEmail(existing.get()));
            log.info("Sign-up requested for an existing account; sent a notice instead of a code");
            return;
        }

        String code = newCode();
        pendingSignupRepositoryPort.replace(PendingSignup.builder()
                .email(email)
                .emailKey(emailKey)
                .fullName(command.fullName().strip())
                .passwordHash(passwordHasherPort.hash(command.rawPassword()))
                .codeHash(PasswordResetService.sha256Hex(code))
                .attempts(0)
                .expiresAt(now.plus(CODE_LIFETIME))
                .createdAt(now)
                .build());
        emailSenderPort.send(codeEmail(email, command.fullName().strip(), code));
        log.info("Sign-up verification code sent");
    }

    /** Creates the account if the code matches, and signs the new user in. */
    public AuthResult verifySignup(String email, String code) {
        requireRegistrationOpen();
        String emailKey = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        PendingSignup signup = pendingSignupRepositoryPort.findByEmailKey(emailKey)
                .filter(s -> s.isUsable(now, MAX_ATTEMPTS))
                .orElseThrow(() -> new InvalidRequestException(INVALID_CODE));

        String candidate = code == null ? "" : code.replaceAll("\\s", "");
        if (!MessageDigest.isEqual(
                signup.getCodeHash().getBytes(StandardCharsets.UTF_8),
                PasswordResetService.sha256Hex(candidate).getBytes(StandardCharsets.UTF_8))) {
            pendingSignupRepositoryPort.recordFailedAttempt(signup.getId());
            int left = MAX_ATTEMPTS - signup.getAttempts() - 1;
            throw new InvalidRequestException(left > 0
                    ? "That code isn't right. " + left + (left == 1 ? " try" : " tries") + " left."
                    : INVALID_CODE);
        }

        // Consume the sign-up first; a concurrent request with the same code loses here.
        if (!pendingSignupRepositoryPort.delete(signup.getId())
                || userRepositoryPort.existsByEmail(signup.getEmail())) {
            throw new InvalidRequestException(INVALID_CODE);
        }

        User user = userRepositoryPort.save(User.builder()
                .email(signup.getEmail())
                .passwordHash(signup.getPasswordHash())
                .fullName(signup.getFullName())
                .role(Role.USER)
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        log.info("Account created after email verification: {}", user.getId());
        return new AuthResult(user, tokenIssuerPort.issueTokens(user));
    }

    private static String newCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    private static EmailMessage codeEmail(String email, String fullName, String code) {
        String name = fullName.isBlank() ? "there" : fullName;
        String text = """
                Hi %s,

                Your MemOS verification code is: %s

                Enter it on the sign-up page to create your account. The code expires in %d minutes. \
                If you didn't try to sign up, you can ignore this email.
                """.formatted(name, code, CODE_LIFETIME.toMinutes());
        String html = """
                <div style="font-family:system-ui,-apple-system,Segoe UI,sans-serif;max-width:480px;margin:auto;color:#111">
                  <h2 style="margin:0 0 16px">Verify your email</h2>
                  <p>Hi %s,</p>
                  <p>Enter this code on the sign-up page to create your MemOS account:</p>
                  <p style="margin:24px 0;font-size:32px;font-weight:700;letter-spacing:8px;font-family:ui-monospace,Menlo,monospace">%s</p>
                  <p style="color:#555;font-size:14px">The code expires in %d minutes. If you didn't try to sign up, you can ignore this email.</p>
                </div>
                """.formatted(escapeHtml(name), code, CODE_LIFETIME.toMinutes());
        return new EmailMessage(email, name, code + " is your MemOS verification code", text, html);
    }

    private EmailMessage alreadyRegisteredEmail(User user) {
        String name = user.getFullName() == null || user.getFullName().isBlank() ? "there" : user.getFullName();
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        String text = """
                Hi %s,

                Someone (hopefully you) tried to create a MemOS account with this email, but you already have one.

                Sign in: %s/login
                Forgot your password? %s/forgot-password

                If this wasn't you, you can ignore this email.
                """.formatted(name, base, base);
        String html = """
                <div style="font-family:system-ui,-apple-system,Segoe UI,sans-serif;max-width:480px;margin:auto;color:#111">
                  <h2 style="margin:0 0 16px">You already have an account</h2>
                  <p>Hi %s,</p>
                  <p>Someone (hopefully you) tried to create a MemOS account with this email, but you already have one.</p>
                  <p style="margin:24px 0">
                    <a href="%s/login" style="background:#111;color:#fff;padding:12px 20px;border-radius:8px;text-decoration:none;display:inline-block">Sign in</a>
                  </p>
                  <p style="color:#555;font-size:14px">Forgot your password? <a href="%s/forgot-password">Reset it here</a>. If this wasn't you, you can ignore this email.</p>
                </div>
                """.formatted(escapeHtml(name), base, base);
        return new EmailMessage(user.getEmail(), name, "You already have a MemOS account", text, html);
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
