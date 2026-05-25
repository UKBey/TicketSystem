package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.NotificationPreference;
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
 * Response payload exposing a user's notification preferences for both email and
 * in-app channels. Returned by the preferences endpoint and used by the frontend
 * settings UI; defaults are produced via {@link #defaults()} when no row exists.
 */
@Schema(description = "User notification preferences for email and in-app channels")
public class NotificationPreferenceResponse {

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

    public static NotificationPreferenceResponse fromEntity(NotificationPreference p) {
        return NotificationPreferenceResponse.builder()
                .emailOnTicketCreated(p.getEmailOnTicketCreated())
                .emailOnTicketAssigned(p.getEmailOnTicketAssigned())
                .emailOnStatusChanged(p.getEmailOnStatusChanged())
                .emailOnCommentAdded(p.getEmailOnCommentAdded())
                .emailOnSlaWarning(p.getEmailOnSlaWarning())
                .emailOnSlaBreached(p.getEmailOnSlaBreached())
                .emailOnTicketResolved(p.getEmailOnTicketResolved())
                .notifyOnTicketCreated(p.getNotifyOnTicketCreated())
                .notifyOnTicketAssigned(p.getNotifyOnTicketAssigned())
                .notifyOnStatusChanged(p.getNotifyOnStatusChanged())
                .notifyOnCommentAdded(p.getNotifyOnCommentAdded())
                .notifyOnSlaWarning(p.getNotifyOnSlaWarning())
                .notifyOnSlaBreached(p.getNotifyOnSlaBreached())
                .notifyOnTicketResolved(p.getNotifyOnTicketResolved())
                .build();
    }

    public static NotificationPreferenceResponse defaults() {
        return NotificationPreferenceResponse.builder()
                .emailOnTicketCreated(true)
                .emailOnTicketAssigned(true)
                .emailOnStatusChanged(true)
                .emailOnCommentAdded(true)
                .emailOnSlaWarning(true)
                .emailOnSlaBreached(true)
                .emailOnTicketResolved(true)
                .notifyOnTicketCreated(true)
                .notifyOnTicketAssigned(true)
                .notifyOnStatusChanged(true)
                .notifyOnCommentAdded(true)
                .notifyOnSlaWarning(true)
                .notifyOnSlaBreached(true)
                .notifyOnTicketResolved(true)
                .build();
    }
}
