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

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Transactional
    public Ticket createTicket(Ticket ticket, String customerId) {
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + customerId));

        boolean isAuthorized = customer.getAuthorizedProducts().stream()
                .anyMatch(product -> product.getId().equals(ticket.getProductId()));

        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu ürün için destek kaydı oluşturma yetkiniz yok");
        }

        ticket.setCustomerId(customerId);
        ticket.setStatus("NEW");
        // İleride SLA Policy tablosundan saati çekip slaDeadline'ı burada
        // hesaplayacağız
        return ticketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getAllTickets(String userId, List<String> roles) {
        if (roles.contains("MANAGER")) {
            return ticketRepository.findAll();
        }

        if (userId == null) {
            return new ArrayList<>();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));

        List<Long> productIds = user.getAuthorizedProducts().stream()
                .map(Product::getId)
                .collect(Collectors.toList());

        // Eğer kullanıcı AJAN ise yetkili ürünleri + kendi biletlerini (CUSTOMER) döner
        // Eğer AJAN değilse sadece kendi biletlerini (CUSTOMER) döner
        return ticketRepository.findByCustomerIdOrProductIdIn(userId, productIds);
    }

    public List<Ticket> getCustomerTickets(String customerId) {
        return ticketRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Ticket> getPoolTickets(String userId, List<String> roles) {
        if (roles.contains("MANAGER")) {
            return ticketRepository.findByStatus("NEW");
        }
        
        if (userId == null) {
            return new ArrayList<>();
        }

        User agent = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));
                
        List<Long> productIds = agent.getAuthorizedProducts().stream()
                .map(Product::getId)
                .collect(Collectors.toList());
                
        if (productIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        return ticketRepository.findByStatusAndProductIdIn("NEW", productIds);
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
        Ticket ticket = getTicketById(id);

        // 1. MANAGER ise her şeyi görür
        if (roles.contains("MANAGER")) {
            return ticket;
        }

        // 2. Biletin sahibi (CUSTOMER) ise her zaman görür (Ajan olsa dahi kendi biletini görebilmeli)
        if (userId.equals(ticket.getCustomerId())) {
            return ticket;
        }

        // 3. Eğer AJAN ise, sadece yetkili olduğu ürün grubundaki biletleri görebilir
        if (roles.contains("AGENT")) {
            User agent = userRepository.findById(userId).orElseThrow();
            boolean isAuthorized = agent.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            
            if (isAuthorized) {
                return ticket;
            }
        }

        // Yukarıdaki şartların hiçbirine uymuyorsa yetkisizdir
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu bileti görüntüleme yetkiniz yok.");
    }

    @Transactional
    public Ticket claimTicket(Long id, String agentId) {
        Ticket ticket = getTicketById(id);
        if (!"NEW".equals(ticket.getStatus())) {
            throw new RuntimeException("Sadece NEW statüsündeki biletler üzerinize alınabilir.");
        }

        User agent = userRepository.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + agentId));
        boolean isAuthorized = agent.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(ticket.getProductId()));
        
        if (!isAuthorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu ürüne ait biletleri üzerinize alma yetkiniz yok.");
        }

        ticket.setAssigneeId(agentId);
        ticket.setStatus("IN_PROGRESS");
        return ticketRepository.save(ticket);
    }

    @Transactional
    public Ticket updateTicketStatus(Long id, String newStatus, String userId, List<String> roles) {
        Ticket ticket = getTicketById(id);

        if (!roles.contains("MANAGER")) {
            User agent = userRepository.findById(userId).orElseThrow();
            boolean isAuthorized = agent.getAuthorizedProducts().stream()
                    .anyMatch(p -> p.getId().equals(ticket.getProductId()));
            if (!isAuthorized) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu bileti güncelleme yetkiniz yok.");
            }
        }

        ticket.setStatus(newStatus);

        // Statüye göre tarihleri güncelle
        if ("RESOLVED".equals(newStatus)) {
            ticket.setResolvedAt(ZonedDateTime.now());
        } else if ("CLOSED".equals(newStatus)) {
            ticket.setClosedAt(ZonedDateTime.now());
        }

        return ticketRepository.save(ticket);
    }

    public void deleteTicket(Long id) {
        ticketRepository.deleteById(id);
    }
}