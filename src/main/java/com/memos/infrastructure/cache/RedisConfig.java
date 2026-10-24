package com.memos.infrastructure.cache;

import java.time.Duration;

import java.util.Map;

import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Configures the Redis-backed {@link org.springframework.cache.CacheManager} used by {@code
 * @Cacheable} (e.g. embedding results in {@code GeminiEmbeddingAdapter}), with JSON value
 * serialization so cached values remain readable across application versions/languages.
 */
@Configuration
public class RedisConfig implements CachingConfigurer {

    /**
     * Treat cache failures (Redis down, a hosted Redis over its quota) as cache misses: the value
     * is computed normally and the error logged, instead of failing the request.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer()));

        // The "embeddings" cache stores raw float[] vectors. GenericJackson2JsonRedisSerializer's
        // default typing writes primitive arrays without a type wrapper but deserializes Object-typed
        // values expecting one, so float[] entries fail to round-trip. Use a serializer bound directly
        // to float[] for this cache instead.
        RedisCacheConfiguration embeddingsConfiguration = configuration.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(float[].class)));

        // Chunk embeddings outlive query embeddings so a failed large ingestion can be retried
        // the next day without re-embedding the chunks it already finished.
        RedisCacheConfiguration chunkEmbeddingsConfiguration = embeddingsConfiguration.entryTtl(Duration.ofHours(24));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .withInitialCacheConfigurations(Map.of(
                        "embeddings", embeddingsConfiguration,
                        "chunk-embeddings", chunkEmbeddingsConfiguration))
                .build();
    }
}
