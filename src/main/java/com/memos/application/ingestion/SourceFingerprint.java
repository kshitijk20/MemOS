package com.memos.application.ingestion;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import com.memos.domain.document.DocumentSourceType;

public final class SourceFingerprint {

    private SourceFingerprint() {
    }

    public static String forWebPage(String url) {
        return sha256(DocumentSourceType.WEB_PAGE.name() + "\0" + normalizeUrl(url));
    }

    public static String forGit(String repoUrl, String branch) {
        String normalizedBranch = branch == null ? "" : branch.trim().toLowerCase(Locale.ROOT);
        return sha256(DocumentSourceType.GIT_REPOSITORY.name() + "\0" + normalizeUrl(repoUrl) + "\0" + normalizedBranch);
    }

    public static String forFile(byte[] content) {
        return sha256("FILE\0" + sha256(content));
    }

    static String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - 4);
            }
            String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
            String portPart = port == -1 ? "" : ":" + port;
            if (host.isEmpty()) {
                return trimmed.toLowerCase(Locale.ROOT);
            }
            return scheme + "://" + host + portPart + path + query;
        } catch (IllegalArgumentException e) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
