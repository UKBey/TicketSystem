package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.AlertsBacklogDTO;
import com.ticketsystem.it_service_backend.dto.CSATMetricsDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
import com.ticketsystem.it_service_backend.dto.WorklogCompletionDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.SLAPolicyRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
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
import org.springframework.data.domain.PageRequest;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock TicketRepository ticketRepository;
    @Mock TicketClaimRepository ticketClaimRepository;
    @Mock CsatRepository csatRepository;
    @Mock UserRepository userRepository;
    @Mock WorklogRepository worklogRepository;
    @Mock SLAPolicyRepository slaPolicyRepository;
    @Mock ProductRepository productRepository;
    @Mock SlaPolicyService slaPolicyService;

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
            when(ticketRepository.countByStatusIn(anyList())).thenReturn(0L);
            when(ticketRepository.countSlaBreachedByStatusIn(anyList())).thenReturn(0L);
            when(ticketRepository.countCreatedSinceByStatusIn(anyList(), any())).thenReturn(0L);
            when(ticketRepository.findAvgResolutionHoursForResolved()).thenReturn(null);
            when(ticketRepository.countByStatusInGroupByPriority(anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRating()).thenReturn(null);
            when(csatRepository.count()).thenReturn(0L);

            DashboardMetricsDTO dto = metricsService.getDashboardSummary();

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
            when(ticketRepository.countByStatusIn(anyList())).thenReturn(100L);
            when(ticketRepository.countSlaBreachedByStatusIn(anyList())).thenReturn(10L);
            when(ticketRepository.countCreatedSinceByStatusIn(anyList(), any())).thenReturn(5L);
            when(ticketRepository.findAvgResolutionHoursForResolved()).thenReturn(3.5);
            when(ticketRepository.countByStatusInGroupByPriority(anyList())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRating()).thenReturn(4.2);
            when(csatRepository.count()).thenReturn(80L);

            DashboardMetricsDTO dto = metricsService.getDashboardSummary();

            assertThat(dto.getTotalOpenTickets()).isEqualTo(100L);
            assertThat(dto.getSlaBreachedCount()).isEqualTo(10L);
            assertThat(dto.getSlaBreachedPercentage()).isEqualTo(10.0);
            assertThat(dto.getAvgResponseTimeHours()).isEqualTo(3.5);
            assertThat(dto.getCsatAverage()).isEqualTo(4.2);
        }

        @Test
        @DisplayName("Priority dağılımı DB sonuçlarından doğru map edilir")
        void priorityDistribution_mappedFromDbRows() {
            when(ticketRepository.countByStatusIn(anyList())).thenReturn(50L);
            when(ticketRepository.countSlaBreachedByStatusIn(anyList())).thenReturn(0L);
            when(ticketRepository.countCreatedSinceByStatusIn(anyList(), any())).thenReturn(0L);
            when(ticketRepository.findAvgResolutionHoursForResolved()).thenReturn(0.0);
            when(csatRepository.findAverageRating()).thenReturn(0.0);
            when(csatRepository.count()).thenReturn(0L);

            List<Object[]> priorityRows = List.of(
                    new Object[]{"CRITICAL", 3L},
                    new Object[]{"HIGH", 12L},
                    new Object[]{"MEDIUM", 25L},
                    new Object[]{"LOW", 10L}
            );
            when(ticketRepository.countByStatusInGroupByPriority(anyList())).thenReturn(priorityRows);

            DashboardMetricsDTO dto = metricsService.getDashboardSummary();

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
            when(ticketRepository.countTicketsGroupedByStatus()).thenReturn(Collections.emptyList());

            StatusDistributionDTO dto = metricsService.getStatusDistribution();

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
            when(ticketRepository.countTicketsGroupedByStatus()).thenReturn(rows);

            StatusDistributionDTO dto = metricsService.getStatusDistribution();

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
            when(userRepository.findByRole("AGENT_ADMIN")).thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance();

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
            when(userRepository.findByRole("AGENT_ADMIN")).thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance();

            assertThat(dto.getAgents()).isEmpty();
        }

        @Test
        @DisplayName("Aktif agent aggregated metric query sonucunu DTO'ya yansıtır")
        void activeAgent_withTickets_returnsMetrics() {
            User agent = User.builder().id("uuid-1").fullName("Test Agent").role("AGENT").isActive(true).build();
            when(userRepository.findByRole("AGENT")).thenReturn(List.of(agent));
            when(userRepository.findByRole("AGENT_ADMIN")).thenReturn(Collections.emptyList());

            // Aggregated query: [agent_id, active, resolved24h, slaBreached, avgResHours, csatAvg]
            when(ticketRepository.findAgentPerformanceMetrics(anyList(), any(ZonedDateTime.class)))
                    .thenReturn(List.<Object[]>of(new Object[]{"uuid-1", 1L, 0L, 0L, 0.0, 0.0}));
            when(worklogRepository.findAgentWorklogSummary(any(ZonedDateTime.class)))
                    .thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance();

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
            when(userRepository.findByRole("AGENT_ADMIN")).thenReturn(Collections.emptyList());

            // Aggregated query — DB'den ortalama 2.0 saat dönmüş gibi mock
            when(ticketRepository.findAgentPerformanceMetrics(anyList(), any(ZonedDateTime.class)))
                    .thenReturn(List.<Object[]>of(new Object[]{"uuid-2", 0L, 0L, 0L, 2.0, 0.0}));
            when(worklogRepository.findAgentWorklogSummary(any(ZonedDateTime.class)))
                    .thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance();

            assertThat(dto.getAgents()).hasSize(1);
            assertThat(dto.getAgents().get(0).getAvgResolutionHours()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("Aggregated SQL csatAverage'ı doğrudan döndürür")
        void activeAgent_withCsatData_calculatesCsatAverage() {
            User agent = User.builder().id("uuid-3").fullName("CSAT Agent").role("AGENT").isActive(true).build();
            when(userRepository.findByRole("AGENT")).thenReturn(List.of(agent));
            when(userRepository.findByRole("AGENT_ADMIN")).thenReturn(Collections.emptyList());

            when(ticketRepository.findAgentPerformanceMetrics(anyList(), any(ZonedDateTime.class)))
                    .thenReturn(List.<Object[]>of(new Object[]{"uuid-3", 0L, 0L, 0L, 0.0, 5.0}));
            when(worklogRepository.findAgentWorklogSummary(any(ZonedDateTime.class)))
                    .thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance();

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
            when(ticketRepository.getTicketTimelineMetrics(30)).thenReturn(Collections.emptyList());

            TicketTimelineDTO dto = metricsService.getTicketTimeline(30);

            assertThat(dto).isNotNull();
            assertThat(dto.getTimeline()).isEmpty();
        }

        @Test
        @DisplayName("days parametresi 365 ile sınırlandırılır")
        void days_clampedToMaximum() {
            when(ticketRepository.getTicketTimelineMetrics(365)).thenReturn(Collections.emptyList());

            TicketTimelineDTO dto = metricsService.getTicketTimeline(9999);

            assertThat(dto).isNotNull();
        }

        @Test
        @DisplayName("DB satırları DailyMetricsDTO'ya doğru dönüştürülür")
        void dbRows_convertedToDailyMetrics() {
            List<Object[]> rows = Collections.singletonList(
                    new Object[]{Date.valueOf("2026-05-01"), 5L, 3L, 1L, 0L}
            );
            when(ticketRepository.getTicketTimelineMetrics(7)).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7);

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getCreated()).isEqualTo(5L);
            assertThat(dto.getTimeline().get(0).getResolved()).isEqualTo(3L);
            assertThat(dto.getTimeline().get(0).getClosed()).isEqualTo(1L);
            assertThat(dto.getTimeline().get(0).getSlaBreach()).isEqualTo(0L);
        }

        @Test
        @DisplayName("convertToLocalDate → LocalDate girdi doğrudan döner")
        void dbRows_withLocalDateInput_convertsCorrectly() {
            LocalDate expected = LocalDate.of(2026, 5, 1);
            List<Object[]> rows = Collections.singletonList(new Object[]{expected, 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetrics(7)).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7);

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isEqualTo(expected);
        }

        @Test
        @DisplayName("convertToLocalDate → LocalDateTime LocalDate'e dönüştürülür")
        void dbRows_withLocalDateTimeInput_convertsCorrectly() {
            LocalDateTime ldt = LocalDateTime.of(2026, 5, 2, 10, 0);
            List<Object[]> rows = Collections.singletonList(new Object[]{ldt, 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetrics(7)).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7);

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 2));
        }

        @Test
        @DisplayName("convertToLocalDate → OffsetDateTime LocalDate'e dönüştürülür")
        void dbRows_withOffsetDateTimeInput_convertsCorrectly() {
            OffsetDateTime odt = OffsetDateTime.now();
            List<Object[]> rows = Collections.singletonList(new Object[]{odt, 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetrics(7)).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7);

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isNotNull();
        }

        @Test
        @DisplayName("convertToLocalDate → java.util.Date LocalDate'e dönüştürülür")
        void dbRows_withUtilDateInput_convertsCorrectly() {
            java.util.Date utilDate = new java.util.Date();
            List<Object[]> rows = Collections.singletonList(new Object[]{utilDate, 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetrics(7)).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7);

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isNotNull();
        }

        @Test
        @DisplayName("convertToLocalDate → String LocalDate.parse ile dönüştürülür")
        void dbRows_withStringInput_convertsCorrectly() {
            List<Object[]> rows = Collections.singletonList(new Object[]{"2026-05-03", 1L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetrics(7)).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7);

            assertThat(dto.getTimeline()).hasSize(1);
            assertThat(dto.getTimeline().get(0).getDate()).isEqualTo(LocalDate.of(2026, 5, 3));
        }

        @Test
        @DisplayName("convertToLocalDate → null girdi null döner")
        void dbRows_withNullDate_returnsNullDate() {
            List<Object[]> rows = Collections.singletonList(new Object[]{null, 0L, 0L, 0L, 0L});
            when(ticketRepository.getTicketTimelineMetrics(7)).thenReturn(rows);

            TicketTimelineDTO dto = metricsService.getTicketTimeline(7);

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
            when(csatRepository.findRatingDistributionSince(any())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRatingSince(any())).thenReturn(null);
            when(csatRepository.findAverageRatingByPrioritySince(any())).thenReturn(Collections.emptyList());
            when(csatRepository.findTopPositiveCommentsSince(any(), any())).thenReturn(Collections.emptyList());

            CSATMetricsDTO dto = metricsService.getCSATMetrics(3);

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
            when(csatRepository.findRatingDistributionSince(any())).thenReturn(dist);
            when(csatRepository.findAverageRatingSince(any())).thenReturn(4.5);
            when(csatRepository.findAverageRatingByPrioritySince(any())).thenReturn(Collections.emptyList());
            when(csatRepository.findTopPositiveCommentsSince(any(), any())).thenReturn(List.of("Great!"));

            CSATMetricsDTO dto = metricsService.getCSATMetrics(1);

            assertThat(dto.getTotalResponses()).isEqualTo(17L);
            assertThat(dto.getAverageRating()).isEqualTo(4.5);
            assertThat(dto.getRatingDistribution()).containsEntry(5, 10L);
            assertThat(dto.getTopComments()).containsExactly("Great!");
        }

        @Test
        @DisplayName("months parametresi 1-12 arasına sınırlandırılır")
        void months_clampedToRange() {
            when(csatRepository.findRatingDistributionSince(any())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRatingSince(any())).thenReturn(null);
            when(csatRepository.findAverageRatingByPrioritySince(any())).thenReturn(Collections.emptyList());
            when(csatRepository.findTopPositiveCommentsSince(any(), any())).thenReturn(Collections.emptyList());

            // 0 ve 99 değerleri 1 ve 12'ye sıkıştırılmalıdır
            assertThat(metricsService.getCSATMetrics(0)).isNotNull();
            assertThat(metricsService.getCSATMetrics(99)).isNotNull();
        }

        @Test
        @DisplayName("Bu ay ortalaması geçen ayın üzerindeyse trend UP olur")
        void trendUp_whenThisMonthHigher() {
            when(csatRepository.findRatingDistributionSince(any())).thenReturn(Collections.emptyList());
            when(csatRepository.findAverageRatingByPrioritySince(any())).thenReturn(Collections.emptyList());
            when(csatRepository.findTopPositiveCommentsSince(any(), any())).thenReturn(Collections.emptyList());

            // findAverageRatingSince is called 3 times: overall, thisMonth, lastMonth
            when(csatRepository.findAverageRatingSince(any()))
                    .thenReturn(4.0)   // overall
                    .thenReturn(4.5)   // thisMonth
                    .thenReturn(4.0);  // lastMonth

            CSATMetricsDTO dto = metricsService.getCSATMetrics(3);

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
            when(ticketRepository.findBreachedOpenTickets(anyList(), any())).thenReturn(Collections.emptyList());
            when(slaPolicyService.getWarningThresholdHours(anyString())).thenReturn(2);
            when(ticketRepository.findUpcomingBreachTicketsByPriority(anyList(), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(ticketRepository.findWaitingTooLongTickets(any(), any())).thenReturn(Collections.emptyList());
            when(userRepository.findAllById(anyCollection())).thenReturn(Collections.emptyList());
            when(ticketRepository.countUnassignedByStatusIn(anyList())).thenReturn(0L);
            when(ticketRepository.countByStatus("NEW")).thenReturn(0L);
            when(ticketRepository.avgWaitingHoursForOpen(anyList())).thenReturn(null);
        }

        @Test
        @DisplayName("Tüm listeler boşsa sıfır backlog metrikleri döner")
        void emptyAlerts_returnsZeroBacklog() {
            stubEmptyAlerts();

            AlertsBacklogDTO dto = metricsService.getAlertsAndBacklog();

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
                    .id(1L).title("Broken login").priority("HIGH")
                    .customerId("cust-1").slaDeadline(ZonedDateTime.now().minusHours(1)).build();
            User customer = User.builder().id("cust-1").fullName("Ahmet Yılmaz").build();

            when(ticketRepository.findBreachedOpenTickets(anyList(), any())).thenReturn(List.of(breached));
            when(slaPolicyService.getWarningThresholdHours(anyString())).thenReturn(2);
            when(ticketRepository.findUpcomingBreachTicketsByPriority(anyList(), anyList(), any(), any()))
                    .thenReturn(Collections.emptyList());
            when(ticketRepository.findWaitingTooLongTickets(any(), any())).thenReturn(Collections.emptyList());
            when(userRepository.findAllById(anyCollection())).thenReturn(List.of(customer));
            when(ticketRepository.countUnassignedByStatusIn(anyList())).thenReturn(3L);
            when(ticketRepository.countByStatus("NEW")).thenReturn(5L);
            when(ticketRepository.avgWaitingHoursForOpen(anyList())).thenReturn(2.5);

            AlertsBacklogDTO dto = metricsService.getAlertsAndBacklog();

            assertThat(dto.getBreachedSLA()).hasSize(1);
            assertThat(dto.getBreachedSLA().get(0).getCustomerName()).isEqualTo("Ahmet Yılmaz");
            assertThat(dto.getBacklogMetrics().getUnassignedCount()).isEqualTo(3L);
            assertThat(dto.getBacklogMetrics().getAvgWaitingHours()).isEqualTo(2.5);
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
            when(worklogRepository.findAgentWorklogSummary(any())).thenReturn(Collections.emptyList());
            when(ticketRepository.countCreatedSince(any())).thenReturn(0L);
            when(ticketRepository.countResolvedSince(any())).thenReturn(0L);
            when(ticketRepository.countClosedSince(any())).thenReturn(0L);
            when(ticketRepository.avgResolutionHoursSince(any())).thenReturn(null);
            when(ticketRepository.slaComplianceRateSince(any())).thenReturn(null);

            WorklogCompletionDTO dto = metricsService.getWorklogCompletion(7);

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

            when(worklogRepository.findAgentWorklogSummary(any())).thenReturn(rawRows);
            when(userRepository.findById("agent-1")).thenReturn(java.util.Optional.of(agent));
            when(ticketRepository.countCreatedSince(any())).thenReturn(10L);
            when(ticketRepository.countResolvedSince(any())).thenReturn(6L);
            when(ticketRepository.countClosedSince(any())).thenReturn(2L);
            when(ticketRepository.avgResolutionHoursSince(any())).thenReturn(3.5);
            when(ticketRepository.slaComplianceRateSince(any())).thenReturn(0.85);

            WorklogCompletionDTO dto = metricsService.getWorklogCompletion(30);

            assertThat(dto.getAgentWorklogs()).hasSize(1);
            assertThat(dto.getAgentWorklogs().get(0).getAgentUsername()).isEqualTo("Test Agent");
            assertThat(dto.getAgentWorklogs().get(0).getTotalMinutes()).isEqualTo(120L);
            assertThat(dto.getAgentWorklogs().get(0).getAvgMinutesPerEntry()).isEqualTo(40.0);
            assertThat(dto.getCompletionRates().getCompletionRate()).isEqualTo(80.0);
        }

        @Test
        @DisplayName("days parametresi 1-365 arasına sınırlandırılır")
        void days_clampedToRange() {
            when(worklogRepository.findAgentWorklogSummary(any())).thenReturn(Collections.emptyList());
            when(ticketRepository.countCreatedSince(any())).thenReturn(0L);
            when(ticketRepository.countResolvedSince(any())).thenReturn(0L);
            when(ticketRepository.countClosedSince(any())).thenReturn(0L);
            when(ticketRepository.avgResolutionHoursSince(any())).thenReturn(null);
            when(ticketRepository.slaComplianceRateSince(any())).thenReturn(null);

            WorklogCompletionDTO dto = metricsService.getWorklogCompletion(9999);

            assertThat(dto.getPeriodDays()).isEqualTo(365);
        }
    }
}
