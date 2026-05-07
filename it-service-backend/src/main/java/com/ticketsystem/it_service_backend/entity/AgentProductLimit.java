package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agent_product_limits",
       uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "product_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentProductLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "use_custom_limit", nullable = false)
    @Builder.Default
    private Boolean useCustomLimit = false;

    @Column(name = "max_active_tickets")
    private Integer maxActiveTickets;
}