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
 * SLA ihlali ve yaklasma uyarilarini periyodik olarak tarayan zamanlanmis is.
 *
 * <p>Iki ayri job 15 dakikada bir calisir: deadline'i gecmis bilet ihlal
 * olarak isaretlenir ve atanan/sahibi olan kisilere bildirim gider; deadline'a
 * yaklasan biletler icin tek seferlik uyari damgasi atilir. Tarayici jBPM
 * akisindan bagimsiz calisir, bu sayede KIE Server kesintilerinde de SLA
 * gozlemi devam eder.
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
     *
     * <p>İdempotency: yalnız {@code sla_warning_sent_at IS NULL} olan biletler
     * tetiklenir, mail başarıyla kuyruğa alındıktan sonra timestamp damgalanır.
     * Aynı bilete tekrar tarama yapılırsa atlanır.
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
