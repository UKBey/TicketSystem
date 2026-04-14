package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.WorklogRequestDTO;
import com.ticketsystem.it_service_backend.dto.WorklogResponseDTO;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.service.WorklogService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Tag(name = "Ticket Worklog", description = "Ticket iş kaydı (worklog) işlemleri")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketWorklogController {

    private final WorklogService worklogService;

    @Operation(summary = "Worklog ekle", description = "Bir bilete iş kaydı ekler. Yalnızca biletin atandığı agent işlem yapabilir.")
    @PostMapping("/{id}/worklogs")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<WorklogResponseDTO> addWorklog(
            @PathVariable Long id,
            @RequestBody WorklogRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        log.info("Worklog ekleme isteği. Bilet ID: {}, Agent: {}", id, agentId);

        TicketWorklog saved = worklogService.addWorklog(id, dto, agentId);

        log.info("Worklog başarıyla oluşturuldu. Worklog ID: {}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorklogResponseDTO.fromEntity(saved));
    }

    @Operation(summary = "Bilete ait worklogları listele", description = "Bir biletin iş kayıtlarını getirir. Agent yalnızca kendine atanmış bileti görebilir.")
    @GetMapping("/{id}/worklogs")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<List<WorklogResponseDTO>> getWorklogsByTicket(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Worklog listeleme isteği. Bilet ID: {}, Kullanıcı: {}", id, userId);

        List<TicketWorklog> worklogs = worklogService.getWorklogsByTicket(id, userId, roles);

        return ResponseEntity.ok(worklogs.stream()
                .map(WorklogResponseDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Tüm worklogları listele", description = "Sistemdeki tüm iş kayıtlarını getirir. Yalnızca Manager erişebilir.")
    @GetMapping("/all-worklogs")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<WorklogResponseDTO>> getAllWorklogs() {
        log.info("Tüm worklogları listeleme isteği (Manager).");

        List<TicketWorklog> worklogs = worklogService.getAllWorklogs();

        log.info("Toplam {} worklog listelendi.", worklogs.size());

        return ResponseEntity.ok(worklogs.stream()
                .map(WorklogResponseDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Worklog güncelle", description = "Bir iş kaydını günceller. Yalnızca worklogu oluşturan agent güncelleyebilir.")
    @PutMapping("/{id}/worklogs/{worklogId}")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<WorklogResponseDTO> updateWorklog(
            @PathVariable Long id,
            @PathVariable Long worklogId,
            @RequestBody WorklogRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();

        log.info("Worklog güncelleme isteği. Bilet ID: {}, Worklog ID: {}, Agent: {}", id, worklogId, agentId);

        TicketWorklog updated = worklogService.updateWorklog(id, worklogId, dto, agentId);

        log.info("Worklog başarıyla güncellendi. Worklog ID: {}", worklogId);
        return ResponseEntity.ok(WorklogResponseDTO.fromEntity(updated));
    }

    @Operation(summary = "Worklog sil", description = "Bir iş kaydını siler. Agent yalnızca kendi oluşturduğu worklogu silebilir, Manager hepsini silebilir.")
    @DeleteMapping("/{id}/worklogs/{worklogId}")
    @PreAuthorize("hasAnyRole('AGENT', 'MANAGER')")
    public ResponseEntity<Void> deleteWorklog(
            @PathVariable Long id,
            @PathVariable Long worklogId,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);

        log.info("Worklog silme isteği. Bilet ID: {}, Worklog ID: {}, Kullanıcı: {}", id, worklogId, userId);

        worklogService.deleteWorklog(worklogId, userId, roles);

        log.info("Worklog başarıyla silindi. Worklog ID: {}", worklogId);
        return ResponseEntity.noContent().build();
    }
}
