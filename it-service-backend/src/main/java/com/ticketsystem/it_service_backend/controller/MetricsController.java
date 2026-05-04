package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PrioritySLAMetricsDTO;
import com.ticketsystem.it_service_backend.dto.ProductMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
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
    @PreAuthorize("hasRole('MANAGER')")
    @GetMapping("/product-metrics")
    public ResponseEntity<ProductMetricsDTO> getProductMetrics() {
        log.info("Ürün bazında bilet metrikleri istendi");
        ProductMetricsDTO metrics = metricsService.getProductMetrics();
        return ResponseEntity.ok(metrics);
    }
}
