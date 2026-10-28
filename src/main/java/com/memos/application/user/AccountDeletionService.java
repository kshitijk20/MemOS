package com.memos.application.user;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.memos.application.auth.PasswordHasherPort;
import com.memos.application.ingestion.DocumentIngestionService;
import com.memos.domain.common.PageRequest;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Permanently deletes an account and everything in it. The database removes the user's
 * documents, chunks, conversations, messages, collections and sessions along with the user
 * (ON DELETE CASCADE); documents are deleted first, one by one, so uploads still waiting on disk
 * are removed too.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private static final int BATCH_SIZE = 100;

    private final UserRepositoryPort userRepositoryPort;
    private final PasswordHasherPort passwordHasherPort;
    private final DocumentRepositoryPort documentRepositoryPort;
    private final DocumentIngestionService documentIngestionService;

    public void deleteAccount(UUID userId, String password) {
        User user = userRepositoryPort.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        // A stolen session alone must not be enough to destroy the account.
        if (password == null || !passwordHasherPort.matches(password, user.getPasswordHash())) {
            throw new InvalidRequestException("Your password is incorrect.");
        }

        List<Document> batch;
        do {
            batch = documentRepositoryPort.findAllByOwnerId(userId, new PageRequest(0, BATCH_SIZE)).items();
            batch.forEach(documentIngestionService::delete);
        } while (!batch.isEmpty());

        userRepositoryPort.deleteById(userId);
        log.info("Account {} deleted at the user's request", userId);
    }
}
