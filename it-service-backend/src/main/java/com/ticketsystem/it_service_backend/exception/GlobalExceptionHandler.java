package com.ticketsystem.it_service_backend.exception;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Log4j2 // Log4j2 aktif! Buradaki loglar Kafka üzerinden OpenSearch'e akacak.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. ResponseStatusException (Servislerden fırlatılan kontrollü hatalar)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        // Kontrollü bir iş kuralı hatası olduğu için WARN seviyesi uygundur
        log.warn("Kontrollü Servis Hatası ({}): {}", ex.getStatusCode(), ex.getReason());

        ErrorResponse error = ErrorResponse.builder()
                .status(ex.getStatusCode().value())
                .message(ex.getReason())
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    // 2. IllegalArgumentException (Uygulama içi validasyon hataları)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        // Eksik/hatalı parametre durumlarında çalışır
        log.warn("Doğrulama Hatası (400 BAD_REQUEST): {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // 3. AccessDeniedException (Spring Security yetki hataları)
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        // Güvenlik ve yetki ihlali logu
        log.warn("Yetki İhlali (403 FORBIDDEN): Kullanıcı yetkisi olmayan bir kaynağa erişmeye çalıştı. Detay: {}",
                ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Bu işlem için yetkiniz bulunmuyor.")
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // 4. Genel Beklenmedik Hatalar
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        // DİKKAT: Burada log.error kullanıyoruz ve 'ex' objesini veriyoruz ki hatanın
        // tüm detayları (Stacktrace) loglansın!
        log.error("Sistemde beklenmeyen kritik bir hata oluştu (500 INTERNAL_SERVER_ERROR): ", ex);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Beklenmedik bir hata oluştu: " + ex.getMessage())
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}