package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Support ticket — the system's main business object.
 *
 * <p>Related to {@link User} (customer), {@link Product} and {@link TicketTopic}.
 * Ownership is modeled many-to-many through {@link TicketClaim}; comments and
 * attachments live in separate tables ({@link Comment}, {@link Attachment}). The
 * lifecycle is synchronized with the jBPM process engine ({@code processInstanceId})
 * and SLA state is kept in denormalized fields ({@code slaDeadline},
 * {@code slaBreached}, {@code slaElapsedMs}).
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

    /** Lifecycle status: NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED, CLOSED. */
    @Column(nullable = false, length = 30)
    private String status;

    /** Priority: LOW, MEDIUM, HIGH, CRITICAL — drives the SLA target duration. */
    @Column(nullable = false, length = 10)
    private String priority;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "topic_id")
    private Long topicId;

    /**
     * Snapshot of the topic name at the time the ticket was created. Even if the
     * {@link TicketTopic} is later deleted or renamed, the original name remains
     * visible on the ticket detail.
     */
    @Column(name = "topic_name_snapshot")
    private String topicNameSnapshot;

    /** Keycloak UUID — corresponds to {@link User#id} (denormalized, not an FK). */
    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(name = "sla_deadline")
    private ZonedDateTime slaDeadline;

    @Column(name = "sla_breached")
    @Builder.Default
    private Boolean slaBreached = false;

    /**
     * Stamped once the threshold-based "SLA approaching" warning email has been sent.
     * The scheduler picks up tickets where this column is NULL, so a second scan will
     * not resend the email.
     */
    @Column(name = "sla_warning_sent_at")
    private ZonedDateTime slaWarningSentAt;

    /** Cumulative elapsed time on the SLA counter so far (ms) — updated when the timer is paused. */
    @Column(name = "sla_elapsed_ms")
    @Builder.Default
    private Long slaElapsedMs = 0L;

    /** Moment the counter was paused; while non-null the SLA timer is stopped (WAITING_FOR_CUSTOMER). */
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

    /** Process instance bound to this ticket on the jBPM KIE Server — null means no process has been started. */
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
