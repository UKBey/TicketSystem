package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

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
