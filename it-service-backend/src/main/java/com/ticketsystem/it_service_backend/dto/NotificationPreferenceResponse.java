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
@Schema(description = "Kullanıcı e-posta bildirim tercihleri")
public class NotificationPreferenceResponse {

    @Schema(description = "Bilet oluşturulduğunda e-posta gönderilsin mi?")
    private Boolean emailOnTicketCreated;

    @Schema(description = "Bilet atandığında e-posta gönderilsin mi?")
    private Boolean emailOnTicketAssigned;

    @Schema(description = "Bilet durumu değiştiğinde e-posta gönderilsin mi?")
    private Boolean emailOnStatusChanged;

    @Schema(description = "Yorum eklendiğinde e-posta gönderilsin mi?")
    private Boolean emailOnCommentAdded;

    @Schema(description = "SLA uyarısında e-posta gönderilsin mi?")
    private Boolean emailOnSlaWarning;

    @Schema(description = "SLA ihlalinde e-posta gönderilsin mi?")
    private Boolean emailOnSlaBreached;

    @Schema(description = "Bilet çözüldüğünde e-posta gönderilsin mi?")
    private Boolean emailOnTicketResolved;

    public static NotificationPreferenceResponse fromEntity(NotificationPreference p) {
        return NotificationPreferenceResponse.builder()
                .emailOnTicketCreated(p.getEmailOnTicketCreated())
                .emailOnTicketAssigned(p.getEmailOnTicketAssigned())
                .emailOnStatusChanged(p.getEmailOnStatusChanged())
                .emailOnCommentAdded(p.getEmailOnCommentAdded())
                .emailOnSlaWarning(p.getEmailOnSlaWarning())
                .emailOnSlaBreached(p.getEmailOnSlaBreached())
                .emailOnTicketResolved(p.getEmailOnTicketResolved())
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
                .build();
    }
}
