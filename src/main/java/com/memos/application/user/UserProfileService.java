package com.memos.application.user;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.memos.application.auth.PasswordHasherPort;
import com.memos.application.auth.TokenIssuerPort;
import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final TokenIssuerPort tokenIssuerPort;

    public User getProfile(UUID userId) {
        return userRepositoryPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    public User updateProfile(UUID userId, UpdateProfileCommand command) {
        User current = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        boolean emailChanged = !current.getEmail().equalsIgnoreCase(command.email());
        boolean passwordChanged = command.newPassword() != null && !command.newPassword().isBlank();

        // A stolen session alone must not be enough to take over the account.
        if ((emailChanged || passwordChanged)
                && (command.currentPassword() == null
                        || !passwordHasherPort.matches(command.currentPassword(), current.getPasswordHash()))) {
            throw new InvalidRequestException("Your current password is incorrect.");
        }
        if (emailChanged && userRepositoryPort.existsByEmail(command.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        String newPasswordHash = current.getPasswordHash();
        if (passwordChanged) {
            newPasswordHash = passwordHasherPort.hash(command.newPassword());
        }

        User updated = current.toBuilder()
                .fullName(command.fullName())
                .email(command.email())
                .passwordHash(newPasswordHash)
                .updatedAt(Instant.now())
                .build();

        User saved = userRepositoryPort.save(updated);
        if (passwordChanged) {
            // Sign out every other device; the web app signs in again with the new password.
            tokenIssuerPort.revokeAllForUser(userId);
        }
        return saved;
    }
}
