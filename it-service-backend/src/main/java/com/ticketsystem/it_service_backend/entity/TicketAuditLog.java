package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * Append-only audit log for actions performed on a {@link Ticket}.
 *
 * <p>Events like status changes, claim/unclaim, SLA pause/resume and assignment
 * accumulate here; the old and new states are kept in {@code previousState}/
 * {@code newState}. Rows are never updated (immutable) — read-only for audit and
 * forensic purposes.
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

    /** Keycloak UUID of the acting user — may also be "system" for automated actions. */
    @Column(name = "actor_id", nullable = false, length = 36)
    private String actorId;

    /** Action type: STATUS_CHANGE, CLAIM, UNCLAIM, SLA_PAUSE, SLA_RESUME, etc. */
    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    /**
     * Canonical reason for the action (optional, used in reporting). The closed set is
     * {@link TicketReasonCode}; stored as a plain {@code String} — like {@link #actionType} —
     * because this table is append-only and reads must never fail on a retired code.
     */
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
