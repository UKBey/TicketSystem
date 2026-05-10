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

@Log4j2
@Service
@RequiredArgsConstructor
public class AgentProductLimitService {

    private final AgentProductLimitRepository agentProductLimitRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AgentProductLimitResponseDTO> getAgentLimits(String agentId) {
        log.info("Agent limitleri getiriliyor. Agent: {}", agentId);
        return agentProductLimitRepository.findByAgentId(agentId).stream()
                .map(AgentProductLimitResponseDTO::fromEntity)
                .toList();
    }

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

    @Transactional
    public void deleteAgentLimit(String agentId, Long productId) {
        log.info("Agent limit siliniyor. Agent: {}, Product: {}", agentId, productId);

        agentProductLimitRepository.findByAgentIdAndProductId(agentId, productId)
                .ifPresent(agentProductLimitRepository::delete);
    }
}