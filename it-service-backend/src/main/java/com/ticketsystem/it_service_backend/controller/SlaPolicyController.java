package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.SlaPolicyResponseDTO;
import com.ticketsystem.it_service_backend.dto.SlaPolicyUpdateDTO;
import com.ticketsystem.it_service_backend.entity.SlaPolicy;
import com.ticketsystem.it_service_backend.service.SlaPolicyService;
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

@Log4j2
@Tag(name = "SLA Politika Yönetimi", description = "Admin: öncelik bazlı SLA süresi konfigürasyonu")
@RestController
@RequestMapping("/api/admin/sla-policies")
@RequiredArgsConstructor
@PreAuthorize("hasRole('AGENT_ADMIN')")
public class SlaPolicyController {

    private final SlaPolicyService slaPolicyService;

    @Operation(summary = "Tüm SLA politikalarını listele")
    @GetMapping
    public ResponseEntity<List<SlaPolicyResponseDTO>> getAllPolicies() {
        List<SlaPolicyResponseDTO> policies = slaPolicyService.getAllPolicies()
                .stream()
                .map(SlaPolicyResponseDTO::fromEntity)
                .toList();
        return ResponseEntity.ok(policies);
    }

    @Operation(summary = "SLA politikasını güncelle (cache invalidasyonu dahil)")
    @PutMapping("/{id}")
    public ResponseEntity<SlaPolicyResponseDTO> updatePolicy(
            @Parameter(description = "Güncellenecek SLA politikası PK")
            @PathVariable Long id,
            @Valid @RequestBody SlaPolicyUpdateDTO dto) {

        SlaPolicy updated = slaPolicyService.updatePolicy(
                id,
                dto.getTargetResolutionHours(),
                dto.getWarningThresholdHours()
        );

        log.info("SLA politikası güncellendi: id={} priority={} targetHours={} warningHours={}",
                updated.getId(), updated.getPriority(),
                updated.getTargetResolutionHours(), updated.getWarningThresholdHours());

        return ResponseEntity.ok(SlaPolicyResponseDTO.fromEntity(updated));
    }
}
