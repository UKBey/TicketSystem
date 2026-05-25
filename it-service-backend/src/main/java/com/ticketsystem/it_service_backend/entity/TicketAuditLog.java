package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * Bir {@link Ticket} üzerinde yapılan eylemlerin append-only denetim kaydı.
 *
 * <p>Status değişiklikleri, claim/unclaim, SLA pause/resume, atama gibi olaylar
 * burada birikir; eski/yeni durum {@code previousState}/{@code newState}'te tutulur.
 * Hiç güncellenmez (immutable) — denetim/forensik amaçlı sadece okuma.
 */
@Entity
@Table(name = "ticket_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    /** Eylemi yapan kullanıcının Keycloak UUID'si — sistem otomasyonu için "system" da olabilir. */
    @Column(name = "actor_id", nullable = false, length = 36)
    private String actorId;

    /** Eylem türü: STATUS_CHANGE, CLAIM, UNCLAIM, SLA_PAUSE, SLA_RESUME vb. */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /** Eylemin gerekçe kodu (örn. WAITING_FOR_CUSTOMER) — opsiyonel, raporlamada kullanılır. */
    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "previous_state", length = 255)
    private String previousState;

    @Column(name = "new_state", length = 255)
    private String newState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
