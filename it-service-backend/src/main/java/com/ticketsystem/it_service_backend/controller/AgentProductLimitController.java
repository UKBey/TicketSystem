package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AgentProductLimitRequestDTO;
import com.ticketsystem.it_service_backend.dto.AgentProductLimitResponseDTO;
import com.ticketsystem.it_service_backend.service.AgentProductLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent bazında ürün limit override'larının REST kontrolcüsü.
 *
 * <p>{@code AGENT_ADMIN} ve {@code MANAGER} rolleri, belirli bir agent için varsayılan
 * ürün limitlerini özelleştirip silebilir. İş kuralları {@link AgentProductLimitService}
 * tarafında çözümlenir.
 */
@Log4j2
@Tag(name = "Agent Ürün Limitleri", description = "Agent bazında ürün limit override yönetimi")
@RestController
@RequestMapping("/api/v1/agents/{agentId}/limits")
@RequiredArgsConstructor
public class AgentProductLimitController {

    private final AgentProductLimitService agentProductLimitService;

    /**
     * Belirtilen agent için tanımlı tüm ürün limit override kayıtlarını döner.
     *
     * @param agentId agent kimliği (Keycloak UUID)
     * @return agent'in tüm ürün limit override'larının listesi
     */
    @Operation(summary = "Agent limitlerini listele", description = "Belirtilen agent için tüm ürün limit override'larını döner.")
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<List<AgentProductLimitResponseDTO>> getAgentLimits(
            @Parameter(description = "Agent kimliği", required = true)
            @PathVariable String agentId) {
        log.info("Agent limit listesi isteği. Agent: {}", agentId);
        return ResponseEntity.ok(agentProductLimitService.getAgentLimits(agentId));
    }

    /**
     * Belirli bir ürün için agent override limitini oluşturur veya günceller.
     *
     * @param agentId agent kimliği
     * @param productId ürün kimliği
     * @param request {@code useCustomLimit} bayrağı ve isteğe bağlı {@code maxActiveTickets} değeri
     * @return oluşturulan/güncellenen limit kaydı
     */
    @Operation(summary = "Agent limitini ayarla", description = "Belirli bir ürün için agent override limitini oluşturur veya günceller.")
    @PutMapping("/{productId}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<AgentProductLimitResponseDTO> setAgentLimit(
            @Parameter(description = "Agent kimliği", required = true)
            @PathVariable String agentId,
            @Parameter(description = "Ürün kimliği", required = true)
            @PathVariable Long productId,
            @RequestBody AgentProductLimitRequestDTO request) {
        log.info("Agent limit ayarlama isteği. Agent: {}, Product: {}", agentId, productId);
        return ResponseEntity.ok(agentProductLimitService.setAgentLimit(
                agentId,
                productId,
                request.isUseCustomLimit(),
                request.getMaxActiveTickets()));
    }

    /**
     * Belirtilen agent/ürün override kaydını kalıcı olarak siler.
     *
     * @param agentId agent kimliği
     * @param productId ürün kimliği
     * @return {@code 204 No Content} silme başarılıysa
     */
    @Operation(summary = "Agent limitini sil", description = "Belirtilen agent/product override kaydını siler.")
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteAgentLimit(
            @Parameter(description = "Agent kimliği", required = true)
            @PathVariable String agentId,
            @Parameter(description = "Ürün kimliği", required = true)
            @PathVariable Long productId) {
        log.info("Agent limit silme isteği. Agent: {}, Product: {}", agentId, productId);
        agentProductLimitService.deleteAgentLimit(agentId, productId);
        return ResponseEntity.noContent().build();
    }
}