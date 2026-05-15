package com.ticketsystem.it_service_backend.exception;

/**
 * Keycloak şifre politikasının (uzunluk, karakter zorluğu vb.) ihlal edildiği
 * durumlarda fırlatılır. Mesaj olarak Keycloak'tan dönen ham hata payload'u
 * taşınır; UI tarafı bunu olduğu gibi gösterebilir.
 */
public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException(String message) {
        super(message);
    }
}
