package com.memos.web.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.memos.application.ingestion.DocumentIngestionService;
import com.memos.application.ingestion.IngestionProgressStore;
import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentChunk;
import com.memos.domain.document.DocumentChunkRepositoryPort;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.document.DocumentStatus;
import com.memos.domain.user.Role;
import com.memos.domain.user.User;
import com.memos.infrastructure.security.CustomUserPrincipal;
import com.memos.infrastructure.security.JwtService;
import com.memos.infrastructure.security.UserDetailsServiceImpl;

@WebMvcTest(value = DocumentController.class,
        excludeAutoConfiguration = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentIngestionService documentIngestionService;

    @MockitoBean
    private DocumentRepositoryPort documentRepositoryPort;

    @MockitoBean
    private DocumentChunkRepositoryPort documentChunkRepositoryPort;

    @MockitoBean
    private IngestionProgressStore progressStore;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsServiceImpl;

    private final UUID userId = UUID.randomUUID();

    private org.springframework.test.web.servlet.request.RequestPostProcessor asUser() {
        User user = User.builder()
                .id(userId)
                .email("alice@example.com")
                .passwordHash("hashed")
                .fullName("Alice")
                .role(Role.USER)
                .enabled(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        CustomUserPrincipal principal = new CustomUserPrincipal(user);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private Document document(UUID id, DocumentStatus status) {
        Instant now = Instant.now();
        return Document.builder()
                .id(id)
                .ownerId(userId)
                .title("Sample doc")
                .sourceType(DocumentSourceType.MARKDOWN)
                .sourceUri("/tmp/sample.md")
                .status(status)
                .metadata(Map.of())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    void uploadAcceptsMarkdownFileAndStartsIngestion() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document pending = document(documentId, DocumentStatus.PENDING);

        when(documentIngestionService.submit(eq(userId), eq("My Notes"), eq(DocumentSourceType.MARKDOWN), any(), any(), any()))
                .thenReturn(pending);

        MockMultipartFile file = new MockMultipartFile("file", "notes.md", "text/markdown", "# Hello".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("title", "My Notes")
                        .with(asUser())
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void uploadRejectsUnsupportedFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "data.bin", "application/octet-stream", "binary".getBytes());

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("title", "Binary")
                        .with(asUser())
                        .with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ingestGitRepositoryStartsIngestion() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document pending = document(documentId, DocumentStatus.PENDING).toBuilder()
                .sourceType(DocumentSourceType.GIT_REPOSITORY)
                .sourceUri("https://github.com/example/repo.git")
                .build();

        when(documentIngestionService.submit(eq(userId), eq("Example Repo"), eq(DocumentSourceType.GIT_REPOSITORY), any(), any(), any()))
                .thenReturn(pending);

        mockMvc.perform(post("/api/v1/documents/git")
                        .with(asUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"repoUrl":"https://github.com/example/repo.git","title":"Example Repo"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sourceType").value("GIT_REPOSITORY"));
    }

    @Test
    void listReturnsPagedDocuments() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document doc = document(documentId, DocumentStatus.COMPLETED);

        when(documentRepositoryPort.findAllByOwnerId(eq(userId), any(PageRequest.class)))
                .thenReturn(new PageResult<>(List.of(doc), 1, 0, 20));

        mockMvc.perform(get("/api/v1/documents").with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(documentId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getReturnsDocumentForOwner() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document doc = document(documentId, DocumentStatus.COMPLETED);

        when(documentRepositoryPort.findByIdAndOwnerId(documentId, userId)).thenReturn(Optional.of(doc));

        mockMvc.perform(get("/api/v1/documents/{id}", documentId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(documentId.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getReturnsNotFoundForMissingDocument() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findByIdAndOwnerId(documentId, userId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/documents/{id}", documentId).with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void chunksReturnsPagedChunksForOwnedDocument() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();

        when(documentRepositoryPort.existsByIdAndOwnerId(documentId, userId)).thenReturn(true);

        DocumentChunk chunk = DocumentChunk.builder()
                .id(chunkId)
                .documentId(documentId)
                .chunkIndex(0)
                .content("chunk content")
                .embedding(new float[] {0.1f, 0.2f})
                .tokenCount(5)
                .metadata(Map.of())
                .createdAt(Instant.now())
                .build();

        when(documentChunkRepositoryPort.findByDocumentId(eq(documentId), any(PageRequest.class)))
                .thenReturn(new PageResult<>(List.of(chunk), 1, 0, 20));

        mockMvc.perform(get("/api/v1/documents/{id}/chunks", documentId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(chunkId.toString()))
                .andExpect(jsonPath("$.items[0].content").value("chunk content"));
    }

    @Test
    void getChunkReturnsSourceForOwnedDocument() throws Exception {
        UUID documentId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        Document doc = document(documentId, DocumentStatus.COMPLETED).toBuilder()
                .sourceType(DocumentSourceType.WEB_PAGE)
                .sourceUri("https://example.com/page")
                .title("Example page")
                .build();
        DocumentChunk chunk = DocumentChunk.builder()
                .id(chunkId)
                .documentId(documentId)
                .chunkIndex(2)
                .content("full chunk text")
                .embedding(new float[] {0.1f})
                .tokenCount(3)
                .metadata(Map.of("path", "README.md"))
                .createdAt(Instant.now())
                .build();

        when(documentRepositoryPort.findByIdAndOwnerId(documentId, userId)).thenReturn(Optional.of(doc));
        when(documentChunkRepositoryPort.findByIdAndDocumentId(chunkId, documentId)).thenReturn(Optional.of(chunk));

        mockMvc.perform(get("/api/v1/documents/{id}/chunks/{chunkId}", documentId, chunkId).with(asUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("full chunk text"))
                .andExpect(jsonPath("$.chunkIndex").value(2))
                .andExpect(jsonPath("$.documentTitle").value("Example page"))
                .andExpect(jsonPath("$.sourceUri").value("https://example.com/page"))
                .andExpect(jsonPath("$.sourceType").value("WEB_PAGE"));
    }

    @Test
    void chunksReturnsNotFoundWhenDocumentNotOwned() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.existsByIdAndOwnerId(documentId, userId)).thenReturn(false);

        mockMvc.perform(get("/api/v1/documents/{id}/chunks", documentId).with(asUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ingestUrlStartsIngestion() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document pending = document(documentId, DocumentStatus.PENDING).toBuilder()
                .sourceType(DocumentSourceType.WEB_PAGE)
                .sourceUri("https://example.com/page")
                .build();

        when(documentIngestionService.submit(eq(userId), eq("Example"), eq(DocumentSourceType.WEB_PAGE), any(), any(), any()))
                .thenReturn(pending);

        mockMvc.perform(post("/api/v1/documents/url")
                        .with(asUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url":"https://example.com/page","title":"Example"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sourceType").value("WEB_PAGE"));
    }

    @Test
    void retryFailedDocument() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document failed = document(documentId, DocumentStatus.FAILED);
        Document pending = document(documentId, DocumentStatus.PENDING);

        when(documentRepositoryPort.findByIdAndOwnerId(documentId, userId)).thenReturn(Optional.of(failed));
        when(documentIngestionService.retry(failed)).thenReturn(pending);

        mockMvc.perform(post("/api/v1/documents/{id}/retry", documentId).with(asUser()).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(documentIngestionService).ingest(documentId);
    }

    @Test
    void reingestCompletedUrlDocument() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document completed = document(documentId, DocumentStatus.COMPLETED).toBuilder()
                .sourceType(DocumentSourceType.WEB_PAGE)
                .sourceUri("https://example.com/page")
                .version(1)
                .build();
        Document pending = completed.toBuilder().status(DocumentStatus.PENDING).version(2).build();

        when(documentRepositoryPort.findByIdAndOwnerId(documentId, userId)).thenReturn(Optional.of(completed));
        when(documentIngestionService.reingest(completed)).thenReturn(pending);

        mockMvc.perform(post("/api/v1/documents/{id}/reingest", documentId).with(asUser()).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.version").value(2));

        verify(documentIngestionService).ingest(documentId);
    }

    @Test
    void deleteRemovesOwnedDocument() throws Exception {
        UUID documentId = UUID.randomUUID();
        Document doc = document(documentId, DocumentStatus.COMPLETED);
        when(documentRepositoryPort.findByIdAndOwnerId(documentId, userId)).thenReturn(Optional.of(doc));

        mockMvc.perform(delete("/api/v1/documents/{id}", documentId).with(asUser()).with(csrf()))
                .andExpect(status().isNoContent());

        verify(documentIngestionService).delete(doc);
    }

    @Test
    void deleteReturnsNotFoundWhenDocumentNotOwned() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(documentRepositoryPort.findByIdAndOwnerId(documentId, userId)).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/documents/{id}", documentId).with(asUser()).with(csrf()))
                .andExpect(status().isNotFound());

        verify(documentIngestionService, never()).delete(any());
    }
}
