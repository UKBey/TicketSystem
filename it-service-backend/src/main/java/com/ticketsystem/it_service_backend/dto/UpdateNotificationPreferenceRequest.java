package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bildirim tercihi güncelleme isteği — null gönderilen alanlar değiştirilmez")
public class UpdateNotificationPreferenceRequest {

    private Boolean emailOnTicketCreated;
    private Boolean emailOnTicketAssigned;
    private Boolean emailOnStatusChanged;
    private Boolean emailOnCommentAdded;
    private Boolean emailOnSlaWarning;
    private Boolean emailOnSlaBreached;
    private Boolean emailOnTicketResolved;
}
