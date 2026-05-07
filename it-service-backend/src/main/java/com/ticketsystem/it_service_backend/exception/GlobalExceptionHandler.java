package com.ticketsystem.it_service_backend.exception;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Log4j2 // Uygulama genelindeki hatalari merkezden loglayip standart hata cevabi uretir.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Servis katmaninin bilerek firlattigi is kurali hatalarini yakalar.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        // Beklenen is kurali ihlallerini hata yerine uyari seviyesinde kaydederiz.
        log.warn("Kontrollü Servis Hatası ({}): {}", ex.getStatusCode(), ex.getReason());

        ErrorResponse error = ErrorResponse.builder()
                .status(ex.getStatusCode().value())
                .message(ex.getReason())
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    // Parametre ve dogrulama kaynakli hatalari 400 olarak dondurur.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        // Giris verisi hatalarinda istemciye acik bir geri bildirim verir.
        log.warn("Doğrulama Hatası (400 BAD_REQUEST): {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.warn("Doğrulama Hatası (400 BAD_REQUEST): {}", fieldErrors);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message("İstek doğrulaması başarısız oldu.")
                .fieldErrors(fieldErrors)
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Spring Security tarafinda yakalanan yetki ihlallerini ele alir.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        // Yetkisiz erisim denemeleri denetim amacli kayda gecirilir.
        log.warn("Yetki İhlali (403 FORBIDDEN): Kullanıcı yetkisi olmayan bir kaynağa erişmeye çalıştı. Detay: {}",
                ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.FORBIDDEN.value())
                .message("Bu işlem için yetkiniz bulunmuyor.")
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // Agent kapasite limiti asimlarini conflict olarak dondurur.
    @ExceptionHandler(TicketLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleTicketLimitExceededException(TicketLimitExceededException ex) {
        log.warn("Bilet limiti aşıldı (409 CONFLICT): {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .error("TICKET_LIMIT_EXCEEDED")
                .message(ex.getMessage())
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    // Eslesen endpoint bulunamadiginda standart 404 cevabi uretir.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("Kaynak bulunamadı (404 NOT_FOUND): {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.NOT_FOUND.value())
                .message("İstenen kaynak bulunamadı: " + ex.getResourcePath())
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    // Beklenmeyen tum hatalar icin son guvenlik agi gorevi gorur.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        // Stacktrace'i koruyarak loglamak, uretim ortami hata analizi icin kritiktir.
        log.error("Sistemde beklenmeyen kritik bir hata oluştu (500 INTERNAL_SERVER_ERROR): ", ex);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Beklenmedik bir hata oluştu: " + ex.getMessage())
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}