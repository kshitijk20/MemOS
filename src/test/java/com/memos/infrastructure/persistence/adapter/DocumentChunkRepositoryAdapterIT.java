package com.memos.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.memos.application.rag.RetrievalPort;
import com.memos.application.rag.RetrievedChunk;
import com.memos.domain.common.PageRequest;
import com.memos.domain.document.DocumentChunk;
import com.memos.domain.document.DocumentChunkRepositoryPort;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.document.DocumentStatus;
import com.memos.domain.user.Role;
import com.memos.infrastructure.persistence.entity.DocumentEntity;
import com.memos.infrastructure.persistence.entity.UserEntity;
import com.memos.infrastructure.persistence.repository.DocumentJpaRepository;
import com.memos.infrastructure.persistence.repository.UserJpaRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DocumentChunkRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DocumentChunkRepositoryPort documentChunkRepositoryPort;

    @Autowired
    private RetrievalPort retrievalPort;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DocumentJpaRepository documentJpaRepository;

    private static final int DIMENSIONS = 768;

    @Test
    void findSimilarOrdersChunksByCosineSimilarityWithinOwnersDocuments() {
        UUID ownerId = createUser("owner@example.com");
        UUID otherOwnerId = createUser("other@example.com");

        UUID documentId = createDocument(ownerId, DocumentStatus.COMPLETED);
        UUID otherDocumentId = createDocument(otherOwnerId, DocumentStatus.COMPLETED);

        DocumentChunk matching = chunk(documentId, 0, "matching chunk", unitVector(0));
        DocumentChunk orthogonal = chunk(documentId, 1, "orthogonal chunk", unitVector(1));
        DocumentChunk opposite = chunk(documentId, 2, "opposite chunk", negate(unitVector(0)));
        DocumentChunk otherOwnersChunk = chunk(otherDocumentId, 0, "other owner's chunk", unitVector(0));

        documentChunkRepositoryPort.saveAll(List.of(matching, orthogonal, opposite, otherOwnersChunk));

        List<RetrievedChunk> results = retrievalPort.findSimilar(ownerId, unitVector(0), 10);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).content()).isEqualTo("matching chunk");
        assertThat(results.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-3));
        assertThat(results.get(1).content()).isEqualTo("orthogonal chunk");
        assertThat(results.get(2).content()).isEqualTo("opposite chunk");
        assertThat(results).extracting(RetrievedChunk::content).doesNotContain("other owner's chunk");
    }

    @Test
    void findSimilarExcludesChunksFromIncompleteDocuments() {
        UUID ownerId = createUser("pending-owner@example.com");
        UUID documentId = createDocument(ownerId, DocumentStatus.PROCESSING);

        documentChunkRepositoryPort.saveAll(List.of(chunk(documentId, 0, "not yet ready", unitVector(0))));

        List<RetrievedChunk> results = retrievalPort.findSimilar(ownerId, unitVector(0), 10);

        assertThat(results).isEmpty();
    }

    @Test
    void findByDocumentIdPaginatesChunksAndSupportsCountAndDelete() {
        UUID ownerId = createUser("paginate-owner@example.com");
        UUID documentId = createDocument(ownerId, DocumentStatus.COMPLETED);

        documentChunkRepositoryPort.saveAll(List.of(
                chunk(documentId, 0, "chunk zero", unitVector(0)),
                chunk(documentId, 1, "chunk one", unitVector(1)),
                chunk(documentId, 2, "chunk two", unitVector(2))));

        assertThat(documentChunkRepositoryPort.countByDocumentId(documentId)).isEqualTo(3);

        var firstPage = documentChunkRepositoryPort.findByDocumentId(documentId, PageRequest.of(0, 2));
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        documentChunkRepositoryPort.deleteByDocumentId(documentId);
        assertThat(documentChunkRepositoryPort.countByDocumentId(documentId)).isZero();
    }

    @Test
    void replaceForDocumentSwapsChunksAndKeepsOldOnesWhenSaveFails() {
        UUID ownerId = createUser("replace-owner@example.com");
        UUID documentId = createDocument(ownerId, DocumentStatus.COMPLETED);
        documentChunkRepositoryPort.saveAll(List.of(
                chunk(documentId, 0, "old zero", unitVector(0)),
                chunk(documentId, 1, "old one", unitVector(1))));

        documentChunkRepositoryPort.replaceForDocument(documentId, List.of(chunk(documentId, 0, "new zero", unitVector(2))));

        var chunks = documentChunkRepositoryPort.findByDocumentId(documentId, PageRequest.of(0, 10)).items();
        assertThat(chunks).extracting(DocumentChunk::getContent).containsExactly("new zero");

        // The second chunk's vector doesn't fit the vector(768) column, so the save fails after
        // the delete has already run inside the same transaction.
        assertThatThrownBy(() -> documentChunkRepositoryPort.replaceForDocument(documentId, List.of(
                chunk(documentId, 0, "broken a", unitVector(3)),
                chunk(documentId, 1, "broken b", new float[3]))))
                .isInstanceOf(RuntimeException.class);

        chunks = documentChunkRepositoryPort.findByDocumentId(documentId, PageRequest.of(0, 10)).items();
        assertThat(chunks).extracting(DocumentChunk::getContent).containsExactly("new zero");
    }

    @Test
    void findHybridRanksKeywordMatchesForErrorCodes() {
        UUID ownerId = createUser("hybrid-owner@example.com");
        UUID documentId = createDocument(ownerId, DocumentStatus.COMPLETED);

        documentChunkRepositoryPort.saveAll(List.of(
                chunk(documentId, 0, "matching semantic text", unitVector(0)),
                chunk(documentId, 1, "the socket failed with ECONNRESET during handshake", unitVector(5))));

        List<RetrievedChunk> results = retrievalPort.findHybrid(
                ownerId, "ECONNRESET during handshake", unitVector(0), List.of(), 10);

        assertThat(results).extracting(RetrievedChunk::content)
                .anyMatch(content -> content.contains("ECONNRESET"));
        assertThat(results.stream().filter(RetrievedChunk::lexicalHit))
                .anyMatch(chunk -> chunk.content().contains("ECONNRESET"));
    }

    @Test
    void findByIdAndDocumentIdReturnsOwnedChunk() {
        UUID ownerId = createUser("chunk-get-owner@example.com");
        UUID documentId = createDocument(ownerId, DocumentStatus.COMPLETED);
        DocumentChunk saved = documentChunkRepositoryPort.save(chunk(documentId, 0, "look me up", unitVector(0)));

        assertThat(documentChunkRepositoryPort.findByIdAndDocumentId(saved.getId(), documentId))
                .isPresent()
                .get()
                .extracting(DocumentChunk::getContent)
                .isEqualTo("look me up");
        assertThat(documentChunkRepositoryPort.findByIdAndDocumentId(saved.getId(), UUID.randomUUID())).isEmpty();
    }

    private UUID createUser(String email) {
        Instant now = Instant.now();
        UserEntity user = userJpaRepository.save(UserEntity.builder()
                .email(email)
                .passwordHash("hashed")
                .fullName("Test User")
                .role(Role.USER)
                .enabled(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return user.getId();
    }

    private UUID createDocument(UUID ownerId, DocumentStatus status) {
        Instant now = Instant.now();
        DocumentEntity document = documentJpaRepository.save(DocumentEntity.builder()
                .ownerId(ownerId)
                .title("Test document")
                .sourceType(DocumentSourceType.MARKDOWN)
                .sourceUri("/tmp/test.md")
                .status(status)
                .metadata(Map.of())
                .createdAt(now)
                .updatedAt(now)
                .build());
        return document.getId();
    }

    private DocumentChunk chunk(UUID documentId, int chunkIndex, String content, float[] embedding) {
        return DocumentChunk.builder()
                .documentId(documentId)
                .chunkIndex(chunkIndex)
                .content(content)
                .embedding(embedding)
                .tokenCount(content.split("\\s+").length)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .build();
    }

    private float[] unitVector(int dimensionIndex) {
        float[] vector = new float[DIMENSIONS];
        vector[dimensionIndex] = 1.0f;
        return vector;
    }

    private float[] negate(float[] vector) {
        float[] negated = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            negated[i] = -vector[i];
        }
        return negated;
    }
}
