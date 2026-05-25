package com.ticketsystem.it_service_backend.exception;

/**
 * Thrown when, during the password change flow, the user supplies the wrong
 * current password. Triggered when the direct-grant verification against
 * the Keycloak token endpoint fails.
 */
public class WrongCurrentPasswordException extends RuntimeException {
    public WrongCurrentPasswordException() {
        super("Current password verification failed");
    }
}
