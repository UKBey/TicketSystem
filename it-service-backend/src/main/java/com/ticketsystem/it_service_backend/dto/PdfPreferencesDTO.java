package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The user's last-used PDF export modal selections (which sections, PDF language, PDF
 * theme). The backend treats {@code preferences} as an opaque JSON string owned by the
 * frontend; only its length is bounded. Used by both GET and PUT on
 * {@code /api/v1/users/me/pdf-preferences}.
 *
 * <p>Only {@code @Data} (no all-args constructor): a single-arg Lombok constructor on a
 * one-field class is misdetected by Jackson's parameter-names module as a delegating
 * creator, which silently leaves {@code preferences} null on bind. {@code @Data} alone
 * gives Jackson the no-args constructor + setter path (same as the other request DTOs).
 */
@Data
@Schema(description = "Kullanıcının PDF dışa aktarma tercihleri (opak JSON string)")
public class PdfPreferencesDTO {

    @Size(max = 2000, message = "PDF preferences payload too large (max 2000 characters)")
    @Schema(description = "Frontend'in tanımladığı JSON string; bölümler + dil + tema", nullable = true)
    private String preferences;
}
