package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    /**
     * Legacy fully-rendered message text. Populated for pre-V33 rows only; new
     * rows store {@link #messageKey} + {@link #messageArgs} and leave this null.
     * Rendered at read time as a fallback when {@code messageKey} is null.
     */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** MessageSource key for the notification text (e.g. notification.ticket.created). */
    @Column(name = "message_key", length = 160)
    private String messageKey;

    /** Ordered substitution arguments for {@link #messageKey}, stored as a JSON array. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message_args", columnDefinition = "jsonb")
    private List<String> messageArgs;

    @Column(name = "is_read")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private NotificationType type = NotificationType.GENERAL;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "email_sent")
    @Builder.Default
    private Boolean emailSent = false;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
