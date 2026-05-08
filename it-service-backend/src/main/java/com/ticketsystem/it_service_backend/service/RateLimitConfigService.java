package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.config.CacheConfig;
import com.ticketsystem.it_service_backend.entity.RateLimitConfig;
import com.ticketsystem.it_service_backend.repository.RateLimitConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service responsible for reading and updating {@link RateLimitConfig} entries.
 *
 * <p>Read path: {@link #getConfig} is called by the interceptor on every request.
 * The Caffeine cache (TTL 5 min, fallback) keeps DB hits rare.
 *
 * <p>Write path: {@link #updateConfig} persists the new values, evicts the cache
 * entry for that endpointKey, then signals the caller to invalidate in-process buckets.
 * The bucket invalidation itself is done by {@code RateLimitInterceptor} after this
 * call returns (wired in Commit 6 controller).
 */
@Service
@RequiredArgsConstructor
public class RateLimitConfigService {

    private final RateLimitConfigRepository repository;

    /**
     * Returns the config for the given endpoint key.
     * Result is cached in the {@code rateLimitConfigs} Caffeine cache keyed by endpointKey.
     * Returns {@link Optional#empty()} when the key is not found — the interceptor
     * treats a missing config as "rate limiting disabled for this endpoint".
     */
    @Cacheable(cacheNames = CacheConfig.RATE_LIMIT_CONFIGS, key = "#endpointKey")
    @Transactional(readOnly = true)
    public Optional<RateLimitConfig> getConfig(String endpointKey) {
        return repository.findByEndpointKey(endpointKey);
    }

    /**
     * Persists updated values for an existing config entry.
     * Evicts the Caffeine cache for the affected endpointKey so the next
     * interceptor call re-reads from DB (and re-populates the cache).
     *
     * @param id             the PK of the config to update
     * @param maxRequests    new token-bucket capacity
     * @param durationSeconds new refill window in seconds
     * @param enabled        kill-switch flag
     * @return the saved {@link RateLimitConfig}
     * @throws EntityNotFoundException if no config exists with the given id
     */
    @CacheEvict(cacheNames = CacheConfig.RATE_LIMIT_CONFIGS, key = "#result.endpointKey")
    @Transactional
    public RateLimitConfig updateConfig(Long id, int maxRequests, int durationSeconds, boolean enabled) {
        RateLimitConfig config = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "RateLimitConfig not found with id: " + id));
        config.setMaxRequests(maxRequests);
        config.setDurationSeconds(durationSeconds);
        config.setEnabled(enabled);
        return repository.save(config);
    }

    /**
     * Returns all config entries. Used by the admin panel to populate the list.
     * Not cached — admin list calls are infrequent and must always show fresh data.
     */
    @Transactional(readOnly = true)
    public List<RateLimitConfig> getAllConfigs() {
        return repository.findAll();
    }
}
