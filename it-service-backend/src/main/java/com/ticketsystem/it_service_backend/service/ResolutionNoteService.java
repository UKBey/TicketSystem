package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.ResolutionNoteRequestDTO;
import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.ResolutionNoteRepository;
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

    /**
     * Claim'li agent, üzerindeki bilete ilk kez çözüm notu ekler.
     * Kural: ticket.assigneeId == agentId olmalı; bilete daha önce not eklenmemiş olmalı.
     */
    public ResolutionNote createResolutionNote(Long ticketId, ResolutionNoteRequestDTO dto, String agentId) {
        log.info("Çözüm notu oluşturma isteği. Bilet ID: {}, Agent: {}", ticketId, agentId);

        // 1. Not içeriği boş olamaz
        if (dto.getNote() == null || dto.getNote().isBlank()) {
            log.warn("Çözüm notu reddedildi: Not içeriği boş olamaz.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Çözüm notu içeriği boş olamaz.");
        }

        // 2. Bileti çek (varlık kontrolü)
        Ticket ticket = ticketService.getTicketById(ticketId);

        // 3. Yetki kontrolü: Yalnızca claim'li agent (ticket.assigneeId) yazabilir
        if (!agentId.equals(ticket.getAssigneeId())) {
            log.warn("Çözüm notu reddedildi: Agent (ID: {}) bu biletin atanan kişisi değil. Assignee: {}",
                    agentId, ticket.getAssigneeId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Çözüm notu yalnızca bileti üzerine almış (claim'li) agent tarafından yazılabilir.");
        }

        // 4. Kapalı bilete not eklenemez
        if ("CLOSED".equals(ticket.getStatus())) {
            log.warn("Çözüm notu reddedildi: Bilet CLOSED statüsünde. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kapalı (CLOSED) biletlere çözüm notu eklenemez.");
        }

        // 5. Bu bilete zaten bir not eklenmiş mi? (one-to-one)
        if (resolutionNoteRepository.existsByTicketId(ticketId)) {
            log.warn("Çözüm notu reddedildi: Bilete (ID: {}) zaten bir çözüm notu eklenmiş.", ticketId);
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bu bilete zaten bir çözüm notu eklenmiş. Güncellemek için PUT kullanın.");
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
     * Claim'li agent, mevcut çözüm notunu günceller.
     * Kural: ticket.assigneeId == agentId olmalı; bilete daha önce not eklenmiş olmalı.
     */
    public ResolutionNote updateResolutionNote(Long ticketId, ResolutionNoteRequestDTO dto, String agentId) {
        log.info("Çözüm notu güncelleme isteği. Bilet ID: {}, Agent: {}", ticketId, agentId);

        // 1. Not içeriği boş olamaz
        if (dto.getNote() == null || dto.getNote().isBlank()) {
            log.warn("Çözüm notu güncelleme reddedildi: Not içeriği boş olamaz.");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Çözüm notu içeriği boş olamaz.");
        }

        // 2. Bileti çek (varlık kontrolü)
        Ticket ticket = ticketService.getTicketById(ticketId);

        // 3. Yetki kontrolü: Yalnızca claim'li agent güncelleyebilir
        if (!agentId.equals(ticket.getAssigneeId())) {
            log.warn("Çözüm notu güncelleme reddedildi: Agent (ID: {}) bu biletin atanan kişisi değil. Assignee: {}",
                    agentId, ticket.getAssigneeId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Çözüm notu yalnızca bileti üzerine almış (claim'li) agent tarafından güncellenebilir.");
        }

        // 4. Kapalı biletlerde güncelleme yapılamaz
        if ("CLOSED".equals(ticket.getStatus())) {
            log.warn("Çözüm notu güncelleme reddedildi: Bilet CLOSED statüsünde. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kapalı (CLOSED) biletlerin çözüm notu güncellenemez.");
        }

        // 5. Mevcut notu bul
        ResolutionNote resolutionNote = resolutionNoteRepository.findByTicketId(ticketId)
                .orElseThrow(() -> {
                    log.warn("Güncellenecek çözüm notu bulunamadı. Bilet ID: {}", ticketId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Bu bilete ait bir çözüm notu bulunamadı. Önce POST ile oluşturun.");
                });

        resolutionNote.setNote(dto.getNote());

        ResolutionNote saved = resolutionNoteRepository.save(resolutionNote);
        log.info("Çözüm notu başarıyla güncellendi. Not ID: {}, Bilet ID: {}", saved.getId(), ticketId);
        return saved;
    }


    /**
     * Bir biletin çözüm notunu getirir.
     * - Claim'li agent (ticket.assigneeId == agentId) kendi biletinin notunu görebilir.
     * - Manager tüm notları görebilir.
     */
    public ResolutionNote getResolutionNoteByTicket(Long ticketId, String userId, List<String> roles) {
        log.info("Çözüm notu görüntüleme isteği. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        Ticket ticket = ticketService.getTicketById(ticketId);

        boolean isManager = roles.contains("MANAGER");
        boolean isAgent = roles.contains("AGENT");

        if (isManager) {
            log.debug("Manager erişimi: Çözüm notu görüntüleniyor. Bilet ID: {}", ticketId);
        } else if (isAgent) {
            if (!userId.equals(ticket.getAssigneeId())) {
                log.warn("Çözüm notu görüntüleme reddedildi: Agent (ID: {}) bu biletin assignee'si değil.", userId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Yalnızca size atanmış biletin çözüm notunu görüntüleyebilirsiniz.");
            }
        } else {
            log.warn("Çözüm notu görüntüleme reddedildi: Yetersiz rol. Kullanıcı: {}", userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu kaynağa erişim yetkiniz bulunmamaktadır.");
        }

        return resolutionNoteRepository.findByTicketId(ticketId)
                .orElseThrow(() -> {
                    log.warn("Çözüm notu bulunamadı. Bilet ID: {}", ticketId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "Bu bilete ait bir çözüm notu bulunamadı.");
                });
    }

    /**
     * Manager: sistemdeki tüm çözüm notlarını listeler.
     */
    public List<ResolutionNote> getAllResolutionNotes() {
        log.info("Tüm çözüm notlarını listeleme isteği (Manager).");
        return resolutionNoteRepository.findAll();
    }
}

