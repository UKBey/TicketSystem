package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.RateLimitConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Data access layer for {@link RateLimitConfig}.
 * <p>
 * {@code findByEndpointKey} is the primary lookup used by
 * {@code RateLimitConfigService} (called on every intercepted request,
 * but shielded by Caffeine cache so DB hits are rare).
 */
@Repository
public interface RateLimitConfigRepository extends JpaRepository<RateLimitConfig, Long> {

    Optional<RateLimitConfig> findByEndpointKey(String endpointKey);
}
