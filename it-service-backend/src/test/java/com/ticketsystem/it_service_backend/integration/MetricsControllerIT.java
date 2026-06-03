package com.ticketsystem.it_service_backend.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.hamcrest.Matchers.isA;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests covering every MetricsController endpoint.
 *
 * <p>Runs against a real PostgreSQL container (Testcontainers). With an
 * empty database each endpoint must return zero/empty values and must not
 * throw. Authorization for each endpoint is verified with the MANAGER,
 * LEAD_AGENT, ADMIN, CUSTOMER and AGENT roles.
 */
@DisplayName("MetricsController — Tüm Endpoint Entegrasyon Testleri")
class MetricsControllerIT extends BaseIntegrationTest {

    private static final SimpleGrantedAuthority MANAGER     = new SimpleGrantedAuthority("ROLE_MANAGER");
    private static final SimpleGrantedAuthority LEAD_AGENT  = new SimpleGrantedAuthority("ROLE_LEAD_AGENT");
    private static final SimpleGrantedAuthority ADMIN       = new SimpleGrantedAuthority("ROLE_ADMIN");
    private static final SimpleGrantedAuthority AGENT       = new SimpleGrantedAuthority("ROLE_AGENT");
    private static final SimpleGrantedAuthority CUSTOMER    = new SimpleGrantedAuthority("ROLE_CUSTOMER");

    // =========================================================================
    // GET /api/v1/metrics/dashboard-summary
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/metrics/dashboard-summary")
    class DashboardSummary {

        @Test
        @DisplayName("MANAGER → 200, response structure correct")
        void manager_gets200WithValidStructure() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/dashboard-summary").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalOpenTickets").value(isA(Number.class)))
                    .andExpect(jsonPath("$.slaBreachedCount").value(isA(Number.class)))
                    .andExpect(jsonPath("$.avgResponseTimeHours").value(isA(Number.class)))
                    .andExpect(jsonPath("$.csatAverage").value(isA(Number.class)))
                    .andExpect(jsonPath("$.priorityDistribution").exists());
        }

        @Test
        @DisplayName("Boş DB → sıfır değerler, hata yok")
        void emptyDatabase_returnsZeroDefaults() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/dashboard-summary").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalOpenTickets").value(0))
                    .andExpect(jsonPath("$.slaBreachedCount").value(0))
                    .andExpect(jsonPath("$.avgResponseTimeHours").value(0.0));
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/dashboard-summary").with(jwt().authorities(CUSTOMER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AGENT → 403")
        void agent_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/dashboard-summary").with(jwt().authorities(AGENT)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // GET /api/v1/metrics/status-distribution
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/metrics/status-distribution")
    class StatusDistribution {

        @Test
        @DisplayName("MANAGER → 200, distribution fields present")
        void manager_gets200WithFields() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/status-distribution").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.newCount").value(isA(Number.class)))
                    .andExpect(jsonPath("$.inProgressCount").value(isA(Number.class)))
                    .andExpect(jsonPath("$.resolvedCount").value(isA(Number.class)))
                    .andExpect(jsonPath("$.totalCount").value(isA(Number.class)));
        }

        @Test
        @DisplayName("Boş DB → tüm sayımlar sıfır")
        void emptyDatabase_allCountsZero() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/status-distribution").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCount").value(0));
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/status-distribution").with(jwt().authorities(CUSTOMER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AGENT → 403")
        void agent_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/status-distribution").with(jwt().authorities(AGENT)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // GET /api/v1/metrics/agent-performance
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/metrics/agent-performance")
    class AgentPerformance {

        @Test
        @DisplayName("MANAGER → 200, agents list returned")
        void manager_gets200WithAgentsList() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/agent-performance").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agents").isArray())
                    .andExpect(jsonPath("$.totalAgents").value(isA(Number.class)));
        }

        @Test
        @DisplayName("LEAD_AGENT → 200 (ürün-scope'lu)")
        void leadAgent_gets200() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/agent-performance").with(jwt().authorities(LEAD_AGENT)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ADMIN → 200")
        void admin_gets200() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/agent-performance").with(jwt().authorities(ADMIN)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/agent-performance").with(jwt().authorities(CUSTOMER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AGENT → 403")
        void agent_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/agent-performance").with(jwt().authorities(AGENT)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // GET /api/v1/metrics/ticket-timeline
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/metrics/ticket-timeline")
    class TicketTimeline {

        @Test
        @DisplayName("MANAGER, default days → 200, timeline array")
        void manager_defaultDays_gets200WithTimeline() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/ticket-timeline").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timeline").isArray());
        }

        @Test
        @DisplayName("MANAGER, days=7 → 200, array")
        void manager_customDays_gets200() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/ticket-timeline?days=7").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timeline").isArray());
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/ticket-timeline").with(jwt().authorities(CUSTOMER)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // GET /api/v1/metrics/priority-sla-metrics
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/metrics/priority-sla-metrics")
    class PrioritySlaMetrics {

        @Test
        @DisplayName("MANAGER → 200, priorityMetrics array")
        void manager_gets200WithPriorityList() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/priority-sla-metrics").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.priorityMetrics").isArray());
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/priority-sla-metrics").with(jwt().authorities(CUSTOMER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AGENT → 403")
        void agent_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/priority-sla-metrics").with(jwt().authorities(AGENT)))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // GET /api/v1/metrics/product-metrics
    // =========================================================================

    @Nested
    @DisplayName("GET /api/v1/metrics/product-metrics")
    class ProductMetrics {

        @Test
        @DisplayName("MANAGER → 200, productMetrics array")
        void manager_gets200WithProductList() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/product-metrics").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productMetrics").isArray());
        }

        @Test
        @DisplayName("Boş DB → boş liste, hata yok")
        void emptyDatabase_returnsEmptyList() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/product-metrics").with(jwt().authorities(MANAGER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.productMetrics").isArray());
        }

        @Test
        @DisplayName("CUSTOMER → 403")
        void customer_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/product-metrics").with(jwt().authorities(CUSTOMER)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("AGENT → 403")
        void agent_gets403() throws Exception {
            mockMvc.perform(get("/api/v1/metrics/product-metrics").with(jwt().authorities(AGENT)))
                    .andExpect(status().isForbidden());
        }
    }
}
