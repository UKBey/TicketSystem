package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentProductLimitRepository extends JpaRepository<AgentProductLimit, Long> {

    Optional<AgentProductLimit> findByAgentIdAndProductId(String agentId, Long productId);

    List<AgentProductLimit> findByAgentId(String agentId);

    void deleteByProductId(Long productId);
}