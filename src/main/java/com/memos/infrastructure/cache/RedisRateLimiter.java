package com.memos.infrastructure.cache;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.memos.application.limits.RateLimiterPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fixed-window counters in Redis: one key per user, limit and window ({@code INCR}, then
 * {@code EXPIRE} on the first hit). If Redis is unreachable the request is allowed: limits
 * protect quota, and failing closed would take the whole app down with the cache.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiterPort {

    private static final String PREFIX = "ratelimit:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public Result tryAcquire(String key, int limit, Duration window) {
        long windowSeconds = window.toSeconds();
        long nowSeconds = Instant.now().getEpochSecond();
        long windowIndex = nowSeconds / windowSeconds;
        String redisKey = PREFIX + key + ":" + windowIndex;
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, window.plusSeconds(5));
            }
            if (count != null && count > limit) {
                long retryAfter = (windowIndex + 1) * windowSeconds - nowSeconds;
                return new Result(false, Math.max(1, retryAfter));
            }
            return Result.allow();
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request for {}: {}", key, e.getMessage());
            return Result.allow();
        }
    }
}
