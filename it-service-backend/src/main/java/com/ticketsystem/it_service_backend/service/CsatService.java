package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.CsatDTO;
import com.ticketsystem.it_service_backend.entity.Csat;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Manages the post-resolution customer satisfaction (CSAT) survey.
 *
 * <p>Only the ticket's customer can submit a survey, only when the ticket is
 * RESOLVED or CLOSED, and only once per ticket. When the response arrives while
 * the ticket is RESOLVED, it is automatically moved to CLOSED.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class CsatService {
    private static final String ST_CLOSED = "CLOSED";
    private static final String ST_RESOLVED = "RESOLVED";
    private static final String ACT_CSAT_SUBMITTED = "CSAT_SUBMITTED";

    private final CsatRepository csatRepository;
    private final TicketService ticketService;
    private final TicketAuditHelper auditHelper;

    /**
     * Persists a CSAT survey response for the given ticket.
     *
     * <p>Ownership, status (RESOLVED/CLOSED), uniqueness and 1-5 rating range
     * are all validated. When the ticket is RESOLVED, it is moved to CLOSED
     * after the record is saved (audited with the CSAT_SUBMITTED reason code).
     *
     * @param ticketId target ticket ID
     * @param dto survey payload (rating + optional comment)
     * @param userId ID of the submitting customer
     * @param roles role list of the user
     * @return the persisted {@link Csat}
     * @throws ResponseStatusException 400 on rating/status/duplicate, 403 on ownership
     */
    public Csat submitCsat(Long ticketId, CsatDTO dto, String userId, List<String> roles) {
        log.info("CSAT anketi gönderimi başlatıldı. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // Anket puani beklenen aralikta degilse islem erken sonlandirilir.
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            log.warn("CSAT reddedildi: Geçersiz puan (Rating: {})", dto.getRating());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.csat.rating.range");
        }

        // CSAT gonderimi icin goruntuleme yetkisi degil, dogrudan sahiplik dogrulanir.
        Ticket ticket = ticketService.getTicketById(ticketId);

        if (!userId.equals(ticket.getCustomerId())) {
            log.warn("CSAT reddedildi: Kullanıcı (ID: {}) biletin sahibi değil (Owner: {})", userId, ticket.getCustomerId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.csat.own.tickets.only");
        }

        // Anket yalnizca cozulmus ya da kapanmis kayitlarda kabul edilir.
        if (!ST_CLOSED.equals(ticket.getStatus()) && !ST_RESOLVED.equals(ticket.getStatus())) {
            log.warn("CSAT reddedildi: Bilet statüsü uygun değil (Statü: {})", ticket.getStatus());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.csat.closed.or.resolved.only");
        }

        // Ayni bilete ikinci kez CSAT olusmasini engeller. Ancak anket daha once
        // kaydedilip otomatik kapatma basarisiz olduysa (orn. workflow geçici hatasi)
        // bilet RESOLVED'da takili kalir; bu durumda hata vermek yerine kapatmayi
        // tamamlariz (idempotent kurtarma) — boylece musteri kalici sekilde sikismaz
        // ve verdigi geri bildirim korunur.
        if (csatRepository.existsByTicketId(ticketId)) {
            if (ST_RESOLVED.equals(ticket.getStatus())) {
                log.warn("CSAT zaten mevcut ama bilet RESOLVED — kapatma tamamlanıyor (kurtarma). Bilet ID: {}", ticketId);
                ticketService.updateTicketStatus(ticketId, ST_CLOSED, ACT_CSAT_SUBMITTED, null, userId, roles);
                return csatRepository.findByTicketId(ticketId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.csat.not.found"));
            }
            log.warn("CSAT reddedildi: Bu bilet için zaten bir anket mevcut. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.csat.already.exists");
        }

        Csat csat = Csat.builder()
                .ticketId(ticketId)
                .rating(dto.getRating())
                .comment(dto.getComment())
                .build();

        Csat savedCsat = csatRepository.save(csat);
        log.info("CSAT anketi başarıyla kaydedildi. Bilet ID: {}, CSAT ID: {}", ticketId, savedCsat.getId());

        // Puani tasiyan ayri bir denetim kaydi (ADMIN/MANAGER + musteriye gorunur).
        // Bilet zaten CLOSED ise asagidaki statu gecisi olusmaz; bu kayit her durumda yazilir.
        auditHelper.record(ticket, userId, ACT_CSAT_SUBMITTED, dto.getComment(),
                null, String.valueOf(savedCsat.getRating()));

        // Musteri onayi geldiyse RESOLVED kayit CLOSED durumuna tasinir.
        if (ST_RESOLVED.equals(ticket.getStatus())) {
            ticketService.updateTicketStatus(ticketId, ST_CLOSED, ACT_CSAT_SUBMITTED, null, userId, roles);
        }

        return savedCsat;
    }

    /**
     * Returns whether the given ticket already has a CSAT response.
     *
     * @param ticketId ticket ID
     * @return {@code true} if a record exists
     */
    public boolean hasCsat(Long ticketId) {
        return csatRepository.existsByTicketId(ticketId);
    }

    /**
     * Returns the CSAT record for a ticket. Authorization is delegated to
     * {@link TicketService}.
     *
     * @param ticketId target ticket ID
     * @param userId requesting user
     * @param roles role list of the user
     * @return the CSAT record
     * @throws ResponseStatusException 404 if not found, 403 on ticket access denial
     */
    public Csat getCsatByTicketId(Long ticketId, String userId, List<String> roles) {
        log.debug("CSAT detay isteği. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // CSAT detayi donmeden once bilet erisim kurali mevcut servisle dogrulanir.
        ticketService.getTicketWithAuth(ticketId, userId, roles);

        return csatRepository.findByTicketId(ticketId)
                .orElseThrow(() -> {
                    log.warn("CSAT bulunamadı. Bilet ID: {}", ticketId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "error.csat.not.found");
                });
    }

    /**
     * Returns all CSAT records. Access is restricted to ADMIN by
     * {@code @PreAuthorize} on the controller.
     *
     * @return all CSAT responses
     */
    public List<Csat> getAllCsats() {
        log.debug("Tüm CSAT anketlerini listeleme isteği (Agent admin).");
        return csatRepository.findAll();
    }
}
