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
}
