package com.memos.application.auth;

import java.time.Instant;

import org.springframework.stereotype.Service;

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

    public User register(RegisterCommand command) {
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
