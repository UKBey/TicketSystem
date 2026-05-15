package com.ticketsystem.it_service_backend.exception;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
import java.util.Locale;
import java.util.Map;

@Log4j2 // Uygulama genelindeki hatalari merkezden loglayip standart hata cevabi uretir.
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    // useCodeAsDefaultMessage=true olduğundan: key properties'te varsa çeviri döner,
    // yoksa key'in kendisi döner — geriye dönük uyumluluğu korur.
    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    // Servis katmaninin bilerek firlattigi is kurali hatalarini yakalar.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        // Beklenen is kurali ihlallerini hata yerine uyari seviyesinde kaydederiz.
        log.warn("Kontrollü Servis Hatası ({}): {}", ex.getStatusCode(), ex.getReason());

        String message = ex.getReason() != null ? msg(ex.getReason()) : "";
        ErrorResponse error = ErrorResponse.builder()
                .status(ex.getStatusCode().value())
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, ex.getStatusCode());
    }

    // Parametre ve dogrulama kaynakli hatalari 400 olarak dondurur.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        // Giris verisi hatalarinda istemciye acik bir geri bildirim verir.
        log.warn("Doğrulama Hatası (400 BAD_REQUEST): {}", ex.getMessage());

        String message = ex.getMessage() != null ? msg(ex.getMessage()) : msg("error.unexpected", (Object) null);
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // Spring'in MessageSourceResolvable implementasyonunu kullanarak field mesajını locale'e göre çözer.
            fieldErrors.put(fieldError.getField(), messageSource.getMessage(fieldError, locale));
        }

        log.warn("Doğrulama Hatası (400 BAD_REQUEST): {}", fieldErrors);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(msg("error.validation.failed"))
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
                .message(msg("error.access.denied"))
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    // Keycloak'ta email veya username çakışması olduğunda 409 Conflict döner.
    // fieldErrors map'i frontend'in hangi form alanında hata göstereceğini belirler.
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExistsException(UserAlreadyExistsException ex) {
        log.warn("Kullanıcı çakışması (409 CONFLICT): field={}, value={}", ex.getField(), ex.getValue());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .error("USER_ALREADY_EXISTS")
                .message(msg("error.user.already.exists", ex.getField(), ex.getValue()))
                .fieldErrors(Map.of(ex.getField(), msg("error.user.already.exists." + ex.getField())))
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    // Self-service sifre degistirme akisinda mevcut sifre yanlis girildiginde 400 doner.
    @ExceptionHandler(WrongCurrentPasswordException.class)
    public ResponseEntity<ErrorResponse> handleWrongCurrentPassword(WrongCurrentPasswordException ex) {
        log.warn("Şifre değiştirme: mevcut şifre yanlış.");
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("WRONG_CURRENT_PASSWORD")
                .message(msg("error.password.current.wrong"))
                .fieldErrors(Map.of("currentPassword", msg("error.password.current.wrong")))
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Keycloak realm-level sifre politikasi ihlal edildiginde 400 doner.
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPassword(InvalidPasswordException ex) {
        log.warn("Şifre değiştirme: yeni şifre politikayı ihlal etti.");
        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .error("INVALID_PASSWORD")
                .message(msg("error.password.policy"))
                .fieldErrors(Map.of("newPassword", msg("error.password.policy")))
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Agent kapasite limiti asimlarini conflict olarak dondurur.
    @ExceptionHandler(TicketLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleTicketLimitExceededException(TicketLimitExceededException ex) {
        log.warn("Bilet limiti aşıldı (409 CONFLICT): {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.CONFLICT.value())
                .error("TICKET_LIMIT_EXCEEDED")
                .message(msg(ex.getMessage(), ex.getMessageArgs()))
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
                .message(msg("error.resource.not.found", ex.getResourcePath()))
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
                .message(msg("error.unexpected", ex.getMessage()))
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
