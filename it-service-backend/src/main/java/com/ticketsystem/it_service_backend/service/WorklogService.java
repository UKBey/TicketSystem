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

/**
 * Bilet üzerinde geçirilen çalışma süresinin (worklog) yönetimi.
 *
 * <p>Worklog kaydı/güncellemesi sadece bileti claim almış ajan tarafından yapılabilir;
 * CLOSED bilete yeni kayıt eklenemez veya mevcut kayıt değiştirilemez. AGENT_ADMIN
 * listeleme/silme aşamasında bypass yetkisine sahiptir.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class WorklogService {

    private final WorklogRepository worklogRepository;
    private final TicketService ticketService;
    private final TicketClaimRepository ticketClaimRepository;

    /**
     * Atanmış ajanın bilete süre + açıklama kaydı eklemesini sağlar. Dakika
     * pozitif olmalı, ajan bileti claim almış olmalı ve bilet CLOSED olmamalıdır.
     *
     * @param ticketId hedef bilet ID
     * @param dto dakika ve açıklama
     * @param agentId kaydı oluşturan ajan
     * @return kaydedilmiş {@link TicketWorklog}
     * @throws ResponseStatusException 400 dakika/statü, 403 claim yoksa
     */
    public TicketWorklog addWorklog(Long ticketId, WorklogRequestDTO dto, String agentId) {
        log.info("Worklog ekleme isteği. Bilet ID: {}, Agent: {}", ticketId, agentId);

        // Gecersiz dakika degeri erken reddedilir.
        if (dto.getMinutes() == null || dto.getMinutes() <= 0) {
            log.warn("Worklog reddedildi: Geçersiz dakika değeri ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.worklog.minutes.positive");
        }

        // Isleme konu biletin varligi dogrulanir.
        Ticket ticket = ticketService.getTicketById(ticketId);

        // Worklog yalnizca bileti claim alan agent tarafindan eklenebilir.
        if (!ticketClaimRepository.existsByTicketIdAndAgentId(ticketId, agentId)) {
            log.warn("Worklog reddedildi: Agent (ID: {}) bu bileti claim almamış.", agentId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.worklog.create.requires.claim");
        }

        // Kapali kayitta yeni worklog olusturulmaz.
        if ("CLOSED".equals(ticket.getStatus())) {
            log.warn("Worklog reddedildi: Bilet CLOSED statüsünde. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "error.worklog.ticket.closed");
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
     * Bilete bağlı worklog kayıtlarını rol kurallarına göre listeler.
     * AGENT_ADMIN her şeyi görür; AGENT yalnızca claim aldığı biletlerin
     * worklog'larını görebilir; diğer roller erişemez.
     *
     * @param ticketId hedef bilet ID
     * @param userId istek yapan kullanıcı
     * @param roles kullanıcının rolleri
     * @return worklog listesi
     * @throws ResponseStatusException 403 — yetkili değilse
     */
    public List<TicketWorklog> getWorklogsByTicket(Long ticketId, String userId, List<String> roles) {
        log.debug("Worklog listeleme isteği. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

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
                        "error.worklog.view.requires.claim");
            }
        } else {
            log.warn("Worklog görüntüleme reddedildi: Yetersiz rol. Kullanıcı: {}", userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.resource.access.forbidden");
        }

        return worklogRepository.findByTicketId(ticketId);
    }

    /**
     * Tüm worklog kayıtlarını yönetici ekranı için döner. Yetki controller
     * tarafında {@code @PreAuthorize} ile yönetilir.
     *
     * @return tüm worklog'lar
     */
    public List<TicketWorklog> getAllWorklogs() {
        log.debug("Tüm worklogları listeleme isteği (Manager).");
        return worklogRepository.findAll();
    }

    /**
     * Worklog kaydını kısmi olarak günceller. Yalnızca kaydı oluşturan ajan
     * değiştirebilir; kayıt URL'deki bilete ait olmalı; CLOSED bilette değişiklik
     * kabul edilmez; minutes alanı verilmişse pozitif olmalıdır.
     *
     * @param ticketId URL'deki bilet ID
     * @param worklogId güncellenecek kayıt ID
     * @param dto kısmi yeni değerler (minutes/description)
     * @param agentId işlemi yapan ajan
     * @return güncellenmiş kayıt
     * @throws ResponseStatusException 404 yoksa, 400 mismatch/statü, 403 sahip değilse
     */
    public TicketWorklog updateWorklog(Long ticketId, Long worklogId, WorklogRequestDTO dto, String agentId) {
        log.info("Worklog güncelleme isteği. Worklog ID: {}, Agent: {}", worklogId, agentId);

        // Dakika alani gonderildiyse pozitif deger kontrol edilir.
        if (dto.getMinutes() != null && dto.getMinutes() <= 0) {
            log.warn("Worklog güncelleme reddedildi: Geçersiz dakika değeri ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.worklog.minutes.positive");
        }

        // Hedef worklog kaydi bulunur.
        TicketWorklog worklog = worklogRepository.findById(worklogId)
                .orElseThrow(() -> {
                    log.warn("Güncellenecek worklog bulunamadı. ID: {}", worklogId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "error.worklog.not.found");
                });

        // URL'deki bilet kimligi ile kaydin ait oldugu bilet uyusmalidir.
        if (!worklog.getTicketId().equals(ticketId)) {
            log.warn("Worklog güncelleme reddedildi: Worklog (ID: {}) bu bilete (ID: {}) ait değil.", worklogId, ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "error.worklog.wrong.ticket");
        }

        // Guncelleme sadece kaydi olusturan agente aciktir.
        if (!agentId.equals(worklog.getAgentId())) {
            log.warn("Worklog güncelleme reddedildi: Agent (ID: {}) bu worklogun sahibi değil (Owner: {}).",
                    agentId, worklog.getAgentId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.worklog.update.own.only");
        }

        // Kapali bilette worklog degisikligi kabul edilmez.
        Ticket ticket = ticketService.getTicketById(ticketId);
        if ("CLOSED".equals(ticket.getStatus())) {
            log.warn("Worklog güncelleme reddedildi: Bilet CLOSED statüsünde. Bilet ID: {}", ticketId);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "error.worklog.update.ticket.closed");
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
     * Worklog kaydını siler. AGENT_ADMIN her kaydı; AGENT yalnızca kendi
     * oluşturduğu kaydı silebilir; diğer roller erişemez.
     *
     * @param worklogId silinecek kayıt ID
     * @param userId işlemi yapan kullanıcı
     * @param roles kullanıcının rolleri
     * @throws ResponseStatusException 404 yoksa, 403 yetki/sahiplik ihlali
     */
    public void deleteWorklog(Long worklogId, String userId, List<String> roles) {
        log.info("Worklog silme isteği. Worklog ID: {}, Kullanıcı: {}", worklogId, userId);

        TicketWorklog worklog = worklogRepository.findById(worklogId)
                .orElseThrow(() -> {
                    log.warn("Silinmek istenen worklog bulunamadı. ID: {}", worklogId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "error.worklog.not.found");
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
                        "error.worklog.delete.own.only");
            }
        } else {
            log.warn("Worklog silme reddedildi: Yetersiz rol. Kullanıcı: {}", userId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.forbidden");
        }

        worklogRepository.deleteById(worklogId);
        log.info("Worklog başarıyla silindi. ID: {}", worklogId);
    }
}
