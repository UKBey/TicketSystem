package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.WorklogRequestDTO;
import com.ticketsystem.it_service_backend.dto.WorklogResponseDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketStatus;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import com.ticketsystem.it_service_backend.util.AuthRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Manages time entries (worklogs) recorded against a ticket.
 *
 * <p>Worklog creation/update is restricted to the agent who has claimed the ticket;
 * CLOSED tickets accept neither new entries nor updates. ADMIN bypasses
 * these rules for listing and deletion.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class WorklogService {

    private final WorklogRepository worklogRepository;
    private final TicketService ticketService;
    private final TicketClaimRepository ticketClaimRepository;
    private final UserService userService;

    /** Upper bound for a single worklog entry: 48 hours (in minutes). */
    private static final int MAX_WORKLOG_MINUTES = 2880;

    /**
     * Lets the assigned agent record a duration + description against a ticket.
     * Minutes must be positive, the agent must have claimed the ticket, and the
     * ticket must not be CLOSED.
     *
     * @param ticketId target ticket ID
     * @param dto minutes and description
     * @param agentId agent creating the entry
     * @return the persisted {@link TicketWorklog}
     * @throws ResponseStatusException 400 on minutes/status, 403 if claim is missing
     */
    public TicketWorklog addWorklog(Long ticketId, WorklogRequestDTO dto, String agentId) {
        log.info("Worklog ekleme isteği. Bilet ID: {}, Agent: {}", ticketId, agentId);

        // Gecersiz dakika degeri erken reddedilir.
        if (dto.getMinutes() == null || dto.getMinutes() <= 0) {
            log.warn("Worklog reddedildi: Geçersiz dakika değeri ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.worklog.minutes.positive");
        }
        if (dto.getMinutes() > MAX_WORKLOG_MINUTES) {
            log.warn("Worklog reddedildi: Dakika değeri üst sınırı aştı ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.worklog.minutes.max");
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
        if (ticket.getStatus() == TicketStatus.CLOSED) {
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
     * Lists worklog entries for a ticket subject to role rules.
     * ADMIN sees everything; AGENT sees only worklogs on tickets they have
     * claimed; other roles are denied.
     *
     * @param ticketId target ticket ID
     * @param userId requesting user
     * @param roles role list of the user
     * @return list of worklog entries
     * @throws ResponseStatusException 403 if the user is not authorized
     */
    public List<TicketWorklog> getWorklogsByTicket(Long ticketId, String userId, List<String> roles) {
        log.debug("Worklog listeleme isteği. Bilet ID: {}, Kullanıcı: {}", ticketId, userId);

        // Listeleme oncesi biletin varligi teyit edilir.
        Ticket ticket = ticketService.getTicketById(ticketId);

        // Elevated: ADMIN/MANAGER global, LEAD_AGENT yetkili ürünleri içinde tüm worklogları görür.
        boolean isElevated = AuthRoles.isGlobal(roles) || AuthRoles.isLeadAgent(roles);
        boolean isAgent = roles.contains(AuthRoles.AGENT);

        if (isElevated) {
            // Yükseltilmiş yetkide tum kayitlar listelenebilir.
            log.debug("Yükseltilmiş erişim: Tüm workloglar listeleniyor. Bilet ID: {}", ticketId);
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
     * Returns every worklog entry for the manager screen. Access is enforced
     * at the controller via {@code @PreAuthorize}.
     *
     * @return all worklog entries
     */
    public List<TicketWorklog> getAllWorklogs() {
        log.debug("Tüm worklogları listeleme isteği (Manager).");
        return worklogRepository.findAll();
    }

    // --------------------------------------------------------------------
    // DTO assembly — agent display-name resolution lives here (not in the
    // controller), batch-resolved in a single query to avoid N+1.
    // --------------------------------------------------------------------

    /**
     * Converts a single worklog to a DTO, resolving the agent's display name.
     *
     * @param worklog the worklog to convert
     * @return the DTO (agent name {@code null} when missing)
     */
    @Transactional(readOnly = true)
    public WorklogResponseDTO toDto(TicketWorklog worklog) {
        String name = worklog.getAgentId() == null ? null
                : userService.getDisplayNames(List.of(worklog.getAgentId())).get(worklog.getAgentId());
        return WorklogResponseDTO.fromEntity(worklog, name);
    }

    /**
     * Converts a list of worklogs to DTOs, batch-resolving agent names in a single
     * query (no N+1).
     *
     * @param worklogs worklogs to convert
     * @return DTOs in the same order
     */
    @Transactional(readOnly = true)
    public List<WorklogResponseDTO> toDtos(List<TicketWorklog> worklogs) {
        Map<String, String> nameById = userService.getDisplayNames(
                worklogs.stream().map(TicketWorklog::getAgentId).toList());
        return worklogs.stream()
                .map(w -> WorklogResponseDTO.fromEntity(w,
                        w.getAgentId() == null ? null : nameById.get(w.getAgentId())))
                .toList();
    }

    /**
     * Role-aware worklog listing for a ticket, returned as DTOs. Applies the same
     * authorization as {@link #getWorklogsByTicket} and fills agent display names.
     *
     * @param ticketId target ticket ID
     * @param userId requesting user
     * @param roles role list of the user
     * @return worklog DTOs visible to the caller
     */
    @Transactional(readOnly = true)
    public List<WorklogResponseDTO> getWorklogDtosByTicket(Long ticketId, String userId, List<String> roles) {
        return toDtos(getWorklogsByTicket(ticketId, userId, roles));
    }

    /**
     * All worklogs in the system as DTOs (reporting screen). Caller authorization is
     * enforced at the controller via {@code @PreAuthorize}.
     *
     * @return all worklog DTOs
     */
    @Transactional(readOnly = true)
    public List<WorklogResponseDTO> getAllWorklogDtos() {
        return toDtos(getAllWorklogs());
    }

    /**
     * All worklogs for a ticket as DTOs, with no role-based filtering. Intended for
     * internal/service-to-service consumers (e.g. the LLM context bundle).
     *
     * @param ticketId target ticket ID
     * @return all worklog DTOs for the ticket
     */
    @Transactional(readOnly = true)
    public List<WorklogResponseDTO> getAllWorklogDtosByTicket(Long ticketId) {
        return toDtos(worklogRepository.findByTicketId(ticketId));
    }

    /**
     * Partially updates a worklog entry. Only the creator can change it, the entry
     * must belong to the ticket in the URL, updates on CLOSED tickets are rejected,
     * and minutes (if supplied) must be positive.
     *
     * @param ticketId ticket ID from the URL
     * @param worklogId worklog ID to update
     * @param dto partial new values (minutes/description)
     * @param agentId acting agent
     * @return the updated entry
     * @throws ResponseStatusException 404 if not found, 400 on mismatch/status, 403 if not owner
     */
    public TicketWorklog updateWorklog(Long ticketId, Long worklogId, WorklogRequestDTO dto, String agentId) {
        log.info("Worklog güncelleme isteği. Worklog ID: {}, Agent: {}", worklogId, agentId);

        // Dakika alani gonderildiyse pozitif ve ust sinir icinde olmali.
        if (dto.getMinutes() != null && dto.getMinutes() <= 0) {
            log.warn("Worklog güncelleme reddedildi: Geçersiz dakika değeri ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.worklog.minutes.positive");
        }
        if (dto.getMinutes() != null && dto.getMinutes() > MAX_WORKLOG_MINUTES) {
            log.warn("Worklog güncelleme reddedildi: Dakika değeri üst sınırı aştı ({})", dto.getMinutes());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.worklog.minutes.max");
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
        if (ticket.getStatus() == TicketStatus.CLOSED) {
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
     * Deletes a worklog entry. ADMIN can delete any entry, AGENT can delete
     * only the entries they created, other roles are denied.
     *
     * @param worklogId worklog ID to delete
     * @param userId acting user
     * @param roles role list of the user
     * @throws ResponseStatusException 404 if not found, 403 on authorization/ownership
     */
    public void deleteWorklog(Long worklogId, String userId, List<String> roles) {
        log.info("Worklog silme isteği. Worklog ID: {}, Kullanıcı: {}", worklogId, userId);

        TicketWorklog worklog = worklogRepository.findById(worklogId)
                .orElseThrow(() -> {
                    log.warn("Silinmek istenen worklog bulunamadı. ID: {}", worklogId);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "error.worklog.not.found");
                });

        // Elevated: ADMIN/MANAGER global, LEAD_AGENT yetkili ürünleri içinde her worklogu silebilir.
        boolean isElevated = AuthRoles.isGlobal(roles) || AuthRoles.isLeadAgent(roles);
        boolean isAgent = roles.contains(AuthRoles.AGENT);

        if (isElevated) {
            // Yükseltilmiş yetkide tum worklog kayitlarini silebilir.
            log.debug("Yükseltilmiş yetkiyle worklog silme izni verildi. Worklog ID: {}", worklogId);
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
