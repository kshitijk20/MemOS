package com.memos.infrastructure.ai.embedding;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.memos.domain.ai.EmbeddingPort;
import com.memos.domain.exception.AiProviderException;
import com.memos.infrastructure.ai.config.AiProvidersProperties;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link EmbeddingPort} backed by the embedding provider configured in {@code
 * memos.ai.embedding.provider} (Gemini's {@code text-embedding-004} by default, accessed via
 * Google's OpenAI-compatible endpoint). All stored chunk/query vectors share this provider's
 * dimensionality ({@code memos.ai.embedding.dimensions}, 768 for Gemini).
 */
@Slf4j
@Component
public class GeminiEmbeddingAdapter implements EmbeddingPort {

    private static final int CHARS_PER_TOKEN = 4;
    private static final long WINDOW_MILLIS = 60_000L;

    /** Timestamps and estimated token counts of recent embedding requests, oldest first. */
    private final Deque<long[]> recentUsage = new ArrayDeque<>();

    private final OpenAiEmbeddingModel embeddingModel;
    private final int dimensions;
    private final int maxBatchSize;
    private final int tokensPerMinute;

    public GeminiEmbeddingAdapter(AiProvidersProperties properties) {
        AiProvidersProperties.ProviderConfig config = properties.provider(properties.getEmbedding().getProvider());
        this.dimensions = properties.getEmbedding().getDimensions();
        this.maxBatchSize = properties.getEmbedding().getMaxBatchSize();
        this.tokensPerMinute = properties.getEmbedding().getTokensPerMinute();

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(config.getEmbeddingModel())
                .dimensions(dimensions)
                .build();

        this.embeddingModel = new OpenAiEmbeddingModel(
                api, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
    }

    /**
     * Cached so that repeated questions (e.g. RAG retrieval for a common query) and
     * ingestion retries don't re-spend free-tier embedding API quota.
     */
    @Override
    @Cacheable(value = "embeddings", key = "#text")
    public float[] embed(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            throw new AiProviderException("Embedding request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Splits large inputs into sub-batches of at most {@code maxBatchSize} texts, pacing
     * requests so the estimated token volume sent in any rolling 60s window stays under
     * {@code tokensPerMinute}. A single request covering an entire multi-page document can
     * exceed Gemini's free-tier per-minute embedding quota and fail with a 429 even when each
     * smaller sub-batch would succeed on its own.
     */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.size() <= maxBatchSize) {
            awaitCapacity(estimateTokens(texts));
            return embedChunk(texts);
        }

        log.info("Splitting {} chunks into sub-batches of {} to stay under the {} tokens/min embedding budget",
                texts.size(), maxBatchSize, tokensPerMinute);
        List<float[]> embeddings = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += maxBatchSize) {
            List<String> batch = texts.subList(start, Math.min(start + maxBatchSize, texts.size()));
            awaitCapacity(estimateTokens(batch));
            embeddings.addAll(embedChunk(batch));
        }
        return embeddings;
    }

    private List<float[]> embedChunk(List<String> texts) {
        try {
            return embeddingModel.embed(texts);
        } catch (Exception e) {
            throw new AiProviderException("Batch embedding request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Blocks until sending {@code tokens} more would not push the rolling 60s usage total
     * above {@code tokensPerMinute}, then records this request's usage.
     */
    private synchronized void awaitCapacity(int tokens) {
        while (true) {
            long now = System.currentTimeMillis();
            recentUsage.removeIf(entry -> now - entry[0] >= WINDOW_MILLIS);

            long used = recentUsage.stream().mapToLong(entry -> entry[1]).sum();
            if (used == 0 || used + tokens <= tokensPerMinute) {
                recentUsage.addLast(new long[] {now, tokens});
                return;
            }

            long oldestTimestamp = recentUsage.peekFirst()[0];
            long waitMillis = WINDOW_MILLIS - (now - oldestTimestamp) + 1000;
            log.info("Pausing {}ms to stay under the {} tokens/min embedding budget", waitMillis, tokensPerMinute);
            sleep(Math.max(waitMillis, 1000));
        }
    }

    private int estimateTokens(List<String> texts) {
        int chars = texts.stream().mapToInt(String::length).sum();
        return Math.max(1, chars / CHARS_PER_TOKEN);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Embedding batch interrupted while waiting for rate limit capacity", e);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }
}
