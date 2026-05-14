package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

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

    @Column(name = "actor_id", nullable = false, length = 36)
    private String actorId;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "previous_state", length = 30)
    private String previousState;

    @Column(name = "new_state", length = 30)
    private String newState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
