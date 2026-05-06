package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "user_notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreference {

    @Id
    @Column(length = 36)
    private String userId;

    @Column(name = "email_on_ticket_created", nullable = false)
    @Builder.Default
    private Boolean emailOnTicketCreated = true;

    @Column(name = "email_on_ticket_assigned", nullable = false)
    @Builder.Default
    private Boolean emailOnTicketAssigned = true;

    @Column(name = "email_on_status_changed", nullable = false)
    @Builder.Default
    private Boolean emailOnStatusChanged = true;

    @Column(name = "email_on_comment_added", nullable = false)
    @Builder.Default
    private Boolean emailOnCommentAdded = true;

    @Column(name = "email_on_sla_warning", nullable = false)
    @Builder.Default
    private Boolean emailOnSlaWarning = true;

    @Column(name = "email_on_sla_breached", nullable = false)
    @Builder.Default
    private Boolean emailOnSlaBreached = true;

    @Column(name = "email_on_ticket_resolved", nullable = false)
    @Builder.Default
    private Boolean emailOnTicketResolved = true;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
