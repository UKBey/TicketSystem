package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link AgentProductLimit} — reads per-agent product-specific
 * active-ticket limit overrides and clears related overrides when a product is deleted.
 */
public interface AgentProductLimitRepository extends JpaRepository<AgentProductLimit, Long> {

    Optional<AgentProductLimit> findByAgentIdAndProductId(String agentId, Long productId);

    List<AgentProductLimit> findByAgentId(String agentId);

    void deleteByProductId(Long productId);
}