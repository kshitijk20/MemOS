package com.memos.infrastructure.config;

import java.lang.reflect.Method;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

import lombok.extern.slf4j.Slf4j;

/**
 * Logs uncaught exceptions from {@code @Async void} methods (e.g. {@code
 * DocumentIngestionService.ingest}), since such exceptions cannot otherwise propagate to a
 * caller. The executor itself is Spring Boot's default virtual-thread-based task executor
 * ({@code spring.threads.virtual.enabled=true}).
 */
@Slf4j
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error("Unhandled exception in async method '{}'", method.getName(), ex);
    }
}
