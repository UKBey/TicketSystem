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

/**
 * Scheduled job that periodically scans for SLA breaches and upcoming-breach
 * warnings.
 *
 * <p>Two separate jobs run every 15 minutes: tickets past their deadline are
 * marked as breached and a notification goes out to the assignee/owner;
 * tickets approaching their deadline receive a one-shot warning timestamp.
 * The scanner runs independently of the jBPM flow, so SLA observation
 * continues even while the KIE Server is unavailable.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class SlaNotificationScheduler {

    private static final List<String> ACTIVE_STATUSES = List.of("NEW", "IN_PROGRESS");

    private final TicketRepository ticketRepository;
    private final NotificationService notificationService;
    private final SlaPolicyService slaPolicyService;

    /**
     * Finds tickets whose deadline has passed but whose
     * {@code slaBreached} flag is still false, sets the breach flag and
     * notifies the involved parties.
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
     * Sends warnings for tickets approaching their deadline that are not
     * yet breached. The warning threshold is read from the SLA policy for
     * each priority.
     *
     * <p>Idempotency: only tickets with {@code sla_warning_sent_at IS NULL}
     * are triggered; once the email is successfully queued the timestamp is
     * stamped. Subsequent scans skip the same ticket.
     */
    @Transactional
    @Scheduled(fixedRate = 900_000)
    public void checkUpcomingSlaBreaches() {
        for (String priority : List.of("CRITICAL", "HIGH", "MEDIUM", "LOW")) {
            int thresholdHours = slaPolicyService.getWarningThresholdHours(priority);
            if (thresholdHours <= 0) continue;

            ZonedDateTime warningThreshold = ZonedDateTime.now().plusHours(thresholdHours);

            List<Ticket> warningTickets = ticketRepository.findPendingWarningTicketsByPriority(
                    ACTIVE_STATUSES, java.util.List.of(priority), warningThreshold, PageRequest.of(0, 100));

            if (warningTickets.isEmpty()) continue;

            log.info("SLA uyarı taraması [{}]: {} bilet deadline yaklaşıyor (eşik: {} saat).",
                    priority, warningTickets.size(), thresholdHours);

            ZonedDateTime now = ZonedDateTime.now();
            for (Ticket ticket : warningTickets) {
                notificationService.notifySlaWarning(ticket);
                ticket.setSlaWarningSentAt(now);
                ticketRepository.save(ticket);
                log.info("SLA uyarısı gönderildi. Bilet ID: {}, Deadline: {}", ticket.getId(), ticket.getSlaDeadline());
            }
        }
    }
}
