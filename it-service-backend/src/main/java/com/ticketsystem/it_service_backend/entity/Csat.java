package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * Customer satisfaction (CSAT) survey — a 1-5 rating and optional comment submitted
 * by the customer after the {@link Ticket} is closed.
 *
 * <p>{@code ticket_id} is unique — only one survey can be recorded per ticket.
 * Dashboard KPIs (average score, breakdown by priority/product) are derived from here.
 */
@Entity
@Table(name = "csat_surveys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Csat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", unique = true, nullable = false)
    private Long ticketId;

    /** Integer rating from 1 to 5 (5 = highest satisfaction). */
    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
