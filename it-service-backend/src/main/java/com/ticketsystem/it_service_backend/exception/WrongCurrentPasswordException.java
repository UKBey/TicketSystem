package com.ticketsystem.it_service_backend.exception;

/**
 * Kullanıcı şifre değiştirme akışında mevcut şifresini yanlış girdiğinde fırlatılır.
 * Keycloak token endpoint'ine direct-grant ile yapılan doğrulama başarısız olursa
 * tetiklenir.
 */
public class WrongCurrentPasswordException extends RuntimeException {
    public WrongCurrentPasswordException() {
        super("Current password verification failed");
    }
}
