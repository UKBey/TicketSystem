package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.config.AlertProperties;
import com.ticketsystem.it_service_backend.dto.AgentDashboardDTO;
import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.AlertsBacklogDTO;
import com.ticketsystem.it_service_backend.dto.CSATMetricsDTO;
import com.ticketsystem.it_service_backend.dto.CustomerDashboardDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
import com.ticketsystem.it_service_backend.dto.WorklogCompletionDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.SLAPolicyRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import com.ticketsystem.it_service_backend.entity.TicketStatus;
import com.ticketsystem.it_service_backend.entity.Priority;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock CsatRepository csatRepository;
    @Mock UserRepository userRepository;
    @Mock WorklogRepository worklogRepository;
    @Mock SLAPolicyRepository slaPolicyRepository;
    @Mock ProductRepository productRepository;
    @Mock SlaPolicyService slaPolicyService;
    @Mock AlertProperties alertProperties;

    @InjectMocks
    MetricsService metricsService;

    // =========================================================================
    // getDashboardSummary
    // =========================================================================

    @Nested
    @DisplayName("getDashboardSummary()")
    class GetDashboardSummary {

        @Test
        @DisplayName("Boş DB → tüm metrikler sıfır, hata yok")
        void emptyDatabase_returnsZeroDefaults() {
            when(ticketRepository.countByStatusInScoped(anyList(), anyBoolean(), anyList())).thenReturn(0L);
            when(ticketRepository.countSlaBreachedByStatusInScoped(anyList(), anyBoolean(), anyList())).thenReturn(0L);
            when(ticketRepository.countCreatedSinceByStatusInScoped(anyList(), any(), anyBoolean(), anyList())).thenReturn(0L);
            when(ticketRepository.findAvgResolutionHoursForResolvedScoped(anyBoolean(), anyList())).thenReturn(null);
            when(ticketRepository.countByStatusInGroupByPriorityScoped(anyList(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRatingScoped(anyBoolean(), anyList())).thenReturn(null);
            when(csatRepository.countScoped(anyBoolean(), anyList())).thenReturn(0L);

            DashboardMetricsDTO dto = metricsService.getDashboardSummary(null, "global");

            assertThat(dto).isNotNull();
            assertThat(dto.getTotalOpenTickets()).isZero();
            assertThat(dto.getSlaBreachedCount()).isZero();
            assertThat(dto.getSlaBreachedPercentage()).isEqualTo(0.0);
            assertThat(dto.getAvgResponseTimeHours()).isEqualTo(0.0);
            assertThat(dto.getCsatAverage()).isEqualTo(0.0);
            assertThat(dto.getPriorityDistribution()).isNotNull();
            assertThat(dto.getPriorityDistribution().getCritical()).isZero();
        }

        @Test
        @DisplayName("SLA breach yüzdesi doğru hesaplanır")
        void slaBreachPercentage_calculatedCorrectly() {
            when(ticketRepository.countByStatusInScoped(anyList(), anyBoolean(), anyList())).thenReturn(100L);
            when(ticketRepository.countSlaBreachedByStatusInScoped(anyList(), anyBoolean(), anyList())).thenReturn(10L);
            when(ticketRepository.countCreatedSinceByStatusInScoped(anyList(), any(), anyBoolean(), anyList())).thenReturn(5L);
            when(ticketRepository.findAvgResolutionHoursForResolvedScoped(anyBoolean(), anyList())).thenReturn(3.5);
            when(ticketRepository.countByStatusInGroupByPriorityScoped(anyList(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRatingScoped(anyBoolean(), anyList())).thenReturn(4.2);
            when(csatRepository.countScoped(anyBoolean(), anyList())).thenReturn(80L);

            DashboardMetricsDTO dto = metricsService.getDashboardSummary(null, "global");

            assertThat(dto.getTotalOpenTickets()).isEqualTo(100L);
            assertThat(dto.getSlaBreachedCount()).isEqualTo(10L);
            assertThat(dto.getSlaBreachedPercentage()).isEqualTo(10.0);
            assertThat(dto.getAvgResponseTimeHours()).isEqualTo(3.5);
            assertThat(dto.getCsatAverage()).isEqualTo(4.2);
        }

        @Test
        @DisplayName("Priority dağılımı DB sonuçlarından doğru map edilir")
        void priorityDistribution_mappedFromDbRows() {
            when(ticketRepository.countByStatusInScoped(anyList(), anyBoolean(), anyList())).thenReturn(50L);
            when(ticketRepository.countSlaBreachedByStatusInScoped(anyList(), anyBoolean(), anyList())).thenReturn(0L);
            when(ticketRepository.countCreatedSinceByStatusInScoped(anyList(), any(), anyBoolean(), anyList())).thenReturn(0L);
            when(ticketRepository.findAvgResolutionHoursForResolvedScoped(anyBoolean(), anyList())).thenReturn(0.0);
            when(csatRepository.findAverageRatingScoped(anyBoolean(), anyList())).thenReturn(0.0);
            when(csatRepository.countScoped(anyBoolean(), anyList())).thenReturn(0L);

            List<Object[]> priorityRows = List.of(
                    new Object[]{"CRITICAL", 3L},
                    new Object[]{"HIGH", 12L},
                    new Object[]{"MEDIUM", 25L},
                    new Object[]{"LOW", 10L}
            );
            when(ticketRepository.countByStatusInGroupByPriorityScoped(anyList(), anyBoolean(), anyList())).thenReturn(priorityRows);

            DashboardMetricsDTO dto = metricsService.getDashboardSummary(null, "global");

            assertThat(dto.getPriorityDistribution().getCritical()).isEqualTo(3L);
            assertThat(dto.getPriorityDistribution().getHigh()).isEqualTo(12L);
            assertThat(dto.getPriorityDistribution().getMedium()).isEqualTo(25L);
            assertThat(dto.getPriorityDistribution().getLow()).isEqualTo(10L);
        }
    }

    // =========================================================================
    // getStatusDistribution
    // =========================================================================

    @Nested
    @DisplayName("getStatusDistribution()")
    class GetStatusDistribution {

        @Test
        @DisplayName("Boş DB → tüm sayılar sıfır")
        void emptyDatabase_returnsZeros() {
            when(ticketRepository.countTicketsGroupedByStatusScoped(anyBoolean(), anyList())).thenReturn(Collections.emptyList());

            StatusDistributionDTO dto = metricsService.getStatusDistribution(null, "global");

            assertThat(dto).isNotNull();
            assertThat(dto.getNewCount()).isZero();
            assertThat(dto.getInProgressCount()).isZero();
            assertThat(dto.getWaitingForCustomerCount()).isZero();
            assertThat(dto.getResolvedCount()).isZero();
            assertThat(dto.getClosedCount()).isZero();
            assertThat(dto.getTotalCount()).isZero();
        }

        @Test
        @DisplayName("DB satırları doğru alanlara map edilir")
        void dbRows_mappedCorrectly() {
            List<Object[]> rows = List.of(
                    new Object[]{"NEW", 44L},
                    new Object[]{"IN_PROGRESS", 103L},
                    new Object[]{"WAITING_FOR_CUSTOMER", 54L},
                    new Object[]{"RESOLVED", 38L},
                    new Object[]{"CLOSED", 6L}
            );
            when(ticketRepository.countTicketsGroupedByStatusScoped(anyBoolean(), anyList())).thenReturn(rows);

            StatusDistributionDTO dto = metricsService.getStatusDistribution(null, "global");

            assertThat(dto.getNewCount()).isEqualTo(44L);
            assertThat(dto.getInProgressCount()).isEqualTo(103L);
            assertThat(dto.getWaitingForCustomerCount()).isEqualTo(54L);
            assertThat(dto.getResolvedCount()).isEqualTo(38L);
            assertThat(dto.getClosedCount()).isEqualTo(6L);
            assertThat(dto.getTotalCount()).isEqualTo(245L);
        }
    }

    // =========================================================================
    // getAgentPerformance
    // =========================================================================

    @Nested
    @DisplayName("getAgentPerformance()")
    class GetAgentPerformance {

        @Test
        @DisplayName("Aktif agent yoksa boş liste ve sıfır toplamlar döner")
        void noActiveAgents_returnsEmptyList() {
            when(userRepository.findByRole("AGENT")).thenReturn(Collections.emptyList());
            when(userRepository.findByRole("LEAD_AGENT")).thenReturn(Collections.emptyList());
            when(worklogRepository.findAgentWorklogSummaryScoped(any(ZonedDateTime.class), anyBoolean(), anyList()))
                    .thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance(null, "global");

            assertThat(dto).isNotNull();
            assertThat(dto.getAgents()).isEmpty();
            assertThat(dto.getTotalAgents()).isZero();
            assertThat(dto.getTotalActiveTickets()).isZero();
        }

        @Test
        @DisplayName("Pasif agent filtrelenir, aktif olanlar işlenir")
        void inactiveAgent_isFiltered() {
            User inactive = User.builder().id("uuid-1").fullName("Pasif Kullanıcı").role("AGENT").isActive(false).build();
            when(userRepository.findByRole("AGENT")).thenReturn(List.of(inactive));
            when(userRepository.findByRole("LEAD_AGENT")).thenReturn(Collections.emptyList());
            when(worklogRepository.findAgentWorklogSummaryScoped(any(ZonedDateTime.class), anyBoolean(), anyList()))
                    .thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance(null, "global");

            assertThat(dto.getAgents()).isEmpty();
        }

        @Test
        @DisplayName("Aktif agent aggregated metric query sonucunu DTO'ya yansıtır")
        void activeAgent_withTickets_returnsMetrics() {
            User agent = User.builder().id("uuid-1").fullName("Test Agent").role("AGENT").isActive(true).build();
            when(userRepository.findByRole("AGENT")).thenReturn(List.of(agent));
            when(userRepository.findByRole("LEAD_AGENT")).thenReturn(Collections.emptyList());

            // Aggregated query: [agent_id, active, resolved24h, slaBreached, avgResHours, csatAvg]
            when(ticketRepository.findAgentPerformanceMetricsScoped(anyList(), any(ZonedDateTime.class), anyBoolean(), anyList()))
                    .thenReturn(List.<Object[]>of(new Object[]{"uuid-1", 1L, 0L, 0L, 0.0, 0.0}));
            when(worklogRepository.findAgentWorklogSummaryScoped(any(ZonedDateTime.class), anyBoolean(), anyList()))
                    .thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance(null, "global");

            assertThat(dto.getAgents()).hasSize(1);
            assertThat(dto.getAgents().get(0).getAgentName()).isEqualTo("Test Agent");
            assertThat(dto.getAgents().get(0).getActiveTickets()).isEqualTo(1L);
            assertThat(dto.getTotalActiveTickets()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Aggregated SQL avgResolutionHours hesabını DB tarafında yapar")
        void activeAgent_withResolvedTicket_calculatesAvgResolutionHours() {
            User agent = User.builder().id("uuid-2").fullName("Resolver Agent").role("AGENT").isActive(true).build();
            when(userRepository.findByRole("AGENT")).thenReturn(List.of(agent));
            when(userRepository.findByRole("LEAD_AGENT")).thenReturn(Collections.emptyList());

            // Aggregated query — DB'den ortalama 2.0 saat dönmüş gibi mock
            when(ticketRepository.findAgentPerformanceMetricsScoped(anyList(), any(ZonedDateTime.class), anyBoolean(), anyList()))
                    .thenReturn(List.<Object[]>of(new Object[]{"uuid-2", 0L, 0L, 0L, 2.0, 0.0}));
            when(worklogRepository.findAgentWorklogSummaryScoped(any(ZonedDateTime.class), anyBoolean(), anyList()))
                    .thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance(null, "global");

            assertThat(dto.getAgents()).hasSize(1);
            assertThat(dto.getAgents().get(0).getAvgResolutionHours()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Aggregated SQL csatAverage'ı doğrudan döndürür")
        void activeAgent_withCsatData_calculatesCsatAverage() {
            User agent = User.builder().id("uuid-3").fullName("CSAT Agent").role("AGENT").isActive(true).build();
            when(userRepository.findByRole("AGENT")).thenReturn(List.of(agent));
            when(userRepository.findByRole("LEAD_AGENT")).thenReturn(Collections.emptyList());

            when(ticketRepository.findAgentPerformanceMetricsScoped(anyList(), any(ZonedDateTime.class), anyBoolean(), anyList()))
                    .thenReturn(List.<Object[]>of(new Object[]{"uuid-3", 0L, 0L, 0L, 0.0, 5.0}));
            when(worklogRepository.findAgentWorklogSummaryScoped(any(ZonedDateTime.class), anyBoolean(), anyList()))
                    .thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance(null, "global");

            assertThat(dto.getAgents()).hasSize(1);
            assertThat(dto.getAgents().get(0).getCsatAverage()).isGreaterThan(0.0);
        }
    }

    // =========================================================================
    // getTicketTimeline
    // =========================================================================

    @Nested
    @DisplayName("getTicketTimeline()")
    class GetTicketTimeline {

        @Test
        @DisplayName("Boş DB → boş timeline listesi döner")
        void emptyDatabase_returnsEmptyTimeline() {
            when(ticketRepository.getTicketTimelineMetricsScoped(eq(30), anyBoolean(), anyList())).thenReturn(Collections.emptyList());

            TicketTimelineDTO dto = metricsService.getTicketTimeline(30, null, "global");

            assertThat(dto).isNotNull();
            assertThat(dto.getTimeline()).isEmpty();
        }

        @Test
        @DisplayName("days parametresi 365 ile sınırlandırılır")
        void days_clampedToMaximum() {
            when(ticketRepository.getTicketTimelineMetricsScoped(eq(365), anyBoolean(), anyList())).thenReturn(Collections.emptyList());

            TicketTimelineDTO dto = metricsService.getTicketTimeline(9999, null, "global");

            assertThat(dto).isNotNull();
        }

        @Test
        @DisplayName("DB satırları DailyMetricsDTO'ya doğru dönüştürülür")
        void dbRows_convertedToDailyMetrics() {
            List<Object[]> rows = Collections.singletonList(
                    new Object[]{Date.valueOf("2026-05-01"), 5L, 3L, 1L, 0L}
            );
            when(ticketRepository.getTicketTimelineMetricsScoped(eq(7), anyBoolean(), anyList())).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7, null, "global");

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getCreated()).isEqualTo(5L);
            assertThat(dto.getTimeline().get(0).getResolved()).isEqualTo(3L);
            assertThat(dto.getTimeline().get(0).getClosed()).isEqualTo(1L);
            assertThat(dto.getTimeline().get(0).getSlaBreach()).isZero();
        }

        @Test
        @DisplayName("convertToLocalDate → LocalDate girdi doğrudan döner")
        void dbRows_withLocalDateInput_convertsCorrectly() {
            LocalDate expected = LocalDate.of(2026, 5, 1);
            List<Object[]> rows = Collections.singletonList(new Object[]{expected, 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetricsScoped(eq(7), anyBoolean(), anyList())).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7, null, "global");

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isEqualTo(expected);
        }

        @Test
        @DisplayName("convertToLocalDate → LocalDateTime LocalDate'e dönüştürülür")
        void dbRows_withLocalDateTimeInput_convertsCorrectly() {
            LocalDateTime ldt = LocalDateTime.of(2026, 5, 2, 10, 0);
            List<Object[]> rows = Collections.singletonList(new Object[]{ldt, 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetricsScoped(eq(7), anyBoolean(), anyList())).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7, null, "global");

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 2));
        }

        @Test
        @DisplayName("convertToLocalDate → OffsetDateTime LocalDate'e dönüştürülür")
        void dbRows_withOffsetDateTimeInput_convertsCorrectly() {
            OffsetDateTime odt = OffsetDateTime.now();
            List<Object[]> rows = Collections.singletonList(new Object[]{odt, 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetricsScoped(eq(7), anyBoolean(), anyList())).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7, null, "global");

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isNotNull();
        }

        @Test
        @DisplayName("convertToLocalDate → java.util.Date LocalDate'e dönüştürülür")
        void dbRows_withUtilDateInput_convertsCorrectly() {
            java.util.Date utilDate = new java.util.Date();
            List<Object[]> rows = Collections.singletonList(new Object[]{utilDate, 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetricsScoped(eq(7), anyBoolean(), anyList())).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7, null, "global");

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isNotNull();
        }

        @Test
        @DisplayName("convertToLocalDate → String LocalDate.parse ile dönüştürülür")
        void dbRows_withStringInput_convertsCorrectly() {
            List<Object[]> rows = Collections.singletonList(new Object[]{"2026-05-03", 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetricsScoped(eq(7), anyBoolean(), anyList())).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7, null, "global");

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 3));
        }

        @Test
        @DisplayName("convertToLocalDate → null girdi null döner")
        void dbRows_withNullDate_returnsNullDate() {
            List<Object[]> rows = Collections.singletonList(new Object[]{null, 0L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetricsScoped(eq(7), anyBoolean(), anyList())).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7, null, "global");

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isNull();
        }
    }

    // =========================================================================
    // getCSATMetrics
    // =========================================================================

    @Nested
    @DisplayName("getCSATMetrics()")
    class GetCSATMetrics {

        @Test
        @DisplayName("Boş DB → sıfır değerler ve STABLE trend")
        void emptyDatabase_returnsZeroDefaults() {
            when(csatRepository.findRatingDistributionSinceScoped(any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRatingSinceScoped(any(), anyBoolean(), anyList())).thenReturn(null);
            when(csatRepository.findAverageRatingByPrioritySinceScoped(any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findTopPositiveCommentsSinceScoped(any(), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());

            CSATMetricsDTO dto = metricsService.getCSATMetrics(3, null, "global");

            assertThat(dto.getTotalResponses()).isZero();
            assertThat(dto.getAverageRating()).isEqualTo(0.0);
            assertThat(dto.getTrend().getTrend()).isEqualTo("STABLE");
            assertThat(dto.getTopComments()).isEmpty();
        }

        @Test
        @DisplayName("Dağılım satırları rating haritasına doğru map edilir")
        void ratingDistribution_mappedCorrectly() {
            List<Object[]> dist = List.of(
                    new Object[]{5, 10L},
                    new Object[]{4, 5L},
                    new Object[]{3, 2L}
            );
            when(csatRepository.findRatingDistributionSinceScoped(any(), anyBoolean(), anyList())).thenReturn(dist);
            when(csatRepository.findAverageRatingSinceScoped(any(), anyBoolean(), anyList())).thenReturn(4.5);
            when(csatRepository.findAverageRatingByPrioritySinceScoped(any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findTopPositiveCommentsSinceScoped(any(), anyBoolean(), anyList(), any())).thenReturn(List.of("Great!"));

            CSATMetricsDTO dto = metricsService.getCSATMetrics(1, null, "global");

            assertThat(dto.getTotalResponses()).isEqualTo(17L);
            assertThat(dto.getAverageRating()).isEqualTo(4.5);
            assertThat(dto.getRatingDistribution()).containsEntry(5, 10L);
            assertThat(dto.getTopComments()).containsExactly("Great!");
        }

        @Test
        @DisplayName("months parametresi 1-12 arasına sınırlandırılır")
        void months_clampedToRange() {
            when(csatRepository.findRatingDistributionSinceScoped(any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRatingSinceScoped(any(), anyBoolean(), anyList())).thenReturn(null);
            when(csatRepository.findAverageRatingByPrioritySinceScoped(any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findTopPositiveCommentsSinceScoped(any(), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());

            // 0 ve 99 değerleri 1 ve 12'ye sıkıştırılmalıdır
            assertThat(metricsService.getCSATMetrics(0, null, "global")).isNotNull();
            assertThat(metricsService.getCSATMetrics(99, null, "global")).isNotNull();
        }

        @Test
        @DisplayName("Bu ay ortalaması geçen ayın üzerindeyse trend UP olur")
        void trendUp_whenThisMonthHigher() {
            when(csatRepository.findRatingDistributionSinceScoped(any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRatingByPrioritySinceScoped(any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findTopPositiveCommentsSinceScoped(any(), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());

            // findAverageRatingSinceScoped is called 3 times: overall, thisMonth, lastMonth
            when(csatRepository.findAverageRatingSinceScoped(any(), anyBoolean(), anyList()))
                    .thenReturn(4.0)   // overall
                    .thenReturn(4.5)   // thisMonth
                    .thenReturn(4.0);  // lastMonth

            CSATMetricsDTO dto = metricsService.getCSATMetrics(3, null, "global");

            assertThat(dto.getTrend().getTrend()).isEqualTo("UP");
        }
    }

    // =========================================================================
    // getAlertsAndBacklog
    // =========================================================================

    @Nested
    @DisplayName("getAlertsAndBacklog()")
    class GetAlertsAndBacklog {

        private void stubEmptyAlerts() {
            when(ticketRepository.findBreachedOpenTicketsScoped(anyList(), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());
            when(slaPolicyService.getWarningThresholdHours(anyString())).thenReturn(2);
            when(ticketRepository.findUpcomingBreachTicketsByPriorityScoped(anyList(), anyList(), any(), anyBoolean(), anyList(), any()))
                    .thenReturn(Collections.emptyList());
            when(ticketRepository.findWaitingTooLongTicketsScoped(any(), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());
            when(ticketRepository.findResolvedTooLongTicketsScoped(any(), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());
            when(userRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());
            when(ticketRepository.countUnassignedByStatusInScoped(anyList(), anyBoolean(), anyList())).thenReturn(0L);
            when(ticketRepository.countByStatusScoped(eq("NEW"), anyBoolean(), anyList())).thenReturn(0L);
            when(ticketRepository.avgWaitingHoursForOpenScoped(anyList(), anyBoolean(), anyList())).thenReturn(null);
        }

        @Test
        @DisplayName("Tüm listeler boşsa sıfır backlog metrikleri döner")
        void emptyAlerts_returnsZeroBacklog() {
            stubEmptyAlerts();

            AlertsBacklogDTO dto = metricsService.getAlertsAndBacklog(null, "global");

            assertThat(dto.getBreachedSLA()).isEmpty();
            assertThat(dto.getUpcomingBreach()).isEmpty();
            assertThat(dto.getWaitingTooLong()).isEmpty();
            assertThat(dto.getBacklogMetrics().getUnassignedCount()).isZero();
            assertThat(dto.getBacklogMetrics().getAvgWaitingHours()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Aşılan bilet customerId → müşteri adına map edilir")
        void breachedTicket_customerNameResolved() {
            Ticket breached = Ticket.builder()
                    .id(1L).title("Broken login").priority(Priority.HIGH)
                    .customerId("cust-1").slaDeadline(ZonedDateTime.now().minusHours(1)).build();
            User customer = User.builder().id("cust-1").fullName("Ahmet Yılmaz").build();

            when(ticketRepository.findBreachedOpenTicketsScoped(anyList(), anyBoolean(), anyList(), any())).thenReturn(List.of(breached));
            when(slaPolicyService.getWarningThresholdHours(anyString())).thenReturn(2);
            when(ticketRepository.findUpcomingBreachTicketsByPriorityScoped(anyList(), anyList(), any(), anyBoolean(), anyList(), any()))
                    .thenReturn(Collections.emptyList());
            when(ticketRepository.findWaitingTooLongTicketsScoped(any(), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());
            when(ticketRepository.findResolvedTooLongTicketsScoped(any(), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());
            when(userRepository.findAllById(anyCollection())).thenReturn(List.of(customer));
            when(ticketRepository.countUnassignedByStatusInScoped(anyList(), anyBoolean(), anyList())).thenReturn(3L);
            when(ticketRepository.countByStatusScoped(eq("NEW"), anyBoolean(), anyList())).thenReturn(5L);
            when(ticketRepository.avgWaitingHoursForOpenScoped(anyList(), anyBoolean(), anyList())).thenReturn(2.5);

            AlertsBacklogDTO dto = metricsService.getAlertsAndBacklog(null, "global");

            assertThat(dto.getBreachedSLA()).hasSize(1);
            assertThat(dto.getBreachedSLA().get(0).getCustomerName()).isEqualTo("Ahmet Yılmaz");
            assertThat(dto.getBacklogMetrics().getUnassignedCount()).isEqualTo(3L);
            assertThat(dto.getBacklogMetrics().getAvgWaitingHours()).isEqualTo(2.5);
        }

        @Test
        @DisplayName("WAITING + RESOLVED takılı biletler tek listede; duruma giriş anına göre, en uzun bekleyen önce")
        void waitingAndResolved_mergedAndSortedByEntryTime() {
            ZonedDateTime now = ZonedDateTime.now();
            // Oluşturulma çok eski ama bekleme durumuna giriş (slaPausedAt) yakın → süre giriş anından sayılmalı.
            Ticket waiting = Ticket.builder()
                    .id(10L).title("Need info").priority(Priority.MEDIUM).status(TicketStatus.WAITING_FOR_CUSTOMER)
                    .customerId("cust-1").createdAt(now.minusDays(5)).slaPausedAt(now.minusHours(10)).build();
            Ticket resolved = Ticket.builder()
                    .id(11L).title("Fixed?").priority(Priority.LOW).status(TicketStatus.RESOLVED)
                    .customerId("cust-2").createdAt(now.minusDays(8))
                    .resolvedAt(now.minusHours(20)).slaPausedAt(now.minusHours(20)).build();

            when(ticketRepository.findBreachedOpenTicketsScoped(anyList(), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());
            when(slaPolicyService.getWarningThresholdHours(anyString())).thenReturn(2);
            when(ticketRepository.findUpcomingBreachTicketsByPriorityScoped(anyList(), anyList(), any(), anyBoolean(), anyList(), any()))
                    .thenReturn(Collections.emptyList());
            when(ticketRepository.findWaitingTooLongTicketsScoped(any(), anyBoolean(), anyList(), any())).thenReturn(List.of(waiting));
            when(ticketRepository.findResolvedTooLongTicketsScoped(any(), anyBoolean(), anyList(), any())).thenReturn(List.of(resolved));
            when(userRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());
            when(ticketRepository.countUnassignedByStatusInScoped(anyList(), anyBoolean(), anyList())).thenReturn(0L);
            when(ticketRepository.countByStatusScoped(eq("NEW"), anyBoolean(), anyList())).thenReturn(0L);
            when(ticketRepository.avgWaitingHoursForOpenScoped(anyList(), anyBoolean(), anyList())).thenReturn(null);

            AlertsBacklogDTO dto = metricsService.getAlertsAndBacklog(null, "global");

            assertThat(dto.getWaitingTooLong()).hasSize(2);
            // En uzun bekleyen (RESOLVED, ~20s) başta
            assertThat(dto.getWaitingTooLong().get(0).getStatus()).isEqualTo(TicketStatus.RESOLVED);
            assertThat(dto.getWaitingTooLong().get(0).getHoursWaiting()).isGreaterThan(19.0).isLessThan(21.0);
            assertThat(dto.getWaitingTooLong().get(1).getStatus()).isEqualTo(TicketStatus.WAITING_FOR_CUSTOMER);
            assertThat(dto.getWaitingTooLong().get(1).getHoursWaiting()).isGreaterThan(9.0).isLessThan(11.0);
        }
    }

    // =========================================================================
    // getWorklogCompletion
    // =========================================================================

    @Nested
    @DisplayName("getWorklogCompletion()")
    class GetWorklogCompletion {

        @Test
        @DisplayName("Worklog kaydı yoksa boş liste ve sıfır oranlar döner")
        void noWorklogs_returnsZeroRates() {
            when(worklogRepository.findAgentWorklogSummaryScoped(any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(ticketRepository.findWorklogCompletionAggregatesScoped(any(), anyBoolean(), anyList()))
                    .thenReturn(List.<Object[]>of(new Object[]{0L, 0L, 0L, null, null, 0L}));

            WorklogCompletionDTO dto = metricsService.getWorklogCompletion(7, null, "global");

            assertThat(dto.getAgentWorklogs()).isEmpty();
            assertThat(dto.getCompletionRates().getCompletionRate()).isEqualTo(0.0);
            assertThat(dto.getCompletionRates().getAvgResolutionHours()).isEqualTo(0.0);
            assertThat(dto.getCompletionRates().getSlaComplianceRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Worklog satırları agent adıyla birleştirilir")
        void worklogRows_enrichedWithAgentName() {
            User agent = User.builder().id("agent-1").fullName("Test Agent").build();
            List<Object[]> rawRows = List.<Object[]>of(new Object[]{"agent-1", 120L, 3L});

            when(worklogRepository.findAgentWorklogSummaryScoped(any(), anyBoolean(), anyList())).thenReturn(rawRows);
            when(userRepository.findAllById(anyIterable())).thenReturn(List.of(agent));
            when(ticketRepository.findWorklogCompletionAggregatesScoped(any(), anyBoolean(), anyList()))
                    .thenReturn(List.<Object[]>of(new Object[]{10L, 6L, 2L, 3.5, 0.85, 8L}));

            WorklogCompletionDTO dto = metricsService.getWorklogCompletion(30, null, "global");

            assertThat(dto.getAgentWorklogs()).hasSize(1);
            assertThat(dto.getAgentWorklogs().get(0).getAgentUsername()).isEqualTo("Test Agent");
            assertThat(dto.getAgentWorklogs().get(0).getTotalMinutes()).isEqualTo(120L);
            assertThat(dto.getAgentWorklogs().get(0).getAvgMinutesPerEntry()).isEqualTo(40.0);
            assertThat(dto.getCompletionRates().getCompletionRate()).isEqualTo(80.0);
            assertThat(dto.getCompletionRates().getResolvedInPeriod()).isEqualTo(8L);
        }

        @Test
        @DisplayName("days parametresi 1-365 arasına sınırlandırılır")
        void days_clampedToRange() {
            when(worklogRepository.findAgentWorklogSummaryScoped(any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(ticketRepository.findWorklogCompletionAggregatesScoped(any(), anyBoolean(), anyList()))
                    .thenReturn(List.<Object[]>of(new Object[]{0L, 0L, 0L, null, null, 0L}));

            WorklogCompletionDTO dto = metricsService.getWorklogCompletion(9999, null, "global");

            assertThat(dto.getPeriodDays()).isEqualTo(365);
        }
    }

    // =========================================================================
    // getMyCustomerDashboard — kişisel müşteri dashboard'u (customer_id kapsamlı)
    // =========================================================================

    @Nested
    @DisplayName("getMyCustomerDashboard()")
    class GetMyCustomerDashboard {

        @Test
        @DisplayName("status rows + CSAT maplenir, open/resolved türetilir")
        void mapsRowsAndDerivesTotals() {
            String customer = "cust-1";
            when(ticketRepository.countTicketsGroupedByStatusForCustomer(customer)).thenReturn(List.of(
                    new Object[]{"NEW", 2L},
                    new Object[]{"IN_PROGRESS", 1L},
                    new Object[]{"RESOLVED", 5L},
                    new Object[]{"CLOSED", 3L}
            ));
            when(ticketRepository.countSlaBreachedByCustomerAndStatusIn(eq(customer), anyList())).thenReturn(1L);
            when(ticketRepository.findAvgResolutionHoursForCustomer(customer)).thenReturn(6.5);
            when(csatRepository.findCustomerCsat(customer)).thenReturn(List.<Object[]>of(new Object[]{4.5, 10L}));
            when(ticketRepository.getCustomerTicketTimelineMetrics(anyInt(), eq(customer))).thenReturn(List.<Object[]>of(
                    new Object[]{LocalDate.of(2026, 1, 1), 1L, 2L, 0L, 0L}
            ));
            when(ticketRepository.findRecentByCustomerId(eq(customer), any())).thenReturn(List.of(
                    Ticket.builder().id(9L).title("VPN").status(TicketStatus.RESOLVED).priority(Priority.HIGH)
                            .createdAt(ZonedDateTime.now()).build()
            ));

            CustomerDashboardDTO dto = metricsService.getMyCustomerDashboard(customer, 30);

            assertThat(dto.getTotalTickets()).isEqualTo(11L);
            assertThat(dto.getOpenTickets()).isEqualTo(3L);
            assertThat(dto.getResolvedTickets()).isEqualTo(8L);
            assertThat(dto.getSlaBreachedCount()).isEqualTo(1L);
            assertThat(dto.getAvgResolutionHours()).isEqualTo(6.5);
            assertThat(dto.getCsatAverage()).isEqualTo(4.5);
            assertThat(dto.getCsatCount()).isEqualTo(10L);
            assertThat(dto.getStatusDistribution().getNewCount()).isEqualTo(2L);
            assertThat(dto.getTimeline().getTimeline()).hasSize(1);
            assertThat(dto.getRecentTickets()).hasSize(1);
            assertThat(dto.getRecentTickets().get(0).getTitle()).isEqualTo("VPN");
        }

        @Test
        @DisplayName("CSAT yoksa ve avg null ise 0 döner, boş listeler")
        void noDataDefaultsToZero() {
            String customer = "cust-2";
            when(ticketRepository.countTicketsGroupedByStatusForCustomer(customer)).thenReturn(Collections.emptyList());
            when(ticketRepository.countSlaBreachedByCustomerAndStatusIn(eq(customer), anyList())).thenReturn(0L);
            when(ticketRepository.findAvgResolutionHoursForCustomer(customer)).thenReturn(null);
            when(csatRepository.findCustomerCsat(customer)).thenReturn(List.<Object[]>of(new Object[]{0.0, 0L}));
            when(ticketRepository.getCustomerTicketTimelineMetrics(anyInt(), eq(customer))).thenReturn(Collections.emptyList());
            when(ticketRepository.findRecentByCustomerId(eq(customer), any())).thenReturn(Collections.emptyList());

            CustomerDashboardDTO dto = metricsService.getMyCustomerDashboard(customer, null);

            assertThat(dto.getTotalTickets()).isZero();
            assertThat(dto.getAvgResolutionHours()).isEqualTo(0.0);
            assertThat(dto.getCsatAverage()).isEqualTo(0.0);
            assertThat(dto.getCsatCount()).isZero();
            assertThat(dto.getRecentTickets()).isEmpty();
        }
    }

    // =========================================================================
    // getMyAgentDashboard — kişisel ajan performansı (claim agent_id kapsamlı)
    // =========================================================================

    @Nested
    @DisplayName("getMyAgentDashboard()")
    class GetMyAgentDashboard {

        @Test
        @DisplayName("self metrik satırı maplenir, SLA ihlal oranı hesaplanır")
        void mapsRowAndComputesRate() {
            String agent = "agent-1";
            // [active, resolvedInRange, slaBreachedInRange, totalClaimed, avgRes, csatAvg, csatCount]
            when(ticketRepository.findAgentSelfMetricsScoped(eq(agent), any(), anyBoolean(), anyList())).thenReturn(List.<Object[]>of(
                    new Object[]{7L, 18L, 4L, 120L, 5.1, 4.4, 52L}
            ));
            when(worklogRepository.sumAgentWorklogMinutesSinceScoped(eq(agent), any(ZonedDateTime.class), anyBoolean(), anyList())).thenReturn(640L);
            when(worklogRepository.findAgentWorklogByDayScoped(eq(agent), anyInt(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAgentRatingDistributionSince(eq(agent), any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAgentCsatByDayScoped(eq(agent), anyInt(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(ticketRepository.countClaimedTicketsGroupedByStatusScoped(eq(agent), anyBoolean(), anyList())).thenReturn(List.<Object[]>of(
                    new Object[]{"IN_PROGRESS", 7L},
                    new Object[]{"RESOLVED", 50L}
            ));
            when(ticketRepository.getAgentTicketTimelineMetricsScoped(anyInt(), eq(agent), anyBoolean(), anyList())).thenReturn(List.<Object[]>of(
                    new Object[]{LocalDate.of(2026, 1, 1), 1L, 1L, 0L, 0L}
            ));
            when(ticketRepository.findRecentClaimedByAgentScoped(eq(agent), anyBoolean(), anyList(), any())).thenReturn(List.of(
                    Ticket.builder().id(3L).title("Mail").status(TicketStatus.IN_PROGRESS).priority(Priority.MEDIUM)
                            .createdAt(ZonedDateTime.now()).build()
            ));

            AgentDashboardDTO dto = metricsService.getMyAgentDashboard(agent, 7);

            assertThat(dto.getActiveTickets()).isEqualTo(7L);
            assertThat(dto.getResolvedInRange()).isEqualTo(18L);
            assertThat(dto.getSlaBreachedCount()).isEqualTo(4L);
            assertThat(dto.getTotalClaimed()).isEqualTo(120L);
            assertThat(dto.getAvgResolutionHours()).isEqualTo(5.1);
            assertThat(dto.getSlaBreachRate()).isBetween(22.2, 22.3); // 4/18*100
            assertThat(dto.getWorklogMinutesInRange()).isEqualTo(640L);
            assertThat(dto.getCsatAverage()).isEqualTo(4.4);
            assertThat(dto.getCsatCount()).isEqualTo(52L);
            assertThat(dto.getCsat().getAverage()).isEqualTo(4.4);
            assertThat(dto.getCsat().getTotalResponses()).isEqualTo(52L);
            assertThat(dto.getCsat().getRatingDistribution()).containsKeys(1, 2, 3, 4, 5);
            assertThat(dto.getWorklogTimeline()).isEmpty();
            assertThat(dto.getStatusDistribution().getResolvedCount()).isEqualTo(50L);
            assertThat(dto.getTimeline().getTimeline()).hasSize(1);
            assertThat(dto.getRecentTickets()).hasSize(1);
        }

        @Test
        @DisplayName("claim yoksa hepsi 0, oran 0")
        void noClaimsDefaultsToZero() {
            String agent = "agent-2";
            when(ticketRepository.findAgentSelfMetricsScoped(eq(agent), any(), anyBoolean(), anyList())).thenReturn(List.<Object[]>of(
                    new Object[]{0L, 0L, 0L, 0L, 0.0, 0.0, 0L}
            ));
            when(worklogRepository.sumAgentWorklogMinutesSinceScoped(eq(agent), any(ZonedDateTime.class), anyBoolean(), anyList())).thenReturn(0L);
            when(worklogRepository.findAgentWorklogByDayScoped(eq(agent), anyInt(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAgentRatingDistributionSince(eq(agent), any(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAgentCsatByDayScoped(eq(agent), anyInt(), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(ticketRepository.countClaimedTicketsGroupedByStatusScoped(eq(agent), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(ticketRepository.getAgentTicketTimelineMetricsScoped(anyInt(), eq(agent), anyBoolean(), anyList())).thenReturn(Collections.emptyList());
            when(ticketRepository.findRecentClaimedByAgentScoped(eq(agent), anyBoolean(), anyList(), any())).thenReturn(Collections.emptyList());

            AgentDashboardDTO dto = metricsService.getMyAgentDashboard(agent, 30);

            assertThat(dto.getActiveTickets()).isZero();
            assertThat(dto.getTotalClaimed()).isZero();
            assertThat(dto.getSlaBreachRate()).isEqualTo(0.0);
            assertThat(dto.getWorklogMinutesInRange()).isZero();
            assertThat(dto.getRecentTickets()).isEmpty();
        }
    }
}
