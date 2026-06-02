package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.AlertsBacklogDTO;
import com.ticketsystem.it_service_backend.dto.CSATMetricsDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PrioritySLAMetricsDTO;
import com.ticketsystem.it_service_backend.dto.ProductMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
import com.ticketsystem.it_service_backend.dto.WorklogCompletionDTO;
import com.ticketsystem.it_service_backend.service.MetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the manager dashboard's KPIs and analytic metrics.
 *
 * <p>Restricted to the {@code MANAGER} role (and {@code AGENT_ADMIN} on a few endpoints).
 * Most endpoints are wrapped with a Caffeine cache; business calculations are
 * performed by {@link MetricsService}.
 */
@Log4j2
@Tag(name = "Dashboard Metrikleri", description = "Sistem metrikleri, KPI'ları ve analitiği — Manager rolü için")
@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;

    /**
     * Dashboard summary metrics endpoint.
     * Returns the data backing the KPI cards (open tickets, SLA breaches, response time, CSAT).
     *
     * @return DashboardMetricsDTO — all dashboard KPI metrics
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
    @Cacheable(value = "metrics", key = "'dashboard-summary'")
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<DashboardMetricsDTO> getDashboardSummary() {
        log.debug("Dashboard özet metrikleri istendi");
        DashboardMetricsDTO metrics = metricsService.getDashboardSummary();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Ticket status distribution endpoint.
     * Returns the NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED and CLOSED counts for the dashboard chart.
     *
     * @return StatusDistributionDTO — ticket status distribution
     */
    @Operation(
            summary = "Ticket durum dağılımı",
            description = "Tüm ticket'ların status bazlı dağılımını döner. Dashboard chart'ı için kullanılır. "
                    + "Sadece Manager rolü erişebilir."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Durum dağılımı başarıyla döndürdü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StatusDistributionDTO.class)
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
    @Cacheable(value = "metrics", key = "'status-distribution'")
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/status-distribution")
    public ResponseEntity<StatusDistributionDTO> getStatusDistribution() {
        log.debug("Ticket durum dağılımı istendi");
        StatusDistributionDTO distribution = metricsService.getStatusDistribution();
        return ResponseEntity.ok(distribution);
    }

    /**
     * Agent performance leaderboard endpoint.
     * Returns active ticket load, resolution rate, CSAT, SLA breach and worklog data for the dashboard table.
     *
     * @return AgentPerformanceDTO — agent leaderboard summary
     */
    @Operation(
            summary = "Ajan performans leaderboard",
            description = "Ajanların aktif ticket yükü, son 24 saat çözüm sayısı, ortalama çözüm süresi, CSAT ve SLA breach bilgilerini döner. "
                    + "Sadece Manager ve Agent Admin erişebilir."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ajan performans verisi başarıyla döndürdü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AgentPerformanceDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Yetkisiz erişim (Manager veya Agent Admin rolü gerekli)"
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Sunucu hatası"
            )
    })
    @Cacheable(value = "metrics", key = "'agent-performance'")
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/agent-performance")
    public ResponseEntity<AgentPerformanceDTO> getAgentPerformance() {
        log.debug("Ajan performans leaderboard isteği alındı");
        AgentPerformanceDTO performance = metricsService.getAgentPerformance();
        return ResponseEntity.ok(performance);
    }

    /**
     * Ticket timeline metrics endpoint.
     * Returns daily ticket trend data for the last N days (created, resolved, closed, SLA breaches).
     *
     * @param days number of days of data to retrieve (default 30, max 365)
     * @return TicketTimelineDTO — timeline of daily metrics
     */
    @Operation(
            summary = "Ticket timeline metrikleri",
            description = "Son N günün günlük ticket trend verilerini döner. "
                    + "Günlük oluşturulan, çözülen, kapalı bilet sayılarını ve SLA breach sayılarını içerir. "
                    + "Sadece Manager rolü erişebilir."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Timeline metrikleri başarıyla döndürdü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TicketTimelineDTO.class)
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
    @Cacheable(value = "metrics", key = "'ticket-timeline-' + #days")
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/ticket-timeline")
    public ResponseEntity<TicketTimelineDTO> getTicketTimeline(
            @RequestParam(defaultValue = "30") int days) {
        log.debug("Ticket timeline metrikleri istendi (days={})", days);
        TicketTimelineDTO timeline = metricsService.getTicketTimeline(days);
        return ResponseEntity.ok(timeline);
    }

    /**
     * Priority-SLA metrics endpoint.
     * Returns per-priority ticket volume, SLA target, average resolution time, breach and on-time rates.
     *
     * @return PrioritySLAMetricsDTO — detailed metrics per priority
     */
    @Operation(
            summary = "Priority-SLA metrikleri",
            description = "CRITICAL, HIGH, MEDIUM ve LOW öncelik seviyeleri için SLA hedef karşılaştırmalı metriklerini döner. "
                    + "Sadece Manager rolü erişebilir."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Priority-SLA metrikleri başarıyla döndürdü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PrioritySLAMetricsDTO.class)
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
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/priority-sla-metrics")
    public ResponseEntity<PrioritySLAMetricsDTO> getPrioritySlaMetrics(
            @RequestParam(required = false) Integer days) {
        log.debug("Priority-SLA metrikleri istendi (days={})", days);
        PrioritySLAMetricsDTO metrics = metricsService.getPrioritySlaMetrics(days);
        return ResponseEntity.ok(metrics);
    }

    /**
     * Per-product ticket metrics endpoint.
     * For each active product, returns total tickets, open tickets, average resolution, CSAT and SLA breach rate.
     *
     * @return ProductMetricsDTO — detailed per-product metrics
     */
    @Operation(
            summary = "Ürün bazında bilet metrikleri",
            description = "Aktif ürünlerin bilet yükü, ortalama çözüm süresi, CSAT ortalaması ve SLA breach yüzdesini döner. "
                    + "Toplam bilet sayısına göre azalan sırada listelenir. Sadece Manager rolü erişebilir."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Ürün metrikleri başarıyla döndürdü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProductMetricsDTO.class)
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
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/product-metrics")
    public ResponseEntity<ProductMetricsDTO> getProductMetrics(
            @RequestParam(required = false) Integer days) {
        log.debug("Ürün bazında bilet metrikleri istendi (days={})", days);
        ProductMetricsDTO metrics = metricsService.getProductMetrics(days);
        return ResponseEntity.ok(metrics);
    }

    /**
     * CSAT detailed analytics endpoint.
     * Returns score distribution, monthly trend, per-priority CSAT and top comments for the last N months.
     *
     * @param months number of months to analyze (default 3, max 12)
     * @return CSATMetricsDTO — CSAT analytic summary
     */
    @Operation(
            summary = "CSAT detaylı analitik metrikleri",
            description = "Son N aylık CSAT verilerini analiz eder. Puan dağılımı, bu ay ile geçen ay trendi, "
                    + "priority bazlı CSAT ortalamaları ve en yüksek puanlı yorumları döner. "
                    + "Sadece Manager rolü erişebilir."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "CSAT metrikleri başarıyla döndürdü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CSATMetricsDTO.class)
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
    @Cacheable(value = "metrics", key = "'csat-metrics-' + #months")
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/csat-metrics")
    public ResponseEntity<CSATMetricsDTO> getCSATMetrics(
            @RequestParam(defaultValue = "3") int months) {
        log.debug("CSAT detaylı metrikleri istendi (months={})", months);
        CSATMetricsDTO metrics = metricsService.getCSATMetrics(months);
        return ResponseEntity.ok(metrics);
    }

    /**
     * SLA breach alerts and backlog metrics endpoint.
     * Returns already-breached tickets, tickets at risk of breaching within 4 hours, long-pending tickets and a backlog summary.
     *
     * @return AlertsBacklogDTO — alert lists and backlog statistics
     */
    @Operation(
            summary = "SLA breach uyarıları ve backlog metrikleri",
            description = "SLA'yı aşmış açık biletleri, 4 saat içinde SLA breach riski taşıyanları, "
                    + "3+ gün WAITING_FOR_CUSTOMER statüsünde kalanları ve atanmamış bilet sayısını döner. "
                    + "Sadece Manager rolü erişebilir."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Alert ve backlog metrikleri başarıyla döndürdü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AlertsBacklogDTO.class)
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
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/alerts-backlog")
    public ResponseEntity<AlertsBacklogDTO> getAlertsAndBacklog() {
        log.debug("Alert ve backlog metrikleri istendi");
        AlertsBacklogDTO dto = metricsService.getAlertsAndBacklog();
        return ResponseEntity.ok(dto);
    }

    /**
     * Worklog summary and ticket completion metrics endpoint.
     * Returns per-agent logged work time and ticket completion statistics for the period.
     *
     * @param days number of days to analyze (default 30, max 365)
     * @return WorklogCompletionDTO — worklog summaries and completion rates
     */
    @Operation(
            summary = "Worklog özeti ve bilet tamamlanma metrikleri",
            description = "Son N günün agent bazında kayıtlı çalışma dakikalarını, toplam bilet oluşturma/çözme/kapatma sayılarını, "
                    + "ortalama çözüm süresini ve SLA uyum oranını döner. Sadece Manager rolü erişebilir."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Worklog ve tamamlanma metrikleri başarıyla döndürdü",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WorklogCompletionDTO.class)
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
    @Cacheable(value = "metrics", key = "'worklog-completion-' + #days")
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/worklog-completion")
    public ResponseEntity<WorklogCompletionDTO> getWorklogCompletion(
            @RequestParam(defaultValue = "30") int days) {
        log.debug("Worklog ve tamamlanma metrikleri istendi (days={})", days);
        WorklogCompletionDTO dto = metricsService.getWorklogCompletion(days);
        return ResponseEntity.ok(dto);
    }
}
