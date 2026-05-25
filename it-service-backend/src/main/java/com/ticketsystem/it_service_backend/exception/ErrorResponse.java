package com.ticketsystem.it_service_backend.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tum hata cevaplarinin ortak JSON govdesi.
 *
 * <p>{@link GlobalExceptionHandler} her exception tipi icin bu DTO'yu
 * builder'lar ile uretir. {@code status} HTTP kodunu, {@code error} bilinen bir
 * hata kategori adi (ornek {@code USER_ALREADY_EXISTS}), {@code message}
 * locale'a gore cevrilmis mesaj, {@code fieldErrors} ise dogrulama hatalarinda
 * her alan icin ayri hata metnini tasir. Sade primitive alanlar kullanildigi
 * icin OpenAPI ve istemci JSON parser'lari sorunsuz isler.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private int status;
    private String error;
    private String message;
    private long timestamp;
    private Map<String, String> fieldErrors;
}
