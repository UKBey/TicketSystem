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

    public Csat submitCsat(Long ticketId, CsatDTO dto, String userId) {
        log.info("CSAT anketi gönderimi başlatıldı. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // 1. Puan kontrolü
        if (dto.getRating() == null || dto.getRating() < 1 || dto.getRating() > 5) {
            log.warn("CSAT reddedildi: Geçersiz puan (Rating: {})", dto.getRating());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating değeri 1 ile 5 arasında olmalıdır.");
        }

        // 2. Biletin varlığını ve yetkiyi kontrol et (Owner check)
        // TicketService.getTicketById kullanırsak yetki kontrolü yapmaz.
        // getTicketWithAuth kullanırsak sadece view yetkisine bakar.
        // Bizim durumumuzda bu biletin müşterisi olduğunu doğrulamalıyız.
        Ticket ticket = ticketService.getTicketById(ticketId);

        if (!userId.equals(ticket.getCustomerId())) {
            log.warn("CSAT reddedildi: Kullanıcı (ID: {}) biletin sahibi değil (Owner: {})", userId, ticket.getCustomerId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece kendi biletlerinize anket yapabilirsiniz.");
        }

        // 3. Statü kontrolü (Sadece CLOSED)
        if (!"CLOSED".equals(ticket.getStatus())) {
            log.warn("CSAT reddedildi: Bilet statüsü CLOSED değil (Statü: {})", ticket.getStatus());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sadece kapalı (CLOSED) biletler için anket yapılabilir.");
        }

        // 4. Mükerrer kayıt kontrolü
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
        return savedCsat;
    }

    public boolean hasCsat(Long ticketId) {
        return csatRepository.existsByTicketId(ticketId);
    }

    public Csat getCsatByTicketId(Long ticketId, String userId, List<String> roles) {
        log.info("CSAT detay isteği. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // Bilet erişim yetkisi kontrolü (Mevcut TicketService mantığından faydalanıyoruz)
        ticketService.getTicketWithAuth(ticketId, userId, roles);

        return csatRepository.findByTicketId(ticketId)
                .orElseThrow(() -> {
                    log.warn("CSAT bulunamadı. Bilet ID: {}", ticketId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Bu bilet için henüz bir anket yapılmamış.");
                });
    }

    public List<Csat> getAllCsats() {
        log.info("Tüm CSAT anketlerini listeleme isteği (Manager).");
        return csatRepository.findAll();
    }
}
