package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.WorklogRequestDTO;
import com.ticketsystem.it_service_backend.dto.WorklogResponseDTO;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.service.WorklogService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Ticket Worklog", description = "Agent ve Manager'ların bilet üzerinde harcadıkları sürenin takibi")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketWorklogController {

    private final WorklogService worklogService;

    @Operation(summary = "Worklog ekle",
            description = "Belirtilen bilete yeni bir iş kaydı (süre + açıklama) ekler. "
                    + "Agent yalnızca üzerine atanmış bilete worklog ekleyebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "İş kaydı başarıyla oluşturuldu",
                    content = @Content(schema = @Schema(implementation = WorklogResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Bu bilete worklog ekleme yetkiniz yok"),
            @ApiResponse(responseCode = "400", description = "Geçersiz süre değeri")
    })
    @PostMapping("/{id}/worklogs")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<WorklogResponseDTO> addWorklog(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @RequestBody WorklogRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();
        log.info("Worklog ekleme isteği. Bilet ID: {}, Agent: {}", id, agentId);

        TicketWorklog saved = worklogService.addWorklog(id, dto, agentId);

        log.info("Worklog başarıyla oluşturuldu. Worklog ID: {}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(WorklogResponseDTO.fromEntity(saved));
    }

    @Operation(summary = "Bilete ait worklogları listele",
            description = "Belirtilen biletin tüm iş kayıtlarını kronolojik sırada getirir. "
                    + "Agent yalnızca kendine atanmış biletin workloglarını görebilir, Manager tüm biletleri görebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Worklog listesi başarıyla döndü"),
            @ApiResponse(responseCode = "403", description = "Bu biletin workloglarını görüntüleme yetkiniz yok")
    })
    @GetMapping("/{id}/worklogs")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<List<WorklogResponseDTO>> getWorklogsByTicket(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
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

    @Operation(summary = "Tüm worklogları listele",
            description = "Sistemdeki tüm biletlere ait iş kayıtlarını getirir. Raporlama ve yönetim amaçlıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tüm workloglar başarıyla listelendi"),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER erişebilir")
    })
    @GetMapping("/all-worklogs")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<List<WorklogResponseDTO>> getAllWorklogs() {
        log.info("Tüm worklogları listeleme isteği (Agent admin).");;

        List<TicketWorklog> worklogs = worklogService.getAllWorklogs();

        log.info("Toplam {} worklog listelendi.", worklogs.size());

        return ResponseEntity.ok(worklogs.stream()
                .map(WorklogResponseDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Worklog güncelle",
            description = "Mevcut bir iş kaydının süre ve açıklamasını günceller. "
                    + "Yalnızca worklogu oluşturan agent güncelleyebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Worklog başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = WorklogResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Bu worklogu güncelleme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Worklog bulunamadı")
    })
    @PutMapping("/{id}/worklogs/{worklogId}")
    @PreAuthorize("hasRole('AGENT')")
    public ResponseEntity<WorklogResponseDTO> updateWorklog(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @Parameter(description = "Güncellenecek worklogun ID'si", example = "15", required = true)
            @PathVariable Long worklogId,
            @RequestBody WorklogRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {
        String agentId = jwt.getSubject();

        log.info("Worklog güncelleme isteği. Bilet ID: {}, Worklog ID: {}, Agent: {}", id, worklogId, agentId);

        TicketWorklog updated = worklogService.updateWorklog(id, worklogId, dto, agentId);

        log.info("Worklog başarıyla güncellendi. Worklog ID: {}", worklogId);
        return ResponseEntity.ok(WorklogResponseDTO.fromEntity(updated));
    }

    @Operation(summary = "Worklog sil",
                    + "Bir iş kaydını kalıcı olarak siler. Agent yalnızca kendi oluşturduğu worklogu silebilir, Agent Admin hepsini silebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Worklog başarıyla silindi"),
            @ApiResponse(responseCode = "403", description = "Bu worklogu silme yetkiniz yok"),
            @ApiResponse(responseCode = "404", description = "Worklog bulunamadı")
    })
    @DeleteMapping("/{id}/worklogs/{worklogId}")
    @PreAuthorize("hasAnyRole('AGENT', 'AGENT_ADMIN')")
    public ResponseEntity<Void> deleteWorklog(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @Parameter(description = "Silinecek worklogun ID'si", example = "15", required = true)
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
