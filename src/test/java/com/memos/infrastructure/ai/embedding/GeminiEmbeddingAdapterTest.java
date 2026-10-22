package com.memos.infrastructure.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import com.memos.domain.ai.AiProvider;
import com.memos.infrastructure.ai.config.AiProvidersProperties;

class GeminiEmbeddingAdapterTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private AiProvidersProperties properties;

    @BeforeEach
    void setUp() {
        AiProvidersProperties.ProviderConfig gemini = new AiProvidersProperties.ProviderConfig();
        gemini.setEmbeddingModel("gemini-embedding-001");
        properties = new AiProvidersProperties();
        properties.setProviders(Map.of("gemini", gemini));
        properties.getEmbedding().setMaxBatchSize(2);
        properties.getEmbedding().setTokensPerMinute(1_000_000);
    }

    @Test
    void embedBatchOnlyEmbedsChunksMissingFromCache() {
        GeminiEmbeddingAdapter adapter = new GeminiEmbeddingAdapter(
                properties, embeddingModel, new ConcurrentMapCacheManager());
        float[] a = {1f};
        float[] b = {2f};
        float[] c = {3f};
        when(embeddingModel.embed(List.of("a", "b"))).thenReturn(List.of(a, b));
        when(embeddingModel.embed(List.of("c"))).thenReturn(List.of(c));

        assertThat(adapter.embedBatch(List.of("a", "b"))).containsExactly(a, b);
        // A retry after a failure re-sends every chunk; only the new one should hit the API.
        assertThat(adapter.embedBatch(List.of("a", "c", "b"))).containsExactly(a, c, b);

        verify(embeddingModel).embed(List.of("a", "b"));
        verify(embeddingModel).embed(List.of("c"));
    }

    @Test
    void embedBatchSplitsMissesIntoSubBatches() {
        GeminiEmbeddingAdapter adapter = new GeminiEmbeddingAdapter(
                properties, embeddingModel, new ConcurrentMapCacheManager());
        when(embeddingModel.embed(List.of("a", "b"))).thenReturn(List.of(new float[] {1f}, new float[] {2f}));
        when(embeddingModel.embed(List.of("c"))).thenReturn(List.of(new float[] {3f}));

        assertThat(adapter.embedBatch(List.of("a", "b", "c"))).hasSize(3);

        verify(embeddingModel).embed(List.of("a", "b"));
        verify(embeddingModel).embed(List.of("c"));
    }

    @Test
    void cachedEmbeddingsAreNotReusedAcrossModels() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        when(embeddingModel.embed(List.of("a"))).thenReturn(List.of(new float[] {1f}));
        new GeminiEmbeddingAdapter(properties, embeddingModel, cacheManager).embedBatch(List.of("a"));

        properties.provider(AiProvider.GEMINI).setEmbeddingModel("another-model");
        new GeminiEmbeddingAdapter(properties, embeddingModel, cacheManager).embedBatch(List.of("a"));

        verify(embeddingModel, times(2)).embed(List.of("a"));
    }

    @Test
    void embedBatchStillWorksWhenCacheFails() {
        Cache brokenCache = mock(Cache.class);
        when(brokenCache.get(anyString(), any(Class.class))).thenThrow(new IllegalStateException("redis down"));
        doThrow(new IllegalStateException("redis down")).when(brokenCache).put(any(), any());
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache(GeminiEmbeddingAdapter.CHUNK_EMBEDDINGS_CACHE)).thenReturn(brokenCache);

        GeminiEmbeddingAdapter adapter = new GeminiEmbeddingAdapter(properties, embeddingModel, cacheManager);
        float[] a = {1f};
        when(embeddingModel.embed(List.of("a"))).thenReturn(List.of(a));

        assertThat(adapter.embedBatch(List.of("a"))).containsExactly(a);
    }

    @Test
    void embedBatchSkipsApiWhenEverythingIsCached() {
        GeminiEmbeddingAdapter adapter = new GeminiEmbeddingAdapter(
                properties, embeddingModel, new ConcurrentMapCacheManager());
        when(embeddingModel.embed(List.of("a"))).thenReturn(List.of(new float[] {1f}));
        adapter.embedBatch(List.of("a"));
        clearInvocations(embeddingModel);

        adapter.embedBatch(List.of("a"));

        verify(embeddingModel, never()).embed(anyList());
    }
}
