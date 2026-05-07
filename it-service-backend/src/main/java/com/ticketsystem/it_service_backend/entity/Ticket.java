package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, length = 10)
    private String priority;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "sla_deadline")
    private ZonedDateTime slaDeadline;

    @Column(name = "sla_breached")
    @Builder.Default
    private Boolean slaBreached = false;

    @Column(name = "sla_elapsed_ms")
    @Builder.Default
    private Long slaElapsedMs = 0L;

    @Column(name = "sla_paused_at")
    private ZonedDateTime slaPausedAt;

    @Column(name = "sla_resumed_at")
    private ZonedDateTime slaResumedAt;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    @Column(name = "closed_at")
    private ZonedDateTime closedAt;

    @Column(name = "process_instance_id")
    private Long processInstanceId;

    // Bileti sahiplenen ajanlar — cok-agentli claim yapisi.
    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnore
    @Builder.Default
    private List<TicketClaim> claims = new ArrayList<>();

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Attachment> attachments;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        if (this.status == null) {
            this.status = "NEW";
        }
    }
}
