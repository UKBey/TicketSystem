package com.ticketsystem.it_service_backend.exception;

/**
 * Thrown when the Keycloak password policy (length, character complexity,
 * etc.) is violated. The message carries the raw error payload returned by
 * Keycloak so the UI can surface it verbatim.
 */
public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
