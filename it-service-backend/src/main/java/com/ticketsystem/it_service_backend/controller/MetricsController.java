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
    @Cacheable(value = "metrics", key = "'dashboard-summary'")
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<DashboardMetricsDTO> getDashboardSummary() {
        log.info("Dashboard özet metrikleri istendi");
        DashboardMetricsDTO metrics = metricsService.getDashboardSummary();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Ticket durum dağılımı endpoint'i.
     * Dashboard chart'ı için NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED ve CLOSED sayımlarını döner.
     *
     * @return StatusDistributionDTO — ticket durum dağılımı
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
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/status-distribution")
    public ResponseEntity<StatusDistributionDTO> getStatusDistribution() {
        log.info("Ticket durum dağılımı istendi");
        StatusDistributionDTO distribution = metricsService.getStatusDistribution();
        return ResponseEntity.ok(distribution);
    }

    /**
     * Ajan performans leaderboard endpoint'i.
     * Dashboard tablosu için aktif ticket, çözüm hızı, CSAT, SLA breach ve worklog verilerini döner.
     *
     * @return AgentPerformanceDTO — agent leaderboard özeti
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
    @PreAuthorize("hasAnyRole('MANAGER', 'AGENT_ADMIN')")
    @GetMapping("/agent-performance")
    public ResponseEntity<AgentPerformanceDTO> getAgentPerformance() {
        log.info("Ajan performans leaderboard isteği alındı");
        AgentPerformanceDTO performance = metricsService.getAgentPerformance();
        return ResponseEntity.ok(performance);
    }

    /**
     * Ticket timeline metrikleri endpoint'i.
     * Son N günün günlük ticket trend verilerini (oluşturulan, çözülen, kapalı, SLA breach) döner.
     *
     * @param days Kaç günlük veri isteneceği (default 30, max 365)
     * @return TicketTimelineDTO — günlük metriklerin timeline'ı
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
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/ticket-timeline")
    public ResponseEntity<TicketTimelineDTO> getTicketTimeline(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Ticket timeline metrikleri istendi (days={})", days);
        TicketTimelineDTO timeline = metricsService.getTicketTimeline(days);
        return ResponseEntity.ok(timeline);
    }

    /**
     * Priority-SLA metrikleri endpoint'i.
     * Priority bazlı ticket hacmi, SLA hedefi, ortalama çözüm süresi, breach ve on-time oranlarını döner.
     *
     * @return PrioritySLAMetricsDTO — priority detay metrikleri
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
    @Cacheable(value = "metrics", key = "'priority-sla-metrics'")
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/priority-sla-metrics")
    public ResponseEntity<PrioritySLAMetricsDTO> getPrioritySlaMetrics() {
        log.info("Priority-SLA metrikleri istendi");
        PrioritySLAMetricsDTO metrics = metricsService.getPrioritySlaMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * Ürün bazında bilet metrikleri endpoint'i.
     * Her aktif ürün için toplam bilet, açık bilet, ort. çözüm, CSAT ve SLA breach oranını döner.
     *
     * @return ProductMetricsDTO — ürün detay metrikleri
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
    @Cacheable(value = "metrics", key = "'product-metrics'")
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/product-metrics")
    public ResponseEntity<ProductMetricsDTO> getProductMetrics() {
        log.info("Ürün bazında bilet metrikleri istendi");
        ProductMetricsDTO metrics = metricsService.getProductMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * CSAT detaylı analitik endpoint'i.
     * Son N ay için puan dağılımı, aylık trend, priority bazlı CSAT ve en iyi yorumları döner.
     *
     * @param months Analiz edilecek ay sayısı (default 3, max 12)
     * @return CSATMetricsDTO — CSAT analitik özeti
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
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/csat-metrics")
    public ResponseEntity<CSATMetricsDTO> getCSATMetrics(
            @RequestParam(defaultValue = "3") int months) {
        log.info("CSAT detaylı metrikleri istendi (months={})", months);
        CSATMetricsDTO metrics = metricsService.getCSATMetrics(months);
        return ResponseEntity.ok(metrics);
    }

    /**
     * SLA breach uyarıları ve backlog metrikleri endpoint'i.
     * Zaten aşılmış biletler, 4 saat içinde aşılacak biletler, uzun süre bekleyenler ve backlog özeti.
     *
     * @return AlertsBacklogDTO — alert listeleri ve backlog istatistikleri
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
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/alerts-backlog")
    public ResponseEntity<AlertsBacklogDTO> getAlertsAndBacklog() {
        log.info("Alert ve backlog metrikleri istendi");
        AlertsBacklogDTO dto = metricsService.getAlertsAndBacklog();
        return ResponseEntity.ok(dto);
    }

    /**
     * Worklog özeti ve bilet tamamlanma metrikleri endpoint'i.
     * Agent bazında kayıtlı çalışma sürelerini ve dönem bilet tamamlanma istatistiklerini döner.
     *
     * @param days Analiz edilecek gün sayısı (default 30, max 365)
     * @return WorklogCompletionDTO — worklog özetleri ve tamamlanma oranları
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
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/worklog-completion")
    public ResponseEntity<WorklogCompletionDTO> getWorklogCompletion(
            @RequestParam(defaultValue = "30") int days) {
        log.info("Worklog ve tamamlanma metrikleri istendi (days={})", days);
        WorklogCompletionDTO dto = metricsService.getWorklogCompletion(days);
        return ResponseEntity.ok(dto);
    }
}
