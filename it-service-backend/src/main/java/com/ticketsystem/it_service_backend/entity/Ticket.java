package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Destek bileti — sistemin ana iş nesnesi.
 *
 * <p>{@link User} (müşteri), {@link Product} ve {@link TicketTopic} ile ilişkilidir.
 * Sahiplenme {@link TicketClaim} üzerinden many-to-many olarak tutulur; yorumlar ve
 * ekler ayrı tablolarda ({@link Comment}, {@link Attachment}). Yaşam döngüsü jBPM
 * süreç motoruyla senkronize edilir ({@code processInstanceId}) ve SLA durumu
 * denormalize alanlarla ({@code slaDeadline}, {@code slaBreached}, {@code slaElapsedMs})
 * tutulur.
 */
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

    /** Yaşam döngüsü durumu: NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED, CLOSED. */
    @Column(nullable = false, length = 30)
    private String status;

    /** Öncelik: LOW, MEDIUM, HIGH, CRITICAL — SLA hedef süresini belirler. */
    @Column(nullable = false, length = 10)
    private String priority;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "topic_id")
    private Long topicId;

    /**
     * Topic adının bilet oluşturulduğu andaki snapshot'ı. {@link TicketTopic} silinse
     * ya da yeniden adlandırılsa bile bilet detayında orijinal isim görünür.
     */
    @Column(name = "topic_name_snapshot")
    private String topicNameSnapshot;

    /** Keycloak UUID'si — {@link User#id}'ye karşılık gelir (FK değil, denormalize). */
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "sla_deadline")
    private ZonedDateTime slaDeadline;

    @Column(name = "sla_breached")
    @Builder.Default
    private Boolean slaBreached = false;

    /**
     * Threshold-based SLA "yaklaşıyor" uyarı maili gönderildiyse damgalanır.
     * Scheduler bu sütunu NULL olan biletleri seçer; ikinci tarama tekrar mail atmaz.
     */
    @Column(name = "sla_warning_sent_at")
    private ZonedDateTime slaWarningSentAt;

    /** SLA sayacının şu ana kadar geçen kümülatif süresi (ms) — duraklatma toplamasıyla güncellenir. */
    @Column(name = "sla_elapsed_ms")
    @Builder.Default
    private Long slaElapsedMs = 0L;

    /** Sayacın duraklatıldığı an; non-null iken SLA sayacı durmuş demektir (WAITING_FOR_CUSTOMER). */
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

    /** jBPM KIE Server'da bu bilete bağlı süreç örneği — null ise süreç başlatılmamış. */
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
