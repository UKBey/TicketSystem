package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.event.TicketCreatedEvent;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ResolutionNoteRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.entity.Product;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;
import jakarta.persistence.EntityNotFoundException;

@Log4j2
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final WorkflowService workflowService;
    private final ApplicationEventPublisher eventPublisher;
    private final CsatRepository csatRepository;
    private final ResolutionNoteRepository resolutionNoteRepository;
    private final WorklogRepository worklogRepository;
    private final AttachmentRepository attachmentRepository;

    // Durum makinesi: her statuden hangi statulere gecilebilecegini tanimlar.

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "NEW", Set.of("IN_PROGRESS"),
            "IN_PROGRESS", Set.of("NEW", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"),
            "WAITING_FOR_CUSTOMER", Set.of("IN_PROGRESS"),
            "RESOLVED", Set.of("IN_PROGRESS", "CLOSED"),
            "CLOSED", Set.of() // CLOSED son durumdur, buradan cikis yoktur.
    );

    // Bu durumlarda SLA sayaci aktif olarak islemez.
    private static final Set<String> SLA_PAUSED_STATES = Set.of("WAITING_FOR_CUSTOMER", "RESOLVED");

    // Bu durumlarda SLA suresi aktif olarak ilerler.
    private static final Set<String> SLA_ACTIVE_STATES = Set.of("NEW", "IN_PROGRESS");

    @Transactional
    public Ticket createTicket(Ticket ticket, String customerId) {
        log.info("Yeni bilet oluşturma işlemi. Müşteri ID: {}, Ürün ID: {}", customerId, ticket.getProductId());

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> {
                    log.error("Bilet oluşturulurken müşteri bulunamadı. ID: {}", customerId);
                    return new RuntimeException("Kullanıcı bulunamadı: " + customerId);
                });

        boolean isAuthorized = customer.getAuthorizedProducts().stream()
                .anyMatch(product -> product.getId().equals(ticket.getProductId()));

        if (!isAuthorized) {
            log.warn("Bilet oluşturma reddedildi: Müşteri (ID: {}) ürün (ID: {}) için yetkili değil.", customerId,
                    ticket.getProductId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu ürün için destek kaydı oluşturma yetkiniz yok");
        }

        ticket.setCustomerId(customerId);
        ticket.setStatus("NEW");

        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Bilet başarıyla oluşturuldu. Bilet ID: {}", savedTicket.getId());

        // Bilet acilis metnini ilk yorum olarak saklayip gecmisi tek yerde topluyoruz.
        Comment firstComment = Comment.builder()
                .ticket(savedTicket)
                .authorId(customerId)
                .message(savedTicket.getDescription())
                .type("EXTERNAL")
                .build();
        commentRepository.save(firstComment);

        // Event, commit sonrasinda tetiklenir; boylece workflow tarafi kaydedilmis bileti gorur.
        eventPublisher.publishEvent(new TicketCreatedEvent(savedTicket));

        return savedTicket;
    }

    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets(String userId, List<String> roles) {
        log.info("Tüm biletleri listeleme işlemi. Kullanıcı: {}, Roller: {}", userId, roles);

        if (roles.contains("AGENT_ADMIN")) {
            log.debug("Agent admin rolü algılandı, tüm biletler getiriliyor.");
            return ticketRepository.findAll();
        }

        if (userId == null) {
            log.warn("Kullanıcı ID bulunamadı, boş liste dönülüyor.");
            return new ArrayList<>();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Kullanıcı bulunamadı: {}", userId);
                    return new RuntimeException("Kullanıcı bulunamadı: " + userId);
                });

        List<Long> productIds = user.getAuthorizedProducts().stream()
                .map(Product::getId)
                .collect(Collectors.toList());

        List<Ticket> tickets = ticketRepository.findByCustomerIdOrProductIdIn(userId, productIds);
        log.info("Kullanıcı (ID: {}) için {} bilet bulundu (Kendi biletleri + Yetkili olduğu ürünler).", userId,
                tickets.size());
        return tickets;
    }

    public List<Ticket> getCustomerTickets(String customerId) {
        return ticketRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getPoolTickets(String userId, List<String> roles) {
        log.info("Bilet havuzu listeleme işlemi. Kullanıcı: {}, Roller: {}", userId, roles);

        if (roles.contains("AGENT_ADMIN")) {
            log.debug("Agent admin rolü için tüm NEW biletler getiriliyor.");
            return ticketRepository.findByStatus("NEW");
        }

        if (userId == null) {
            return new ArrayList<>();
        }

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Ajan bulunamadı: {}", userId);
                    return new RuntimeException("Kullanıcı bulunamadı: " + userId);
                });

        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId)
                .collect(Collectors.toList());

        if (productIds.isEmpty()) {
            log.warn("Ajanın (ID: {}) atanmış hiçbir ürünü yok, havuz boş dönülüyor.", userId);
            return new ArrayList<>();
        }

        List<Ticket> poolTickets = ticketRepository.findByStatusAndProductIdIn("NEW", productIds);
        log.info("Havuzda ajan (ID: {}) için {} adet uygun bilet listelendi.", userId, poolTickets.size());
        return poolTickets;
    }

    public List<Ticket> getAgentAssignedTickets(String agentId) {
        return ticketRepository.findByAssigneeId(agentId);
    }

    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bilet bulunamadı: " + id));
    }

    @Transactional(readOnly = true)
    public Ticket getTicketWithAuth(Long id, String userId, List<String> roles) {
        log.info("Bilet detayı (yetkili) çekme işlemi. Bilet ID: {}, Kullanıcı: {}", id, userId);
        Ticket ticket = getTicketById(id);

        // Agent admin rolunde urun veya sahiplik siniri olmadan erisim verilir.
        if (roles.contains("AGENT_ADMIN")) {
            log.debug("Agent admin yetkisiyle erişim sağlandı.");
            return ticket;
        }

        // Bilet sahibi her zaman kendi kaydini gorur.
        if (userId.equals(ticket.getCustomerId())) {
            log.debug("Bilet sahibine (CUSTOMER) erişim sağlandı.");
            return ticket;
        }

        // Agent yalnizca yetkili oldugu urun grubuna ait biletleri gorebilir.
        if (roles.contains("AGENT")) {
            User agent = userRepository.findById(userId).orElseThrow();
            boolean isAuthorized = agent.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));

            if (isAuthorized) {
                log.debug("Yetkili ajana (AGENT) erişim sağlandı.");
                return ticket;
            }
        }

        log.warn("Yetkisiz bilet erişim denemesi! Kullanıcı: {}, Bilet ID: {}", userId, id);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu bileti görüntüleme yetkiniz yok.");
    }

    /**
     * Yorum ekleme, dosya yukleme/silme gibi veriyi degistiren islemler icin
     * goruntulemeden daha siki yetki denetimi uygular.
     */
    @Transactional(readOnly = true)
    public Ticket validateMutationAccess(Long id, String userId, List<String> roles) {
        log.info("Kritik işlem yetki kontrolü (Mutation). Bilet ID: {}, Kullanıcı: {}", id, userId);
        Ticket ticket = getTicketById(id);

        // Agent admin degisiklik yapan tum islemlerde dogrudan yetkilidir.
        if (roles.contains("AGENT_ADMIN")) {
            log.debug("Agent admin için işlem izni verildi.");
            return ticket;
        }

        // Agent sadece uzerine atanmis kayitta mutasyon yapabilir.
        if (roles.contains("AGENT")) {
            if (userId.equals(ticket.getAssigneeId())) {
                log.debug("Atanan ajan için işlem izni verildi.");
                return ticket;
            }
            log.warn("İşlem reddedildi: Bilet ajana atanmamış.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sadece üzerinize atanan biletlerde işlem yapabilirsiniz.");
        }

        // Musteri sadece kendi olusturdugu kayitta islem yapabilir.
        if (roles.contains("CUSTOMER")) {
            if (userId.equals(ticket.getCustomerId())) {
                log.debug("Bilet sahibi müşteri için işlem izni verildi.");
                return ticket;
            }
        }

        log.warn("Kritik işlem yetki reddi! Kullanıcı: {}, Bilet ID: {}", userId, id);
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmuyor.");
    }

    @Transactional
    public Ticket claimTicket(Long id, String agentId) {
        log.info("Bilet sahiplenme (claim) işlemi başlatıldı. Bilet ID: {}, Ajan: {}", id, agentId);
        Ticket ticket = getTicketById(id);
        if (!"NEW".equals(ticket.getStatus())) {
            log.warn("Sahiplenme reddedildi: Bilet statüsü NEW değil ({})", ticket.getStatus());
            throw new RuntimeException("Sadece NEW statüsündeki biletler üzerinize alınabilir.");
        }

        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> {
                    log.error("Ajan bulunamadı: {}", agentId);
                    return new RuntimeException("Kullanıcı bulunamadı: " + agentId);
                });

        boolean isAuthorized = agent.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(ticket.getProductId()));

        if (!isAuthorized) {
            log.warn("Sahiplenme reddedildi: Ajan bu ürün grubu için yetkili değil.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu ürüne ait biletleri üzerinize alma yetkiniz yok.");
        }

        String oldStatus = ticket.getStatus();
        ticket.setAssigneeId(agentId);
        ticket.setStatus("IN_PROGRESS");
        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Bilet başarıyla sahiplenildi. Bilet ID: {}, Yeni Statü: {}", id, savedTicket.getStatus());

        // Claim sonrasi atama bilgisini workflow degiskenlerine de yansitir.
        try {
            workflowService.syncTicketAssignment(savedTicket);
        } catch (Exception e) {
            log.error("Workflow atama sync başarısız. TicketId={}, Hata={}", id, e.getMessage());
        }

        return savedTicket;
    }

    /**
     * Durum degisimini tek akista yonetir: gecis dogrulama, yetki denetimi,
     * duruma ozel kurallar ve workflow/SLA senkronizasyonu burada calisir.
     */
    @Transactional
    public Ticket updateTicketStatus(Long id, String newStatus, String userId, List<String> roles) {
        log.info("Statü güncelleme işlemi. Bilet ID: {}, Yeni Statü: {}, Kullanıcı: {}", id, newStatus, userId);
        Ticket ticket = getTicketById(id);

        String oldStatus = ticket.getStatus();

        // Hedef statuye gecis kurallara uygun mu kontrol edilir.
        validateStateTransition(oldStatus, newStatus);

        // Bu gecisi yapan kullanicinin rol ve sahiplik yetkisi denetlenir.
        validateStatusChangePermission(ticket, oldStatus, newStatus, userId, roles);

        // RESOLVED gecisi icin cozum notu zorunludur.
        if ("RESOLVED".equals(newStatus)) {
            boolean hasResolutionNote = resolutionNoteRepository.existsByTicketId(id);
            if (!hasResolutionNote) {
                log.warn("RESOLVED geçişi reddedildi: Çözüm notu bulunamadı. Bilet ID: {}", id);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Bileti çözüldü olarak işaretlemek için önce bir çözüm notu oluşturmalısınız.");
            }
        }

        // Unclaim gibi gecise ozel ek etkiler uygulanir.
        applyStatusSpecificRules(ticket, oldStatus, newStatus, userId);

        log.debug("Bilet statüsü güncelleniyor: {} → {}", oldStatus, newStatus);
        ticket.setStatus(newStatus);

        // Is kapanis cizelgesi icin ilgili zaman damgalari burada set edilir.
        if ("RESOLVED".equals(newStatus)) {
            ticket.setResolvedAt(ZonedDateTime.now());
        } else if ("CLOSED".equals(newStatus)) {
            ticket.setClosedAt(ZonedDateTime.now());
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Statü başarıyla güncellendi. Bilet ID: {}, Statü: {} → {}", id, oldStatus, savedTicket.getStatus());

        // Durum degisiminden sonra workflow ve SLA tarafi senkronize edilir.
        handleWorkflowSignals(savedTicket, oldStatus, newStatus);

        return savedTicket;
    }

    /**
     * Mevcut durumdan hedef duruma gecisin izinli olup olmadigini denetler.
     */
    private void validateStateTransition(String currentStatus, String newStatus) {
        Set<String> allowedTargets = VALID_TRANSITIONS.get(currentStatus);

        if (allowedTargets == null) {
            log.error("Bilinmeyen mevcut statü: {}", currentStatus);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bilinmeyen mevcut durum: " + currentStatus);
        }

        if (!allowedTargets.contains(newStatus)) {
            log.warn("Geçersiz durum geçişi: {} → {} (İzin verilen hedefler: {})",
                    currentStatus, newStatus, allowedTargets);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.format("Geçersiz durum geçişi: %s → %s. İzin verilen geçişler: %s",
                            currentStatus, newStatus, allowedTargets));
        }
    }

    /**
     * Durum degisimini yapan kullanicinin rolu ve kayit iliskisine gore yetkisini kontrol eder.
     */
    private void validateStatusChangePermission(Ticket ticket, String oldStatus, String newStatus,
                                                 String userId, List<String> roles) {
        // Agent admin, durum gecislerinde kisitsiz yetkiye sahiptir.
        if (roles.contains("AGENT_ADMIN")) {
            return;
        }

        // Musteri sadece kendi kaydinda belirli geri donus/onay gecislerini yapabilir.
        if (roles.contains("CUSTOMER")) {
            boolean isOwner = userId.equals(ticket.getCustomerId());
            if (!isOwner) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Sadece kendi biletlerinizin statüsünü değiştirebilirsiniz.");
            }

            // Musteri gecisleri yalnizca yanit verme, yeniden acma ve onayla kapatma ile sinirlidir.
            boolean customerAllowed =
                    ("WAITING_FOR_CUSTOMER".equals(oldStatus) && "IN_PROGRESS".equals(newStatus)) ||
                    ("RESOLVED".equals(oldStatus) && "IN_PROGRESS".equals(newStatus)) ||
                    ("RESOLVED".equals(oldStatus) && "CLOSED".equals(newStatus));

            if (!customerAllowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Müşteri olarak bu durum geçişini yapamazsınız: " + oldStatus + " → " + newStatus);
            }
            return;
        }

        // Agent tarafinda urun bazli yetki dogrulanir.
        if (roles.contains("AGENT")) {
            User agent = userRepository.findById(userId).orElseThrow();
            boolean isAuthorizedForProduct = agent.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));

            if (!isAuthorizedForProduct) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu bileti güncelleme yetkiniz yok.");
            }
            return;
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz bulunmuyor.");
    }

    /**
     * Bazi gecislerde alan guncellemesi ve denetim kaydi gibi ek kurallari uygular.
     */
    private void applyStatusSpecificRules(Ticket ticket, String oldStatus, String newStatus, String userId) {

        // Agent bileti biraktiginda atama temizlenir ve kayit tekrar havuza doner.
        if ("IN_PROGRESS".equals(oldStatus) && "NEW".equals(newStatus)) {
            log.warn("AUDIT LOG: Agent (ID: {}) bileti (ID: {}) bıraktı (Unclaim). Sebep loglanmalı.",
                    userId, ticket.getId());
            ticket.setAssigneeId(null); // Atamayi sifirlayarak havuza geri yollar.
        }

        // Agent tarafindan kapatma gecisinde denetim izi birakilir.
        if ("IN_PROGRESS".equals(oldStatus) && "CLOSED".equals(newStatus)) {
            log.warn("AUDIT LOG: Agent (ID: {}) müşteri cevap vermediği için bileti (ID: {}) kapatıyor.",
                    userId, ticket.getId());
        }
    }

    /**
     * Durum degisimine gore workflow sinyali gonderir ve SLA sayaç davranisini ayarlar.
     */
    private void handleWorkflowSignals(Ticket ticket, String oldStatus, String newStatus) {
        try {
            // Uygulamadaki son statuyu workflow degiskenine yazar.
            workflowService.syncTicketStatus(ticket);

            // Aktiften bekleme/resolve durumuna gecince sayac durdurulur.
            if (SLA_ACTIVE_STATES.contains(oldStatus) && SLA_PAUSED_STATES.contains(newStatus)) {
                workflowService.pauseSla(ticket);
                ticketRepository.save(ticket); // pause islemi ile guncellenen alanlari kalici hale getirir.
            }

            // Beklemeden tekrar aktif duruma gecince sayac kaldigi yerden devam eder.
            if (SLA_PAUSED_STATES.contains(oldStatus) && SLA_ACTIVE_STATES.contains(newStatus)) {
                workflowService.resumeSla(ticket);
                ticketRepository.save(ticket); // resume sonrasi guncel SLA alanlarini kaydeder.
            }

            // Bilet kapaninca ilgili workflow ornegi de sonlandirilir.
            if ("CLOSED".equals(newStatus)) {
                workflowService.closeTicketWorkflow(ticket);
            }

        } catch (Exception e) {
            log.error("Workflow sinyal gönderimi başarısız. TicketId={}, Geçiş={} → {}, Hata={}",
                    ticket.getId(), oldStatus, newStatus, e.getMessage());
        }
    }

    @Transactional
    public void deleteTicket(Long id) {
        log.info("Bilet silme işlemi. Bilet ID: {}", id);

        // Fiziksel silmeden once workflow tarafindaki sureci sonlandirmaya calisir.
        try {
            Ticket ticket = getTicketById(id);
            log.warn("AUDIT LOG: Bilet (ID: {}) siliniyor. Bu eylem loglanmıştır.", id);
            workflowService.abortTicketWorkflow(ticket);
        } catch (Exception e) {
            log.error("Workflow iptal başarısız (ticket silinecek). TicketId={}, Hata={}", id, e.getMessage());
        }

        // Bagli kayitlar elle temizlenir; veritabani butunlugu korunur.
        commentRepository.deleteByTicketId(id);
        csatRepository.deleteByTicketId(id);
        resolutionNoteRepository.deleteByTicketId(id);
        worklogRepository.deleteByTicketId(id);
        attachmentRepository.deleteByTicketId(id);

        ticketRepository.deleteById(id);
        log.info("Bilet başarıyla silindi. Bilet ID: {}", id);
    }


    /**
     * Bilet icin SLA geri sayim bilgisini workflow katmanindan alir.
     */
    public java.util.Map<String, Long> getSlaTimerInfo(Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bilet bulunamadı: " + id));
        return workflowService.getSlaTimerInfo(ticket);
    }

    public java.util.Map<String, Long> getSlaTimerInfo(Ticket ticket) {
        return workflowService.getSlaTimerInfo(ticket);
    }
}
