package com.ticketsystem.it_service_backend.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Executor and exception handler for {@code @Async} methods.
 *
 * <p>Async methods such as {@code EmailService.sendXxx} swallow errors with
 * their own try/catch; the handler is wired explicitly so that any future
 * {@code @Async} method does not silently dump to stderr — uncaught
 * exceptions surface in the central log.
 *
 * <p>The executor is a bounded {@link ThreadPoolTaskExecutor} rather than
 * Spring's default {@code SimpleAsyncTaskExecutor} (which spawns an unbounded
 * thread-per-invocation). A burst of notification/email-triggering events
 * (mass comment/SLA activity) is therefore capped by the pool + queue, and
 * overflow falls back to {@code CallerRunsPolicy} for backpressure instead of
 * exhausting memory/CPU.
 */
@Log4j2
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Bounded executor backing every {@code @Async} method.
     *
     * <p>Replaces the default {@code SimpleAsyncTaskExecutor}: a core/max pool
     * with a finite queue. When both the pool and queue are saturated,
     * {@link ThreadPoolExecutor.CallerRunsPolicy} runs the task on the calling
     * thread, throttling producers rather than dropping work or leaking
     * threads.
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

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
