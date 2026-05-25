package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * Müşteri memnuniyet anketi (CSAT) — {@link Ticket} kapandıktan sonra müşterinin
 * verdiği 1-5 arası puan ve opsiyonel yorum.
 *
 * <p>{@code ticket_id} unique — her bilet için yalnızca bir anket kaydedilebilir.
 * Dashboard KPI'leri (ortalama puan, priority/product bazlı dağılım) buradan beslenir.
 */
@Entity
@Table(name = "csat_surveys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Csat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", unique = true, nullable = false)
    private Long ticketId;

    /** 1-5 arası tamsayı puan (5 = en yüksek memnuniyet). */
    @Column(nullable = false)
    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private ZonedDateTime createdAt = ZonedDateTime.now();
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
