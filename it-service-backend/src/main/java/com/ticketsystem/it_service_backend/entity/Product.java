package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A product supported by IT services (e.g. "ERP", "CRM").
 *
 * <p>Has a many-to-many authorization relationship with {@link User} via the
 * {@code user_products} bridge; {@link Ticket}, {@link TicketTopic},
 * {@link KnownIssue} and {@link AgentProductLimit} all reference this product. The
 * {@code isActive} flag implements a soft-delete pattern — records are deactivated
 * rather than deleted.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Otomatik artan ID
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /** Default cap on the number of active tickets a single agent may hold for this product at one time. */
    @Column(name = "max_active_tickets")
    private Integer maxActiveTickets;
}