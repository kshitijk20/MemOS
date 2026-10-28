package com.memos.application.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memos.application.auth.PasswordHasherPort;
import com.memos.application.ingestion.DocumentIngestionService;
import com.memos.domain.common.PageResult;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.domain.user.UserRepositoryPort;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock private UserRepositoryPort userRepositoryPort;
    @Mock private PasswordHasherPort passwordHasherPort;
    @Mock private DocumentRepositoryPort documentRepositoryPort;
    @Mock private DocumentIngestionService documentIngestionService;

    @InjectMocks private AccountDeletionService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(userId).email("alice@example.com").fullName("Alice").passwordHash("hash")
                .role(Role.USER).enabled(true).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        when(userRepositoryPort.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    void deletesEveryDocumentThenTheUser() {
        Document first = Document.builder().id(UUID.randomUUID()).build();
        Document second = Document.builder().id(UUID.randomUUID()).build();
        when(passwordHasherPort.matches("right", "hash")).thenReturn(true);
        when(documentRepositoryPort.findAllByOwnerId(eq(userId), any()))
                .thenReturn(new PageResult<>(List.of(first, second), 2, 0, 100))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 100));

        service.deleteAccount(userId, "right");

        verify(documentIngestionService).delete(first);
        verify(documentIngestionService).delete(second);
        verify(userRepositoryPort).deleteById(userId);
    }

    @Test
    void wrongPasswordDeletesNothing() {
        assertThatThrownBy(() -> service.deleteAccount(userId, "wrong"))
                .isInstanceOf(InvalidRequestException.class);

        verify(documentIngestionService, never()).delete(any());
        verify(userRepositoryPort, never()).deleteById(any());
    }
}
