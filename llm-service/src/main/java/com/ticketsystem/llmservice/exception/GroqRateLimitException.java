package com.ticketsystem.llmservice.exception;

import lombok.Getter;

/**
 * Thrown when the Groq API token limit is exceeded.
 */
@Getter
public class GroqRateLimitException extends RuntimeException {

    /** Number of seconds after which the request can be retried (value reported by Groq) */
    private final double retryAfterSeconds;

    public GroqRateLimitException(double retryAfterSeconds, String groqMessage) {
        super(groqMessage);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
