package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.WorklogRequestDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class WorklogService {

    private final WorklogRepository worklogRepository;
    private final TicketService ticketService;

    /**
     * Agent kendi üzerine atanmış bir bilete worklog ekler.
     * Kural: ticket.assigneeId == agentId olmalı
     */
    public TicketWorklog addWorklog(Long ticketId, WorklogRequestDTO dto, String agentId) {
        log.info("Worklog ekleme isteği. Bilet ID: {}, Agent: {}", ticketId, agentId);

        // 1. Dakika doğrulaması
        if (dto.getMinutes() == null || dto.getMinutes() <= 0) {
            log.warn("Worklog reddedildi: Geçersiz dakika değeri ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dakika değeri 0'dan büyük olmalıdır.");
        }

        // 2. Bileti çek (varlık kontrolü)
        Ticket ticket = ticketService.getTicketById(ticketId);

        // 3. Yetki kontrolü: Agent yalnızca kendine atanmış bilete ekleyebilir
        if (!agentId.equals(ticket.getAssigneeId())) {
            log.warn("Worklog reddedildi: Agent (ID: {}) bu biletin atanan kişisi değil. Assignee: {}",
                    agentId, ticket.getAssigneeId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sadece size atanmış biletlere worklog ekleyebilirsiniz.");
        }

        // 4. Kapalı biletelere ekleme yapılamaz
        if ("CLOSED".equals(ticket.getStatus())) {
            log.warn("Worklog reddedildi: Bilet CLOSED statüsünde. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kapalı (CLOSED) biletlere worklog eklenemez.");
        }

        TicketWorklog worklog = TicketWorklog.builder()
                .ticketId(ticketId)
                .agentId(agentId)
                .minutes(dto.getMinutes())
                .description(dto.getDescription())
                .build();

        TicketWorklog saved = worklogRepository.save(worklog);
        log.info("Worklog başarıyla kaydedildi. ID: {}, Bilet: {}, Dakika: {}", saved.getId(), ticketId, dto.getMinutes());
        return saved;
    }

    /**
     * Belirli bir bilete ait tüm worklogları getirir.
     * Yalnızca Agent (kendi biletiyse) veya Manager görebilir.
     */
    public List<TicketWorklog> getWorklogsByTicket(Long ticketId, String userId, List<String> roles) {
        log.info("Worklog listeleme isteği. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // Biletin varlığını doğrula
        Ticket ticket = ticketService.getTicketById(ticketId);

        boolean isManager = roles.contains("MANAGER");
        boolean isAgent = roles.contains("AGENT");

        if (isManager) {
            // Manager tümünü görebilir
            log.debug("Manager erişimi: Tüm workloglar listeleniyor. Bilet ID: {}", ticketId);
        } else if (isAgent) {
            // Agent yalnızca kendi atanmış olduğu bileti görebilir
            if (!userId.equals(ticket.getAssigneeId())) {
                log.warn("Worklog görüntüleme reddedildi: Agent (ID: {}) bu biletin assignee'si değil.", userId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Sadece size atanmış biletlerin workloglarını görüntüleyebilirsiniz.");
            }
        } else {
            log.warn("Worklog görüntüleme reddedildi: Yetersiz rol. Kullanıcı: {}", userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu kaynağa erişim yetkiniz bulunmamaktadır.");
        }

        return worklogRepository.findByTicketId(ticketId);
    }

    /**
     * Manager: tüm sistemdeki worklogları listeler.
     */
    public List<TicketWorklog> getAllWorklogs() {
        log.info("Tüm worklogları listeleme isteği (Manager).");
        return worklogRepository.findAll();
    }

    /**
     * Worklog günceller.
     * Kural: Yalnızca worklogu oluşturan agent (worklog.agentId == agentId) güncelleyebilir.
     *        Bağlı bilet CLOSED olmamalı.
     */
    public TicketWorklog updateWorklog(Long ticketId, Long worklogId, WorklogRequestDTO dto, String agentId) {
        log.info("Worklog güncelleme isteği. Worklog ID: {}, Agent: {}", worklogId, agentId);

        // 1. Dakika doğrulaması (verilmişse)
        if (dto.getMinutes() != null && dto.getMinutes() <= 0) {
            log.warn("Worklog güncelleme reddedildi: Geçersiz dakika değeri ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dakika değeri 0'dan büyük olmalıdır.");
        }

        // 2. Worklog'u bul
        TicketWorklog worklog = worklogRepository.findById(worklogId)
                .orElseThrow(() -> {
                    log.warn("Güncellenecek worklog bulunamadı. ID: {}", worklogId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Worklog bulunamadı: " + worklogId);
                });

        // 3. Worklog belirtilen bilete ait mi?
        if (!worklog.getTicketId().equals(ticketId)) {
            log.warn("Worklog güncelleme reddedildi: Worklog (ID: {}) bu bilete (ID: {}) ait değil.", worklogId, ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bu worklog belirtilen bilete ait değil.");
        }

        // 4. Yetki: Yalnızca worklogu oluşturan agent güncelleyebilir
        if (!agentId.equals(worklog.getAgentId())) {
            log.warn("Worklog güncelleme reddedildi: Agent (ID: {}) bu worklogun sahibi değil (Owner: {}).",
                    agentId, worklog.getAgentId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sadece kendi oluşturduğunuz worklogları güncelleyebilirsiniz.");
        }

        // 5. Bağlı bilet kapalı mı?
        Ticket ticket = ticketService.getTicketById(ticketId);
        if ("CLOSED".equals(ticket.getStatus())) {
            log.warn("Worklog güncelleme reddedildi: Bilet CLOSED statüsünde. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kapalı (CLOSED) biletlerin worklogları güncellenemez.");
        }

        // 6. Güncelle (yalnızca gönderilen alanlar)
        if (dto.getMinutes() != null) {
            worklog.setMinutes(dto.getMinutes());
        }
        if (dto.getDescription() != null) {
            worklog.setDescription(dto.getDescription());
        }

        TicketWorklog saved = worklogRepository.save(worklog);
        log.info("Worklog başarıyla güncellendi. ID: {}, Bilet: {}", saved.getId(), ticketId);
        return saved;
    }

    /**
     * Worklog siler.
     * Kural: Agent yalnızca kendi oluşturduğu worklogu silbilir (worklog.agentId == agentId).
     *        Manager herhangi bir worklogu silebilir.
     */
    public void deleteWorklog(Long worklogId, String userId, List<String> roles) {
        log.info("Worklog silme isteği. Worklog ID: {}, Kullanıcı: {}", worklogId, userId);

        TicketWorklog worklog = worklogRepository.findById(worklogId)
                .orElseThrow(() -> {
                    log.warn("Silinmek istenen worklog bulunamadı. ID: {}", worklogId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Worklog bulunamadı: " + worklogId);
                });

        boolean isManager = roles.contains("MANAGER");
        boolean isAgent = roles.contains("AGENT");

        if (isManager) {
            // Manager her worklogu silebilir
            log.debug("Manager yetkisiyle worklog silme izni verildi. Worklog ID: {}", worklogId);
        } else if (isAgent) {
            // Agent yalnızca kendi worklogunu silebilir
            if (!userId.equals(worklog.getAgentId())) {
                log.warn("Worklog silme reddedildi: Agent (ID: {}) bu worklogun sahibi değil (Owner: {}).",
                        userId, worklog.getAgentId());
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Sadece kendi oluşturduğunuz worklogları silebilirsiniz.");
            }
        } else {
            log.warn("Worklog silme reddedildi: Yetersiz rol. Kullanıcı: {}", userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu işlem için yetkiniz bulunmamaktadır.");
        }

        worklogRepository.deleteById(worklogId);
        log.info("Worklog başarıyla silindi. ID: {}", worklogId);
    }
}
