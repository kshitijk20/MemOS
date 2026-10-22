package com.memos.web.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.memos.application.ingestion.DocumentIngestionService;
import com.memos.application.ingestion.IngestionProgressStore;
import com.memos.application.ingestion.SourceFingerprint;
import com.memos.domain.common.PageRequest;
import com.memos.domain.common.PageResult;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentChunkRepositoryPort;
import com.memos.domain.document.DocumentRepositoryPort;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.exception.IngestionException;
import com.memos.domain.exception.ResourceNotFoundException;
import com.memos.infrastructure.security.CustomUserPrincipal;
import com.memos.web.common.PageResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Documents")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;
    private final DocumentRepositoryPort documentRepositoryPort;
    private final DocumentChunkRepositoryPort documentChunkRepositoryPort;
    private final IngestionProgressStore progressStore;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> upload(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam("title") String title) {

        DocumentSourceType sourceType = resolveSourceType(file);
        StoredUpload stored = storeTempFile(file, sourceType);

        Document document = documentIngestionService.submit(
                principal.getId(),
                title,
                sourceType,
                stored.path().toString(),
                stored.fingerprint(),
                Map.of("originalFilename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename()));
        documentIngestionService.ingest(document.getId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentResponse.from(document));
    }

    @PostMapping("/url")
    public ResponseEntity<DocumentResponse> ingestUrl(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody UrlIngestRequest request) {

        Document document = documentIngestionService.submit(
                principal.getId(),
                request.title(),
                DocumentSourceType.WEB_PAGE,
                request.url(),
                SourceFingerprint.forWebPage(request.url()),
                Map.of("url", request.url()));
        documentIngestionService.ingest(document.getId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentResponse.from(document));
    }

    @PostMapping("/git")
    public ResponseEntity<DocumentResponse> ingestGitRepository(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody GitIngestRequest request) {

        Map<String, Object> metadata = new HashMap<>();
        if (request.branch() != null && !request.branch().isBlank()) {
            metadata.put("branch", request.branch());
        }

        Document document = documentIngestionService.submit(
                principal.getId(),
                request.title(),
                DocumentSourceType.GIT_REPOSITORY,
                request.repoUrl(),
                SourceFingerprint.forGit(request.repoUrl(), request.branch()),
                metadata);
        documentIngestionService.ingest(document.getId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentResponse.from(document));
    }

    @GetMapping
    public ResponseEntity<PageResponse<DocumentResponse>> list(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResult<Document> result = documentRepositoryPort.findAllByOwnerId(principal.getId(), PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.of(result, DocumentResponse::from));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> get(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {

        Document document = documentRepositoryPort.findByIdAndOwnerId(id, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        return ResponseEntity.ok(DocumentResponse.from(document));
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<PageResponse<DocumentChunkResponse>> chunks(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!documentRepositoryPort.existsByIdAndOwnerId(id, principal.getId())) {
            throw new ResourceNotFoundException("Document", id);
        }

        PageResult<com.memos.domain.document.DocumentChunk> result =
                documentChunkRepositoryPort.findByDocumentId(id, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponse.of(result, DocumentChunkResponse::from));
    }

    @GetMapping("/{id}/chunks/{chunkId}")
    public ResponseEntity<DocumentChunkSourceResponse> chunk(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID chunkId) {

        Document document = documentRepositoryPort.findByIdAndOwnerId(id, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        var chunk = documentChunkRepositoryPort.findByIdAndDocumentId(chunkId, id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentChunk", chunkId));
        return ResponseEntity.ok(DocumentChunkSourceResponse.from(chunk, document));
    }

    @GetMapping("/{id}/progress")
    public ResponseEntity<IngestionProgressResponse> progress(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {

        if (!documentRepositoryPort.existsByIdAndOwnerId(id, principal.getId())) {
            throw new ResourceNotFoundException("Document", id);
        }
        return progressStore.get(id)
                .map(p -> ResponseEntity.ok(new IngestionProgressResponse(p.phase(), p.processed(), p.total(), p.percentComplete())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<DocumentResponse> retry(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {

        Document document = ownedDocument(principal.getId(), id);
        document = documentIngestionService.retry(document);
        documentIngestionService.ingest(document.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentResponse.from(document));
    }

    @PostMapping("/{id}/reingest")
    public ResponseEntity<DocumentResponse> reingest(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {

        Document document = ownedDocument(principal.getId(), id);
        if (document.getSourceType() == DocumentSourceType.PDF
                || document.getSourceType() == DocumentSourceType.MARKDOWN) {
            throw new IngestionException("Re-ingest of a file requires uploading the file again.");
        }
        document = documentIngestionService.reingest(document);
        documentIngestionService.ingest(document.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentResponse.from(document));
    }

    @PostMapping(value = "/{id}/reingest", consumes = "multipart/form-data")
    public ResponseEntity<DocumentResponse> reingestFile(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file) {

        Document document = ownedDocument(principal.getId(), id);
        boolean fileSource = document.getSourceType() == DocumentSourceType.PDF
                || document.getSourceType() == DocumentSourceType.MARKDOWN;
        if (!fileSource) {
            throw new IngestionException("Upload a replacement file only for PDF or Markdown documents.");
        }
        if (file == null || file.isEmpty()) {
            throw new IngestionException("Re-ingest of a file requires uploading the file again.");
        }
        DocumentSourceType uploadedType = resolveSourceType(file);
        if (uploadedType != document.getSourceType()) {
            throw new IngestionException("Uploaded file type does not match this document (" + document.getSourceType() + ").");
        }
        StoredUpload stored = storeTempFile(file, uploadedType);
        Map<String, Object> metadata = new HashMap<>();
        if (document.getMetadata() != null) {
            metadata.putAll(document.getMetadata());
        }
        metadata.put("originalFilename", file.getOriginalFilename() == null ? "" : file.getOriginalFilename());
        document = documentIngestionService.replaceFileSource(
                document, stored.path().toString(), stored.fingerprint(), metadata);
        document = documentIngestionService.reingest(document);
        documentIngestionService.ingest(document.getId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DocumentResponse.from(document));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @PathVariable UUID id) {

        if (!documentRepositoryPort.existsByIdAndOwnerId(id, principal.getId())) {
            throw new ResourceNotFoundException("Document", id);
        }

        documentRepositoryPort.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Document ownedDocument(UUID ownerId, UUID id) {
        return documentRepositoryPort.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
    }

    private DocumentSourceType resolveSourceType(MultipartFile file) {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase(Locale.ROOT) : "";
        String contentType = file.getContentType() != null ? file.getContentType() : "";

        if (filename.endsWith(".pdf") || contentType.equals("application/pdf")) {
            return DocumentSourceType.PDF;
        }
        if (filename.endsWith(".md") || filename.endsWith(".markdown") || contentType.equals("text/markdown")) {
            return DocumentSourceType.MARKDOWN;
        }
        throw new IngestionException("Unsupported file type for '" + file.getOriginalFilename() + "'. Only PDF and Markdown files are supported.");
    }

    private StoredUpload storeTempFile(MultipartFile file, DocumentSourceType sourceType) {
        String suffix = sourceType == DocumentSourceType.PDF ? ".pdf" : ".md";
        try {
            byte[] bytes = file.getBytes();
            Path tempFile = Files.createTempFile("memos-upload-", suffix);
            Files.write(tempFile, bytes);
            return new StoredUpload(tempFile, SourceFingerprint.forFile(bytes));
        } catch (IOException | UncheckedIOException e) {
            throw new IngestionException("Failed to store uploaded file: " + e.getMessage(), e);
        }
    }

    private record StoredUpload(Path path, String fingerprint) {
    }
}
