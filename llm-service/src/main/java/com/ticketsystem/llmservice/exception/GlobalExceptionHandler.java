package com.ticketsystem.llmservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler that catches exceptions arising in the REST layer
 * at a single point and returns them to the client as RFC 7807
 * {@link ProblemDetail} responses.
 *
 * <p>Produces appropriate HTTP status codes and messages for Groq rate-limit,
 * resource-not-found and unexpected runtime errors.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Returns a 429 response when the Groq token limit is exceeded and writes
     * the {@code retryAfterSeconds} field onto the {@link ProblemDetail}.
     *
     * @param ex the caught rate-limit exception
     * @return 429 Too Many Requests body
     */
    @ExceptionHandler(GroqRateLimitException.class)
    public ProblemDetail handleRateLimit(GroqRateLimitException ex) {
        log.warn("Groq rate limit: {}s sonra tekrar denenebilir", ex.getRetryAfterSeconds());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Token limiti aşıldı. Lütfen birkaç saniye bekleyip tekrar deneyin."
        );
        pd.setType(URI.create("about:blank"));
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("retryAfterSeconds", Math.ceil(ex.getRetryAfterSeconds()));
        return pd;
    }

    /**
     * Converts {@link IllegalArgumentException}s that carry "resource not found"
     * semantics into 404 responses.
     *
     * @param ex the caught exception
     * @return 404 Not Found body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        log.warn("Kaynak bulunamadı: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Converts all other unexpected runtime errors into 500 responses and logs
     * the stack trace.
     *
     * @param ex the caught exception
     * @return 500 Internal Server Error body
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntime(RuntimeException ex) {
        log.error("Beklenmeyen hata: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "İşlem sırasında bir hata oluştu: " + ex.getMessage()
        );
        pd.setType(URI.create("about:blank"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
