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

@Log4j2
@Service
@RequiredArgsConstructor
public class CsatService {

    private final CsatRepository csatRepository;
    private final TicketService ticketService;

    public Csat submitCsat(Long ticketId, CsatDTO dto, String userId, List<String> roles) {
        log.info("CSAT anketi gönderimi başlatıldı. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // Anket puani beklenen aralikta degilse islem erken sonlandirilir.
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            log.warn("CSAT reddedildi: Geçersiz puan (Rating: {})", dto.getRating());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating değeri 1 ile 5 arasında olmalıdır.");
        }

        // CSAT gonderimi icin goruntuleme yetkisi degil, dogrudan sahiplik dogrulanir.
        Ticket ticket = ticketService.getTicketById(ticketId);

        if (!userId.equals(ticket.getCustomerId())) {
            log.warn("CSAT reddedildi: Kullanıcı (ID: {}) biletin sahibi değil (Owner: {})", userId, ticket.getCustomerId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece kendi biletlerinize anket yapabilirsiniz.");
        }

        // Anket yalnizca cozulmus ya da kapanmis kayitlarda kabul edilir.
        if (!"CLOSED".equals(ticket.getStatus()) && !"RESOLVED".equals(ticket.getStatus())) {
            log.warn("CSAT reddedildi: Bilet statüsü uygun değil (Statü: {})", ticket.getStatus());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sadece kapalı veya çözülmüş biletler için anket yapılabilir.");
        }

        // Ayni bilete ikinci kez CSAT olusmasini engeller.
        if (csatRepository.existsByTicketId(ticketId)) {
            log.warn("CSAT reddedildi: Bu bilet için zaten bir anket mevcut. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zaten bir CSAT var, yeni kayıt oluşturulamaz.");
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
            ticketService.updateTicketStatus(ticketId, "CLOSED", userId, roles);
        }

        return savedCsat;
    }

    public boolean hasCsat(Long ticketId) {
        return csatRepository.existsByTicketId(ticketId);
    }

    public Csat getCsatByTicketId(Long ticketId, String userId, List<String> roles) {
        log.info("CSAT detay isteği. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // CSAT detayi donmeden once bilet erisim kurali mevcut servisle dogrulanir.
        ticketService.getTicketWithAuth(ticketId, userId, roles);

        return csatRepository.findByTicketId(ticketId)
                .orElseThrow(() -> {
                    log.warn("CSAT bulunamadı. Bilet ID: {}", ticketId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Bu bilet için henüz bir anket yapılmamış.");
                });
    }

    public List<Csat> getAllCsats() {
        log.info("Tüm CSAT anketlerini listeleme isteği (Agent admin).");
        return csatRepository.findAll();
    }
}
