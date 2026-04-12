package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.event.TicketCreatedEvent;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
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

@Log4j2
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final WorkflowService workflowService;
    private final ApplicationEventPublisher eventPublisher;

    // ────────────────────────────────────────────────────────────────────────────
    // Şekil 2 — Ticket State Flow: Geçerli durum geçişleri matrisi
    // ────────────────────────────────────────────────────────────────────────────
    //
    // NEW                  → IN_PROGRESS (Agent Claim)
    // NEW                  → DELETED (Uygunsuz bilet — Agent siler, loglanır)
    // IN_PROGRESS          → NEW (Agent bileti bırakır — Unclaim, loglanır)
    // IN_PROGRESS          → WAITING_FOR_CUSTOMER (Agent bilgi/dosya ister)
    // IN_PROGRESS          → RESOLVED (Agent sorunu çözdüğünü bildirir)
    // IN_PROGRESS          → CLOSED (Agent, customer cevap vermediği için kapatır, loglanır)
    // WAITING_FOR_CUSTOMER → IN_PROGRESS (Customer yanıt verir)
    // RESOLVED             → IN_PROGRESS (Customer sorunun çözülmediğini iletir)
    // RESOLVED             → CLOSED (Customer onaylar veya Agent kapatma kararı verir)
    //
    // CLOSED               → (Hiçbir yere geçemez — terminal durum)
    // ────────────────────────────────────────────────────────────────────────────

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "NEW", Set.of("IN_PROGRESS"),
            "IN_PROGRESS", Set.of("NEW", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"),
            "WAITING_FOR_CUSTOMER", Set.of("IN_PROGRESS"),
            "RESOLVED", Set.of("IN_PROGRESS", "CLOSED"),
            "CLOSED", Set.of() // Terminal durum — geçiş yok
    );

    // SLA duraklatılması gereken durumlar
    private static final Set<String> SLA_PAUSED_STATES = Set.of("WAITING_FOR_CUSTOMER", "RESOLVED");

    // SLA aktif olması gereken durumlar
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

        // Transaction commit'lendikten SONRA workflow başlatılacak (Fix 6: Transaction boundary)
        // WorkflowEventListener.onTicketCreated() metodu tetiklenecek
        eventPublisher.publishEvent(new TicketCreatedEvent(savedTicket));

        return savedTicket;
    }

    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets(String userId, List<String> roles) {
        log.info("Tüm biletleri listeleme işlemi. Kullanıcı: {}, Roller: {}", userId, roles);

        if (roles.contains("MANAGER")) {
            log.debug("Yönetici rolü algılandı, tüm biletler getiriliyor.");
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

        if (roles.contains("MANAGER")) {
            log.debug("Yönetici rolü için tüm NEW biletler getiriliyor.");
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

        // 1. MANAGER ise her şeyi görür
        if (roles.contains("MANAGER")) {
            log.debug("Yönetici yetkisiyle erişim sağlandı.");
            return ticket;
        }

        // 2. Biletin sahibi (CUSTOMER) ise her zaman görür (Ajan olsa dahi kendi
        // biletini görebilmeli)
        if (userId.equals(ticket.getCustomerId())) {
            log.debug("Bilet sahibine (CUSTOMER) erişim sağlandı.");
            return ticket;
        }

        // 3. Eğer AJAN ise, sadece yetkili olduğu ürün grubundaki biletleri görebilir
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
     * Yorum veya Dosya ekleme/silme gibi kritik işlemler için 'Sıkı' yetkilendirme
     * kontrolü.
     */
    @Transactional(readOnly = true)
    public Ticket validateMutationAccess(Long id, String userId, List<String> roles) {
        log.info("Kritik işlem yetki kontrolü (Mutation). Bilet ID: {}, Kullanıcı: {}", id, userId);
        Ticket ticket = getTicketById(id);

        // 1. MANAGER her zaman yetkilidir
        if (roles.contains("MANAGER")) {
            log.debug("Yönetici için işlem izni verildi.");
            return ticket;
        }

        // 2. Eğer kullanıcı AJAN ise, SADECE kendisinin üzerinde (assignee) olan
        // biletlerde işlem yapabilir
        if (roles.contains("AGENT")) {
            if (userId.equals(ticket.getAssigneeId())) {
                log.debug("Atanan ajan için işlem izni verildi.");
                return ticket;
            }
            log.warn("İşlem reddedildi: Bilet ajana atanmamış.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Sadece üzerinize atanan biletlerde işlem yapabilirsiniz.");
        }

        // 3. Eğer kullanıcı CUSTOMER ise, SADECE kendi oluşturduğu biletlerde işlem
        // yapabilir
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

        // jBPM sürecine atama bilgisini senkronize et
        try {
            workflowService.syncTicketAssignment(savedTicket);
        } catch (Exception e) {
            log.error("Workflow atama sync başarısız. TicketId={}, Hata={}", id, e.getMessage());
        }

        return savedTicket;
    }

    /**
     * Katı State Machine mantığıyla statü güncelleme.
     * Şekil 2 Ticket State Flow diyagramındaki oklara uyar.
     * Her geçişte SLA kronometresini uygun şekilde durdurur veya devam ettirir.
     */
    @Transactional
    public Ticket updateTicketStatus(Long id, String newStatus, String userId, List<String> roles) {
        log.info("Statü güncelleme işlemi. Bilet ID: {}, Yeni Statü: {}, Kullanıcı: {}", id, newStatus, userId);
        Ticket ticket = getTicketById(id);

        String oldStatus = ticket.getStatus();

        // ── 1. DURUM GEÇİŞ VALİDASYONU ───────────────────────────────────────────
        validateStateTransition(oldStatus, newStatus);

        // ── 2. YETKİ KONTROLÜ ─────────────────────────────────────────────────────
        validateStatusChangePermission(ticket, oldStatus, newStatus, userId, roles);

        // ── 3. DURUMA ÖZEL İŞ KURALLARI ───────────────────────────────────────────
        applyStatusSpecificRules(ticket, oldStatus, newStatus, userId);

        log.debug("Bilet statüsü güncelleniyor: {} → {}", oldStatus, newStatus);
        ticket.setStatus(newStatus);

        // Statüye göre tarihleri güncelle
        if ("RESOLVED".equals(newStatus)) {
            ticket.setResolvedAt(ZonedDateTime.now());
        } else if ("CLOSED".equals(newStatus)) {
            ticket.setClosedAt(ZonedDateTime.now());
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Statü başarıyla güncellendi. Bilet ID: {}, Statü: {} → {}", id, oldStatus, savedTicket.getStatus());

        // ── 4. jBPM SLA SİNYALLERİ ───────────────────────────────────────────────
        handleWorkflowSignals(savedTicket, oldStatus, newStatus);

        return savedTicket;
    }

    /**
     * Şekil 2'deki oklara göre geçişin geçerli olup olmadığını denetler.
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
     * Durum geçişi için yetki kontrolü.
     */
    private void validateStatusChangePermission(Ticket ticket, String oldStatus, String newStatus,
                                                 String userId, List<String> roles) {
        // MANAGER her şeyi yapabilir
        if (roles.contains("MANAGER")) {
            return;
        }

        // CUSTOMER yetkileri
        if (roles.contains("CUSTOMER")) {
            boolean isOwner = userId.equals(ticket.getCustomerId());
            if (!isOwner) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Sadece kendi biletlerinizin statüsünü değiştirebilirsiniz.");
            }

            // Customer sadece şunları yapabilir:
            // WAITING_FOR_CUSTOMER → IN_PROGRESS (Yanıt vererek)
            // RESOLVED → IN_PROGRESS (Sorun çözülmediğini bildirerek)
            // RESOLVED → CLOSED (Onaylayarak)
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

        // AGENT yetkileri — ürün bazlı kontrol
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
     * Duruma özel iş kuralları.
     * Unclaim, audit logları ve özel geçiş mantıkları burada uygulanır.
     */
    private void applyStatusSpecificRules(Ticket ticket, String oldStatus, String newStatus, String userId) {

        // ── UNCLAIM: IN_PROGRESS → NEW (Agent bileti bırakıyor) ───────────────
        if ("IN_PROGRESS".equals(oldStatus) && "NEW".equals(newStatus)) {
            log.warn("AUDIT LOG: Agent (ID: {}) bileti (ID: {}) bıraktı (Unclaim). Sebep loglanmalı.",
                    userId, ticket.getId());
            ticket.setAssigneeId(null); // Atama kaldırıldı, havuza geri düşürülecek
        }

        // ── AGENT CUSTOMER CEVAP VERMEDİ İÇİN KAPATIYOR ──────────────────────
        if ("IN_PROGRESS".equals(oldStatus) && "CLOSED".equals(newStatus)) {
            log.warn("AUDIT LOG: Agent (ID: {}) müşteri cevap vermediği için bileti (ID: {}) kapatıyor.",
                    userId, ticket.getId());
        }
    }

    /**
     * jBPM workflow sinyallerini durum geçişlerine göre gönderir.
     * SLA kronometresini duraklat / devam ettir / kapat.
     */
    private void handleWorkflowSignals(Ticket ticket, String oldStatus, String newStatus) {
        try {
            // Statüyü jBPM'e senkronize et
            workflowService.syncTicketStatus(ticket);

            // ── SLA DURAKLAT (Aktif → Donuk geçişlerde) ───────────────────────
            if (SLA_ACTIVE_STATES.contains(oldStatus) && SLA_PAUSED_STATES.contains(newStatus)) {
                workflowService.pauseSla(ticket);
                ticketRepository.save(ticket); // slaPausedAt ve slaElapsedMs güncellendi
            }

            // ── SLA DEVAM ETTİR (Donuk → Aktif geçişlerde) ───────────────────
            if (SLA_PAUSED_STATES.contains(oldStatus) && SLA_ACTIVE_STATES.contains(newStatus)) {
                workflowService.resumeSla(ticket);
                ticketRepository.save(ticket); // slaPausedAt temizlendi
            }

            // ── SÜREÇ TAMAMEN KAPAT (CLOSED durumuna geçişte) ─────────────────
            if ("CLOSED".equals(newStatus)) {
                workflowService.closeTicketWorkflow(ticket);
            }

        } catch (Exception e) {
            log.error("Workflow sinyal gönderimi başarısız. TicketId={}, Geçiş={} → {}, Hata={}",
                    ticket.getId(), oldStatus, newStatus, e.getMessage());
        }
    }

    public void deleteTicket(Long id) {
        log.info("Bilet silme işlemi. Bilet ID: {}", id);

        // Silmeden önce workflow sürecini iptal et
        try {
            Ticket ticket = getTicketById(id);
            log.warn("AUDIT LOG: Bilet (ID: {}) siliniyor. Bu eylem loglanmıştır.", id);
            workflowService.abortTicketWorkflow(ticket);
        } catch (Exception e) {
            log.error("Workflow iptal başarısız (ticket silinecek). TicketId={}, Hata={}", id, e.getMessage());
        }

        ticketRepository.deleteById(id);
        log.info("Bilet başarıyla silindi. Bilet ID: {}", id);
    }
}