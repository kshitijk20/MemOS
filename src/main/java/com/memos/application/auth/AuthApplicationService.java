package com.memos.application.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.memos.domain.exception.AccessDeniedException;
import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.exception.InvalidCredentialsException;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final TokenIssuerPort tokenIssuerPort;

    /** Off: nobody can create accounts (existing users can still sign in). */
    @Value("${memos.auth.registration-enabled:true}")
    private boolean registrationEnabled = true;

    /** When set, new accounts need this code, so only people you share it with can sign up. */
    @Value("${memos.auth.invite-code:}")
    private String inviteCode = "";

    /** How sign-up works on this server, for the registration page. */
    public record RegistrationPolicy(boolean open, boolean inviteCodeRequired) {}

    public RegistrationPolicy registrationPolicy() {
        return new RegistrationPolicy(registrationEnabled, registrationEnabled && hasInviteCode());
    }

    public User register(RegisterCommand command) {
        if (!registrationEnabled) {
            throw new AccessDeniedException("Registration is closed on this server.");
        }
        if (hasInviteCode() && !inviteCodeMatches(command.inviteCode())) {
            throw new AccessDeniedException("That invite code isn't valid.");
        }
        if (userRepositoryPort.existsByEmail(command.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        User user = User.builder()
                .email(command.email())
                .passwordHash(passwordHasherPort.hash(command.rawPassword()))
                .fullName(command.fullName())
                .role(Role.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        return userRepositoryPort.save(user);
    }

    private boolean hasInviteCode() {
        return inviteCode != null && !inviteCode.isBlank();
    }

    /** Constant-time comparison, so the code can't be guessed one character at a time. */
    private boolean inviteCodeMatches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                inviteCode.strip().getBytes(StandardCharsets.UTF_8),
                candidate.strip().getBytes(StandardCharsets.UTF_8));
    }

    public AuthResult login(LoginCommand command) {
        User user = userRepositoryPort.findByEmail(command.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (!user.isEnabled() || !passwordHasherPort.matches(command.rawPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return new AuthResult(user, tokenIssuerPort.issueTokens(user));
    }

    public TokenPair refresh(String refreshToken) {
        return tokenIssuerPort.refreshTokens(refreshToken);
    }

    public void logout(String refreshToken) {
        tokenIssuerPort.revokeRefreshToken(refreshToken);
    }
}
