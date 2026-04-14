package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CsatDTO;
import com.ticketsystem.it_service_backend.entity.Csat;
import com.ticketsystem.it_service_backend.service.CsatService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Ticket CSAT", description = "Ticket memnuniyet (CSAT) işlemleri")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketCsatController {

    private final CsatService csatService;

    @Operation(summary = "CSAT anketini gönder", description = "Çözülen bilet için müşteri memnuniyet anketi doldurur.")
    @PostMapping("/{id}/csat")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Csat> submitCsat(
            @PathVariable Long id,
            @RequestBody CsatDTO csatDTO,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(csatService.submitCsat(id, csatDTO, userId, roles));
    }

    @Operation(summary = "CSAT detayını getir", description = "Belirli bir biletin anket sonucunu getirir.")
    @GetMapping("/{id}/csat")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<Csat> getCsat(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(csatService.getCsatByTicketId(id, userId, roles));
    }

    @Operation(summary = "Tüm CSAT anketlerini listele", description = "Sistemdeki tüm müşteri memnuniyet sonuçlarını getirir.")
    @GetMapping("/all-csats")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<Csat>> getAllCsats() {
        return ResponseEntity.ok(csatService.getAllCsats());
    }
}
