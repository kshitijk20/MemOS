package com.memos.application.limits;

import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.memos.domain.exception.RateLimitExceededException;

import lombok.RequiredArgsConstructor;

/**
 * Per-user request limits, so one user (or script) can't use up the shared AI quota or flood the
 * ingestion pipeline. A limit of 0 disables that check.
 */
@Service
@RequiredArgsConstructor
public class UsageLimitService {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final RateLimiterPort rateLimiterPort;

    @Value("${memos.limits.questions-per-minute:5}")
    private int questionsPerMinute = 5;

    @Value("${memos.limits.questions-per-day:200}")
    private int questionsPerDay = 200;

    @Value("${memos.limits.ingestions-per-hour:20}")
    private int ingestionsPerHour = 20;

    /** Call before answering a question; throws if the user is over a limit. */
    public void checkQuestion(UUID userId) {
        check("questions:minute:" + userId, questionsPerMinute, MINUTE,
                "You're asking questions too quickly (limit " + questionsPerMinute + " per minute).");
        check("questions:day:" + userId, questionsPerDay, DAY,
                "You've reached today's limit of " + questionsPerDay + " questions.");
    }

    /** Call before starting an ingestion (upload, URL, repository, retry or re-ingest). */
    public void checkIngestion(UUID userId) {
        check("ingestions:hour:" + userId, ingestionsPerHour, HOUR,
                "You've added too many documents in the last hour (limit " + ingestionsPerHour + ").");
    }

    private void check(String key, int limit, Duration window, String message) {
        if (limit <= 0) {
            return;
        }
        RateLimiterPort.Result result = rateLimiterPort.tryAcquire(key, limit, window);
        if (!result.allowed()) {
            throw new RateLimitExceededException(
                    message + " Try again in " + humanize(result.retryAfterSeconds()) + ".",
                    result.retryAfterSeconds());
        }
    }

    static String humanize(long seconds) {
        if (seconds < 90) {
            return Math.max(1, seconds) + " second" + (seconds == 1 ? "" : "s");
        }
        long minutes = Math.round(seconds / 60.0);
        if (minutes < 90) {
            return minutes + " minutes";
        }
        return Math.round(minutes / 60.0) + " hours";
    }
}
