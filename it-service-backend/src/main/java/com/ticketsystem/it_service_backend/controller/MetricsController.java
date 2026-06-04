package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AgentDashboardDTO;
import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.AlertsBacklogDTO;
import com.ticketsystem.it_service_backend.dto.CSATMetricsDTO;
import com.ticketsystem.it_service_backend.dto.CustomerDashboardDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PrioritySLAMetricsDTO;
import com.ticketsystem.it_service_backend.dto.ProductMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
import com.ticketsystem.it_service_backend.dto.WorklogCompletionDTO;
import com.ticketsystem.it_service_backend.service.MetricsService;
import com.ticketsystem.it_service_backend.util.AuthRoles;
import com.ticketsystem.it_service_backend.util.JwtUtils;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST controller for the manager dashboard's KPIs and analytic metrics.
 *
 * <p>Restricted to the {@code MANAGER}, {@code LEAD_AGENT} and {@code ADMIN} roles.
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
     * Resolved metric visibility scope for the calling user.
     *
     * @param productIds {@code null} for a global caller (ADMIN/MANAGER, no filter);
     *                   otherwise the LEAD_AGENT's authorized product IDs (possibly empty)
     * @param scopeKey   cache-key discriminator: {@code "global"} for global callers,
     *                   the user id for product-scoped callers — so global and per-lead
     *                   results never share a cache entry
     */
    private record MetricsScope(List<Long> productIds, String scopeKey) {}

    /**
     * Computes the metric scope from the JWT. ADMIN/MANAGER see everything (global);
     * a pure LEAD_AGENT is restricted to the products they are authorized on.
     */
    private MetricsScope resolveScope(Jwt jwt) {
        List<String> roles = JwtUtils.extractRoles(jwt);
        if (AuthRoles.isGlobal(roles)) {
            return new MetricsScope(null, "global");
        }
        String userId = jwt.getSubject();
        List<Long> productIds = metricsService.resolveScopedProductIds(userId);
        return new MetricsScope(productIds, userId);
    }

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
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<DashboardMetricsDTO> getDashboardSummary(@AuthenticationPrincipal Jwt jwt) {
        MetricsScope scope = resolveScope(jwt);
        log.debug("Dashboard özet metrikleri istendi (scope={})", scope.scopeKey());
        DashboardMetricsDTO metrics = metricsService.getDashboardSummary(scope.productIds(), scope.scopeKey());
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
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/status-distribution")
    public ResponseEntity<StatusDistributionDTO> getStatusDistribution(@AuthenticationPrincipal Jwt jwt) {
        MetricsScope scope = resolveScope(jwt);
        log.debug("Ticket durum dağılımı istendi (scope={})", scope.scopeKey());
        StatusDistributionDTO distribution = metricsService.getStatusDistribution(scope.productIds(), scope.scopeKey());
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
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/agent-performance")
    public ResponseEntity<AgentPerformanceDTO> getAgentPerformance(@AuthenticationPrincipal Jwt jwt) {
        MetricsScope scope = resolveScope(jwt);
        log.debug("Ajan performans leaderboard isteği alındı (scope={})", scope.scopeKey());
        AgentPerformanceDTO performance = metricsService.getAgentPerformance(scope.productIds(), scope.scopeKey());
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
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/ticket-timeline")
    public ResponseEntity<TicketTimelineDTO> getTicketTimeline(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal Jwt jwt) {
        MetricsScope scope = resolveScope(jwt);
        log.debug("Ticket timeline metrikleri istendi (days={}, scope={})", days, scope.scopeKey());
        TicketTimelineDTO timeline = metricsService.getTicketTimeline(days, scope.productIds(), scope.scopeKey());
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
            @RequestParam(required = false) Integer days,
            @AuthenticationPrincipal Jwt jwt) {
        MetricsScope scope = resolveScope(jwt);
        log.debug("Priority-SLA metrikleri istendi (days={}, scope={})", days, scope.scopeKey());
        PrioritySLAMetricsDTO metrics = metricsService.getPrioritySlaMetrics(days, scope.productIds(), scope.scopeKey());
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
            @RequestParam(required = false) Integer days,
            @AuthenticationPrincipal Jwt jwt) {
        MetricsScope scope = resolveScope(jwt);
        log.debug("Ürün bazında bilet metrikleri istendi (days={}, scope={})", days, scope.scopeKey());
        ProductMetricsDTO metrics = metricsService.getProductMetrics(days, scope.productIds(), scope.scopeKey());
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
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/csat-metrics")
    public ResponseEntity<CSATMetricsDTO> getCSATMetrics(
            @RequestParam(defaultValue = "3") int months,
            @AuthenticationPrincipal Jwt jwt) {
        MetricsScope scope = resolveScope(jwt);
        log.debug("CSAT detaylı metrikleri istendi (months={}, scope={})", months, scope.scopeKey());
        CSATMetricsDTO metrics = metricsService.getCSATMetrics(months, scope.productIds(), scope.scopeKey());
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
    public ResponseEntity<AlertsBacklogDTO> getAlertsAndBacklog(@AuthenticationPrincipal Jwt jwt) {
        MetricsScope scope = resolveScope(jwt);
        log.debug("Alert ve backlog metrikleri istendi (scope={})", scope.scopeKey());
        AlertsBacklogDTO dto = metricsService.getAlertsAndBacklog(scope.productIds(), scope.scopeKey());
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
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/worklog-completion")
    public ResponseEntity<WorklogCompletionDTO> getWorklogCompletion(
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal Jwt jwt) {
        MetricsScope scope = resolveScope(jwt);
        log.debug("Worklog ve tamamlanma metrikleri istendi (days={}, scope={})", days, scope.scopeKey());
        WorklogCompletionDTO dto = metricsService.getWorklogCompletion(days, scope.productIds(), scope.scopeKey());
        return ResponseEntity.ok(dto);
    }

    // =========================================================================
    // Kişisel dashboard'lar — self-scoped (JWT subject). Yönetim dashboard'undan
    // ayrı: başkasının verisi dönmez, ürün-scope'u gerekmez (kendi verin).
    // =========================================================================

    /**
     * Personal customer dashboard for the authenticated user — metrics over the tickets
     * they opened ({@code customer_id = jwt.sub}). Available to any authenticated user;
     * a non-customer simply sees zeros.
     *
     * @param days timeline window in days (default 30, clamped 1–365)
     * @return the caller's customer dashboard
     */
    @Operation(summary = "Kişisel müşteri dashboard'u",
            description = "Oturum açan kullanıcının KENDİ açtığı biletler üzerinden metrikler: "
                    + "durum dağılımı, SLA, ortalama çözüm süresi, verdiği CSAT, zaman çizelgesi ve son biletler.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Müşteri dashboard'u döndü",
                    content = @Content(schema = @Schema(implementation = CustomerDashboardDTO.class))),
            @ApiResponse(responseCode = "401", description = "Kimlik doğrulanmadı")
    })
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me/customer")
    public ResponseEntity<CustomerDashboardDTO> getMyCustomerDashboard(
            @RequestParam(required = false) Integer days,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.debug("Kişisel müşteri dashboard istendi (user={}, days={})", userId, days);
        return ResponseEntity.ok(metricsService.getMyCustomerDashboard(userId, days));
    }

    /**
     * Personal performance dashboard for the authenticated agent / lead agent — metrics
     * over the tickets they claimed ({@code ticket_claims.agent_id = jwt.sub}) plus their
     * own worklogs.
     *
     * @param days timeline window in days (default 30, clamped 1–365)
     * @return the caller's agent dashboard
     */
    @Operation(summary = "Kişisel ajan performans dashboard'u",
            description = "Oturum açan ajanın/lead'in claim'lediği biletler üzerinden metrikler: "
                    + "aktif yük, çözülen (24s/7g/30g), ortalama çözüm, SLA ihlal oranı, worklog, CSAT, "
                    + "durum dağılımı, zaman çizelgesi ve son biletler.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajan dashboard'u döndü",
                    content = @Content(schema = @Schema(implementation = AgentDashboardDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yetkisiz erişim (AGENT veya LEAD_AGENT gerekli)")
    })
    @PreAuthorize("hasAnyRole('AGENT', 'LEAD_AGENT')")
    @GetMapping("/me/agent")
    public ResponseEntity<AgentDashboardDTO> getMyAgentDashboard(
            @RequestParam(required = false) Integer days,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.debug("Kişisel ajan dashboard istendi (user={}, days={})", userId, days);
        return ResponseEntity.ok(metricsService.getMyAgentDashboard(userId, days));
    }

    // -------------------------------------------------------------------------
    //  Viewing ANOTHER user's dashboard (oversight) — from User Management / the
    //  agent leaderboard. ADMIN/MANAGER are global; a pure LEAD_AGENT is restricted
    //  to agents authorized on its own products and only sees those products' data.
    // -------------------------------------------------------------------------

    /**
     * Agent performance dashboard for a specific user, for oversight roles.
     * ADMIN/MANAGER see the agent's full claimed-ticket history; a pure LEAD_AGENT
     * sees only the slice within their authorized products and is forbidden from
     * viewing agents that share no product with them.
     *
     * @param userId target agent's Keycloak id
     * @param days   timeline window (default 30, clamped 1–365)
     */
    @Operation(summary = "Bir kullanıcının ajan performans dashboard'u (oversight)",
            description = "Belirtilen kullanıcının ajan metriklerini döner. ADMIN/MANAGER global görür; "
                    + "LEAD_AGENT yalnızca kendi yetkili olduğu ürünlerdeki veriyi görür ve ortak ürünü "
                    + "olmayan ajanları görüntüleyemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajan dashboard'u döndü",
                    content = @Content(schema = @Schema(implementation = AgentDashboardDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yetkisiz erişim / kapsam dışı kullanıcı")
    })
    @PreAuthorize("hasAnyRole('MANAGER', 'LEAD_AGENT', 'ADMIN')")
    @GetMapping("/users/{userId}/agent")
    public ResponseEntity<AgentDashboardDTO> getUserAgentDashboard(
            @PathVariable String userId,
            @RequestParam(required = false) Integer days,
            @AuthenticationPrincipal Jwt jwt) {
        List<String> roles = JwtUtils.extractRoles(jwt);
        log.debug("Kullanıcı ajan dashboard istendi (target={}, days={})", userId, days);
        if (AuthRoles.isGlobal(roles)) {
            // ADMIN/MANAGER → global view (same data the agent sees of themselves).
            return ResponseEntity.ok(metricsService.getMyAgentDashboard(userId, days));
        }
        // Pure LEAD_AGENT → product-scoped, and only for agents sharing a product.
        String leadId = jwt.getSubject();
        List<Long> leadProducts = metricsService.resolveScopedProductIds(leadId);
        if (!metricsService.userSharesAnyProduct(userId, leadProducts)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.metrics.user.forbidden");
        }
        return ResponseEntity.ok(metricsService.getUserAgentDashboard(userId, days, leadProducts, leadId));
    }

    /**
     * Customer dashboard for a specific user, for the global oversight roles.
     * Restricted to ADMIN/MANAGER — leads do not have access to customer accounts.
     *
     * @param userId target customer's Keycloak id
     * @param days   timeline window (default 30, clamped 1–365)
     */
    @Operation(summary = "Bir kullanıcının müşteri dashboard'u (oversight)",
            description = "Belirtilen kullanıcının müşteri olarak açtığı biletler üzerinden metrikleri döner. "
                    + "Yalnızca ADMIN/MANAGER erişebilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Müşteri dashboard'u döndü",
                    content = @Content(schema = @Schema(implementation = CustomerDashboardDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yetkisiz erişim (ADMIN/MANAGER gerekli)")
    })
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @GetMapping("/users/{userId}/customer")
    public ResponseEntity<CustomerDashboardDTO> getUserCustomerDashboard(
            @PathVariable String userId,
            @RequestParam(required = false) Integer days,
            @AuthenticationPrincipal Jwt jwt) {
        log.debug("Kullanıcı müşteri dashboard istendi (target={}, days={})", userId, days);
        return ResponseEntity.ok(metricsService.getMyCustomerDashboard(userId, days));
    }
}
