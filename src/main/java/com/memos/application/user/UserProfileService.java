package com.memos.application.user;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.memos.application.auth.PasswordHasherPort;
import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;

    public User getProfile(UUID userId) {
        return userRepositoryPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    public User updateProfile(UUID userId, UpdateProfileCommand command) {
        User current = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!current.getEmail().equalsIgnoreCase(command.email())
                && userRepositoryPort.existsByEmail(command.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        String newPasswordHash = current.getPasswordHash();
        if (command.newPassword() != null && !command.newPassword().isBlank()) {
            newPasswordHash = passwordHasherPort.hash(command.newPassword());
        }

        User updated = current.toBuilder()
                .fullName(command.fullName())
                .email(command.email())
                .passwordHash(newPasswordHash)
                .updatedAt(Instant.now())
                .build();

        return userRepositoryPort.save(updated);
    }
}
