package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

/**
 * Policy defining SLA targets per ticket priority.
 *
 * <p>One row per {@code priority} (unique) — on {@link Ticket} creation the target
 * resolution duration is read from here and {@code slaDeadline} is computed.
 * {@code warningThresholdHours} is consulted by the scheduler to trigger a warning
 * notification as the deadline approaches. Runs alongside the
 * {@code app.sla.policies} configuration on the application side.
 */
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

    /** Priority level: LOW, MEDIUM, HIGH, CRITICAL. */
    @Column(nullable = false, unique = true, length = 10)
    private String priority;

    /** Target resolution duration (hours). */
    @Column(name = "target_resolution_hours", nullable = false)
    private Integer targetResolutionHours;

    /**
     * The warning notification is sent this many hours before the deadline.
     * Default: 2 hours.
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
