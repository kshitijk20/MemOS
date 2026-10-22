package com.memos.domain.exception;

/**
 * Maps provider failures to short, user-facing messages. Raw HTTP bodies (JSON, HTML)
 * stay in logs, not in {@code documents.error_message}.
 */
public final class ProviderErrorMessages {

    public static final String EMBEDDING_QUOTA =
            "Embedding quota exceeded. Wait and retry later, or check your AI plan and billing.";

    public static final String EMBEDDING_FAILED = "Embedding failed. Retry later.";

    public static final String INGESTION_FAILED = "Ingestion failed. Retry later.";

    private ProviderErrorMessages() {
    }

    public static String forEmbedding(Throwable error) {
        if (looksLikeQuota(stringify(error))) {
            return EMBEDDING_QUOTA;
        }
        return EMBEDDING_FAILED;
    }

    public static String sanitizeIngestionFailure(String message) {
        if (message == null || message.isBlank()) {
            return INGESTION_FAILED;
        }
        if (looksLikeQuota(message)) {
            return EMBEDDING_QUOTA;
        }
        if (isProviderDump(message)) {
            return INGESTION_FAILED;
        }
        return message;
    }

    static boolean looksLikeQuota(String text) {
        String lower = text.toLowerCase();
        return lower.contains("429")
                || lower.contains("resource_exhausted")
                || lower.contains("quota exceeded")
                || lower.contains("rate-limit")
                || lower.contains("rate_limit");
    }

    static boolean isProviderDump(String text) {
        return text.contains("{") && (text.contains("\"error\"") || text.contains("\"message\""));
    }

    private static String stringify(Throwable error) {
        StringBuilder builder = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append('\n');
            }
            builder.append(current.getClass().getName()).append('\n');
        }
        return builder.toString();
    }
}
