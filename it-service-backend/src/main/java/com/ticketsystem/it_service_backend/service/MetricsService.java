package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentCsatDTO;
import com.ticketsystem.it_service_backend.dto.AgentDashboardDTO;
import com.ticketsystem.it_service_backend.dto.AgentPerformanceDTO;
import com.ticketsystem.it_service_backend.dto.CsatDailyDTO;
import com.ticketsystem.it_service_backend.dto.WorklogDailyDTO;
import com.ticketsystem.it_service_backend.dto.AgentPerformanceItemDTO;
import com.ticketsystem.it_service_backend.dto.AlertTicketItemDTO;
import com.ticketsystem.it_service_backend.dto.CustomerDashboardDTO;
import com.ticketsystem.it_service_backend.dto.RecentTicketDTO;
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
import com.ticketsystem.it_service_backend.dto.ProductDashboardDTO;
import com.ticketsystem.it_service_backend.dto.ProductDetailDTO;
import com.ticketsystem.it_service_backend.dto.ProductMetricsDTO;
import com.ticketsystem.it_service_backend.dto.StatusDistributionDTO;
import com.ticketsystem.it_service_backend.dto.TicketTimelineDTO;
import com.ticketsystem.it_service_backend.dto.WorklogCompletionDTO;
import com.ticketsystem.it_service_backend.dto.WorklogSummaryItemDTO;
import com.ticketsystem.it_service_backend.config.AlertProperties;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.sql.Date;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.data.domain.PageRequest;

/**
 * Aggregate metric calculations that feed the manager dashboard.
 *
 * <p>All results are kept in Caffeine caches (see {@code CacheConfig}; default
 * TTL 5 minutes). Computations are pushed into single native/JPQL queries
 * wherever possible, avoiding Java-side joins. SLA target hours are read from
 * the env-driven {@link SlaPolicyService} so values don't flicker across
 * cache transitions.
 */
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
    private final SlaPolicyService slaPolicyService;
    private final AlertProperties alertProperties;

    /**
     * Computes the dashboard summary metrics.
     * Base data for the KPI cards: total open tickets, SLA breaches, average
     * response time, CSAT score and priority distribution.
     *
     * @return DashboardMetricsDTO — all KPI metrics
     */
    private static final List<String> OPEN_STATUSES = List.of("NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER");

    /**
     * Whether to apply the product filter. A {@code null} {@code productIds} means a
     * global caller (ADMIN/MANAGER) → no filter. A non-null list (LEAD_AGENT scope)
     * means filter; an empty list yields zero/empty results (lead authorized on nothing).
     */
    private static boolean filterByProduct(List<Long> productIds) {
        return productIds != null;
    }

    /**
     * Resolves the authorized product IDs for a product-scoped (LEAD_AGENT) caller.
     * Runs inside this service's read-only transaction so the lazy
     * {@code authorizedProducts} collection initializes safely. A lead authorized on
     * nothing returns an empty list (→ zero/empty metrics, not the global view).
     * A missing user likewise yields an empty list rather than leaking global data.
     *
     * @param userId the JWT subject (Keycloak UUID)
     * @return the IDs of products the user is authorized on (possibly empty)
     */
    public List<Long> resolveScopedProductIds(String userId) {
        if (userId == null) {
            return List.of();
        }
        return userRepository.findById(userId)
                .map(user -> user.getAuthorizedProducts().stream()
                        .map(p -> p.getId())
                        .filter(id -> id != null)
                        .collect(Collectors.toList()))
                .orElseGet(() -> {
                    log.warn("Scoped metrics: kullanıcı bulunamadı, boş ürün listesi dönülüyor: {}", userId);
                    return List.of();
                });
    }

    /**
     * Whether the target user is authorized on at least one of the given products —
     * used to decide if a product-scoped LEAD_AGENT may view that user's dashboard.
     * Returns false when {@code productIds} is null/empty (a lead authorized on nothing
     * can view no one).
     *
     * @param userId     the user being viewed
     * @param productIds the viewer's authorized product IDs
     */
    public boolean userSharesAnyProduct(String userId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return false;
        }
        return resolveScopedProductIds(userId).stream().anyMatch(productIds::contains);
    }

    /**
     * A non-null product id list safe to pass into {@code IN (...)} clauses. When the
     * caller is global ({@code productIds == null}) the filter flag is false, so the
     * actual values are ignored — but JPA still requires a non-empty collection to bind
     * the {@code IN} parameter, hence the placeholder {@code [-1]}.
     */
    private static List<Long> safeProductIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return List.of(-1L);
        }
        return productIds;
    }

    @Cacheable(value = DASHBOARD_SUMMARY, key = "#scopeKey")
    public DashboardMetricsDTO getDashboardSummary(List<Long> productIds, String scopeKey) {
        log.info("Dashboard özet metrikleri hesaplanıyor... (scope={})", scopeKey);

        boolean filter = filterByProduct(productIds);
        List<Long> pids = safeProductIds(productIds);

        // 1. Açık bilet sayısı — tek COUNT sorgusu
        Long totalOpenTickets = ticketRepository.countByStatusInScoped(OPEN_STATUSES, filter, pids);
        if (totalOpenTickets == null) totalOpenTickets = 0L;

        // 2. Son 24 saatte açılan biletler — tek COUNT sorgusu
        ZonedDateTime last24Hours = ZonedDateTime.now().minusHours(24);
        Long newTicketsLast24Hours = ticketRepository.countCreatedSinceByStatusInScoped(OPEN_STATUSES, last24Hours, filter, pids);
        if (newTicketsLast24Hours == null) newTicketsLast24Hours = 0L;

        // 3. SLA breach biletleri — tek COUNT sorgusu
        Long slaBreachedCount = ticketRepository.countSlaBreachedByStatusInScoped(OPEN_STATUSES, filter, pids);
        if (slaBreachedCount == null) slaBreachedCount = 0L;
        Double slaBreachedPercentage = totalOpenTickets > 0
                ? (double) slaBreachedCount / totalOpenTickets * 100
                : 0.0;

        // 4. Ortalama çözüm süresi — DB'de AVG ile hesaplanır, entity yüklenmiyor
        Double avgResponseTimeHours = ticketRepository.findAvgResolutionHoursForResolvedScoped(filter, pids);
        if (avgResponseTimeHours == null) avgResponseTimeHours = 0.0;

        // 5. CSAT ortalaması — SQL AVG() boş tabloda NULL döner, 0.0 yap
        Double csatAverage = csatRepository.findAverageRatingScoped(filter, pids);
        if (csatAverage == null) csatAverage = 0.0;
        Long csatTotalResponses = csatRepository.countScoped(filter, pids);

        // 6. Priority dağılımı — GROUP BY ile tek sorgu
        PriorityMetricsDTO priorityDistribution = getPriorityDistributionFromDb(filter, pids);

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
         * Computes the ticket status distribution.
         *
         * @return StatusDistributionDTO — ticket counts for every status
         */
        @Cacheable(value = STATUS_DISTRIBUTION, key = "#scopeKey")
        public StatusDistributionDTO getStatusDistribution(List<Long> productIds, String scopeKey) {
                log.info("Ticket durum dağılımı hesaplanıyor... (scope={})", scopeKey);

                List<Object[]> rawRows = ticketRepository.countTicketsGroupedByStatusScoped(
                                filterByProduct(productIds), safeProductIds(productIds));

                return buildStatusDistribution(rawRows);
        }

        /**
         * Builds a {@link StatusDistributionDTO} from {@code [status, count]} rows. Shared
         * by the global/scoped manager view and the personal (customer/agent) dashboards.
         */
        private StatusDistributionDTO buildStatusDistribution(List<Object[]> rawRows) {
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
     * Computes the agent performance leaderboard data.
     *
     * @return AgentPerformanceDTO — per-agent rows and summary metrics
     */
    @Cacheable(value = AGENT_PERFORMANCE, key = "#scopeKey")
    public AgentPerformanceDTO getAgentPerformance(List<Long> productIds, String scopeKey) {
        log.info("Agent performans metrikleri hesaplanıyor... (scope={})", scopeKey);

        boolean filter = filterByProduct(productIds);
        List<Long> pids = safeProductIds(productIds);

        List<User> agents = Stream.concat(
                userRepository.findByRole("AGENT").stream(),
                userRepository.findByRole("LEAD_AGENT").stream()
        ).collect(Collectors.toList());

        List<User> activeAgents = agents.stream()
                .filter(agent -> Boolean.TRUE.equals(agent.getIsActive()))
                .filter(agent -> agent.getId() != null)
                .collect(Collectors.toMap(User::getId, agent -> agent, (left, right) -> left))
                .values()
                .stream()
                .sorted(Comparator.comparing(User::getFullName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<String> agentIds = activeAgents.stream().map(User::getId).collect(Collectors.toList());
        ZonedDateTime last24Hours = ZonedDateTime.now().minusHours(24);
        ZonedDateTime last7Days = ZonedDateTime.now().minusDays(7);

        // DB tarafında aggregate ediliyor — eski 3 findAll() yerine 2 sorgu.
        // Sonuç [agent_id, active, resolved24h, slaBreached, avgResolutionHours, csatAvg]
        Map<String, Object[]> metricsByAgent = agentIds.isEmpty() ? Map.of()
                : ticketRepository.findAgentPerformanceMetricsScoped(agentIds, last24Hours, filter, pids).stream()
                        .collect(Collectors.toMap(
                                row -> (String) row[0],
                                row -> row,
                                (left, right) -> left));

        Map<String, Long> worklogMinutesByAgent = worklogRepository.findAgentWorklogSummaryScoped(last7Days, filter, pids).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> ((Number) row[1]).longValue(),
                        (left, right) -> left));

        List<AgentPerformanceItemDTO> agentRows = activeAgents.stream()
                .map(agent -> buildAgentPerformanceRow(agent,
                        metricsByAgent.get(agent.getId()),
                        worklogMinutesByAgent.getOrDefault(agent.getId(), 0L)))
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

    /**
     * Builds the DTO from the aggregated query output. The old Java-side joins
     * (3 findAll + stream filter) are no longer needed — every count/average is
     * computed inside
     * {@link com.ticketsystem.it_service_backend.repository.TicketRepository#findAgentPerformanceMetrics}.
     */
    private AgentPerformanceItemDTO buildAgentPerformanceRow(User agent,
                                                             Object[] metricsRow,
                                                             long worklogMinutesLast7Days) {
        long activeTickets       = metricsRow == null ? 0L : ((Number) metricsRow[1]).longValue();
        long resolvedLast24Hours = metricsRow == null ? 0L : ((Number) metricsRow[2]).longValue();
        long slaBreachedCount    = metricsRow == null ? 0L : ((Number) metricsRow[3]).longValue();
        double avgResolutionHrs  = metricsRow == null ? 0.0 : ((Number) metricsRow[4]).doubleValue();
        double csatAverage       = metricsRow == null ? 0.0 : ((Number) metricsRow[5]).doubleValue();

        return AgentPerformanceItemDTO.builder()
                .agentId(agent.getId())
                .agentName(agent.getFullName())
                .role(agent.getRole())
                .activeTickets(activeTickets)
                .resolvedLast24Hours(resolvedLast24Hours)
                .avgResolutionHours(avgResolutionHrs)
                .csatAverage(csatAverage)
                .slaBreachedCount(slaBreachedCount)
                .worklogMinutesLast7Days(worklogMinutesLast7Days)
                .build();
    }

    private PriorityMetricsDTO getPriorityDistributionFromDb(boolean filter, List<Long> productIds) {
        List<Object[]> rows = ticketRepository.countByStatusInGroupByPriorityScoped(OPEN_STATUSES, filter, productIds);

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
     * Computes the daily ticket timeline metrics over the last N days.
     * Returns daily counts of created, resolved and closed tickets along with SLA breach counts.
     *
     * @param days number of days to include (defaults to 30)
     * @return TicketTimelineDTO — timeline of daily metrics
     */
    @Cacheable(value = TICKET_TIMELINE, key = "#scopeKey + ':' + #days")
    public TicketTimelineDTO getTicketTimeline(int days, List<Long> productIds, String scopeKey) {
        log.info("Ticket timeline metrikleri hesaplanıyor... (days={}, scope={})", days, scopeKey);

        // days<=0 → "All time": pencere ilk bilet tarihine göre genişler, baştaki boş günler kırpılır.
        int window = resolveWindow(days);

        List<Object[]> rawRows = ticketRepository.getTicketTimelineMetricsScoped(
                window, filterByProduct(productIds), safeProductIds(productIds));

        TicketTimelineDTO result = trimTimeline(buildTimeline(rawRows), isAllTime(days));

        log.info("Ticket timeline hesaplandı: {} günlük veri elde edildi", result.getTimeline().size());

        return result;
    }

    /**
     * Maps {@code [date, created, resolved, closed, slaBreach]} rows into a
     * {@link TicketTimelineDTO}. Shared by the global/scoped manager timeline and the
     * personal (customer/agent) timelines.
     */
    private TicketTimelineDTO buildTimeline(List<Object[]> rawRows) {
        List<DailyMetricsDTO> timeline = rawRows.stream()
                .map(row -> DailyMetricsDTO.builder()
                        .date(convertToLocalDate(row[0]))
                        .created(((Number) row[1]).longValue())
                        .resolved(((Number) row[2]).longValue())
                        .closed(((Number) row[3]).longValue())
                        .slaBreach(((Number) row[4]).longValue())
                        .build())
                .toList();

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
     * Computes SLA target and performance metrics per priority.
     * For each priority returns ticket count, SLA target hours, average resolution
     * time, breach percentage and on-time percentage. SLA target hours are read
     * from the env-driven SlaPolicyService; days null/0 means all time.
     *
     * @param days window in days; null or 0 means all time
     * @return PrioritySLAMetricsDTO — per-priority detail rows
     */
    @Cacheable(value = PRIORITY_SLA_METRICS, key = "#scopeKey + ':' + (#days == null ? 'all' : #days)")
    public PrioritySLAMetricsDTO getPrioritySlaMetrics(Integer days, List<Long> productIds, String scopeKey) {
        log.info("Priority-SLA metrikleri hesaplanıyor (days={}, scope={})...", days, scopeKey);

        Map<String, Integer> priorityHours = Map.of(
                "CRITICAL", slaPolicyService.getResolutionHours("CRITICAL"),
                "HIGH",     slaPolicyService.getResolutionHours("HIGH"),
                "MEDIUM",   slaPolicyService.getResolutionHours("MEDIUM"),
                "LOW",      slaPolicyService.getResolutionHours("LOW")
        );

        // productIds == null → global (no product filter); non-null → lead-scoped.
        List<Object[]> rawRows = slaPolicyRepository.findPrioritySlaMetrics(priorityHours, days, productIds);

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
     * Computes per-product ticket metrics.
     * For each active product returns total tickets, open tickets, average
     * resolution time, CSAT average and SLA breach percentage, sorted by total
     * tickets in descending order. days null/0 means all time.
     *
     * @param days window in days; null or 0 means all time
     * @return ProductMetricsDTO — per-product detail rows
     */
    @Cacheable(value = PRODUCT_METRICS, key = "#scopeKey + ':' + (#days == null ? 'all' : #days)")
    public ProductMetricsDTO getProductMetrics(Integer days, List<Long> productIds, String scopeKey) {
        log.info("Ürün bazında bilet metrikleri hesaplanıyor (days={}, scope={})...", days, scopeKey);

        Integer dayWindow = (days != null && days > 0) ? days : null;
        List<Object[]> rawRows = productRepository.findProductMetricsScoped(
                dayWindow, filterByProduct(productIds), safeProductIds(productIds));

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
     * Computes CSAT metrics over the given month window: average score, rating
     * distribution, month-over-month trend (UP/DOWN/STABLE), per-priority
     * breakdown and a top-5 sample of positive comments. {@code months} is
     * clamped between 1 and 12.
     *
     * @param months window length in months — between 1 and 12
     * @return CSAT metrics summary DTO
     */
    @Cacheable(value = CSAT_METRICS, key = "#scopeKey + ':' + #months")
    public CSATMetricsDTO getCSATMetrics(int months, List<Long> productIds, String scopeKey) {
        int safeMonths = Math.max(1, Math.min(months, 12));
        ZonedDateTime since = ZonedDateTime.now().minusMonths(safeMonths);
        ZonedDateTime thisMonthStart = ZonedDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        ZonedDateTime lastMonthStart = thisMonthStart.minusMonths(1);

        boolean filter = filterByProduct(productIds);
        List<Long> pids = safeProductIds(productIds);

        List<Object[]> rawDist = csatRepository.findRatingDistributionSinceScoped(since, filter, pids);
        Map<Integer, Long> ratingDistribution = new HashMap<>();
        long totalResponses = 0;
        for (Object[] row : rawDist) {
            int rating = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            ratingDistribution.put(rating, count);
            totalResponses += count;
        }

        Double avg = csatRepository.findAverageRatingSinceScoped(since, filter, pids);
        double averageRating = avg != null ? avg : 0.0;

        Double thisMonthAvg = csatRepository.findAverageRatingSinceScoped(thisMonthStart, filter, pids);
        Double lastMonthAvg = csatRepository.findAverageRatingSinceScoped(lastMonthStart, filter, pids);
        double thisMonth = thisMonthAvg != null ? thisMonthAvg : 0.0;
        double lastMonth = lastMonthAvg != null ? lastMonthAvg : 0.0;
        String trendDir = thisMonth > lastMonth + 0.05 ? "UP" : thisMonth < lastMonth - 0.05 ? "DOWN" : "STABLE";

        List<Object[]> rawPriority = csatRepository.findAverageRatingByPrioritySinceScoped(since, filter, pids);
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

        List<String> topComments = csatRepository.findTopPositiveCommentsSinceScoped(since, filter, pids, PageRequest.of(0, 5));

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

    /**
     * Builds the composite DTO that feeds the alerts and backlog panel.
     *
     * <p>Three alert lists: (1) top-10 breached SLA, (2) top-10 upcoming-breach
     * tickets falling inside the per-priority warning window, (3) tickets stuck in
     * WAITING_FOR_CUSTOMER or RESOLVED beyond the configurable {@code app.alerts}
     * thresholds, measured from when they entered that state. Backlog: unassigned
     * count, NEW count, average waiting time. Customer names are fetched with a
     * single {@code findAllById} to avoid N+1.
     *
     * @return DTO carrying alert lists and backlog metrics
     */
    public AlertsBacklogDTO getAlertsAndBacklog(List<Long> productIds, String scopeKey) {
        log.debug("Alert ve backlog metrikleri hesaplanıyor... (scope={})", scopeKey);
        List<String> openStatuses = List.of("NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER");
        List<String> activeStatuses = List.of("NEW", "IN_PROGRESS");
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime waitingThreshold  = now.minusHours(alertProperties.getWaitingForCustomerMaxHours());
        ZonedDateTime resolvedThreshold = now.minusHours(alertProperties.getResolvedMaxHours());
        PageRequest top10 = PageRequest.of(0, 10);

        boolean filter = filterByProduct(productIds);
        List<Long> pids = safeProductIds(productIds);

        List<Ticket> breachedTickets = ticketRepository.findBreachedOpenTicketsScoped(openStatuses, filter, pids, top10);

        // Fetch upcoming-breach tickets per priority using the configured warning threshold,
        // then merge, deduplicate, sort by deadline, and cap at 10.
        List<String> priorities = List.of("CRITICAL", "HIGH", "MEDIUM", "LOW");
        List<Ticket> upcomingTickets = priorities.stream()
                .flatMap(priority -> {
                    double thresholdHours = slaPolicyService.getWarningThresholdHours(priority);
                    ZonedDateTime window = now.plusHours((long) thresholdHours);
                    return ticketRepository.findUpcomingBreachTicketsByPriorityScoped(
                            activeStatuses, List.of(priority), window, filter, pids, top10).stream();
                })
                .collect(Collectors.toMap(Ticket::getId, t -> t, (a, b) -> a))
                .values().stream()
                .filter(t -> t.getSlaDeadline() != null)
                .sorted(Comparator.comparing(Ticket::getSlaDeadline))
                .limit(10)
                .toList();
        List<Ticket> waitingTickets  = ticketRepository.findWaitingTooLongTicketsScoped(waitingThreshold, filter, pids, top10);
        List<Ticket> resolvedTickets = ticketRepository.findResolvedTooLongTicketsScoped(resolvedThreshold, filter, pids, top10);

        // Tüm müşteri ID'lerini tek sorguda çek
        Set<String> customerIds = Stream.of(breachedTickets, upcomingTickets, waitingTickets, resolvedTickets)
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

        // WAITING_FOR_CUSTOMER + RESOLVED takılı biletler tek listede; her satır kendi
        // durumuyla etiketlenir. Süre, biletin o duruma GİRDİĞİ andan (slaPausedAt /
        // resolvedAt) itibaren ölçülür — oluşturulma anından değil. En uzun bekleyen önce.
        List<AlertTicketItemDTO> waitingTooLong = Stream.concat(waitingTickets.stream(), resolvedTickets.stream())
                .map(t -> {
                    ZonedDateTime enteredAt = t.getSlaPausedAt() != null ? t.getSlaPausedAt()
                            : (t.getResolvedAt() != null ? t.getResolvedAt() : t.getCreatedAt());
                    return AlertTicketItemDTO.builder()
                            .ticketId(t.getId())
                            .title(t.getTitle())
                            .priority(t.getPriority())
                            .status(t.getStatus())
                            .customerId(t.getCustomerId())
                            .customerName(customerNames.getOrDefault(t.getCustomerId(), t.getCustomerId()))
                            .hoursWaiting(enteredAt != null
                                    ? ChronoUnit.MINUTES.between(enteredAt, now) / 60.0
                                    : null)
                            .build();
                })
                .sorted(Comparator.comparing(AlertTicketItemDTO::getHoursWaiting,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .toList();

        long unassigned = ticketRepository.countUnassignedByStatusInScoped(openStatuses, filter, pids);
        long newWaiting = ticketRepository.countByStatusScoped("NEW", filter, pids);
        Double avgWaiting = ticketRepository.avgWaitingHoursForOpenScoped(openStatuses, filter, pids);

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

    /**
     * Computes worklog and completion metrics.
     *
     * <p>For the given day window, returns each agent's total worklog minutes and
     * entry count along with the overall completion rate (resolved+closed / created),
     * average resolution time and SLA compliance rate — all in a single aggregated
     * query. {@code days} is clamped between 1 and 365.
     *
     * @param days window in days
     * @return worklog summaries and completion rates
     */
    @Cacheable(value = WORKLOG_COMPLETION, key = "#scopeKey + ':' + #days")
    public WorklogCompletionDTO getWorklogCompletion(int days, List<Long> productIds, String scopeKey) {
        // days<=0 → "All time": pencere ilk bilet tarihine kadar genişler (per-agent toplamlar).
        int safeDays = resolveWindow(days);
        ZonedDateTime since = ZonedDateTime.now().minusDays(safeDays);

        boolean filter = filterByProduct(productIds);
        List<Long> pids = safeProductIds(productIds);

        // B-9: Agent name lookup N+1'i kaldirildi — tum agent'lari tek findAllById ile cek.
        List<Object[]> rawWorklogs = worklogRepository.findAgentWorklogSummaryScoped(since, filter, pids);
        List<String> agentIds = rawWorklogs.stream()
                .map(row -> String.valueOf(row[0]))
                .toList();
        Map<String, String> agentNameById = agentIds.isEmpty() ? Map.of()
                : userRepository.findAllById(agentIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));

        List<WorklogSummaryItemDTO> agentWorklogs = rawWorklogs.stream()
                .map(row -> {
                    String agentId = String.valueOf(row[0]);
                    long totalMinutes = ((Number) row[1]).longValue();
                    long totalEntries = ((Number) row[2]).longValue();
                    return WorklogSummaryItemDTO.builder()
                            .agentId(agentId)
                            .agentUsername(agentNameById.getOrDefault(agentId, agentId))
                            .totalMinutes(totalMinutes)
                            .totalEntries(totalEntries)
                            .avgMinutesPerEntry(totalEntries > 0 ? (double) totalMinutes / totalEntries : 0.0)
                            .build();
                })
                .toList();

        // B-9: 5 ayri COUNT/AVG sorgusu yerine PostgreSQL FILTER ile tek aggregated query.
        List<Object[]> aggregates = ticketRepository.findWorklogCompletionAggregatesScoped(since, filter, pids);
        Object[] row = aggregates.isEmpty() ? new Object[]{0L, 0L, 0L, null, null, 0L} : aggregates.get(0);
        long totalCreated  = row[0] != null ? ((Number) row[0]).longValue() : 0L;
        long totalResolved = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        long totalClosed   = row[2] != null ? ((Number) row[2]).longValue() : 0L;
        Double avgResolutionHours = row[3] != null ? ((Number) row[3]).doubleValue() : null;
        Double slaComplianceRate  = row[4] != null ? ((Number) row[4]).doubleValue() : null;
        // SLA uyumu / ort. çözüm süresi paydası: dönemde çözüme ulaşan TÜM biletler (sonradan
        // kapatılmış olanlar dahil) — anlık RESOLVED olanlarla sınırlı değil.
        long resolvedInPeriod = row[5] != null ? ((Number) row[5]).longValue() : 0L;

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
                        .resolvedInPeriod(resolvedInPeriod)
                        .completionRate(completionRate)
                        .avgResolutionHours(avgResolutionHours != null ? avgResolutionHours : 0.0)
                        .slaComplianceRate(slaComplianceRate != null ? slaComplianceRate : 0.0)
                        .build())
                .build();
    }

    // =========================================================================
    // Kişisel dashboard'lar — ilişki bazlı kapsam (customer_id / claim agent_id).
    // Aynı kullanıcının müşteri-aktivitesi ile ajan-aktivitesi birbirine karışmaz.
    // =========================================================================

    private static final int DEFAULT_DASHBOARD_DAYS = 30;

    /** Safety ceiling for the dynamically-sized "All time" window (~5 years). */
    private static final int MAX_ALL_TIME_DAYS = 1825;

    /** True when the caller asked for the whole history. The UI "All time" option sends {@code days=0}. */
    private static boolean isAllTime(Integer days) {
        return days != null && days <= 0;
    }

    /**
     * Resolves the day window for the daily time-series charts. A positive value is
     * clamped to [1, 365]; {@code null} (param omitted) falls back to the default; 0 or
     * negative ("All time") expands to the span between the earliest ticket and today,
     * bounded by {@link #MAX_ALL_TIME_DAYS}. The generate_series queries then scaffold
     * exactly that many days, and {@link #trimTimeline}/{@link #trimDaily} drop the
     * leading empty run so each chart starts at its own first data point.
     */
    private int resolveWindow(Integer days) {
        if (days == null) return DEFAULT_DASHBOARD_DAYS;
        if (days > 0) return Math.min(days, 365);
        LocalDate earliest = ticketRepository.findEarliestTicketDate();
        if (earliest == null) return DEFAULT_DASHBOARD_DAYS;
        long span = ChronoUnit.DAYS.between(earliest, LocalDate.now(ZoneOffset.UTC)) + 1;
        return (int) Math.min(Math.max(span, 1), MAX_ALL_TIME_DAYS);
    }

    /**
     * For "All time" charts, drops the leading run of empty days so the leftmost point
     * is the first day with real activity (created/resolved/closed/SLA-breach). No-op for
     * fixed windows, where the full N-day axis is intentionally shown even when empty.
     */
    private TicketTimelineDTO trimTimeline(TicketTimelineDTO dto, boolean allTime) {
        if (!allTime) return dto;
        List<DailyMetricsDTO> trimmed = trimDaily(dto.getTimeline(), true, DailyMetricsDTO::getDate,
                r -> positive(r.getCreated()) || positive(r.getResolved())
                        || positive(r.getClosed()) || positive(r.getSlaBreach()));
        return TicketTimelineDTO.builder().timeline(trimmed).build();
    }

    /**
     * Generic leading-empty trimmer for a date-ordered daily series: keeps only rows on
     * or after the earliest row that {@code hasData}. Order-independent. No-op unless
     * {@code allTime}.
     */
    private <T> List<T> trimDaily(List<T> rows, boolean allTime,
                                  Function<T, LocalDate> dateFn, Predicate<T> hasData) {
        if (!allTime || rows.isEmpty()) return rows;
        LocalDate first = rows.stream()
                .filter(hasData)
                .map(dateFn)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (first == null) return rows;
        return rows.stream()
                .filter(r -> dateFn.apply(r) != null && !dateFn.apply(r).isBefore(first))
                .toList();
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    /**
     * Personal dashboard for the customer who opened the tickets. Scoped strictly to
     * {@code tickets.customer_id = customerId}, so it never reflects any agent activity
     * the same user id may have.
     */
    @Cacheable(value = ME_CUSTOMER_DASHBOARD, key = "#customerId + ':' + (#days == null ? 'all' : #days)")
    public CustomerDashboardDTO getMyCustomerDashboard(String customerId, Integer days) {
        log.info("Müşteri kişisel dashboard hesaplanıyor (customer={}, days={})", customerId, days);
        int window = resolveWindow(days);

        StatusDistributionDTO dist = buildStatusDistribution(
                ticketRepository.countTicketsGroupedByStatusForCustomer(customerId));
        long open = dist.getNewCount() + dist.getInProgressCount() + dist.getWaitingForCustomerCount();
        long resolvedClosed = dist.getResolvedCount() + dist.getClosedCount();

        Long slaBreached = ticketRepository.countSlaBreachedByCustomerAndStatusIn(customerId, OPEN_STATUSES);
        Double avgResolution = ticketRepository.findAvgResolutionHoursForCustomer(customerId);

        double csatAvg = 0.0;
        long csatCount = 0L;
        List<Object[]> csatRows = csatRepository.findCustomerCsat(customerId);
        if (!csatRows.isEmpty()) {
            Object[] row = csatRows.get(0);
            csatAvg = row[0] != null ? ((Number) row[0]).doubleValue() : 0.0;
            csatCount = row[1] != null ? ((Number) row[1]).longValue() : 0L;
        }

        TicketTimelineDTO timeline = trimTimeline(buildTimeline(
                ticketRepository.getCustomerTicketTimelineMetrics(window, customerId)), isAllTime(days));

        List<RecentTicketDTO> recent = ticketRepository
                .findRecentByCustomerId(customerId, PageRequest.of(0, 5)).stream()
                .map(this::toRecentTicket)
                .toList();

        return CustomerDashboardDTO.builder()
                .totalTickets(dist.getTotalCount())
                .openTickets(open)
                .resolvedTickets(resolvedClosed)
                .slaBreachedCount(slaBreached != null ? slaBreached : 0L)
                .avgResolutionHours(avgResolution != null ? avgResolution : 0.0)
                .csatAverage(csatAvg)
                .csatCount(csatCount)
                .statusDistribution(dist)
                .timeline(timeline)
                .recentTickets(recent)
                .build();
    }

    /**
     * Personal performance dashboard for an agent / lead agent. Scoped strictly to the
     * tickets the user holds a claim on ({@code ticket_claims.agent_id = agentId}) plus
     * their own worklogs — independent of any tickets the same user opened as a customer.
     */
    @Cacheable(value = ME_AGENT_DASHBOARD, key = "#agentId + ':' + (#days == null ? 'all' : #days)")
    public AgentDashboardDTO getMyAgentDashboard(String agentId, Integer days) {
        log.info("Ajan kişisel dashboard hesaplanıyor (agent={}, days={})", agentId, days);
        // Kişisel görünüm ürün filtresi uygulamaz (kullanıcının TÜM claim'leri).
        return computeAgentDashboard(agentId, days, null);
    }

    /**
     * Agent performance dashboard for a manager/admin/lead viewing <i>another</i> user.
     * A global caller ({@code productIds == null}) sees the agent's full claimed-ticket
     * history (identical to the agent's own view); a {@code LEAD_AGENT} passes their
     * authorized product IDs so the agent's metrics are restricted to those products.
     *
     * @param agentId    the target agent's Keycloak id
     * @param days       timeline window (clamped 1–365; null → default)
     * @param productIds {@code null} = global view; non-null = restrict to these products
     * @param scopeKey   cache discriminator ({@code "global"} or the lead's user id)
     */
    @Cacheable(value = USER_AGENT_DASHBOARD,
            key = "#agentId + ':' + #scopeKey + ':' + (#days == null ? 'all' : #days)")
    public AgentDashboardDTO getUserAgentDashboard(String agentId, Integer days,
                                                   List<Long> productIds, String scopeKey) {
        log.info("Ajan dashboard görüntüleniyor (agent={}, scope={}, days={})", agentId, scopeKey, days);
        return computeAgentDashboard(agentId, days, productIds);
    }

    /**
     * Shared agent-dashboard computation. {@code productIds == null} → global (no product
     * filter); a non-null list restricts every metric to the agent's tickets in those
     * products. Worklog minutes are likewise product-scoped so a lead's view never leaks
     * effort logged on out-of-scope products.
     */
    private AgentDashboardDTO computeAgentDashboard(String agentId, Integer days, List<Long> productIds) {
        int window = resolveWindow(days);
        boolean allTime = isAllTime(days);
        ZonedDateTime now = ZonedDateTime.now();
        boolean filter = filterByProduct(productIds);
        List<Long> pids = safeProductIds(productIds);

        // Tüm aktivite metrikleri seçili pencereye (since) göre — sabit 7g/30g yerine.
        ZonedDateTime since = now.minusDays(window);

        List<Object[]> rows = ticketRepository.findAgentSelfMetricsScoped(agentId, since, filter, pids);
        Object[] m = rows.isEmpty() ? null : rows.get(0);

        long active            = asLong(m, 0);
        long resolvedInRange   = asLong(m, 1);
        long slaBreachedInRange = asLong(m, 2);
        long totalClaimed      = asLong(m, 3);
        double avgResolution   = asDouble(m, 4);
        double csatAvg         = asDouble(m, 5);
        long csatCount         = asLong(m, 6);
        double slaBreachRate   = resolvedInRange > 0 ? (double) slaBreachedInRange / resolvedInRange * 100.0 : 0.0;

        long worklogMinutes = worklogRepository.sumAgentWorklogMinutesSinceScoped(agentId, since, filter, pids);

        StatusDistributionDTO dist = buildStatusDistribution(
                ticketRepository.countClaimedTicketsGroupedByStatusScoped(agentId, filter, pids));

        TicketTimelineDTO timeline = trimTimeline(buildTimeline(
                ticketRepository.getAgentTicketTimelineMetricsScoped(window, agentId, filter, pids)), allTime);

        // Günlük worklog dağılımı (gap-fill SQL tarafında, her gün mevcut). All time'da baştaki boş günler kırpılır.
        List<WorklogDailyDTO> worklogTimeline = trimDaily(
                worklogRepository.findAgentWorklogByDayScoped(agentId, window, filter, pids).stream()
                        .map(r -> WorklogDailyDTO.builder()
                                .date(convertToLocalDate(r[0]))
                                .minutes(asLong(r, 1))
                                .build())
                        .toList(),
                allTime, WorklogDailyDTO::getDate, w -> w.getMinutes() > 0);

        AgentCsatDTO csat = buildAgentCsat(agentId, window, since, csatAvg, csatCount, filter, pids, allTime);

        List<RecentTicketDTO> recent = ticketRepository
                .findRecentClaimedByAgentScoped(agentId, filter, pids, PageRequest.of(0, 5)).stream()
                .map(this::toRecentTicket)
                .toList();

        return AgentDashboardDTO.builder()
                .activeTickets(active)
                .totalClaimed(totalClaimed)
                .resolvedInRange(resolvedInRange)
                .slaBreachedCount(slaBreachedInRange)
                .slaBreachRate(slaBreachRate)
                .avgResolutionHours(avgResolution)
                .worklogMinutesInRange(worklogMinutes)
                .csatAverage(csatAvg)
                .csatCount(csatCount)
                .statusDistribution(dist)
                .timeline(timeline)
                .worklogTimeline(worklogTimeline)
                .csat(csat)
                .recentTickets(recent)
                .build();
    }

    /**
     * Builds the agent CSAT card payload: the 1–5 rating distribution (gap-filled so
     * every rating key is present) plus a daily average-rating trend over the window.
     * Headline {@code average}/{@code totalResponses} reuse the values already computed
     * by the self-metrics query.
     */
    private AgentCsatDTO buildAgentCsat(String agentId, int window, ZonedDateTime since,
                                        double csatAvg, long csatCount, boolean filter, List<Long> pids,
                                        boolean allTime) {
        Map<Integer, Long> distribution = new HashMap<>();
        for (int r = 1; r <= 5; r++) distribution.put(r, 0L);
        for (Object[] row : csatRepository.findAgentRatingDistributionSince(agentId, since, filter, pids)) {
            distribution.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        // All time'da yanıt gelmeyen baştaki günler kırpılır → trend ilk değerlendirmeden başlar.
        List<CsatDailyDTO> trend = trimDaily(
                csatRepository.findAgentCsatByDayScoped(agentId, window, filter, pids).stream()
                        .map(r -> CsatDailyDTO.builder()
                                .date(convertToLocalDate(r[0]))
                                .avg(r[1] != null ? ((Number) r[1]).doubleValue() : null)
                                .count(asLong(r, 2))
                                .build())
                        .toList(),
                allTime, CsatDailyDTO::getDate, c -> positive(c.getCount()));

        return AgentCsatDTO.builder()
                .average(csatAvg)
                .totalResponses(csatCount)
                .ratingDistribution(distribution)
                .trend(trend)
                .build();
    }

    /**
     * Dedicated dashboard for a single product. Aggregates every metric over the
     * product's tickets ({@code product_id = productId}) using the existing
     * product-scoped queries, and embeds the product-scoped agent leaderboard so the
     * manager can see who works that product.
     *
     * @param productId target product id
     * @param days      timeline window (clamped 1–365; null → default)
     * @param scopeKey  cache discriminator ({@code "global"} or the lead's user id)
     */
    @Cacheable(value = PRODUCT_DASHBOARD,
            key = "#productId + ':' + #scopeKey + ':' + (#days == null ? 'all' : #days)")
    public ProductDashboardDTO getProductDashboard(Long productId, Integer days, String scopeKey) {
        log.info("Ürün dashboard hesaplanıyor (product={}, scope={}, days={})", productId, scopeKey, days);
        int window = resolveWindow(days);
        List<Long> pids = List.of(productId);
        final boolean filter = true;

        String productName = productRepository.findById(productId)
                .map(p -> p.getName())
                .orElse("#" + productId);

        StatusDistributionDTO dist = buildStatusDistribution(
                ticketRepository.countTicketsGroupedByStatusScoped(filter, pids));
        long open = dist.getNewCount() + dist.getInProgressCount() + dist.getWaitingForCustomerCount();
        long resolvedClosed = dist.getResolvedCount() + dist.getClosedCount();

        Long slaBreachedNullable = ticketRepository.countSlaBreachedByStatusInScoped(OPEN_STATUSES, filter, pids);
        long slaBreachedCount = slaBreachedNullable != null ? slaBreachedNullable : 0L;
        double slaBreachRate = open > 0 ? (double) slaBreachedCount / open * 100.0 : 0.0;

        Double avgResolution = ticketRepository.findAvgResolutionHoursForResolvedScoped(filter, pids);
        Double csatAvg = csatRepository.findAverageRatingScoped(filter, pids);
        long csatCount = csatRepository.countScoped(filter, pids);

        PriorityMetricsDTO priority = getPriorityDistributionFromDb(filter, pids);

        TicketTimelineDTO timeline = trimTimeline(buildTimeline(
                ticketRepository.getTicketTimelineMetricsScoped(window, filter, pids)), isAllTime(days));

        // Bu ürün kapsamındaki ajan performansı (kendi içinde scope'lu).
        AgentPerformanceDTO topAgents = getAgentPerformance(pids, "product:" + productId);

        List<RecentTicketDTO> recent = ticketRepository
                .findRecentByProductId(productId, PageRequest.of(0, 5)).stream()
                .map(this::toRecentTicket)
                .toList();

        return ProductDashboardDTO.builder()
                .productId(productId)
                .productName(productName)
                .totalTickets(dist.getTotalCount())
                .openTickets(open)
                .resolvedTickets(resolvedClosed)
                .slaBreachedCount(slaBreachedCount)
                .slaBreachRate(slaBreachRate)
                .avgResolutionHours(avgResolution != null ? avgResolution : 0.0)
                .csatAverage(csatAvg != null ? csatAvg : 0.0)
                .csatCount(csatCount)
                .statusDistribution(dist)
                .priorityDistribution(priority)
                .timeline(timeline)
                .topAgents(topAgents)
                .recentTickets(recent)
                .build();
    }

    private RecentTicketDTO toRecentTicket(Ticket t) {
        return RecentTicketDTO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .status(t.getStatus())
                .priority(t.getPriority())
                .createdAt(t.getCreatedAt())
                .build();
    }

    private static long asLong(Object[] row, int idx) {
        return (row == null || row[idx] == null) ? 0L : ((Number) row[idx]).longValue();
    }

    private static double asDouble(Object[] row, int idx) {
        return (row == null || row[idx] == null) ? 0.0 : ((Number) row[idx]).doubleValue();
    }
}
