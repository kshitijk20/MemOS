package com.memos.infrastructure.ai.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.memos.domain.ai.EmbeddingPort;
import com.memos.domain.exception.AiProviderException;
import com.memos.domain.exception.ProviderErrorMessages;
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

    /** Per-chunk embeddings written during ingestion; lets a retry skip chunks already embedded. */
    public static final String CHUNK_EMBEDDINGS_CACHE = "chunk-embeddings";

    private static final int CHARS_PER_TOKEN = 4;
    private static final long WINDOW_MILLIS = 60_000L;

    /** Timestamps and estimated token counts of recent embedding requests, oldest first. */
    private final Deque<long[]> recentUsage = new ArrayDeque<>();

    /**
     * Guards {@link #recentUsage}. A lock rather than {@code synchronized}: waiters sleep while
     * holding it, and on Java 21 a virtual thread blocked in a monitor pins its carrier thread, so
     * enough concurrent ingestions would starve every other virtual thread (HTTP requests included).
     */
    private final ReentrantLock rateLimitLock = new ReentrantLock(true);

    private final EmbeddingModel embeddingModel;
    private final Cache chunkEmbeddingCache;
    private final String cacheKeyPrefix;
    private final int dimensions;
    private final int maxBatchSize;
    private final int tokensPerMinute;

    @Autowired
    public GeminiEmbeddingAdapter(AiProvidersProperties properties, CacheManager cacheManager) {
        this(properties, createModel(properties), cacheManager);
    }

    GeminiEmbeddingAdapter(AiProvidersProperties properties, EmbeddingModel embeddingModel, CacheManager cacheManager) {
        AiProvidersProperties.ProviderConfig config = properties.provider(properties.getEmbedding().getProvider());
        this.dimensions = properties.getEmbedding().getDimensions();
        this.maxBatchSize = properties.getEmbedding().getMaxBatchSize();
        this.tokensPerMinute = properties.getEmbedding().getTokensPerMinute();
        this.embeddingModel = embeddingModel;
        this.chunkEmbeddingCache = cacheManager.getCache(CHUNK_EMBEDDINGS_CACHE);
        // Vectors from a different model or dimensionality must never be served from cache.
        this.cacheKeyPrefix = config.getEmbeddingModel() + ":" + dimensions + ":";
    }

    private static EmbeddingModel createModel(AiProvidersProperties properties) {
        AiProvidersProperties.ProviderConfig config = properties.provider(properties.getEmbedding().getProvider());

        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(config.getEmbeddingModel())
                .dimensions(properties.getEmbedding().getDimensions())
                .build();

        return new OpenAiEmbeddingModel(
                api, MetadataMode.EMBED, options, RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
    }

    /**
     * Cached so that repeated questions (e.g. RAG retrieval for a common query) don't re-spend
     * free-tier embedding API quota. Ingestion goes through {@link #embedBatch}, which has its own
     * per-chunk cache.
     */
    @Override
    @Cacheable(value = "embeddings", key = "#text")
    public float[] embed(String text) {
        try {
            return embeddingModel.embed(text);
        } catch (Exception e) {
            throw new AiProviderException(ProviderErrorMessages.forEmbedding(e), e);
        }
    }

    /**
     * Embeds only the texts not already in the per-chunk cache, so a retried or re-ingested
     * document spends quota only on chunks that were never embedded. Misses are split into
     * sub-batches of at most {@code maxBatchSize} texts, paced so the estimated token volume sent
     * in any rolling 60s window stays under {@code tokensPerMinute}. A single request covering an
     * entire multi-page document can exceed Gemini's free-tier per-minute embedding quota and fail
     * with a 429 even when each smaller sub-batch would succeed on its own.
     */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        float[][] embeddings = new float[texts.size()][];
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            embeddings[i] = cachedChunkEmbedding(texts.get(i));
            if (embeddings[i] == null) {
                missing.add(i);
            }
        }

        if (missing.size() > maxBatchSize) {
            log.info("Splitting {} chunks into sub-batches of {} to stay under the {} tokens/min embedding budget",
                    missing.size(), maxBatchSize, tokensPerMinute);
        }
        for (int start = 0; start < missing.size(); start += maxBatchSize) {
            List<Integer> indexes = missing.subList(start, Math.min(start + maxBatchSize, missing.size()));
            List<String> batch = indexes.stream().map(texts::get).toList();
            awaitCapacity(estimateTokens(batch));
            List<float[]> batchEmbeddings = embedChunk(batch);
            for (int i = 0; i < indexes.size(); i++) {
                embeddings[indexes.get(i)] = batchEmbeddings.get(i);
                cacheChunkEmbedding(batch.get(i), batchEmbeddings.get(i));
            }
        }
        return Arrays.asList(embeddings);
    }

    /** Cache failures (e.g. Redis down) only cost quota, so they must not fail ingestion. */
    private float[] cachedChunkEmbedding(String text) {
        if (chunkEmbeddingCache == null) {
            return null;
        }
        try {
            return chunkEmbeddingCache.get(cacheKey(text), float[].class);
        } catch (RuntimeException e) {
            log.warn("Chunk embedding cache read failed: {}", e.getMessage());
            return null;
        }
    }

    private void cacheChunkEmbedding(String text, float[] embedding) {
        if (chunkEmbeddingCache == null) {
            return;
        }
        try {
            chunkEmbeddingCache.put(cacheKey(text), embedding);
        } catch (RuntimeException e) {
            log.warn("Chunk embedding cache write failed: {}", e.getMessage());
        }
    }

    private String cacheKey(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return cacheKeyPrefix + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private List<float[]> embedChunk(List<String> texts) {
        try {
            return embeddingModel.embed(texts);
        } catch (Exception e) {
            throw new AiProviderException(ProviderErrorMessages.forEmbedding(e), e);
        }
    }

    /**
     * Blocks until sending {@code tokens} more would not push the rolling 60s usage total
     * above {@code tokensPerMinute}, then records this request's usage.
     */
    private void awaitCapacity(int tokens) {
        rateLimitLock.lock();
        try {
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
        } finally {
            rateLimitLock.unlock();
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
