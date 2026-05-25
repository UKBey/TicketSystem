package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * Worklog entry capturing the time an agent has spent on a {@link Ticket}.
 *
 * <p>Stored in minutes and aggregated on dashboards for total-effort and
 * agent-productivity reports. A single ticket can have many worklogs — the same agent
 * may add entries at different times.
 */
@Entity
@Table(name = "ticket_worklogs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketWorklog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Column(name = "agent_id", nullable = false, length = 36)
    private String agentId;

    /** Time spent — in minutes. */
    @Column(nullable = false)
    private Integer minutes;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
