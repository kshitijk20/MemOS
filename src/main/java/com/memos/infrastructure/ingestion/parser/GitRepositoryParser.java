package com.memos.infrastructure.ingestion.parser;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.memos.application.ingestion.DocumentParserPort;
import com.memos.application.ingestion.ParsedDocument;
import com.memos.domain.document.Document;
import com.memos.domain.document.DocumentSourceType;
import com.memos.domain.exception.IngestionException;

import lombok.extern.slf4j.Slf4j;

/**
 * Shallow-clones a Git repository (the document's {@code sourceUri}) into a temporary
 * directory via JGit, walks the working tree, and produces one {@link ParsedDocument} per file
 * matching {@code memos.ingestion.git.allowed-extensions} and under the size limit. The clone
 * is deleted once parsing finishes.
 */
@Slf4j
@Component
public class GitRepositoryParser implements DocumentParserPort {

    private final Set<String> allowedExtensions;
    private final long maxFileSizeBytes;
    private final int cloneTimeoutSeconds;

    public GitRepositoryParser(
            @Value("${memos.ingestion.git.allowed-extensions}") String allowedExtensionsCsv,
            @Value("${memos.ingestion.git.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${memos.ingestion.git.clone-timeout-seconds}") int cloneTimeoutSeconds) {
        this.allowedExtensions = Arrays.stream(allowedExtensionsCsv.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.cloneTimeoutSeconds = cloneTimeoutSeconds;
    }

    @Override
    public DocumentSourceType sourceType() {
        return DocumentSourceType.GIT_REPOSITORY;
    }

    @Override
    public List<ParsedDocument> parse(Document document) {
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("memos-git-");
        } catch (IOException e) {
            throw new IngestionException("Failed to create temp directory for git clone", e);
        }

        try {
            CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(document.getSourceUri())
                    .setDirectory(tempDir.toFile())
                    .setDepth(1)
                    .setCloneAllBranches(false)
                    .setTimeout(cloneTimeoutSeconds);

            Object branch = document.getMetadata() == null ? null : document.getMetadata().get("branch");
            if (branch instanceof String branchName && !branchName.isBlank()) {
                cloneCommand.setBranch(branchName);
            }

            try (Git git = cloneCommand.call()) {
                log.debug("Cloned {} into {}", document.getSourceUri(), tempDir);
            }

            return walkRepository(tempDir);
        } catch (GitAPIException e) {
            throw new IngestionException("Failed to clone git repository '" + document.getSourceUri() + "': " + e.getMessage(), e);
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private List<ParsedDocument> walkRepository(Path tempDir) {
        List<ParsedDocument> documents = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(tempDir)) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.toString().contains(File.separator + ".git" + File.separator))
                    .filter(this::hasAllowedExtension)
                    .toList();

            for (Path file : files) {
                try {
                    if (Files.size(file) > maxFileSizeBytes) {
                        continue;
                    }
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    String relativePath = tempDir.relativize(file).toString();
                    documents.add(new ParsedDocument(relativePath, content, Map.of("filePath", relativePath)));
                } catch (MalformedInputException e) {
                    log.debug("Skipping non-UTF-8 (likely binary) file {}", file);
                } catch (IOException | UncheckedIOException e) {
                    log.warn("Skipping unreadable file {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IngestionException("Failed to walk cloned repository: " + e.getMessage(), e);
        }
        return documents;
    }

    private boolean hasAllowedExtension(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return allowedExtensions.stream().anyMatch(name::endsWith);
    }

    private void deleteRecursively(Path path) {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("Failed to delete temp file {}: {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean up temp directory {}: {}", path, e.getMessage());
        }
    }
}
