package com.memos.application.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memos.domain.collection.Collection;
import com.memos.domain.collection.CollectionRepositoryPort;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.exception.DuplicateResourceException;
import com.memos.domain.exception.InvalidRequestException;
import com.memos.domain.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class CollectionApplicationServiceTest {

    @Mock
    private CollectionRepositoryPort collectionRepositoryPort;

    @Mock
    private DocumentRepositoryPort documentRepositoryPort;

    @InjectMocks
    private CollectionApplicationService service;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createNormalizesNameAndKeepsOwnedDocuments() {
        UUID documentId = UUID.randomUUID();
        when(collectionRepositoryPort.existsByOwnerIdAndNameIgnoreCase(userId, "HR policies")).thenReturn(false);
        when(documentRepositoryPort.existsByIdAndOwnerId(documentId, userId)).thenReturn(true);
        when(collectionRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Collection result = service.create(userId, "  HR   policies ", List.of(documentId, documentId));

        assertThat(result.getName()).isEqualTo("HR policies");
        assertThat(result.getDocumentIds()).containsExactly(documentId);
    }

    @Test
    void createRejectsDuplicateNamesCaseInsensitively() {
        when(collectionRepositoryPort.existsByOwnerIdAndNameIgnoreCase(userId, "HR")).thenReturn(true);

        assertThatThrownBy(() -> service.create(userId, "HR", List.of()))
                .isInstanceOf(DuplicateResourceException.class);
        verify(collectionRepositoryPort, never()).save(any());
    }

    @Test
    void createRejectsBlankNames() {
        assertThatThrownBy(() -> service.create(userId, "   ", List.of()))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void createRejectsDocumentsTheUserDoesNotOwn() {
        UUID foreign = UUID.randomUUID();
        when(collectionRepositoryPort.existsByOwnerIdAndNameIgnoreCase(userId, "HR")).thenReturn(false);
        when(documentRepositoryPort.existsByIdAndOwnerId(foreign, userId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(userId, "HR", List.of(foreign)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCanChangeCaseOfItsOwnNameAndLeavesDocumentsWhenNull() {
        UUID id = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Collection existing = Collection.builder()
                .id(id).ownerId(userId).name("hr").documentIds(Set.of(documentId)).build();
        when(collectionRepositoryPort.findByIdAndOwnerId(id, userId)).thenReturn(Optional.of(existing));
        when(collectionRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Collection result = service.update(userId, id, "HR", null);

        assertThat(result.getName()).isEqualTo("HR");
        assertThat(result.getDocumentIds()).containsExactly(documentId);
        verify(collectionRepositoryPort, never()).existsByOwnerIdAndNameIgnoreCase(any(), any());
    }
}
