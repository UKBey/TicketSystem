package com.ticketsystem.it_service_backend.exception;

/**
 * Keycloak'ta aynı email veya username ile kayıtlı bir kullanıcı zaten mevcutsa fırlatılır.
 *
 * <p>{@code field} alanı frontend'in hangi form alanında hata göstereceğini belirler
 * ("email" veya "username"). {@link GlobalExceptionHandler} bu exception'ı
 * {@code 409 Conflict} olarak işler.
 */
public class UserAlreadyExistsException extends RuntimeException {

    private final String field;
    private final String value;

    public UserAlreadyExistsException(String field, String value) {
        super(String.format("User already exists with %s: %s", field, value));
        this.field = field;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public String getValue() {
        return value;
    }
}
