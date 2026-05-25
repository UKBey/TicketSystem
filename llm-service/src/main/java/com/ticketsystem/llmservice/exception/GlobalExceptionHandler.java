package com.ticketsystem.llmservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/**
 * REST katmanında ortaya çıkan istisnaları tek noktadan yakalayıp RFC 7807
 * {@link ProblemDetail} formatında istemciye dönen global hata yakalayıcı.
 *
 * <p>Groq rate-limit, kaynak bulunamadı ve beklenmedik çalışma zamanı hatalarına
 * uygun HTTP durum kodlarını ve mesajları üretir.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Groq token limiti aşıldığında 429 yanıt döner ve {@code retryAfterSeconds}
     * alanını {@link ProblemDetail} üzerine yazar.
     *
     * @param ex yakalanan rate-limit istisnası
     * @return 429 Too Many Requests gövdesi
     */
    @ExceptionHandler(GroqRateLimitException.class)
    public ProblemDetail handleRateLimit(GroqRateLimitException ex) {
        log.warn("Groq rate limit: {}s sonra tekrar denenebilir", ex.getRetryAfterSeconds());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Token limiti aşıldı. Lütfen birkaç saniye bekleyip tekrar deneyin."
        );
        pd.setType(URI.create("about:blank"));
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("retryAfterSeconds", Math.ceil(ex.getRetryAfterSeconds()));
        return pd;
    }

    /**
     * "Kaynak bulunamadı" semantiğini taşıyan {@link IllegalArgumentException}
     * çağrılarını 404 yanıta dönüştürür.
     *
     * @param ex yakalanan istisna
     * @return 404 Not Found gövdesi
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        log.warn("Kaynak bulunamadı: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("about:blank"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Diğer tüm beklenmeyen çalışma zamanı hatalarını 500 yanıta dönüştürür ve
     * stack trace'i loglar.
     *
     * @param ex yakalanan istisna
     * @return 500 Internal Server Error gövdesi
     */
    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntime(RuntimeException ex) {
        log.error("Beklenmeyen hata: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "İşlem sırasında bir hata oluştu: " + ex.getMessage()
        );
        pd.setType(URI.create("about:blank"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
