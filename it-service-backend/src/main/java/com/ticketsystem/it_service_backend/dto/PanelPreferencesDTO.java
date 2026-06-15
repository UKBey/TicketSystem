package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The user's sidebar ticket-panel visibility selections (workspace, pool, history, team,
 * all-tickets). The backend treats {@code preferences} as an opaque JSON string owned by
 * the frontend; only its length is bounded. Used by PUT on
 * {@code /api/v1/users/me/panel-preferences}.
 *
 * <p>Only {@code @Data} (no all-args constructor): a single-arg Lombok constructor on a
 * one-field class is misdetected by Jackson's parameter-names module as a delegating
 * creator, which silently leaves {@code preferences} null on bind. {@code @Data} alone
 * gives Jackson the no-args constructor + setter path (same as the other request DTOs).
 */
@Data
@Schema(description = "Kullanıcının sidebar ticket-panel görünürlük tercihleri (opak JSON string)")
public class PanelPreferencesDTO {

    @Size(max = 500, message = "Panel preferences payload too large (max 500 characters)")
    @Schema(description = "Frontend'in tanımladığı JSON string; panel anahtarı → görünür mü", nullable = true)
    private String preferences;
}
