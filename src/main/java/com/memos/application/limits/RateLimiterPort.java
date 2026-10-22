package com.memos.application.limits;

import java.time.Duration;

/** Counts events per key in fixed time windows, shared across app instances. */
public interface RateLimiterPort {

    /**
     * Records one event for {@code key} and reports whether it is within {@code limit} events per
     * {@code window}. Implementations allow the event if the counter store is unavailable.
     */
    Result tryAcquire(String key, int limit, Duration window);

    /** {@code retryAfterSeconds} is when the current window ends (only meaningful when not allowed). */
    record Result(boolean allowed, long retryAfterSeconds) {

        public static Result allow() {
            return new Result(true, 0);
        }
    }
}
