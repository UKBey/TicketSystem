package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ticket topic/category under a {@link Product} (e.g. "Installation", "License").
 *
 * <p>{@link Ticket} references a topic, and at creation time the topic name is also
 * snapshotted as {@link Ticket#getTopicNameSnapshot()}. Topics with {@code isActive=false}
 * are hidden when opening new tickets, but existing tickets keep their reference.
 * {@code (product_id, name)} is unique — two topics with the same name cannot exist
 * under the same product.
 */
@Entity
@Table(name = "ticket_topics",
        uniqueConstraints = @UniqueConstraint(name = "uq_ticket_topics_product_name", columnNames = {"product_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
