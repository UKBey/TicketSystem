package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AgentProductLimitRequestDTO;
import com.ticketsystem.it_service_backend.dto.AgentProductLimitResponseDTO;
import com.ticketsystem.it_service_backend.service.AgentProductLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for per-agent product limit overrides.
 *
 * <p>The {@code ADMIN} role can customize and delete the default
 * product limits for a specific agent. Business rules are resolved by
 * {@link AgentProductLimitService}.
 */
@Log4j2
@Tag(name = "Agent Ürün Limitleri", description = "Agent bazında ürün limit override yönetimi")
@RestController
@RequestMapping("/api/v1/agents/{agentId}/limits")
@RequiredArgsConstructor
public class AgentProductLimitController {

    private final AgentProductLimitService agentProductLimitService;

    /**
     * Returns all product limit override records defined for the given agent.
     *
     * @param agentId agent identifier (Keycloak UUID)
     * @return list of all product limit overrides for the agent
     */
    @Operation(summary = "Agent limitlerini listele", description = "Belirtilen agent için tüm ürün limit override'larını döner.")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AgentProductLimitResponseDTO>> getAgentLimits(
            @Parameter(description = "Agent kimliği", required = true)
            @PathVariable String agentId) {
        log.info("Agent limit listesi isteği. Agent: {}", agentId);
        return ResponseEntity.ok(agentProductLimitService.getAgentLimits(agentId));
    }

    /**
     * Creates or updates the agent override limit for a specific product.
     *
     * @param agentId agent identifier
     * @param productId product identifier
     * @param request the {@code useCustomLimit} flag and the optional {@code maxActiveTickets} value
     * @return the created or updated limit record
     */
    @Operation(summary = "Agent limitini ayarla", description = "Belirli bir ürün için agent override limitini oluşturur veya günceller.")
    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AgentProductLimitResponseDTO> setAgentLimit(
            @Parameter(description = "Agent kimliği", required = true)
            @PathVariable String agentId,
            @Parameter(description = "Ürün kimliği", required = true)
            @PathVariable Long productId,
            @Valid @RequestBody AgentProductLimitRequestDTO request) {
        log.info("Agent limit ayarlama isteği. Agent: {}, Product: {}", agentId, productId);
        return ResponseEntity.ok(agentProductLimitService.setAgentLimit(
                agentId,
                productId,
                request.isUseCustomLimit(),
                request.getMaxActiveTickets()));
    }

    /**
     * Permanently deletes the override record for the given agent/product pair.
     *
     * @param agentId agent identifier
     * @param productId product identifier
     * @return {@code 204 No Content} if the deletion was successful
     */
    @Operation(summary = "Agent limitini sil", description = "Belirtilen agent/product override kaydını siler.")
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
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