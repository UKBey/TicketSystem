package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.WorklogRequestDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
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
    private final TicketClaimRepository ticketClaimRepository;

    /**
     * Atanmis agentin ilgili bilete sure ve aciklama kaydi eklemesini saglar.
     */
    public TicketWorklog addWorklog(Long ticketId, WorklogRequestDTO dto, String agentId) {
        log.info("Worklog ekleme isteği. Bilet ID: {}, Agent: {}", ticketId, agentId);

        // Gecersiz dakika degeri erken reddedilir.
        if (dto.getMinutes() == null || dto.getMinutes() <= 0) {
            log.warn("Worklog reddedildi: Geçersiz dakika değeri ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dakika değeri 0'dan büyük olmalıdır.");
        }

        // Isleme konu biletin varligi dogrulanir.
        Ticket ticket = ticketService.getTicketById(ticketId);

        // Worklog yalnizca bileti claim alan agent tarafindan eklenebilir.
        if (!ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, agentId)) {
            log.warn("Worklog reddedildi: Agent (ID: {}) bu bileti claim almamış.", agentId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sadece claim aldığınız biletlere worklog ekleyebilirsiniz.");
        }

        // Kapali kayitta yeni worklog olusturulmaz.
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
     * Bilete bagli worklog kayitlarini, rol kurallarina uygun sekilde listeler.
     */
    public List<TicketWorklog> getWorklogsByTicket(Long ticketId, String userId, List<String> roles) {
        log.info("Worklog listeleme isteği. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // Listeleme oncesi biletin varligi teyit edilir.
        Ticket ticket = ticketService.getTicketById(ticketId);

        boolean isAgentAdmin = roles.contains("AGENT_ADMIN");
        boolean isAgent = roles.contains("AGENT");

        if (isAgentAdmin) {
            // Agent admin rolunde tum kayitlar listelenebilir.
            log.debug("Agent admin erişimi: Tüm workloglar listeleniyor. Bilet ID: {}", ticketId);
        } else if (isAgent) {
            // Agent yalnizca claim aldigi biletlerin workloglarini gorebilir.
            if (!ticketClaimRepository.existsByTicketIdAndAgentId(ticket.getId(), userId)) {
                log.warn("Worklog görüntüleme reddedildi: Agent (ID: {}) bu bileti claim almamış.", userId);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Sadece claim aldığınız biletlerin workloglarını görüntüleyebilirsiniz.");
            }
        } else {
            log.warn("Worklog görüntüleme reddedildi: Yetersiz rol. Kullanıcı: {}", userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu kaynağa erişim yetkiniz bulunmamaktadır.");
        }

        return worklogRepository.findByTicketId(ticketId);
    }

    /**
     * Tum worklog kayitlarini yonetici ekrani icin dondurur.
     */
    public List<TicketWorklog> getAllWorklogs() {
        log.info("Tüm worklogları listeleme isteği (Manager).");
        return worklogRepository.findAll();
    }

    /**
     * Worklog kaydini gunceller; sahiplik ve bilet durumu kosullari zorunludur.
     */
    public TicketWorklog updateWorklog(Long ticketId, Long worklogId, WorklogRequestDTO dto, String agentId) {
        log.info("Worklog güncelleme isteği. Worklog ID: {}, Agent: {}", worklogId, agentId);

        // Dakika alani gonderildiyse pozitif deger kontrol edilir.
        if (dto.getMinutes() != null && dto.getMinutes() <= 0) {
            log.warn("Worklog güncelleme reddedildi: Geçersiz dakika değeri ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dakika değeri 0'dan büyük olmalıdır.");
        }

        // Hedef worklog kaydi bulunur.
        TicketWorklog worklog = worklogRepository.findById(worklogId)
                .orElseThrow(() -> {
                    log.warn("Güncellenecek worklog bulunamadı. ID: {}", worklogId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Worklog bulunamadı: " + worklogId);
                });

        // URL'deki bilet kimligi ile kaydin ait oldugu bilet uyusmalidir.
        if (!worklog.getTicketId().equals(ticketId)) {
            log.warn("Worklog güncelleme reddedildi: Worklog (ID: {}) bu bilete (ID: {}) ait değil.", worklogId, ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bu worklog belirtilen bilete ait değil.");
        }

        // Guncelleme sadece kaydi olusturan agente aciktir.
        if (!agentId.equals(worklog.getAgentId())) {
            log.warn("Worklog güncelleme reddedildi: Agent (ID: {}) bu worklogun sahibi değil (Owner: {}).",
                    agentId, worklog.getAgentId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sadece kendi oluşturduğunuz worklogları güncelleyebilirsiniz.");
        }

        // Kapali bilette worklog degisikligi kabul edilmez.
        Ticket ticket = ticketService.getTicketById(ticketId);
        if ("CLOSED".equals(ticket.getStatus())) {
            log.warn("Worklog güncelleme reddedildi: Bilet CLOSED statüsünde. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Kapalı (CLOSED) biletlerin worklogları güncellenemez.");
        }

        // Kismi guncelleme: sadece gelen alanlar degistirilir.
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
     * Worklog kaydini rol ve sahiplik kurallarina gore siler.
     */
    public void deleteWorklog(Long worklogId, String userId, List<String> roles) {
        log.info("Worklog silme isteği. Worklog ID: {}, Kullanıcı: {}", worklogId, userId);

        TicketWorklog worklog = worklogRepository.findById(worklogId)
                .orElseThrow(() -> {
                    log.warn("Silinmek istenen worklog bulunamadı. ID: {}", worklogId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Worklog bulunamadı: " + worklogId);
                });

        boolean isAgentAdmin = roles.contains("AGENT_ADMIN");
        boolean isAgent = roles.contains("AGENT");

        if (isAgentAdmin) {
            // Agent admin tum worklog kayitlarini silebilir.
            log.debug("Agent admin yetkisiyle worklog silme izni verildi. Worklog ID: {}", worklogId);
        } else if (isAgent) {
            // Agent yalnizca kendi olusturdugu kaydi silebilir.
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
