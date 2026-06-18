package com.memos.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.memos.domain.ai.EmbeddingPort;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentChunk;
import com.memos.domain.document.DocumentChunkRepositoryPort;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.document.DocumentStatus;
import com.memos.domain.exception.IngestionException;
import com.memos.infrastructure.ingestion.parser.DocumentParserRegistry;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private DocumentRepositoryPort documentRepositoryPort;

    @Mock
    private DocumentChunkRepositoryPort documentChunkRepositoryPort;

    @Mock
    private DocumentParserRegistry parserRegistry;

    @Mock
    private ChunkingPort chunkingPort;

    @Mock
    private EmbeddingPort embeddingPort;

    @Mock
    private DocumentParserPort parserPort;

    private DocumentIngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new DocumentIngestionService(
                documentRepositoryPort, documentChunkRepositoryPort, parserRegistry, chunkingPort, embeddingPort);
    }

    private Document pendingDocument(UUID id) {
        Instant now = Instant.now();
        return Document.builder()
                .id(id)
                .ownerId(UUID.randomUUID())
                .title("Sample doc")
                .sourceType(DocumentSourceType.MARKDOWN)
                .sourceUri("/tmp/sample.md")
                .status(DocumentStatus.PENDING)
                .metadata(Map.of())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void submitCreatesPendingDocument() {
        when(documentRepositoryPort.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Document result = ingestionService.submit(UUID.randomUUID(), "Sample doc", DocumentSourceType.MARKDOWN, "/tmp/sample.md", Map.of());

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(result.getTitle()).isEqualTo("Sample doc");
        assertThat(result.getSourceType()).isEqualTo(DocumentSourceType.MARKDOWN);
    }

    @Test
    void ingestParsesChunksEmbedsAndCompletes() {
        UUID documentId = UUID.randomUUID();
        Document document = pendingDocument(documentId);

        when(documentRepositoryPort.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepositoryPort.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(parserRegistry.resolve(DocumentSourceType.MARKDOWN)).thenReturn(parserPort);

        ParsedDocument parsed = new ParsedDocument("Sample doc", "Some long content", Map.of());
        when(parserPort.parse(any(Document.class))).thenReturn(List.of(parsed));

        TextChunk chunk1 = new TextChunk("chunk one", 3, Map.of("chunkIndexInSource", 0));
        TextChunk chunk2 = new TextChunk("chunk two", 3, Map.of("chunkIndexInSource", 1));
        when(chunkingPort.chunk("Some long content")).thenReturn(List.of(chunk1, chunk2));

        float[] embedding1 = new float[] {0.1f, 0.2f};
        float[] embedding2 = new float[] {0.3f, 0.4f};
        when(embeddingPort.embedBatch(List.of("chunk one", "chunk two"))).thenReturn(List.of(embedding1, embedding2));

        when(documentChunkRepositoryPort.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        ingestionService.ingest(documentId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DocumentChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentChunkRepositoryPort).saveAll(chunksCaptor.capture());

        List<DocumentChunk> savedChunks = chunksCaptor.getValue();
        assertThat(savedChunks).hasSize(2);
        assertThat(savedChunks.get(0).getContent()).isEqualTo("chunk one");
        assertThat(savedChunks.get(0).getChunkIndex()).isEqualTo(0);
        assertThat(savedChunks.get(0).getEmbedding()).isEqualTo(embedding1);
        assertThat(savedChunks.get(0).getMetadata()).containsEntry("sourceLabel", "Sample doc");
        assertThat(savedChunks.get(1).getChunkIndex()).isEqualTo(1);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepositoryPort, org.mockito.Mockito.atLeastOnce()).save(documentCaptor.capture());
        Document finalState = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);
        assertThat(finalState.getStatus()).isEqualTo(DocumentStatus.COMPLETED);
    }

    @Test
    void ingestMarksDocumentFailedOnParserError() {
        UUID documentId = UUID.randomUUID();
        Document document = pendingDocument(documentId);

        when(documentRepositoryPort.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepositoryPort.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(parserRegistry.resolve(DocumentSourceType.MARKDOWN)).thenReturn(parserPort);
        when(parserPort.parse(any(Document.class))).thenThrow(new IngestionException("boom"));

        ingestionService.ingest(documentId);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepositoryPort, org.mockito.Mockito.atLeastOnce()).save(documentCaptor.capture());
        Document finalState = documentCaptor.getAllValues().get(documentCaptor.getAllValues().size() - 1);

        assertThat(finalState.getStatus()).isEqualTo(DocumentStatus.FAILED);
        assertThat(finalState.getErrorMessage()).contains("boom");
    }

    @Test
    void ingestSkipsMissingDocument() {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findById(documentId)).thenReturn(Optional.empty());

        ingestionService.ingest(documentId);

        verify(documentRepositoryPort, org.mockito.Mockito.never()).save(any());
    }
}
