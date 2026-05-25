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
/**
 * Partial-update payload for notification preferences; only non-null booleans are
 * applied so the UI can toggle a single channel without sending the full state.
 */
@Schema(description = "Notification preference update request — null fields are left unchanged")
public class UpdateNotificationPreferenceRequest {

    private Boolean emailOnTicketCreated;
    private Boolean emailOnTicketAssigned;
    private Boolean emailOnStatusChanged;
    private Boolean emailOnCommentAdded;
    private Boolean emailOnSlaWarning;
    private Boolean emailOnSlaBreached;
    private Boolean emailOnTicketResolved;

    private Boolean notifyOnTicketCreated;
    private Boolean notifyOnTicketAssigned;
    private Boolean notifyOnStatusChanged;
    private Boolean notifyOnCommentAdded;
    private Boolean notifyOnSlaWarning;
    private Boolean notifyOnSlaBreached;
    private Boolean notifyOnTicketResolved;
}
