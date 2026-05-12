package com.ticketsystem.llmservice.exception;

import lombok.Getter;

/**
 * Groq API token limiti aşıldığında fırlatılır.
 */
@Getter
public class GroqRateLimitException extends RuntimeException {

    /** Kaç saniye sonra tekrar denenebileceği (Groq'un bildirdiği değer) */
    private final double retryAfterSeconds;

    public GroqRateLimitException(double retryAfterSeconds, String groqMessage) {
        super(groqMessage);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
