package com.ticketsystem.it_service_backend.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Common JSON body for every error response.
 *
 * <p>{@link GlobalExceptionHandler} builds this DTO for every exception
 * type via its builder. {@code status} carries the HTTP code,
 * {@code error} a known error category name (for example
 * {@code USER_ALREADY_EXISTS}), {@code message} the locale-translated
 * message, and {@code fieldErrors} carries per-field error text for
 * validation failures. Plain primitive fields are used so OpenAPI and
 * client JSON parsers handle it without trouble.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private long timestamp;
    private Map<String, String> fieldErrors;
}
