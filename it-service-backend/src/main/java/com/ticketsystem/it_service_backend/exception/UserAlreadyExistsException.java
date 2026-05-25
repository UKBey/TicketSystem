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

    /**
     * @param field cakisma yasanan alan adi ({@code "email"} veya {@code "username"})
     * @param value cakisan deger; istemciye sadece hata mesaji icinde dolayli sekilde donulur
     */
    public UserAlreadyExistsException(String field, String value) {
        super(String.format("User already exists with %s: %s", field, value));
        this.field = field;
        this.value = value;
    }

    /** @return cakisan form alaninin adi */
    public String getField() {
        return field;
    }

    /** @return kullanicinin gondermek istedigi cakisan deger */
    public String getValue() {
        return value;
    }
}
