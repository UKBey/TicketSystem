package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
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

import java.sql.Date;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
        @DisplayName("Aktif agent ticket, worklog ve CSAT verisiyle işlenir")
        void activeAgent_withTickets_returnsMetrics() {
            User agent = User.builder().id("uuid-1").fullName("Test Agent").role("AGENT").isActive(true).build();
            when(userRepository.findByRole("AGENT")).thenReturn(List.of(agent));
            when(userRepository.findByRole("AGENT_ADMIN")).thenReturn(Collections.emptyList());

            Ticket activeTicket = Ticket.builder()
                    .id(1L).status("IN_PROGRESS")
                    .priority("HIGH").slaBreached(false)
                    .createdAt(ZonedDateTime.now().minusDays(2))
                    .build();
            when(ticketRepository.findAll()).thenReturn(List.of(activeTicket));
            when(ticketClaimRepository.findAgentIdAndTicketIdByAgentIdIn(anyList()))
                    .thenReturn(List.<Object[]>of(new Object[]{"uuid-1", 1L}));
            when(worklogRepository.findAll()).thenReturn(Collections.emptyList());
            when(csatRepository.findAll()).thenReturn(Collections.emptyList());

            AgentPerformanceDTO dto = metricsService.getAgentPerformance();

            assertThat(dto.getAgents()).hasSize(1);
            assertThat(dto.getAgents().get(0).getAgentName()).isEqualTo("Test Agent");
            assertThat(dto.getAgents().get(0).getActiveTickets()).isEqualTo(1L);
            assertThat(dto.getTotalActiveTickets()).isEqualTo(1L);
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
    }
}
