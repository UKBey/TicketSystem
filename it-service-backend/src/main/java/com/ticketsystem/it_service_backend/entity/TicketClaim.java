package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ticket_claims",
       uniqueConstraints = @UniqueConstraint(columnNames = {"ticket_id", "agent_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @Column(name = "agent_id", nullable = false, length = 255)
    private String agentId;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    @PrePersist
    protected void onCreate() {
        if (this.claimedAt == null) {
            this.claimedAt = LocalDateTime.now();
        }
    }
}
