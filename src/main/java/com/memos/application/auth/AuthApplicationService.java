package com.memos.application.auth;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.memos.application.limits.RateLimiterPort;
import com.memos.domain.exception.InvalidCredentialsException;
import com.memos.domain.exception.RateLimitExceededException;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

import lombok.RequiredArgsConstructor;

/** Sign-in and sessions. Creating accounts is {@link SignupService}. */
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final TokenIssuerPort tokenIssuerPort;
    private final RateLimiterPort rateLimiterPort;

    /** Sign-in attempts per email per 15 minutes, so passwords can't be guessed at speed. */
    @Value("${memos.auth.login-attempts-per-15-minutes:10}")
    private int loginAttemptsPer15Minutes = 10;

    /** Compared against when the email is unknown, so response time doesn't reveal which emails exist. */
    private volatile String dummyHash;

    public AuthResult login(LoginCommand command) {
        String email = command.email() == null ? "" : command.email().strip();
        RateLimiterPort.Result limit = rateLimiterPort.tryAcquire(
                "login:email:" + PasswordResetService.sha256Hex(email.toLowerCase(Locale.ROOT)),
                loginAttemptsPer15Minutes, Duration.ofMinutes(15));
        if (!limit.allowed()) {
            throw new RateLimitExceededException(
                    "Too many sign-in attempts for this account. Try again in a few minutes, or reset your password.",
                    limit.retryAfterSeconds());
        }

        Optional<User> user = userRepositoryPort.findByEmail(email);
        if (user.isEmpty()) {
            passwordHasherPort.matches(command.rawPassword(), dummyHash());
            throw new InvalidCredentialsException("Invalid email or password");
        }
        if (!passwordHasherPort.matches(command.rawPassword(), user.get().getPasswordHash()) || !user.get().isEnabled()) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return new AuthResult(user.get(), tokenIssuerPort.issueTokens(user.get()));
    }

    public TokenPair refresh(String refreshToken) {
        return tokenIssuerPort.refreshTokens(refreshToken);
    }

    public void logout(String refreshToken) {
        tokenIssuerPort.revokeRefreshToken(refreshToken);
    }

    /** Signs the user out on every device (all refresh tokens revoked). */
    public void logoutEverywhere(UUID userId) {
        tokenIssuerPort.revokeAllForUser(userId);
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordHasherPort.hash("memos-timing-equalizer");
        }
        return dummyHash;
    }
}
