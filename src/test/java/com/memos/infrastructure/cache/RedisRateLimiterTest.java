package com.memos.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.memos.application.limits.RateLimiterPort;

class RedisRateLimiterTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);
    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    private final RedisRateLimiter limiter = new RedisRateLimiter(template);

    RedisRateLimiterTest() {
        when(template.opsForValue()).thenReturn(ops);
    }

    @Test
    void firstHitStartsTheWindowExpiry() {
        when(ops.increment(startsWith("ratelimit:q:"))).thenReturn(1L);

        assertThat(limiter.tryAcquire("q", 2, Duration.ofMinutes(1)).allowed()).isTrue();
        verify(template).expire(startsWith("ratelimit:q:"), eq(Duration.ofSeconds(65)));
    }

    @Test
    void rejectsOnceOverTheLimitWithTimeUntilTheWindowEnds() {
        when(ops.increment(anyString())).thenReturn(3L);

        RateLimiterPort.Result result = limiter.tryAcquire("q", 2, Duration.ofMinutes(1));

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isBetween(1L, 60L);
        verify(template, never()).expire(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));
    }

    @Test
    void allowsRequestsWhenRedisIsDown() {
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(limiter.tryAcquire("q", 2, Duration.ofMinutes(1)).allowed()).isTrue();
    }
}
