package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Log4j2
@Tag(name = "Dashboard Metrikleri", description = "Sistem metrikleri, KPI'ları ve analitiği — Manager rolü için")
@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * Dashboard özet metrikleri endpoint'i.
     * KPI kartları (açık biletler, SLA breach, yanıt süresi, CSAT) için verileri döner.
     *
     * @return DashboardMetricsDTO — tüm dashboard KPI metrikleri
     */
    @Operation(
            summary = "Dashboard özet metrikleri",
            description = "Sistem KPI'larını döner: açık bilet sayısı, SLA breach oranı, "
                    + "ortalama yanıt süresi, CSAT puanı ve priority dağılımı. "
                    + "Sadece Manager rolü erişebilir."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Metrikleri başarıyla döndürdü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DashboardMetricsDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Yetkisiz erişim (Manager rolü gerekli)"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Sunucu hatası"
            )
    })
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<DashboardMetricsDTO> getDashboardSummary() {
        log.info("Dashboard özet metrikleri istendi");
        DashboardMetricsDTO metrics = metricsService.getDashboardSummary();
        return ResponseEntity.ok(metrics);
    }
}
