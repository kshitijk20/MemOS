package com.memos.application.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.memos.domain.exception.RateLimitExceededException;

class UsageLimitServiceTest {

    private final RateLimiterPort limiter = mock(RateLimiterPort.class);
    private final UsageLimitService service = new UsageLimitService(limiter);
    private final UUID userId = UUID.randomUUID();

    @Test
    void allowsQuestionsWithinBothWindows() {
        when(limiter.tryAcquire(anyString(), anyInt(), any())).thenReturn(RateLimiterPort.Result.allow());

        service.checkQuestion(userId);

        verify(limiter).tryAcquire(startsWith("questions:minute:" + userId), any(Integer.class), any());
        verify(limiter).tryAcquire(startsWith("questions:day:" + userId), any(Integer.class), any());
    }

    @Test
    void perMinuteLimitReportsWhenToRetry() {
        when(limiter.tryAcquire(startsWith("questions:minute:"), anyInt(), any()))
                .thenReturn(new RateLimiterPort.Result(false, 42));

        assertThatThrownBy(() -> service.checkQuestion(userId))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("too quickly")
                .hasMessageContaining("42 seconds")
                .satisfies(e -> assertThat(((RateLimitExceededException) e).getRetryAfterSeconds()).isEqualTo(42));
    }

    @Test
    void zeroDisablesALimit() {
        ReflectionTestUtils.setField(service, "ingestionsPerHour", 0);

        service.checkIngestion(userId);

        verify(limiter, never()).tryAcquire(anyString(), anyInt(), any());
    }

    @Test
    void humanizesWaits() {
        assertThat(UsageLimitService.humanize(1)).isEqualTo("1 second");
        assertThat(UsageLimitService.humanize(45)).isEqualTo("45 seconds");
        assertThat(UsageLimitService.humanize(600)).isEqualTo("10 minutes");
        assertThat(UsageLimitService.humanize(7 * 3600)).isEqualTo("7 hours");
    }
}
