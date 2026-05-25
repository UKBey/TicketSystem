package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Bir agent için tek bir {@link Product} bazında aktif bilet limit override'ı.
 *
 * <p>Varsayılan olarak {@link Product#getMaxActiveTickets()} geçerlidir; bir agent
 * için {@code useCustomLimit=true} ise burada tutulan {@code maxActiveTickets} değeri
 * üstün gelir ve claim sırasında kontrol edilir. {@code (agent_id, product_id)} unique.
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

    /** true ise {@code maxActiveTickets} kullanılır; false ise ürünün varsayılan limiti geçerli. */
    @Column(name = "use_custom_limit", nullable = false)
    @Builder.Default
    private Boolean useCustomLimit = false;

    /** Override aktifken bu agent'ın bu ürün için açabileceği maksimum aktif bilet sayısı. */
    @Column(name = "max_active_tickets")
    private Integer maxActiveTickets;
}