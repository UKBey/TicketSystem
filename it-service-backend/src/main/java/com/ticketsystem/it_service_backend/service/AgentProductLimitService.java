package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentProductLimitResponseDTO;
import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Manages the per-agent + product custom concurrent ticket limit.
 *
 * <p>When no custom limit is set, the agent uses the product's default
 * {@code maxActiveTickets}. Sending {@code useCustomLimit=false} deletes the
 * existing custom record and the agent falls back to the default. Used by
 * AGENT_ADMIN.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class AgentProductLimitService {

    private final AgentProductLimitRepository agentProductLimitRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Returns the agent's per-product custom limit records as a DTO list.
     *
     * @param agentId agent ID
     * @return list of limit DTOs (may be empty)
     */
    @Transactional(readOnly = true)
    public List<AgentProductLimitResponseDTO> getAgentLimits(String agentId) {
        log.debug("Agent limitleri getiriliyor. Agent: {}", agentId);
        return agentProductLimitRepository.findByAgentId(agentId).stream()
                .map(AgentProductLimitResponseDTO::fromEntity)
                .toList();
    }

    /**
     * Sets or removes the custom limit for an agent + product pairing.
     *
     * <p>If {@code useCustomLimit=false} is supplied, the existing custom record
     * is deleted and the agent falls back to the product's default limit. If
     * {@code true}, {@code maxActiveTickets} is required.
     *
     * @param agentId agent ID
     * @param productId product ID
     * @param useCustomLimit whether the custom limit is active
     * @param maxActiveTickets value to apply when the custom limit is active
     * @return DTO reflecting the resulting state
     * @throws ResponseStatusException 404 if agent or product is not found
     * @throws IllegalArgumentException if a custom limit is requested without a value
     */
    @Transactional
    public AgentProductLimitResponseDTO setAgentLimit(String agentId, Long productId, boolean useCustomLimit,
                                                      Integer maxActiveTickets) {
        log.info("Agent limit güncelleme. Agent: {}, Product: {}, Custom: {}, Limit: {}",
                agentId, productId, useCustomLimit, maxActiveTickets);

        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.user.not.found"));
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.product.not.found"));

        if (useCustomLimit && maxActiveTickets == null) {
            throw new IllegalArgumentException("error.agent.limit.required.when.custom");
        }

        AgentProductLimit existing = agentProductLimitRepository
                .findByAgentIdAndProductId(agentId, productId)
                .orElse(null);

        if (!useCustomLimit) {
            if (existing != null) {
                agentProductLimitRepository.delete(existing);
            }
            return AgentProductLimitResponseDTO.fromProduct(agent.getId(), product, false, null);
        }

        AgentProductLimit limit = existing != null ? existing : AgentProductLimit.builder()
                .agentId(agent.getId())
                .product(product)
                .build();
        limit.setAgentId(agent.getId());
        limit.setProduct(product);
        limit.setUseCustomLimit(true);
        limit.setMaxActiveTickets(maxActiveTickets);

        AgentProductLimit saved = agentProductLimitRepository.save(limit);
        return AgentProductLimitResponseDTO.fromEntity(saved);
    }

    /**
     * Deletes the custom limit record for the given agent + product pairing.
     * Silently no-ops when no record exists.
     *
     * @param agentId agent ID
     * @param productId product ID
     */
    @Transactional
    public void deleteAgentLimit(String agentId, Long productId) {
        log.info("Agent limit siliniyor. Agent: {}, Product: {}", agentId, productId);

        agentProductLimitRepository.findByAgentIdAndProductId(agentId, productId)
                .ifPresent(agentProductLimitRepository::delete);
    }
}