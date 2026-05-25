package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * Bir agent'ın {@link Ticket} üzerinde harcadığı çalışma süresi kaydı.
 *
 * <p>Dakika cinsinden tutulur ve dashboard'larda toplam efor / agent verimlilik
 * raporları için aggregate edilir. Bir bilet birden fazla worklog'a sahip olabilir
 * (aynı agent farklı zamanlarda ekleyebilir).
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

    /** Harcanan süre — dakika cinsinden. */
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
