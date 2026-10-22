package com.memos.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderErrorMessagesTest {

    @Test
    void quotaJsonBecomesGenericMessage() {
        String dump = """
                Embedding request failed: 429 - { "error": { "code": 429, \
                "message": "You exceeded your current quota", "status": "RESOURCE_EXHAUSTED" } }
                """;

        assertThat(ProviderErrorMessages.forEmbedding(new RuntimeException(dump)))
                .isEqualTo(ProviderErrorMessages.EMBEDDING_QUOTA);
        assertThat(ProviderErrorMessages.sanitizeIngestionFailure(dump))
                .isEqualTo(ProviderErrorMessages.EMBEDDING_QUOTA);
    }

    @Test
    void otherJsonDumpsAreNotPassedThrough() {
        String dump = "Gemini chat completion failed: { \"error\": { \"message\": \"internal\" } }";

        assertThat(ProviderErrorMessages.sanitizeIngestionFailure(dump))
                .isEqualTo(ProviderErrorMessages.INGESTION_FAILED);
    }

    @Test
    void humanMessagesAreKept() {
        assertThat(ProviderErrorMessages.sanitizeIngestionFailure("Page not found (HTTP 404): https://example.com"))
                .contains("Page not found");
    }

    @Test
    void chatQuotaDumpBecomesShortRateLimitMessage() {
        String dump = "429 - [{ \"error\": { \"code\": 429, \"status\": \"RESOURCE_EXHAUSTED\", "
                + "\"message\": \"Quota exceeded for metric generate_content_free_tier_requests\" } }]";

        String message = ProviderErrorMessages.forChat("Gemini", new RuntimeException(dump));

        assertThat(message).isEqualTo("Gemini is rate-limiting requests right now. Wait a few seconds and try again.");
        assertThat(message).doesNotContain("{");
    }

    @Test
    void chatAuthAndTimeoutFailuresGetTheirOwnMessages() {
        assertThat(ProviderErrorMessages.forChat("Claude", new RuntimeException("401 - authentication_error")))
                .contains("rejected the server's API key");
        assertThat(ProviderErrorMessages.forChat("OpenAI",
                new RuntimeException("I/O error", new java.net.SocketTimeoutException("Read timed out"))))
                .contains("took too long");
        assertThat(ProviderErrorMessages.forChat("OpenAI", new RuntimeException("500 internal")))
                .isEqualTo("OpenAI couldn't answer right now. Try again in a moment.");
    }
}
