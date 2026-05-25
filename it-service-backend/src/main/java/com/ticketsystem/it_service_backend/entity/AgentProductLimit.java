package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-{@link Product} active-ticket limit override for a single agent.
 *
 * <p>By default {@link Product#getMaxActiveTickets()} applies; when an agent has
 * {@code useCustomLimit=true}, the {@code maxActiveTickets} value stored here takes
 * precedence and is checked at claim time. {@code (agent_id, product_id)} is unique.
 */
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

    /** When true, {@code maxActiveTickets} is used; when false, the product's default limit applies. */
    @Column(name = "use_custom_limit", nullable = false)
    @Builder.Default
    private Boolean useCustomLimit = false;

    /** Maximum number of active tickets this agent may have on this product while the override is active. */
    @Column(name = "max_active_tickets")
    private Integer maxActiveTickets;
}