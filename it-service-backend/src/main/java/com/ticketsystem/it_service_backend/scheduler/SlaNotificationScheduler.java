package com.ticketsystem.it_service_backend.scheduler;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.service.NotificationService;
import com.ticketsystem.it_service_backend.service.SlaPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Log4j2
@Component
@RequiredArgsConstructor
public class SlaNotificationScheduler {

    private static final List<String> ACTIVE_STATUSES = List.of("NEW", "IN_PROGRESS");

    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;
    private final SlaPolicyService slaPolicyService;

    /**
     * Deadline'ı geçmiş ama henüz slaBreached=false olan biletleri tespit eder,
     * ihlal bayrağını set eder ve ilgili tarafları bildirir.
     */
    @Transactional
    @Scheduled(fixedRate = 900_000)
    public void checkNewlyBreachedTickets() {
        List<Ticket> overdueTickets = ticketRepository
                .findOverdueUnmarkedTickets(ZonedDateTime.now(), ACTIVE_STATUSES);

        if (overdueTickets.isEmpty()) return;

        log.info("SLA ihlali taraması: {} yeni ihlal tespit edildi.", overdueTickets.size());

        for (Ticket ticket : overdueTickets) {
            ticket.setSlaBreached(true);
            ticketRepository.save(ticket);
            notificationService.notifySlaBreached(ticket);
            log.warn("SLA ihlali işaretlendi. Bilet ID: {}, Öncelik: {}", ticket.getId(), ticket.getPriority());
        }
    }

    /**
     * Deadline'ına yaklaşan, henüz ihlal edilmemiş biletler için uyarı gönderir.
     * Uyarı eşiği her öncelik için SLA politikasından okunur.
     */
    @Scheduled(fixedRate = 900_000)
    public void checkUpcomingSlaBreaches() {
        // Her öncelik için ayrı eşik kullanarak uyarı gönder
        for (String priority : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
            int thresholdHours = slaPolicyService.getWarningThresholdHours(priority);
            if (thresholdHours <= 0) continue; // 0 ise bu öncelik için uyarı kapalı

            ZonedDateTime warningThreshold = ZonedDateTime.now().plusHours(thresholdHours);

            List<Ticket> warningTickets = ticketRepository.findUpcomingBreachTicketsByPriority(
                    ACTIVE_STATUSES, priority, warningThreshold, PageRequest.of(0, 100));

            if (warningTickets.isEmpty()) continue;

            log.info("SLA uyarı taraması [{}]: {} bilet deadline yaklaşıyor (eşik: {} saat).",
                    priority, warningTickets.size(), thresholdHours);

            for (Ticket ticket : warningTickets) {
                notificationService.notifySlaWarning(ticket);
                log.info("SLA uyarısı gönderildi. Bilet ID: {}, Deadline: {}", ticket.getId(), ticket.getSlaDeadline());
            }
        }
    }
}
