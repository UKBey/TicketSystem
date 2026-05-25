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
 * {@link RestControllerAdvice} that handles every exception in the
 * application from a single place.
 *
 * <p>Each exception type is mapped to an HTTP status and the standard
 * {@link ErrorResponse} body; the client always sees the same JSON shape.
 * Messages are translated to the client's locale through
 * {@link MessageSource}; keys missing from the bundle are returned as-is
 * (for backwards compatibility).
 *
 * <p>Expected business-rule errors (for example
 * {@link ResponseStatusException}, validation errors) are logged at
 * {@code WARN}; unexpected errors are logged at {@code ERROR}.
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
     * Forwards {@link ResponseStatusException}s deliberately thrown by the
     * service layer to the client with their original status code; the
     * {@code reason} field is interpreted as an i18n key.
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
     * Returns {@link IllegalArgumentException}s raised from the service
     * layer as HTTP {@code 400 Bad Request}. If the message is an i18n key
     * a translation is attempted.
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
     * Returns HTTP {@code 400} when a body parameter annotated with
     * {@code @Valid} fails validation; localized per-field error messages
     * are returned to the client under the {@code fieldErrors} map.
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
     * Returns HTTP {@code 400} when Bean Validation constraints on
     * {@code @RequestParam} / {@code @PathVariable} (such as {@code @Max},
     * {@code @Pattern}) are triggered. Not to be confused with
     * {@link MethodArgumentNotValidException} — that one is thrown for body
     * validation.
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
     * Returns the {@link AccessDeniedException} thrown by Spring Security as
     * HTTP {@code 403 Forbidden}. The attempt is recorded for audit
     * purposes; no detail is leaked to the client.
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
     * Returns HTTP {@code 409 Conflict} when an email/username clash occurs
     * in Keycloak. The {@code fieldErrors} map tells the frontend which
     * form field should display the error.
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
     * Returns HTTP {@code 400 Bad Request} when the current password is
     * entered incorrectly during the password change flow; the
     * {@code currentPassword} field is flagged explicitly so the frontend
     * shows the error under the corresponding input.
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
     * Returns HTTP {@code 400 Bad Request} when the Keycloak realm password
     * policy is violated; the policy message is displayed under the
     * {@code newPassword} field.
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
     * Returns HTTP {@code 409 Conflict} when the agent / product capacity
     * limit is exceeded; the message is translated from the i18n key
     * carried by the exception.
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
     * Produces a standard HTTP {@code 404 Not Found} response for an
     * unknown URI / wrong path, preserving the common JSON format instead
     * of falling back to the default Spring error page.
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
     * Last-resort safety net for every unexpected error — HTTP
     * {@code 500 Internal Server Error}. The stack trace is logged in
     * full and only a summary message is returned to the client.
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
