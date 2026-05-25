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
 * Bilet kapanışı sonrası müşteri memnuniyet anketi (CSAT) yönetimi.
 *
 * <p>Anket yalnızca biletin sahibi olan müşteri tarafından, RESOLVED veya CLOSED
 * statüsünde, bilet başına bir kez verilebilir. RESOLVED durumda yanıt gelirse
 * bilet otomatik olarak CLOSED durumuna taşınır.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class CsatService {

    private final CsatRepository csatRepository;
    private final TicketService ticketService;

    /**
     * Bir bilet için CSAT anket cevabını kaydeder.
     *
     * <p>Sahiplik, statü (RESOLVED/CLOSED), tekil kayıt ve 1-5 puan aralığı
     * doğrulanır. Bilet RESOLVED ise kayıt sonrasında CLOSED durumuna taşınır
     * (CSAT_SUBMITTED reason kodu ile audit'lenir).
     *
     * @param ticketId hedef bilet ID
     * @param dto anket içeriği (rating + opsiyonel yorum)
     * @param userId istemde bulunan müşteri ID
     * @param roles kullanıcının rolleri
     * @return kaydedilmiş {@link Csat}
     * @throws ResponseStatusException 400 puan/statü/tekrar, 403 sahiplik ihlali
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
        if (!"CLOSED".equals(ticket.getStatus()) && !"RESOLVED".equals(ticket.getStatus())) {
            log.warn("CSAT reddedildi: Bilet statüsü uygun değil (Statü: {})", ticket.getStatus());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.csat.closed.or.resolved.only");
        }

        // Ayni bilete ikinci kez CSAT olusmasini engeller.
        if (csatRepository.existsByTicketId(ticketId)) {
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

        // Musteri onayi geldiyse RESOLVED kayit CLOSED durumuna tasinir.
        if ("RESOLVED".equals(ticket.getStatus())) {
            ticketService.updateTicketStatus(ticketId, "CLOSED", "CSAT_SUBMITTED", null, userId, roles);
        }

        return savedCsat;
    }

    /**
     * Bir biletin halihazırda CSAT yanıtının olup olmadığını döner.
     *
     * @param ticketId bilet ID
     * @return kayıt varsa {@code true}
     */
    public boolean hasCsat(Long ticketId) {
        return csatRepository.existsByTicketId(ticketId);
    }

    /**
     * Bir bilete ait CSAT kaydını döner. Yetki kontrolü {@link TicketService}
     * üzerinden yapılır.
     *
     * @param ticketId hedef bilet ID
     * @param userId istek yapan kullanıcı
     * @param roles kullanıcının rolleri
     * @return CSAT kaydı
     * @throws ResponseStatusException 404 yoksa, 403 bilete erişim yoksa
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
     * Tüm CSAT kayıtlarını döner. Erişim yetkisi controller tarafında
     * {@code @PreAuthorize} ile AGENT_ADMIN'e kısıtlıdır.
     *
     * @return tüm CSAT yanıtları
     */
    public List<Csat> getAllCsats() {
        log.debug("Tüm CSAT anketlerini listeleme isteği (Agent admin).");
        return csatRepository.findAll();
    }
}
