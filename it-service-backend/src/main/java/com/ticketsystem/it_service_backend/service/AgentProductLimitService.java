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
 * Bir ajan + ürün kombinasyonu için özel eşzamanlı bilet limitini yönetir.
 *
 * <p>Custom limit set edilmediğinde ajan, ürünün varsayılan {@code maxActiveTickets}
 * değerini kullanır. {@code useCustomLimit=false} gönderildiğinde mevcut özel limit
 * kaydı silinir ve ajan default'a düşer. AGENT_ADMIN tarafından kullanılır.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class AgentProductLimitService {

    private final AgentProductLimitRepository agentProductLimitRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    /**
     * Verilen ajanın tüm ürün-spesifik özel limit kayıtlarını DTO listesi olarak döner.
     *
     * @param agentId ajan ID
     * @return limit kayıtlarının DTO listesi (boş olabilir)
     */
    @Transactional(readOnly = true)
    public List<AgentProductLimitResponseDTO> getAgentLimits(String agentId) {
        log.debug("Agent limitleri getiriliyor. Agent: {}", agentId);
        return agentProductLimitRepository.findByAgentId(agentId).stream()
                .map(AgentProductLimitResponseDTO::fromEntity)
                .toList();
    }

    /**
     * Bir ajan + ürün eşleşmesi için özel limiti ayarlar veya kaldırır.
     *
     * <p>{@code useCustomLimit=false} verilirse mevcut özel limit silinir ve
     * ajan ürünün default limitine düşer. {@code true} verilirse
     * {@code maxActiveTickets} zorunludur.
     *
     * @param agentId ajan ID
     * @param productId ürün ID
     * @param useCustomLimit özel limit aktif mi
     * @param maxActiveTickets özel limit aktifse uygulanacak değer
     * @return son durumu yansıtan DTO
     * @throws ResponseStatusException 404 — ajan veya ürün bulunamazsa
     * @throws IllegalArgumentException özel limit istenip değer verilmezse
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
     * Belirtilen ajan + ürün özel limit kaydını siler. Kayıt yoksa sessiz no-op.
     *
     * @param agentId ajan ID
     * @param productId ürün ID
     */
    @Transactional
    public void deleteAgentLimit(String agentId, Long productId) {
        log.info("Agent limit siliniyor. Agent: {}, Product: {}", agentId, productId);

        agentProductLimitRepository.findByAgentIdAndProductId(agentId, productId)
                .ifPresent(agentProductLimitRepository::delete);
    }
}