package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

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
            log.warn("Bilet oluşturma reddedildi: Müşteri (ID: {}) ürün (ID: {}) için yetkili değil.", customerId, ticket.getProductId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu ürün için destek kaydı oluşturma yetkiniz yok");
        }

        ticket.setCustomerId(customerId);
        ticket.setStatus("NEW");
        
        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Bilet başarıyla oluşturuldu. Bilet ID: {}", savedTicket.getId());
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
        log.info("Kullanıcı (ID: {}) için {} bilet bulundu (Kendi biletleri + Yetkili olduğu ürünler).", userId, tickets.size());
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

        // 2. Biletin sahibi (CUSTOMER) ise her zaman görür (Ajan olsa dahi kendi biletini görebilmeli)
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
     * Yorum veya Dosya ekleme/silme gibi kritik işlemler için 'Sıkı' yetkilendirme kontrolü.
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

        // 2. Eğer kullanıcı AJAN ise, SADECE kendisinin üzerinde (assignee) olan biletlerde işlem yapabilir
        if (roles.contains("AGENT")) {
            if (userId.equals(ticket.getAssigneeId())) {
                log.debug("Atanan ajan için işlem izni verildi.");
                return ticket;
            }
            log.warn("İşlem reddedildi: Bilet ajana atanmamış.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sadece üzerinize atanan biletlerde işlem yapabilirsiniz.");
        }

        // 3. Eğer kullanıcı CUSTOMER ise, SADECE kendi oluşturduğu biletlerde işlem yapabilir
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
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu ürüne ait biletleri üzerinize alma yetkiniz yok.");
        }

        ticket.setAssigneeId(agentId);
        ticket.setStatus("IN_PROGRESS");
        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Bilet başarıyla sahiplenildi. Bilet ID: {}, Yeni Statü: {}", id, savedTicket.getStatus());
        return savedTicket;
    }

    @Transactional
    public Ticket updateTicketStatus(Long id, String newStatus, String userId, List<String> roles) {
        log.info("Statü güncelleme işlemi. Bilet ID: {}, Yeni Statü: {}, Kullanıcı: {}", id, newStatus, userId);
        Ticket ticket = getTicketById(id);

        if (!roles.contains("MANAGER")) {
            User agent = userRepository.findById(userId).orElseThrow();
            boolean isAuthorized = agent.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (!isAuthorized) {
                log.warn("Statü güncelleme reddedildi: Kullanıcı yetkili değil.");
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu bileti güncelleme yetkiniz yok.");
            }
        }

        log.debug("Bilet statüsü güncelleniyor: {} -> {}", ticket.getStatus(), newStatus);
        ticket.setStatus(newStatus);

        // Statüye göre tarihleri güncelle
        if ("RESOLVED".equals(newStatus)) {
            ticket.setResolvedAt(ZonedDateTime.now());
        } else if ("CLOSED".equals(newStatus)) {
            ticket.setClosedAt(ZonedDateTime.now());
        }

        Ticket savedTicket = ticketRepository.save(ticket);
        log.info("Statü başarıyla güncellendi. Bilet ID: {}, Statü: {}", id, savedTicket.getStatus());
        return savedTicket;
    }

    public void deleteTicket(Long id) {
        log.info("Bilet silme işlemi. Bilet ID: {}", id);
        ticketRepository.deleteById(id);
        log.info("Bilet başarıyla silindi. Bilet ID: {}", id);
    }
}