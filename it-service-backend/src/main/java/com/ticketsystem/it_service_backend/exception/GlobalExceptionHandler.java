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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Uygulama genelindeki tum exception'lari merkezden ele alan
 * {@link RestControllerAdvice}.
 *
 * <p>Her exception turu bir HTTP statusune ve standart {@link ErrorResponse}
 * govdesine eslestirilir; istemci her zaman ayni JSON sekli ile karsilasir.
 * Mesajlar {@link MessageSource} uzerinden istemcinin locale'ine gore
 * cevrilir; bundle'da olmayan anahtarlar olduklari gibi geri donulur
 * (geriye-donuk uyumluluk amaciyla).
 *
 * <p>Beklenen is kurali hatalari (ornek: {@link ResponseStatusException},
 * dogrulama hatalari) {@code WARN} seviyesinde, beklenmeyen hatalar
 * {@code ERROR} seviyesinde loglanir.
 */
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

    /**
     * Servis katmaninin bilerek firlattigi {@link ResponseStatusException}'lari
     * orijinal status koduyla istemciye iletir; {@code reason} alani i18n
     * anahtari olarak yorumlanir.
     */
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

    /**
     * Servis katmanindan gelen {@link IllegalArgumentException}'lari HTTP
     * {@code 400 Bad Request} olarak dondurur. Mesaj bir i18n anahtari ise
     * cevirisi denenir.
     */
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

    /**
     * {@code @Valid} ile isaretli body parametrelerinde dogrulama hatasi
     * oldugunda HTTP {@code 400} doner; her field icin lokalize edilmis hata
     * mesaji {@code fieldErrors} map'i altinda istemciye iletilir.
     */
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

    /**
     * {@code @RequestParam} / {@code @PathVariable} uzerindeki Bean Validation
     * kisitlamalari (ornek {@code @Max}, {@code @Pattern}) tetiklendiginde HTTP
     * {@code 400} doner. {@link MethodArgumentNotValidException} ile karistirilmamali —
     * o body validation icin atilir.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String path = v.getPropertyPath().toString();
            // propertyPath = "methodName.paramName" — sadece son segmenti kullan
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fieldErrors.put(field, v.getMessage());
        }

        log.warn("Parametre Doğrulama Hatası (400 BAD_REQUEST): {}", fieldErrors);

        ErrorResponse error = ErrorResponse.builder()
                .status(HttpStatus.BAD_REQUEST.value())
                .message(msg("error.validation.failed"))
                .fieldErrors(fieldErrors)
                .timestamp(System.currentTimeMillis())
                .build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Spring Security'nin firlattigi {@link AccessDeniedException}'i HTTP
     * {@code 403 Forbidden} olarak dondurur. Denetim amacli olarak deneme
     * kaydedilir; istemciye detay sizdirilmaz.
     */
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

    /**
     * Keycloak'ta email/username cakismasi durumunda HTTP {@code 409 Conflict}
     * doner. {@code fieldErrors} map'i frontend'in hangi form alaninda hata
     * gostermesi gerektigini soyler.
     */
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

    /**
     * Sifre degistirme akisinda mevcut sifre yanlis girilirse HTTP
     * {@code 400 Bad Request} doner; {@code currentPassword} field'i ozellikle
     * isaretlenir, frontend hata mesajini ilgili input altinda gosterir.
     */
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

    /**
     * Keycloak realm sifre politikasinin ihlal edildigi durumda HTTP
     * {@code 400 Bad Request} doner; {@code newPassword} field'inin altinda
     * politika mesaji gosterilir.
     */
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

    /**
     * Agent / urun kapasite limiti asildiginda HTTP {@code 409 Conflict}
     * doner; mesaj exception'in tasidigi i18n anahtarindan cevrilir.
     */
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

    /**
     * Bilinmeyen URI / yanlis path geldiginde standart HTTP {@code 404 Not Found}
     * cevabi uretir; varsayilan Spring hata sayfasi yerine ortak JSON formatini
     * korur.
     */
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

    /**
     * Beklenmeyen tum hatalari yakalayan son guvenlik agi — HTTP
     * {@code 500 Internal Server Error}. Stacktrace tam olarak loglanir,
     * istemciye yalnizca ozet mesaj donulur.
     */
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
