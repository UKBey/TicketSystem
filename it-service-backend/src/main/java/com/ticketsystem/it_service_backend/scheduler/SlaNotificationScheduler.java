package com.ticketsystem.it_service_backend.scheduler;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.service.NotificationService;
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
     * Deadline'ına 2 saatten az kalmış, henüz ihlal edilmemiş biletler için uyarı gönderir.
     */
    @Scheduled(fixedRate = 900_000)
    public void checkUpcomingSlaBreaches() {
        ZonedDateTime warningThreshold = ZonedDateTime.now().plusHours(2);

        List<Ticket> warningTickets = ticketRepository.findUpcomingBreachTickets(
                ACTIVE_STATUSES, warningThreshold, PageRequest.of(0, 100));

        if (warningTickets.isEmpty()) return;

        log.info("SLA uyarı taraması: {} bilet deadline yaklaşıyor.", warningTickets.size());

        for (Ticket ticket : warningTickets) {
            notificationService.notifySlaWarning(ticket);
            log.info("SLA uyarısı gönderildi. Bilet ID: {}, Deadline: {}", ticket.getId(), ticket.getSlaDeadline());
        }
    }
}
