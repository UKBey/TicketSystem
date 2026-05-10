package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.AgentPerformanceItemDTO;
import com.ticketsystem.it_service_backend.dto.AlertTicketItemDTO;
import com.ticketsystem.it_service_backend.dto.AlertsBacklogDTO;
import com.ticketsystem.it_service_backend.dto.BacklogMetricsDTO;
import com.ticketsystem.it_service_backend.dto.CSATMetricsDTO;
import com.ticketsystem.it_service_backend.dto.CSATPriorityItemDTO;
import com.ticketsystem.it_service_backend.dto.CSATTrendDTO;
import com.ticketsystem.it_service_backend.dto.CompletionRatesDTO;
import com.ticketsystem.it_service_backend.dto.DailyMetricsDTO;
import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PriorityDetailDTO;
import com.ticketsystem.it_service_backend.dto.PriorityMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PrioritySLAMetricsDTO;
import com.ticketsystem.it_service_backend.dto.ProductDetailDTO;
import com.ticketsystem.it_service_backend.dto.ProductMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
import com.ticketsystem.it_service_backend.dto.WorklogCompletionDTO;
import com.ticketsystem.it_service_backend.dto.WorklogSummaryItemDTO;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.SLAPolicyRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ticketsystem.it_service_backend.config.CacheConfig.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.sql.Date;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsService {

    private final TicketRepository ticketRepository;
    private final TicketClaimRepository ticketClaimRepository;
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
    private static final List<String> OPEN_STATUSES = List.of("NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER");

    @Cacheable(DASHBOARD_SUMMARY)
    public DashboardMetricsDTO getDashboardSummary() {
        log.info("Dashboard özet metrikleri hesaplanıyor...");

        // 1. Açık bilet sayısı — tek COUNT sorgusu
        Long totalOpenTickets = ticketRepository.countByStatusIn(OPEN_STATUSES);
        if (totalOpenTickets == null) totalOpenTickets = 0L;

        // 2. Son 24 saatte açılan biletler — tek COUNT sorgusu
        ZonedDateTime last24Hours = ZonedDateTime.now().minusHours(24);
        Long newTicketsLast24Hours = ticketRepository.countCreatedSinceByStatusIn(OPEN_STATUSES, last24Hours);
        if (newTicketsLast24Hours == null) newTicketsLast24Hours = 0L;

        // 3. SLA breach biletleri — tek COUNT sorgusu
        Long slaBreachedCount = ticketRepository.countSlaBreachedByStatusIn(OPEN_STATUSES);
        if (slaBreachedCount == null) slaBreachedCount = 0L;
        Double slaBreachedPercentage = totalOpenTickets > 0
                ? (double) slaBreachedCount / totalOpenTickets * 100
                : 0.0;

        // 4. Ortalama çözüm süresi — DB'de AVG ile hesaplanır, entity yüklenmiyor
        Double avgResponseTimeHours = ticketRepository.findAvgResolutionHoursForResolved();
        if (avgResponseTimeHours == null) avgResponseTimeHours = 0.0;

        // 5. CSAT ortalaması — SQL AVG() boş tabloda NULL döner, 0.0 yap
        Double csatAverage = csatRepository.findAverageRating();
        if (csatAverage == null) csatAverage = 0.0;
        Long csatTotalResponses = csatRepository.count();

        // 6. Priority dağılımı — GROUP BY ile tek sorgu
        PriorityMetricsDTO priorityDistribution = getPriorityDistributionFromDb();

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
        @Cacheable(STATUS_DISTRIBUTION)
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
    @Cacheable(AGENT_PERFORMANCE)
    public AgentPerformanceDTO getAgentPerformance() {
        log.info("Agent performans metrikleri hesaplanıyor...");

        List<User> agents = Stream.concat(
                userRepository.findByRole("AGENT").stream(),
                userRepository.findByRole("AGENT_ADMIN").stream()
        ).collect(Collectors.toList());

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

        // Ajan → sahiplenilen bilet ID'leri haritası (N+1 önleme).
        List<String> agentIds = activeAgents.stream().map(User::getId).collect(Collectors.toList());
        Map<String, Set<Long>> claimedTicketIdsByAgent = buildClaimedTicketIdMap(agentIds);

        ZonedDateTime last24Hours = ZonedDateTime.now().minusHours(24);
        ZonedDateTime last7Days = ZonedDateTime.now().minusDays(7);

        List<AgentPerformanceItemDTO> agentRows = activeAgents.stream()
                .map(agent -> buildAgentPerformanceRow(
                        agent, tickets, worklogs, csatByTicketId,
                        claimedTicketIdsByAgent.getOrDefault(agent.getId(), Set.of()),
                        last24Hours, last7Days))
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

    private Map<String, Set<Long>> buildClaimedTicketIdMap(List<String> agentIds) {
        if (agentIds.isEmpty()) return Map.of();
        Map<String, Set<Long>> result = new HashMap<>();
        ticketClaimRepository.findAgentIdAndTicketIdByAgentIdIn(agentIds).forEach(row -> {
            String agentId = (String) row[0];
            Long ticketId = (Long) row[1];
            result.computeIfAbsent(agentId, k -> new java.util.HashSet<>()).add(ticketId);
        });
        return result;
    }

    private AgentPerformanceItemDTO buildAgentPerformanceRow(
            User agent,
            List<Ticket> tickets,
            List<TicketWorklog> worklogs,
            Map<Long, Integer> csatByTicketId,
            Set<Long> claimedTicketIds,
            ZonedDateTime last24Hours,
            ZonedDateTime last7Days) {

        List<Ticket> agentTickets = tickets.stream()
                .filter(ticket -> claimedTicketIds.contains(ticket.getId()))
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

    private PriorityMetricsDTO getPriorityDistributionFromDb() {
        List<Object[]> rows = ticketRepository.countByStatusInGroupByPriority(OPEN_STATUSES);

        long critical = 0, high = 0, medium = 0, low = 0;
        for (Object[] row : rows) {
            String priority = String.valueOf(row[0]);
            long count = ((Number) row[1]).longValue();
            switch (priority) {
                case "CRITICAL" -> critical = count;
                case "HIGH"     -> high     = count;
                case "MEDIUM"   -> medium   = count;
                case "LOW"      -> low      = count;
                default -> log.warn("Bilinmeyen priority değeri: {}", priority);
            }
        }

        return PriorityMetricsDTO.builder()
                .critical(critical)
                .high(high)
                .medium(medium)
                .low(low)
                .build();
    }

    /**
     * Son N güne ait günlük ticket timeline metriklerini hesaplar.
     * Günlük oluşturulan, çözülen, kapalı bilet sayılarını ve SLA breach sayılarını döner.
     * 
     * @param days Kaç günlük veri isteneceği (default 30)
     * @return TicketTimelineDTO — günlük metriklerin timeline'ı
     */
    @Cacheable(value = TICKET_TIMELINE, key = "#days")
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
    @Cacheable(PRIORITY_SLA_METRICS)
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
     * Ürün bazında bilet metriklerini hesaplar.
     * Her aktif ürün için toplam bilet, açık bilet, ortalama çözüm süresi,
     * CSAT ortalaması ve SLA breach yüzdesi döner; toplam bilete göre azalan sıralıdır.
     *
     * @return ProductMetricsDTO — ürün detay satırları
     */
    @Cacheable(PRODUCT_METRICS)
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

    @Cacheable(value = CSAT_METRICS, key = "#months")
    public CSATMetricsDTO getCSATMetrics(int months) {
        int safeMonths = Math.max(1, Math.min(months, 12));
        ZonedDateTime since = ZonedDateTime.now().minusMonths(safeMonths);
        ZonedDateTime thisMonthStart = ZonedDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime lastMonthStart = thisMonthStart.minusMonths(1);

        List<Object[]> rawDist = csatRepository.findRatingDistributionSince(since);
        Map<Integer, Long> ratingDistribution = new HashMap<>();
        long totalResponses = 0;
        for (Object[] row : rawDist) {
            int rating = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            ratingDistribution.put(rating, count);
            totalResponses += count;
        }

        Double avg = csatRepository.findAverageRatingSince(since);
        double averageRating = avg != null ? avg : 0.0;

        Double thisMonthAvg = csatRepository.findAverageRatingSince(thisMonthStart);
        Double lastMonthAvg = csatRepository.findAverageRatingSince(lastMonthStart);
        double thisMonth = thisMonthAvg != null ? thisMonthAvg : 0.0;
        double lastMonth = lastMonthAvg != null ? lastMonthAvg : 0.0;
        String trendDir = thisMonth > lastMonth + 0.05 ? "UP" : thisMonth < lastMonth - 0.05 ? "DOWN" : "STABLE";

        List<Object[]> rawPriority = csatRepository.findAverageRatingByPrioritySince(since);
        Map<String, CSATPriorityItemDTO> byPriority = new HashMap<>();
        for (Object[] row : rawPriority) {
            String priority = String.valueOf(row[0]);
            double priorityAvg = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            long priorityResponses = ((Number) row[2]).longValue();
            byPriority.put(priority, CSATPriorityItemDTO.builder()
                    .avg(priorityAvg)
                    .responses(priorityResponses)
                    .build());
        }

        List<String> topComments = csatRepository.findTopPositiveCommentsSince(since, PageRequest.of(0, 5));

        return CSATMetricsDTO.builder()
                .totalResponses(totalResponses)
                .averageRating(averageRating)
                .ratingDistribution(ratingDistribution)
                .trend(CSATTrendDTO.builder()
                        .thisMonth(thisMonth)
                        .lastMonth(lastMonth)
                        .trend(trendDir)
                        .build())
                .byPriority(byPriority)
                .topComments(topComments)
                .build();
    }

    public AlertsBacklogDTO getAlertsAndBacklog() {
        List<String> openStatuses = List.of("NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER");
        List<String> activeStatuses = List.of("NEW", "IN_PROGRESS");
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime upcoming4h = now.plusHours(4);
        ZonedDateTime waitingThreshold = now.minusDays(3);
        PageRequest top10 = PageRequest.of(0, 10);

        List<Ticket> breachedTickets = ticketRepository.findBreachedOpenTickets(openStatuses, top10);
        List<Ticket> upcomingTickets = ticketRepository.findUpcomingBreachTickets(activeStatuses, upcoming4h, top10);
        List<Ticket> waitingTickets  = ticketRepository.findWaitingTooLongTickets(waitingThreshold, top10);

        // Tüm müşteri ID'lerini tek sorguda çek
        Set<String> customerIds = Stream.of(breachedTickets, upcomingTickets, waitingTickets)
                .flatMap(List::stream)
                .map(Ticket::getCustomerId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<String, String> customerNames = userRepository.findAllById(customerIds).stream()
                .collect(Collectors.toMap(
                        u -> u.getId(),
                        u -> u.getFullName(),
                        (a, b) -> a));

        List<AlertTicketItemDTO> breachedSLA = breachedTickets.stream()
                .map(t -> AlertTicketItemDTO.builder()
                        .ticketId(t.getId())
                        .title(t.getTitle())
                        .priority(t.getPriority())
                        .customerId(t.getCustomerId())
                        .customerName(customerNames.getOrDefault(t.getCustomerId(), t.getCustomerId()))
                        .deadline(t.getSlaDeadline())
                        .hoursUntilDeadline(t.getSlaDeadline() != null
                                ? ChronoUnit.MINUTES.between(now, t.getSlaDeadline()) / 60.0
                                : null)
                        .build())
                .toList();

        List<AlertTicketItemDTO> upcomingBreach = upcomingTickets.stream()
                .map(t -> AlertTicketItemDTO.builder()
                        .ticketId(t.getId())
                        .title(t.getTitle())
                        .priority(t.getPriority())
                        .customerId(t.getCustomerId())
                        .customerName(customerNames.getOrDefault(t.getCustomerId(), t.getCustomerId()))
                        .deadline(t.getSlaDeadline())
                        .hoursUntilDeadline(t.getSlaDeadline() != null
                                ? ChronoUnit.MINUTES.between(now, t.getSlaDeadline()) / 60.0
                                : null)
                        .build())
                .toList();

        List<AlertTicketItemDTO> waitingTooLong = waitingTickets.stream()
                .map(t -> AlertTicketItemDTO.builder()
                        .ticketId(t.getId())
                        .title(t.getTitle())
                        .priority(t.getPriority())
                        .customerId(t.getCustomerId())
                        .customerName(customerNames.getOrDefault(t.getCustomerId(), t.getCustomerId()))
                        .hoursWaiting(t.getCreatedAt() != null
                                ? ChronoUnit.MINUTES.between(t.getCreatedAt(), now) / 60.0
                                : null)
                        .build())
                .toList();

        long unassigned = ticketRepository.countUnassignedByStatusIn(openStatuses);
        long newWaiting = ticketRepository.countByStatus("NEW");
        Double avgWaiting = ticketRepository.avgWaitingHoursForOpen(openStatuses);

        return AlertsBacklogDTO.builder()
                .breachedSLA(breachedSLA)
                .upcomingBreach(upcomingBreach)
                .waitingTooLong(waitingTooLong)
                .backlogMetrics(BacklogMetricsDTO.builder()
                        .unassignedCount(unassigned)
                        .newTicketsWaiting(newWaiting)
                        .avgWaitingHours(avgWaiting != null ? avgWaiting : 0.0)
                        .build())
                .build();
    }

    @Cacheable(value = WORKLOG_COMPLETION, key = "#days")
    public WorklogCompletionDTO getWorklogCompletion(int days) {
        int safeDays = Math.max(1, Math.min(days, 365));
        ZonedDateTime since = ZonedDateTime.now().minusDays(safeDays);

        List<Object[]> rawWorklogs = worklogRepository.findAgentWorklogSummary(since);
        List<WorklogSummaryItemDTO> agentWorklogs = rawWorklogs.stream()
                .map(row -> {
                    String agentId = String.valueOf(row[0]);
                    long totalMinutes = ((Number) row[1]).longValue();
                    long totalEntries = ((Number) row[2]).longValue();
                    String agentName = userRepository.findById(agentId)
                            .map(u -> u.getFullName())
                            .orElse(agentId);
                    return WorklogSummaryItemDTO.builder()
                            .agentId(agentId)
                            .agentUsername(agentName)
                            .totalMinutes(totalMinutes)
                            .totalEntries(totalEntries)
                            .avgMinutesPerEntry(totalEntries > 0 ? (double) totalMinutes / totalEntries : 0.0)
                            .build();
                })
                .toList();

        long totalCreated = ticketRepository.countCreatedSince(since);
        long totalResolved = ticketRepository.countResolvedSince(since);
        long totalClosed = ticketRepository.countClosedSince(since);
        Double avgResolutionHours = ticketRepository.avgResolutionHoursSince(since);
        Double slaComplianceRate = ticketRepository.slaComplianceRateSince(since);

        double completionRate = totalCreated > 0
                ? ((double) (totalResolved + totalClosed) / totalCreated) * 100.0
                : 0.0;

        return WorklogCompletionDTO.builder()
                .periodDays(safeDays)
                .agentWorklogs(agentWorklogs)
                .completionRates(CompletionRatesDTO.builder()
                        .totalCreated(totalCreated)
                        .totalResolved(totalResolved)
                        .totalClosed(totalClosed)
                        .completionRate(completionRate)
                        .avgResolutionHours(avgResolutionHours != null ? avgResolutionHours : 0.0)
                        .slaComplianceRate(slaComplianceRate != null ? slaComplianceRate : 0.0)
                        .build())
                .build();
    }
}
