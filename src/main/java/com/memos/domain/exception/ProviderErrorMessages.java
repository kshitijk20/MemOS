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

    /**
     * Short message for a failed chat call, safe to show users and to send in a stream's error
     * event. Keep the original exception as the cause so the provider's response is still logged.
     */
    public static String forChat(String provider, Throwable error) {
        String text = stringify(error);
        if (looksLikeQuota(text)) {
            return provider + " is rate-limiting requests right now. Wait a few seconds and try again.";
        }
        if (looksLikeAuthFailure(text)) {
            return provider + " rejected the server's API key. Check the AI provider configuration.";
        }
        if (looksLikeTimeout(text)) {
            return provider + " took too long to respond. Try again.";
        }
        return provider + " couldn't answer right now. Try again in a moment.";
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

    static boolean looksLikeAuthFailure(String text) {
        String lower = text.toLowerCase();
        return lower.contains("401")
                || lower.contains("403")
                || lower.contains("unauthorized")
                || lower.contains("permission_denied")
                || lower.contains("invalid api key")
                || lower.contains("api key not valid")
                || lower.contains("invalid x-api-key")
                || lower.contains("authentication_error");
    }

    static boolean looksLikeTimeout(String text) {
        String lower = text.toLowerCase();
        return lower.contains("timed out") || lower.contains("timeout");
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
