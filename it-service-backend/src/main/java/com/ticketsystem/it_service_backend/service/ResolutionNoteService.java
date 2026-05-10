package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.ResolutionNoteRequestDTO;
import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.ResolutionNoteRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResolutionNoteService {

    private final ResolutionNoteRepository resolutionNoteRepository;
    private final TicketService ticketService;
    private final TicketClaimRepository ticketClaimRepository;

    /**
     * Bileti sahiplenen agentin, ilgili kayda ilk cozum notunu eklemesini saglar.
     */
    public ResolutionNote createResolutionNote(Long ticketId, ResolutionNoteRequestDTO dto, String agentId) {
        log.info("Çözüm notu oluşturma isteği. Bilet ID: {}, Agent: {}", ticketId, agentId);

        // Bos not kabul edilmez.
        if (dto.getNote() == null || dto.getNote().isBlank()) {
            log.warn("Çözüm notu reddedildi: Not içeriği boş olamaz.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.resolution.note.empty");
        }

        // Islem oncesi biletin varligi dogrulanir.
        Ticket ticket = ticketService.getTicketById(ticketId);

        // Not yazma yetkisi yalnizca bileti claim alan agenta aittir.
        if (!ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, agentId)) {
            log.warn("Çözüm notu reddedildi: Agent (ID: {}) bu bileti claim almamış.", agentId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.resolution.note.create.requires.claim");
        }

        // Kapanmis kayitlara yeni cozum notu eklenmez.
        if ("CLOSED".equals(ticket.getStatus())) {
            log.warn("Çözüm notu reddedildi: Bilet CLOSED statüsünde. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "error.resolution.note.ticket.closed");
        }

        // Her bilet icin tek cozum notu olmasi kuralini korur.
        if (resolutionNoteRepository.existsByTicketId(ticketId)) {
            log.warn("Çözüm notu reddedildi: Bilete (ID: {}) zaten bir çözüm notu eklenmiş.", ticketId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "error.resolution.note.already.exists");
        }

        ResolutionNote resolutionNote = ResolutionNote.builder()
                .ticketId(ticketId)
                .agentId(agentId)
                .note(dto.getNote())
                .build();

        ResolutionNote saved = resolutionNoteRepository.save(resolutionNote);
        log.info("Çözüm notu başarıyla oluşturuldu. Not ID: {}, Bilet ID: {}", saved.getId(), ticketId);
        return saved;
    }

    /**
     * Mevcut cozum notunu, yalnizca ilgili kaydi sahiplenen agentin guncellemesini saglar.
     */
    public ResolutionNote updateResolutionNote(Long ticketId, ResolutionNoteRequestDTO dto, String agentId) {
        log.info("Çözüm notu güncelleme isteği. Bilet ID: {}, Agent: {}", ticketId, agentId);

        // Bos notla guncelleme yapilmaz.
        if (dto.getNote() == null || dto.getNote().isBlank()) {
            log.warn("Çözüm notu güncelleme reddedildi: Not içeriği boş olamaz.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.resolution.note.empty");
        }

        // Islem yapilacak bilet once dogrulanir.
        Ticket ticket = ticketService.getTicketById(ticketId);

        // Guncelleme yetkisi yalnizca bileti claim alan agenta aittir.
        if (!ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, agentId)) {
            log.warn("Çözüm notu güncelleme reddedildi: Agent (ID: {}) bu bileti claim almamış.", agentId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.resolution.note.update.requires.claim");
        }

        // Kapanmis bilette not degistirilemez.
        if ("CLOSED".equals(ticket.getStatus())) {
            log.warn("Çözüm notu güncelleme reddedildi: Bilet CLOSED statüsünde. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "error.resolution.note.update.ticket.closed");
        }

        // Guncellenecek mevcut not kaydi bulunur.
        ResolutionNote resolutionNote = resolutionNoteRepository.findByTicketId(ticketId)
                .orElseThrow(() -> {
                    log.warn("Güncellenecek çözüm notu bulunamadı. Bilet ID: {}", ticketId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "error.resolution.note.not.found.create.first");
                });

        resolutionNote.setNote(dto.getNote());

        ResolutionNote saved = resolutionNoteRepository.save(resolutionNote);
        log.info("Çözüm notu başarıyla güncellendi. Not ID: {}, Bilet ID: {}", saved.getId(), ticketId);
        return saved;
    }


    /**
     * Cozum notunu, rol ve sahiplik kurallarina gore yetkili kullaniciya dondurur.
     */
    public ResolutionNote getResolutionNoteByTicket(Long ticketId, String userId, List<String> roles) {
        log.info("Çözüm notu görüntüleme isteği. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        Ticket ticket = ticketService.getTicketById(ticketId);

        boolean isAgentAdmin = roles.contains("AGENT_ADMIN");
        boolean isAgent = roles.contains("AGENT");

        if (isAgentAdmin) {
            log.debug("Agent admin erişimi: Çözüm notu görüntülenıyor. Bilet ID: {}", ticketId);
        } else if (isAgent) {
            if (!ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, userId)) {
                log.warn("Çözüm notu görüntüleme reddedildi: Agent (ID: {}) bu bileti claim almamış.", userId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "error.resolution.note.view.requires.claim");
            }
        } else {
            log.warn("Çözüm notu görüntüleme reddedildi: Yetersiz rol. Kullanıcı: {}", userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.resource.access.forbidden");
        }

        return resolutionNoteRepository.findByTicketId(ticketId)
                .orElseThrow(() -> {
                    log.warn("Çözüm notu bulunamadı. Bilet ID: {}", ticketId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "error.resolution.note.not.found");
                });
    }

    /**
     * Tum cozum notlarini agent admin gorunumu icin listeler.
     */
    public List<ResolutionNote> getAllResolutionNotes() {
        log.info("Tüm çözüm notlarını listeleme isteği (Agent admin).");
        return resolutionNoteRepository.findAll();
    }
}

