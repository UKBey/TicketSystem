package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "sla_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Öncelik seviyesi: LOW, MEDIUM, HIGH, CRITICAL */
    @Column(nullable = false, unique = true, length = 10)
    private String priority;

    /** Hedef çözüm süresi (saat) */
    @Column(name = "target_resolution_hours", nullable = false)
    private Integer targetResolutionHours;

    /**
     * Deadline'a bu kadar saat kala uyarı bildirimi gönderilir.
     * Varsayılan: 2 saat.
     */
    @Column(name = "warning_threshold_hours", nullable = false)
    @Builder.Default
    private Integer warningThresholdHours = 2;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
