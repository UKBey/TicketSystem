package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.dto.PriorityMetricsDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MetricsService {

    private final TicketRepository ticketRepository;
    private final CsatRepository csatRepository;

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
}
