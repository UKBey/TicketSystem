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
    private String status; // Biletin is akisi durumunu tutar.

    @Column(nullable = false, length = 10)
    private String priority; // SLA suresini etkileyen oncelik seviyesi.

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId; // Kaydi olusturan kullanicinin kimligi.

    @Column(name = "assignee_id", length = 36)
    private String assigneeId; // Kayittan sorumlu agent kimligi.

    @Column(name = "sla_deadline")
    private ZonedDateTime slaDeadline;

    @Column(name = "sla_breached")
    @Builder.Default
    private Boolean slaBreached = false;

    // SLA'nin aktif gecen toplam suresi milisaniye cinsinden tutulur.
    @Column(name = "sla_elapsed_ms")
    @Builder.Default
    private Long slaElapsedMs = 0L;

    // SLA'nin en son durduruldugu zamani saklar.
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

    // jBPM tarafindaki process instance ile iliski kurar.
    @Column(name = "process_instance_id")
    private Long processInstanceId;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore // Cift yonlu iliskide olasi sonsuz JSON dongusunu engeller.
    private List<Attachment> attachments;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        if (this.status == null) {
            this.status = "NEW"; // Kayit olustugunda varsayilan baslangic durumu.
        }
    }
}