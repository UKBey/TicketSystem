package com.ticketsystem.it_service_backend.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * Default exception handler for {@code @Async} void methods.
 *
 * <p>Async methods such as {@code EmailService.sendXxx} swallow errors with
 * their own try/catch; the handler is wired explicitly so that any future
 * {@code @Async} method does not silently dump to stderr — uncaught
 * exceptions surface in the central log.
 */
@Log4j2
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Handler that routes uncaught errors inside {@code @Async} methods to
     * log4j.
     *
     * <p>By default Spring writes such errors to {@code stderr}; here they
     * are logged in a structured form alongside the method signature and
     * class name.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("Uncaught exception in @Async method {}.{}: {}",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        throwable.getMessage(),
                        throwable);
    }
}
