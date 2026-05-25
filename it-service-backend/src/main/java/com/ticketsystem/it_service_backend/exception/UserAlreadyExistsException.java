package com.ticketsystem.it_service_backend.exception;

/**
 * Thrown when a user with the same email or username is already registered
 * in Keycloak.
 *
 * <p>The {@code field} value tells the frontend which form field should
 * display the error ({@code "email"} or {@code "username"}).
 * {@link GlobalExceptionHandler} maps this exception to
 * {@code 409 Conflict}.
 */
public class UserAlreadyExistsException extends RuntimeException {

    private final String field;
    private final String value;

    /**
     * @param field name of the field in conflict ({@code "email"} or
     *              {@code "username"})
     * @param value the conflicting value; returned to the client only
     *              indirectly inside the error message
     */
    public UserAlreadyExistsException(String field, String value) {
        super(String.format("User already exists with %s: %s", field, value));
        this.field = field;
        this.value = value;
    }

    /** @return name of the conflicting form field */
    public String getField() {
        return field;
    }

    /** @return the conflicting value the user attempted to submit */
    public String getValue() {
        return value;
    }
}
