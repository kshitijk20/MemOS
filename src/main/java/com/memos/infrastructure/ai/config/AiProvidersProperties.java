package com.memos.infrastructure.ai.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.memos.domain.ai.AiProvider;
import com.memos.domain.exception.AiProviderException;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds {@code memos.ai.*} configuration: the default chat provider, the embedding
 * provider/dimensionality, and per-provider connection settings (api key, base URL, model
 * names) for Gemini, OpenAI, and Claude.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "memos.ai")
public class AiProvidersProperties {

    private AiProvider defaultProvider = AiProvider.GEMINI;

    private Embedding embedding = new Embedding();

    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * Returns the connection settings for the given provider.
     *
     * @throws AiProviderException if no configuration block exists for the provider
     */
    public ProviderConfig provider(AiProvider provider) {
        ProviderConfig config = providers.get(provider.name().toLowerCase());
        if (config == null) {
            throw new AiProviderException("No configuration found for AI provider: " + provider);
        }
        return config;
    }

    @Getter
    @Setter
    public static class Embedding {
        private AiProvider provider = AiProvider.GEMINI;
        private int dimensions = 768;
        private int maxBatchSize = 8;
        private int tokensPerMinute = 10000;
    }

    @Getter
    @Setter
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private String chatModel;
        private String embeddingModel;
        private Double temperature;
        private Integer maxTokens;
    }
}
