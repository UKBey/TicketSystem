package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
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
    private String status; // NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED, CLOSED

    @Column(nullable = false, length = 10)
    private String priority; // LOW, MEDIUM, HIGH

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId; // Keycloak'tan gelen User ID

    @Column(name = "assignee_id", length = 36)
    private String assigneeId; // Keycloak'tan gelen Agent ID

    @Column(name = "sla_deadline")
    private ZonedDateTime slaDeadline;

    @Column(name = "sla_breached")
    @Builder.Default
    private Boolean slaBreached = false;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    @Column(name = "closed_at")
    private ZonedDateTime closedAt;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore // Sonsuz döngüyü önlemek için listeyi JSON'da gizleyelim (veya DTO kullanın)
    private List<Attachment> attachments;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        if (this.status == null) {
            this.status = "NEW"; // Varsayılan statü
        }
    }
}