package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.AgentPerformanceItemDTO;
import com.ticketsystem.it_service_backend.dto.DailyMetricsDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PriorityMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsService {

    private final TicketRepository ticketRepository;
    private final CsatRepository csatRepository;
        private final UserRepository userRepository;
        private final WorklogRepository worklogRepository;

    /**
     * Dashboard özet metrikleri hesaplar.
     * KPI kartları için gerekli temel veriler: toplam açık bilet, SLA breach,
     * ortalama yanıt süresi, CSAT puanı ve priority dağılımı.
     *
     * @return DashboardMetricsDTO — tüm KPI metrikleri
     */
    public DashboardMetricsDTO getDashboardSummary() {
        log.info("Dashboard özet metrikleri hesaplanıyor...");

        // 1. Açık biletleri getir (NEW, IN_PROGRESS, WAITING_FOR_CUSTOMER)
        List<Ticket> openTickets = ticketRepository.findByStatus("NEW");
        openTickets.addAll(ticketRepository.findByStatus("IN_PROGRESS"));
        openTickets.addAll(ticketRepository.findByStatus("WAITING_FOR_CUSTOMER"));
        Long totalOpenTickets = (long) openTickets.size();

        // 2. Son 24 saatte oluşan biletler
        ZonedDateTime last24Hours = ZonedDateTime.now().minusHours(24);
        Long newTicketsLast24Hours = openTickets.stream()
                .filter(t -> t.getCreatedAt().isAfter(last24Hours))
                .count();

        // 3. SLA breach biletleri
        Long slaBreachedCount = openTickets.stream()
                .filter(t -> t.getSlaBreached() != null && t.getSlaBreached())
                .count();
        Double slaBreachedPercentage = totalOpenTickets > 0
                ? (double) slaBreachedCount / totalOpenTickets * 100
                : 0.0;

        // 4. Ortalama yanıt süresi — RESOLVED biletlerin creation -> resolution süresi
        List<Ticket> resolvedTickets = ticketRepository.findByStatus("RESOLVED");
        Double avgResponseTimeHours = calculateAverageResponseTime(resolvedTickets);

        // 5. CSAT ortalaması
        Double csatAverage = csatRepository.findAverageRating();
        Long csatTotalResponses = csatRepository.count();

        // 6. Priority dağılımı
        PriorityMetricsDTO priorityDistribution = getPriorityDistribution(openTickets);

        log.info("Dashboard metrikleri hesaplandı: açık={}, SLAbreach={}, CSAT={}, yanıt={}h",
                totalOpenTickets, slaBreachedCount, csatAverage, avgResponseTimeHours);

        return DashboardMetricsDTO.builder()
                .totalOpenTickets(totalOpenTickets)
                .newTicketsLast24Hours(newTicketsLast24Hours)
                .slaBreachedCount(slaBreachedCount)
                .slaBreachedPercentage(slaBreachedPercentage)
                .avgResponseTimeHours(avgResponseTimeHours)
                .csatAverage(csatAverage)
                .csatTotalResponses(csatTotalResponses)
                .priorityDistribution(priorityDistribution)
                .build();
    }

        /**
         * Ticket durum dağılımını hesaplar.
         *
         * @return StatusDistributionDTO — tüm durumlar için ticket sayıları
         */
        public StatusDistributionDTO getStatusDistribution() {
                log.info("Ticket durum dağılımı hesaplanıyor...");

                List<Object[]> rawRows = ticketRepository.countTicketsGroupedByStatus();

                StatusDistributionDTO.StatusDistributionDTOBuilder builder = StatusDistributionDTO.builder()
                                .newCount(0L)
                                .inProgressCount(0L)
                                .waitingForCustomerCount(0L)
                                .resolvedCount(0L)
                                .closedCount(0L)
                                .totalCount(0L);

                long totalCount = 0L;

                for (Object[] row : rawRows) {
                        String status = String.valueOf(row[0]);
                        long count = ((Number) row[1]).longValue();
                        totalCount += count;

                        switch (status) {
                                case "NEW" -> builder.newCount(count);
                                case "IN_PROGRESS" -> builder.inProgressCount(count);
                                case "WAITING_FOR_CUSTOMER" -> builder.waitingForCustomerCount(count);
                                case "RESOLVED" -> builder.resolvedCount(count);
                                case "CLOSED" -> builder.closedCount(count);
                                default -> log.warn("Bilinmeyen ticket status değeri: {}", status);
                        }
                }

                return builder.totalCount(totalCount).build();
        }

    /**
     * Ajan performans leaderboard verisini hesaplar.
     *
     * @return AgentPerformanceDTO — agent satırları ve özet metrikler
     */
    public AgentPerformanceDTO getAgentPerformance() {
        log.info("Agent performans metrikleri hesaplanıyor...");

        List<User> agents = userRepository.findByRole("AGENT");
        agents.addAll(userRepository.findByRole("AGENT_ADMIN"));

        List<User> activeAgents = agents.stream()
                .filter(agent -> Boolean.TRUE.equals(agent.getIsActive()))
                .filter(agent -> agent.getId() != null)
                .collect(Collectors.toMap(User::getId, agent -> agent, (left, right) -> left))
                .values()
                .stream()
                .sorted(Comparator.comparing(User::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<Ticket> tickets = ticketRepository.findAll();
        List<TicketWorklog> worklogs = worklogRepository.findAll();
        Map<Long, Integer> csatByTicketId = csatRepository.findAll().stream()
                .collect(Collectors.toMap(
                        csat -> csat.getTicketId(),
                        csat -> csat.getRating(),
                        (left, right) -> left
                ));

        ZonedDateTime last24Hours = ZonedDateTime.now().minusHours(24);
        ZonedDateTime last7Days = ZonedDateTime.now().minusDays(7);

        List<AgentPerformanceItemDTO> agentRows = activeAgents.stream()
                .map(agent -> buildAgentPerformanceRow(agent, tickets, worklogs, csatByTicketId, last24Hours, last7Days))
                .sorted(Comparator
                        .comparing(AgentPerformanceItemDTO::getActiveTickets, Comparator.reverseOrder())
                        .thenComparing(AgentPerformanceItemDTO::getResolvedLast24Hours, Comparator.reverseOrder())
                        .thenComparing(AgentPerformanceItemDTO::getCsatAverage, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long totalActiveTickets = agentRows.stream().mapToLong(row -> row.getActiveTickets() != null ? row.getActiveTickets() : 0L).sum();
        long totalSlaBreachedCount = agentRows.stream().mapToLong(row -> row.getSlaBreachedCount() != null ? row.getSlaBreachedCount() : 0L).sum();
        long totalResolvedLast24Hours = agentRows.stream().mapToLong(row -> row.getResolvedLast24Hours() != null ? row.getResolvedLast24Hours() : 0L).sum();

        double averageCsat = agentRows.stream()
                .map(AgentPerformanceItemDTO::getCsatAverage)
                .filter(value -> value != null && value > 0)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return AgentPerformanceDTO.builder()
                .generatedAt(ZonedDateTime.now()
                        .truncatedTo(ChronoUnit.SECONDS))
                .totalAgents((long) agentRows.size())
                .totalActiveTickets(totalActiveTickets)
                .totalSlaBreachedCount(totalSlaBreachedCount)
                .totalResolvedLast24Hours(totalResolvedLast24Hours)
                .averageCsat(averageCsat)
                .agents(agentRows)
                .build();
    }

    private AgentPerformanceItemDTO buildAgentPerformanceRow(
            User agent,
            List<Ticket> tickets,
            List<TicketWorklog> worklogs,
            Map<Long, Integer> csatByTicketId,
            ZonedDateTime last24Hours,
            ZonedDateTime last7Days) {

        List<Ticket> agentTickets = tickets.stream()
                .filter(ticket -> agent.getId().equals(ticket.getAssigneeId()))
                .toList();

        long activeTickets = agentTickets.stream()
                .filter(ticket -> Set.of("NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER").contains(ticket.getStatus()))
                .count();

        long resolvedLast24Hours = agentTickets.stream()
                .filter(ticket -> ticket.getResolvedAt() != null && ticket.getResolvedAt().isAfter(last24Hours))
                .count();

        long slaBreachedCount = agentTickets.stream()
                .filter(ticket -> Boolean.TRUE.equals(ticket.getSlaBreached()))
                .count();

        Double avgResolutionHours = calculateAverageResolutionHours(agentTickets);
        Double csatAverage = calculateAverageCsat(agentTickets, csatByTicketId);
        Long worklogMinutesLast7Days = worklogs.stream()
                .filter(worklog -> agent.getId().equals(worklog.getAgentId()))
                .filter(worklog -> worklog.getCreatedAt() != null && worklog.getCreatedAt().isAfter(last7Days))
                .mapToLong(worklog -> worklog.getMinutes() != null ? worklog.getMinutes() : 0L)
                .sum();

        return AgentPerformanceItemDTO.builder()
                .agentId(agent.getId())
                .agentName(agent.getFullName())
                .role(agent.getRole())
                .activeTickets(activeTickets)
                .resolvedLast24Hours(resolvedLast24Hours)
                .avgResolutionHours(avgResolutionHours)
                .csatAverage(csatAverage)
                .slaBreachedCount(slaBreachedCount)
                .worklogMinutesLast7Days(worklogMinutesLast7Days)
                .build();
    }

    private Double calculateAverageResolutionHours(List<Ticket> tickets) {
        List<Ticket> resolvedTickets = tickets.stream()
                .filter(ticket -> ticket.getCreatedAt() != null && ticket.getResolvedAt() != null)
                .toList();

        if (resolvedTickets.isEmpty()) {
            return 0.0;
        }

        double totalHours = resolvedTickets.stream()
                .mapToDouble(ticket -> ChronoUnit.MILLIS.between(ticket.getCreatedAt(), ticket.getResolvedAt()) / (1000.0 * 60 * 60))
                .sum();

        return totalHours / resolvedTickets.size();
    }

    private Double calculateAverageCsat(List<Ticket> tickets, Map<Long, Integer> csatByTicketId) {
        List<Integer> ratings = tickets.stream()
                .map(Ticket::getId)
                .map(csatByTicketId::get)
                .filter(rating -> rating != null)
                .toList();

        if (ratings.isEmpty()) {
            return 0.0;
        }

        return ratings.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Açık biletlerin priority'ye göre dağılımını hesaplar.
     *
     * @param openTickets açık biletlerin listesi
     * @return PriorityMetricsDTO — her priority seviyesi için sayı
     */
    private PriorityMetricsDTO getPriorityDistribution(List<Ticket> openTickets) {
        long critical = openTickets.stream()
                .filter(t -> "CRITICAL".equals(t.getPriority()))
                .count();
        long high = openTickets.stream()
                .filter(t -> "HIGH".equals(t.getPriority()))
                .count();
        long medium = openTickets.stream()
                .filter(t -> "MEDIUM".equals(t.getPriority()))
                .count();
        long low = openTickets.stream()
                .filter(t -> "LOW".equals(t.getPriority()))
                .count();

        return PriorityMetricsDTO.builder()
                .critical(critical)
                .high(high)
                .medium(medium)
                .low(low)
                .build();
    }

    /**
     * RESOLVED biletlerin ortalama çözüm süresi hesaplar (saat cinsinden).
     *
     * @param resolvedTickets RESOLVED durumdaki biletler
     * @return Ortalama çözüm süresi saat cinsinden, veya 0.0 eğer boş liste
     */
    private Double calculateAverageResponseTime(List<Ticket> resolvedTickets) {
        if (resolvedTickets.isEmpty()) {
            return 0.0;
        }

        double totalHours = resolvedTickets.stream()
                .filter(t -> t.getCreatedAt() != null && t.getResolvedAt() != null)
                .mapToDouble(t -> {
                    long millis = java.time.temporal.ChronoUnit.MILLIS.between(
                            t.getCreatedAt(), t.getResolvedAt()
                    );
                    return millis / (1000.0 * 60 * 60); // millisaniyeyi saate çevir
                })
                .sum();

        return resolvedTickets.isEmpty() ? 0.0 : totalHours / resolvedTickets.size();
    }

    /**
     * Son N güne ait günlük ticket timeline metriklerini hesaplar.
     * Günlük oluşturulan, çözülen, kapalı bilet sayılarını ve SLA breach sayılarını döner.
     * 
     * @param days Kaç günlük veri isteneceği (default 30)
     * @return TicketTimelineDTO — günlük metriklerin timeline'ı
     */
    public TicketTimelineDTO getTicketTimeline(int days) {
        log.info("Ticket timeline metrikleri hesaplanıyor... (days={})", days);

        // Maksimum 365 gün sınırlaması
        int safeDays = Math.min(Math.max(days, 1), 365);

        List<Object[]> rawRows = ticketRepository.getTicketTimelineMetrics(safeDays);

        List<DailyMetricsDTO> timeline = rawRows.stream()
                .map(row -> DailyMetricsDTO.builder()
                        .date((LocalDate) row[0])
                        .created(((Number) row[1]).longValue())
                        .resolved(((Number) row[2]).longValue())
                        .closed(((Number) row[3]).longValue())
                        .slaBreach(((Number) row[4]).longValue())
                        .build())
                .toList();

        log.info("Ticket timeline hesaplandı: {} günlük veri elde edildi", timeline.size());

        return TicketTimelineDTO.builder()
                .timeline(timeline)
                .build();
    }
}
