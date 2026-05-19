package com.ticketsystem.it_service_backend.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * Default exception handler for {@code @Async} void methods.
 *
 * <p>{@code EmailService.sendXxx} gibi async metodlar kendi içinde try/catch
 * yaparak hatayı yutar; ama gelecekte eklenecek herhangi bir @Async metodun
 * sessizce stderr'a düşmesini önlemek için handler explicit konuyor — uncaught
 * exception merkezi log'da görünür.
 */
@Log4j2
@Configuration
public class AsyncConfig implements AsyncConfigurer {

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
