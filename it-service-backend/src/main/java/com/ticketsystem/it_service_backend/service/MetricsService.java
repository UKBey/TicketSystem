package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.AgentPerformanceItemDTO;
import com.ticketsystem.it_service_backend.dto.AlertsBacklogDTO;
import com.ticketsystem.it_service_backend.dto.AlertTicketItemDTO;
import com.ticketsystem.it_service_backend.dto.BacklogMetricsDTO;
import com.ticketsystem.it_service_backend.dto.CSATMetricsDTO;
import com.ticketsystem.it_service_backend.dto.CSATPriorityItemDTO;
import com.ticketsystem.it_service_backend.dto.CSATTrendDTO;
import com.ticketsystem.it_service_backend.dto.DailyMetricsDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PriorityDetailDTO;
import com.ticketsystem.it_service_backend.dto.PriorityMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PrioritySLAMetricsDTO;
import com.ticketsystem.it_service_backend.dto.CompletionRatesDTO;
import com.ticketsystem.it_service_backend.dto.ProductDetailDTO;
import com.ticketsystem.it_service_backend.dto.ProductMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.WorklogCompletionDTO;
import com.ticketsystem.it_service_backend.dto.WorklogSummaryItemDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.SLAPolicyRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.sql.Date;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsService {

    private final TicketRepository ticketRepository;
    private final CsatRepository csatRepository;
    private final UserRepository userRepository;
    private final WorklogRepository worklogRepository;
    private final SLAPolicyRepository slaPolicyRepository;
    private final ProductRepository productRepository;

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

        // 5. CSAT ortalaması — SQL AVG() boş tabloda NULL döner, 0.0 yap
        Double csatAverage = csatRepository.findAverageRating();
        if (csatAverage == null) csatAverage = 0.0;
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
        List<Ticket> validTickets = resolvedTickets.stream()
                .filter(t -> t.getCreatedAt() != null && t.getResolvedAt() != null)
                .toList();

        if (validTickets.isEmpty()) {
            return 0.0;
        }

        double totalHours = validTickets.stream()
                .mapToDouble(t -> {
                    long millis = java.time.temporal.ChronoUnit.MILLIS.between(
                            t.getCreatedAt(), t.getResolvedAt()
                    );
                    return millis / (1000.0 * 60 * 60);
                })
                .sum();

        return totalHours / validTickets.size();
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
                        .date(convertToLocalDate(row[0]))
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

        private LocalDate convertToLocalDate(Object value) {
                if (value == null) {
                        return null;
                }

                if (value instanceof LocalDate localDate) {
                        return localDate;
                }

                if (value instanceof Date sqlDate) {
                        return sqlDate.toLocalDate();
                }

                if (value instanceof LocalDateTime localDateTime) {
                        return localDateTime.toLocalDate();
                }

                if (value instanceof OffsetDateTime offsetDateTime) {
                        return offsetDateTime.toLocalDate();
                }

                if (value instanceof java.util.Date utilDate) {
                        return utilDate.toInstant().atZone(ZonedDateTime.now().getZone()).toLocalDate();
                }

                return LocalDate.parse(value.toString());
        }

    /**
     * Priority bazlı SLA hedef ve performans metriklerini hesaplar.
     * Her priority için ticket adedi, SLA hedef süresi, ortalama çözüm süresi,
     * breach yüzdesi ve on-time yüzdesi döner.
     *
     * @return PrioritySLAMetricsDTO — priority detay satırları
     */
    public PrioritySLAMetricsDTO getPrioritySlaMetrics() {
        log.info("Priority-SLA metrikleri hesaplanıyor...");

        List<Object[]> rawRows = slaPolicyRepository.findPrioritySlaMetrics();

        List<PriorityDetailDTO> details = rawRows.stream()
                .map(row -> PriorityDetailDTO.builder()
                        .priority(String.valueOf(row[0]))
                        .ticketCount(((Number) row[1]).longValue())
                        .slaTargetHours(((Number) row[2]).intValue())
                        .avgResolutionHours(((Number) row[3]).doubleValue())
                        .breachCount(((Number) row[4]).longValue())
                        .breachPercentage(((Number) row[5]).doubleValue())
                        .onTimePercentage(((Number) row[6]).doubleValue())
                        .build())
                .toList();

        log.info("Priority-SLA metrikleri hesaplandı: {} satır", details.size());

        return PrioritySLAMetricsDTO.builder()
                .priorityMetrics(details)
                .build();
    }

    /**
     * CSAT detaylı analitik metriklerini hesaplar.
     * Son N ay içindeki CSAT yanıtlarını analiz eder: dağılım, trend, priority bazlı ortalama ve en iyi yorumlar.
     *
     * @param months Kaç aylık veri analiz edileceği (default 3, max 12)
     * @return CSATMetricsDTO — CSAT analitik özeti
     */
    public CSATMetricsDTO getCSATMetrics(int months) {
        log.info("CSAT detaylı metrikleri hesaplanıyor... (months={})", months);

        int safeMonths = Math.min(Math.max(months, 1), 12);

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime since = now.minusMonths(safeMonths);
        ZonedDateTime startOfThisMonth = now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime startOfLastMonth = startOfThisMonth.minusMonths(1);

        // Toplam yanıt ve genel ortalama
        long totalResponses = csatRepository.findRatingDistributionSince(since).stream()
                .mapToLong(row -> ((Number) row[1]).longValue())
                .sum();

        Double avgRaw = csatRepository.findAverageRatingSince(since);
        double averageRating = avgRaw != null ? avgRaw : 0.0;

        // Puan dağılımı (1-5 tüm anahtarlar, yoksa 0)
        Map<Integer, Long> ratingDistribution = new LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) ratingDistribution.put(i, 0L);
        for (Object[] row : csatRepository.findRatingDistributionSince(since)) {
            int rating = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            ratingDistribution.put(rating, count);
        }

        // Trend: bu ay vs geçen ay
        Double thisMonthRaw = csatRepository.findAverageRatingSince(startOfThisMonth);
        double thisMonth = thisMonthRaw != null ? thisMonthRaw : 0.0;
        double lastMonthOnlyRaw = computeLastMonthAverage(startOfLastMonth, startOfThisMonth);
        String trendDirection = determineTrend(thisMonth, lastMonthOnlyRaw);

        CSATTrendDTO trend = CSATTrendDTO.builder()
                .thisMonth(thisMonth)
                .lastMonth(lastMonthOnlyRaw)
                .trend(trendDirection)
                .build();

        // Priority bazlı CSAT
        Map<String, CSATPriorityItemDTO> byPriority = new LinkedHashMap<>();
        for (Object[] row : csatRepository.findAverageRatingByPrioritySince(since)) {
            String priority = String.valueOf(row[0]);
            double avg = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            long responses = ((Number) row[2]).longValue();
            byPriority.put(priority, CSATPriorityItemDTO.builder().avg(avg).responses(responses).build());
        }

        // En iyi yorumlar (en fazla 5)
        List<String> topComments = csatRepository.findTopPositiveCommentsSince(since, PageRequest.of(0, 5));

        log.info("CSAT metrikleri hesaplandı: {} yanıt, ort={}, trend={}", totalResponses, averageRating, trendDirection);

        return CSATMetricsDTO.builder()
                .totalResponses(totalResponses)
                .averageRating(averageRating)
                .ratingDistribution(ratingDistribution)
                .trend(trend)
                .byPriority(byPriority)
                .topComments(topComments)
                .build();
    }

    private double computeLastMonthAverage(ZonedDateTime startOfLastMonth, ZonedDateTime startOfThisMonth) {
        List<Object[]> distrib = csatRepository.findRatingDistributionSince(startOfLastMonth);
        List<Object[]> thisMonthDistrib = csatRepository.findRatingDistributionSince(startOfThisMonth);

        Map<Integer, Long> lastMonthMap = new HashMap<>();
        for (Object[] row : distrib) {
            lastMonthMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        Map<Integer, Long> thisMonthMap = new HashMap<>();
        for (Object[] row : thisMonthDistrib) {
            thisMonthMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        long totalCount = 0;
        long totalSum = 0;
        for (int r = 1; r <= 5; r++) {
            long last = lastMonthMap.getOrDefault(r, 0L);
            long cur = thisMonthMap.getOrDefault(r, 0L);
            long onlyLastMonth = Math.max(0, last - cur);
            totalCount += onlyLastMonth;
            totalSum += (long) r * onlyLastMonth;
        }
        return totalCount > 0 ? (double) totalSum / totalCount : 0.0;
    }

    private String determineTrend(double current, double previous) {
        if (previous == 0.0) return "STABLE";
        double diff = current - previous;
        if (diff > 0.05) return "UP";
        if (diff < -0.05) return "DOWN";
        return "STABLE";
    }

    /**
     * SLA breach uyarılarını ve backlog metriklerini hesaplar.
     * Zaten aşılmış biletler, yaklaşan breach (4 saat), uzun süre bekleyenler ve atanmamış bilet sayısı.
     *
     * @return AlertsBacklogDTO — alert listeleri ve backlog özeti
     */
    public AlertsBacklogDTO getAlertsAndBacklog() {
        log.info("Alert ve backlog metrikleri hesaplanıyor...");

        ZonedDateTime now = ZonedDateTime.now();
        List<String> openStatuses = List.of("NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER");

        // SLA'yı aşmış açık biletler
        List<AlertTicketItemDTO> breachedSLA = ticketRepository.findBreachedOpenTickets()
                .stream()
                .limit(10)
                .map(t -> toAlertItem(t, now))
                .toList();

        // 4 saat içinde SLA'yı aşacak biletler
        ZonedDateTime upcomingCutoff = now.plusHours(4);
        List<AlertTicketItemDTO> upcomingBreach = ticketRepository
                .findUpcomingBreachTickets(now, upcomingCutoff)
                .stream()
                .limit(10)
                .map(t -> toAlertItem(t, now))
                .toList();

        // 3+ gün WAITING_FOR_CUSTOMER biletler
        ZonedDateTime waitingCutoff = now.minusDays(3);
        List<AlertTicketItemDTO> waitingTooLong = ticketRepository
                .findWaitingTooLongTickets(waitingCutoff)
                .stream()
                .limit(10)
                .map(t -> AlertTicketItemDTO.builder()
                        .ticketId(t.getId())
                        .title(t.getTitle())
                        .priority(t.getPriority())
                        .customerId(t.getCustomerId())
                        .hoursWaiting(t.getCreatedAt() != null
                                ? ChronoUnit.MILLIS.between(t.getCreatedAt(), now) / (1000.0 * 60 * 60)
                                : 0.0)
                        .build())
                .toList();

        // Backlog metrikleri
        List<Ticket> openTickets = ticketRepository.findByStatusIn(openStatuses);

        long unassignedCount = openTickets.stream()
                .filter(t -> t.getAssigneeId() == null || t.getAssigneeId().isBlank())
                .count();
        long newWaiting = openTickets.stream()
                .filter(t -> "NEW".equals(t.getStatus()))
                .count();
        double avgWaitingHours = openTickets.stream()
                .filter(t -> t.getCreatedAt() != null)
                .mapToDouble(t -> ChronoUnit.MILLIS.between(t.getCreatedAt(), now) / (1000.0 * 60 * 60))
                .average()
                .orElse(0.0);

        log.info("Alert metrikleri hesaplandı: breach={}, upcoming={}, waiting={}, unassigned={}",
                breachedSLA.size(), upcomingBreach.size(), waitingTooLong.size(), unassignedCount);

        return AlertsBacklogDTO.builder()
                .breachedSLA(breachedSLA)
                .upcomingBreach(upcomingBreach)
                .waitingTooLong(waitingTooLong)
                .backlogMetrics(BacklogMetricsDTO.builder()
                        .unassignedCount(unassignedCount)
                        .newTicketsWaiting(newWaiting)
                        .avgWaitingHours(avgWaitingHours)
                        .build())
                .build();
    }

    private AlertTicketItemDTO toAlertItem(Ticket t, ZonedDateTime now) {
        double hoursUntilDeadline = t.getSlaDeadline() != null
                ? ChronoUnit.MILLIS.between(now, t.getSlaDeadline()) / (1000.0 * 60 * 60)
                : 0.0;
        return AlertTicketItemDTO.builder()
                .ticketId(t.getId())
                .title(t.getTitle())
                .priority(t.getPriority())
                .customerId(t.getCustomerId())
                .deadline(t.getSlaDeadline())
                .hoursUntilDeadline(hoursUntilDeadline)
                .build();
    }

    /**
     * Ürün bazında bilet metriklerini hesaplar.
     * Her aktif ürün için toplam bilet, açık bilet, ortalama çözüm süresi,
     * CSAT ortalaması ve SLA breach yüzdesi döner; toplam bilete göre azalan sıralıdır.
     *
     * @return ProductMetricsDTO — ürün detay satırları
     */
    public ProductMetricsDTO getProductMetrics() {
        log.info("Ürün bazında bilet metrikleri hesaplanıyor...");

        List<Object[]> rawRows = productRepository.findProductMetrics();

        List<ProductDetailDTO> products = rawRows.stream()
                .map(row -> ProductDetailDTO.builder()
                        .productId(((Number) row[0]).longValue())
                        .productName(String.valueOf(row[1]))
                        .totalTickets(((Number) row[2]).longValue())
                        .openTickets(((Number) row[3]).longValue())
                        .avgResolutionHours(row[4] != null ? ((Number) row[4]).doubleValue() : 0.0)
                        .csatAverage(row[5] != null ? ((Number) row[5]).doubleValue() : 0.0)
                        .slaBreachCount(((Number) row[6]).longValue())
                        .slaBreachPercentage(((Number) row[7]).doubleValue())
                        .build())
                .toList();

        log.info("Ürün metrikleri hesaplandı: {} ürün", products.size());

        return ProductMetricsDTO.builder()
                .productMetrics(products)
                .build();
    }

    /**
     * Worklog özeti ve bilet tamamlanma metriklerini hesaplar.
     * Agent bazında kayıtlı çalışma sürelerini ve dönem bilet tamamlanma istatistiklerini döner.
     *
     * @param days Analiz edilecek gün sayısı (1-365)
     * @return WorklogCompletionDTO — worklog özetleri ve tamamlanma oranları
     */
    public WorklogCompletionDTO getWorklogCompletion(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        ZonedDateTime since = ZonedDateTime.now().minusDays(safeDays);

        log.info("Worklog ve tamamlanma metrikleri hesaplanıyor (days={})...", safeDays);

        // Agent worklog aggregations
        List<Object[]> rawWorklogs = worklogRepository.findAgentWorklogSummary(since);
        List<String> agentIds = rawWorklogs.stream()
                .map(row -> (String) row[0])
                .toList();

        Map<String, String> usernameByAgentId = userRepository.findAll().stream()
                .filter(u -> agentIds.contains(u.getId()))
                .collect(Collectors.toMap(User::getId, User::getFullName));

        List<WorklogSummaryItemDTO> agentWorklogs = rawWorklogs.stream()
                .map(row -> {
                    String agentId = (String) row[0];
                    long totalMinutes = ((Number) row[1]).longValue();
                    long totalEntries = ((Number) row[2]).longValue();
                    return WorklogSummaryItemDTO.builder()
                            .agentId(agentId)
                            .agentUsername(usernameByAgentId.getOrDefault(agentId, "Unknown"))
                            .totalMinutes(totalMinutes)
                            .totalEntries(totalEntries)
                            .avgMinutesPerEntry(totalEntries > 0 ? (double) totalMinutes / totalEntries : 0.0)
                            .build();
                })
                .toList();

        // Completion rates
        long totalCreated = ticketRepository.countCreatedSince(since);
        long totalResolved = ticketRepository.countResolvedSince(since);
        long totalClosed = ticketRepository.countClosedSince(since);
        double completionRate = totalCreated > 0
                ? Math.min(100.0, (totalResolved + totalClosed) * 100.0 / totalCreated)
                : 0.0;
        Double avgResolutionRaw = ticketRepository.avgResolutionHoursSince(since);
        double avgResolutionHours = avgResolutionRaw != null ? avgResolutionRaw : 0.0;
        Double slaComplianceRaw = ticketRepository.slaComplianceRateSince(since);
        double slaComplianceRate = slaComplianceRaw != null ? slaComplianceRaw : 100.0;

        log.info("Worklog metrikleri hesaplandı: {} agent, created={}, resolved={}, closed={}",
                agentWorklogs.size(), totalCreated, totalResolved, totalClosed);

        return WorklogCompletionDTO.builder()
                .periodDays(safeDays)
                .agentWorklogs(agentWorklogs)
                .completionRates(CompletionRatesDTO.builder()
                        .totalCreated(totalCreated)
                        .totalResolved(totalResolved)
                        .totalClosed(totalClosed)
                        .completionRate(completionRate)
                        .avgResolutionHours(avgResolutionHours)
                        .slaComplianceRate(slaComplianceRate)
                        .build())
                .build();
    }
}
