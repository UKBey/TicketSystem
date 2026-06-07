package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AgentDashboardDTO;
import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.AlertsBacklogDTO;
import com.ticketsystem.it_service_backend.dto.CSATMetricsDTO;
import com.ticketsystem.it_service_backend.dto.CustomerDashboardDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PrioritySLAMetricsDTO;
import com.ticketsystem.it_service_backend.dto.ProductDashboardDTO;
import com.ticketsystem.it_service_backend.dto.ProductMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
import com.ticketsystem.it_service_backend.dto.WorklogCompletionDTO;
import com.ticketsystem.it_service_backend.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsControllerTest {

    @Mock private MetricsService metricsService;
    private MetricsController controller;

    @BeforeEach
    void setUp() {
        controller = new MetricsController(metricsService);
    }

    private Jwt jwt(String subject, String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }

    // ---- global scope (ADMIN/MANAGER) → productIds null, scopeKey "global" ----

    @Test
    void dashboardSummary_globalScope() {
        DashboardMetricsDTO dto = mock(DashboardMetricsDTO.class);
        when(metricsService.getDashboardSummary(isNull(), eq("global"))).thenReturn(dto);

        ResponseEntity<DashboardMetricsDTO> res = controller.getDashboardSummary(jwt("m-1", "manager"));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isSameAs(dto);
    }

    @Test
    void statusDistribution_globalScope() {
        StatusDistributionDTO dto = mock(StatusDistributionDTO.class);
        when(metricsService.getStatusDistribution(isNull(), eq("global"))).thenReturn(dto);

        ResponseEntity<StatusDistributionDTO> res = controller.getStatusDistribution(jwt("a-1", "admin"));

        assertThat(res.getBody()).isSameAs(dto);
    }

    @Test
    void agentPerformance_globalScope() {
        AgentPerformanceDTO dto = mock(AgentPerformanceDTO.class);
        when(metricsService.getAgentPerformance(isNull(), eq("global"))).thenReturn(dto);

        assertThat(controller.getAgentPerformance(jwt("m-1", "manager")).getBody()).isSameAs(dto);
    }

    @Test
    void ticketTimeline_passesDays_globalScope() {
        TicketTimelineDTO dto = mock(TicketTimelineDTO.class);
        when(metricsService.getTicketTimeline(eq(7), isNull(), eq("global"))).thenReturn(dto);

        assertThat(controller.getTicketTimeline(7, jwt("m-1", "manager")).getBody()).isSameAs(dto);
    }

    @Test
    void prioritySlaMetrics_globalScope() {
        PrioritySLAMetricsDTO dto = mock(PrioritySLAMetricsDTO.class);
        when(metricsService.getPrioritySlaMetrics(eq(30), isNull(), eq("global"))).thenReturn(dto);

        assertThat(controller.getPrioritySlaMetrics(30, jwt("a-1", "admin")).getBody()).isSameAs(dto);
    }

    @Test
    void productMetrics_globalScope() {
        ProductMetricsDTO dto = mock(ProductMetricsDTO.class);
        when(metricsService.getProductMetrics(isNull(), isNull(), eq("global"))).thenReturn(dto);

        assertThat(controller.getProductMetrics(null, jwt("m-1", "manager")).getBody()).isSameAs(dto);
    }

    @Test
    void csatMetrics_globalScope() {
        CSATMetricsDTO dto = mock(CSATMetricsDTO.class);
        when(metricsService.getCSATMetrics(eq(3), isNull(), eq("global"))).thenReturn(dto);

        assertThat(controller.getCSATMetrics(3, jwt("m-1", "manager")).getBody()).isSameAs(dto);
    }

    @Test
    void alertsBacklog_globalScope() {
        AlertsBacklogDTO dto = mock(AlertsBacklogDTO.class);
        when(metricsService.getAlertsAndBacklog(isNull(), eq("global"))).thenReturn(dto);

        assertThat(controller.getAlertsAndBacklog(jwt("m-1", "manager")).getBody()).isSameAs(dto);
    }

    @Test
    void worklogCompletion_globalScope() {
        WorklogCompletionDTO dto = mock(WorklogCompletionDTO.class);
        when(metricsService.getWorklogCompletion(eq(30), isNull(), eq("global"))).thenReturn(dto);

        assertThat(controller.getWorklogCompletion(30, jwt("m-1", "manager")).getBody()).isSameAs(dto);
    }

    // ---- product-scoped (pure LEAD_AGENT) → resolveScopedProductIds, scopeKey = userId ----

    @Test
    void dashboardSummary_leadScope_usesResolvedProductIds() {
        when(metricsService.resolveScopedProductIds("lead-1")).thenReturn(List.of(10L, 20L));
        DashboardMetricsDTO dto = mock(DashboardMetricsDTO.class);
        when(metricsService.getDashboardSummary(List.of(10L, 20L), "lead-1")).thenReturn(dto);

        ResponseEntity<DashboardMetricsDTO> res = controller.getDashboardSummary(jwt("lead-1", "lead_agent"));

        assertThat(res.getBody()).isSameAs(dto);
        verify(metricsService).resolveScopedProductIds("lead-1");
    }

    // ---- personal dashboards (self-scoped) ----

    @Test
    void myCustomerDashboard_usesJwtSubject() {
        CustomerDashboardDTO dto = mock(CustomerDashboardDTO.class);
        when(metricsService.getMyCustomerDashboard("u-1", 15)).thenReturn(dto);

        assertThat(controller.getMyCustomerDashboard(15, jwt("u-1", "customer")).getBody()).isSameAs(dto);
    }

    @Test
    void myAgentDashboard_usesJwtSubject() {
        AgentDashboardDTO dto = mock(AgentDashboardDTO.class);
        when(metricsService.getMyAgentDashboard("ag-1", null)).thenReturn(dto);

        assertThat(controller.getMyAgentDashboard(null, jwt("ag-1", "agent")).getBody()).isSameAs(dto);
    }

    // ---- oversight: another user's agent dashboard ----

    @Test
    void userAgentDashboard_globalRole_returnsFullView() {
        AgentDashboardDTO dto = mock(AgentDashboardDTO.class);
        when(metricsService.getMyAgentDashboard("target", 30)).thenReturn(dto);

        assertThat(controller.getUserAgentDashboard("target", 30, jwt("m-1", "manager")).getBody())
                .isSameAs(dto);
    }

    @Test
    void userAgentDashboard_leadSharingProduct_returnsScopedView() {
        when(metricsService.resolveScopedProductIds("lead-1")).thenReturn(List.of(10L));
        when(metricsService.userSharesAnyProduct("target", List.of(10L))).thenReturn(true);
        AgentDashboardDTO dto = mock(AgentDashboardDTO.class);
        when(metricsService.getUserAgentDashboard("target", 30, List.of(10L), "lead-1")).thenReturn(dto);

        assertThat(controller.getUserAgentDashboard("target", 30, jwt("lead-1", "lead_agent")).getBody())
                .isSameAs(dto);
    }

    @Test
    void userAgentDashboard_leadNotSharingProduct_forbidden() {
        when(metricsService.resolveScopedProductIds("lead-1")).thenReturn(List.of(10L));
        when(metricsService.userSharesAnyProduct("target", List.of(10L))).thenReturn(false);

        assertThatThrownBy(() -> controller.getUserAgentDashboard("target", 30, jwt("lead-1", "lead_agent")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void userCustomerDashboard_delegatesToService() {
        CustomerDashboardDTO dto = mock(CustomerDashboardDTO.class);
        when(metricsService.getMyCustomerDashboard("target", 30)).thenReturn(dto);

        assertThat(controller.getUserCustomerDashboard("target", 30, jwt("m-1", "manager")).getBody())
                .isSameAs(dto);
    }

    // ---- product dashboard ----

    @Test
    void productDashboard_globalRole_returnsGlobalScope() {
        ProductDashboardDTO dto = mock(ProductDashboardDTO.class);
        when(metricsService.getProductDashboard(5L, 30, "global")).thenReturn(dto);

        assertThat(controller.getProductDashboard(5L, 30, jwt("a-1", "admin")).getBody()).isSameAs(dto);
    }

    @Test
    void productDashboard_leadAuthorized_returnsScopedView() {
        when(metricsService.resolveScopedProductIds("lead-1")).thenReturn(List.of(5L));
        ProductDashboardDTO dto = mock(ProductDashboardDTO.class);
        when(metricsService.getProductDashboard(5L, 30, "lead-1")).thenReturn(dto);

        assertThat(controller.getProductDashboard(5L, 30, jwt("lead-1", "lead_agent")).getBody())
                .isSameAs(dto);
    }

    @Test
    void productDashboard_leadUnauthorizedProduct_forbidden() {
        when(metricsService.resolveScopedProductIds("lead-1")).thenReturn(List.of(99L));

        assertThatThrownBy(() -> controller.getProductDashboard(5L, 30, jwt("lead-1", "lead_agent")))
                .isInstanceOf(ResponseStatusException.class);
    }
}
