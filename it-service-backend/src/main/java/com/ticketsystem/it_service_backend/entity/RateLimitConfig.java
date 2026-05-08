package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * DB-driven configuration for a single rate-limited endpoint.
 * <p>
 * endpointKey  – logical identifier (e.g. "CLAIM_TICKET") used as the
 *                cache key and interceptor lookup key.
 * maxRequests  – token-bucket capacity refilled every durationSeconds.
 * enabled      – kill switch; false → interceptor passes the request without checking.
 * updatedAt    – auto-maintained by Hibernate; used for audit / cache invalidation.
 */
@Entity
@Table(name = "rate_limit_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RateLimitConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "endpoint_key", nullable = false, unique = true, length = 100)
    private String endpointKey;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "max_requests", nullable = false)
    private int maxRequests;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
