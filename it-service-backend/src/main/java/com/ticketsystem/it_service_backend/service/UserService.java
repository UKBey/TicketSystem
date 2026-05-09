package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentCapacityDTO;
import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.entity.Product;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final AgentProductLimitRepository agentProductLimitRepository;
    private final TicketClaimRepository ticketClaimRepository;

    @Transactional
    public User syncUser(User user) {
        log.info("Kullanıcı senkronizasyon işlemi (Service). ID: {}, Email: {}", user.getId(), user.getEmail());
        
        return userRepository.findById(user.getId()).map(existingUser -> {
            existingUser.setEmail(user.getEmail());
            existingUser.setFullName(user.getFullName());
            existingUser.setRole(user.getRole());
            log.debug("Mevcut kullanıcı güncellendi. Rol: {}", existingUser.getRole());
            return userRepository.save(existingUser);
        }).orElseGet(() -> {
            log.debug("Yeni kullanıcı eklendi. Rol: {}", user.getRole());
            return userRepository.save(user);
        });
    }

    public List<User> getAgents() {
        return userRepository.findByRole("AGENT");
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<User> getUsersFiltered(String search, String role, int page, int size) {
        String searchParam = (search == null || search.isBlank()) ? null : search.trim();
        String roleParam   = (role   == null || role.isBlank())   ? null : role.trim();
        // Native query kullandığımız için sort field adı SQL column adı olmalı
        Pageable pageable  = PageRequest.of(page, size, Sort.by("full_name").ascending());
        return userRepository.findFiltered(roleParam, searchParam, pageable);
    }

    @Transactional(readOnly = true)
    public User getUserById(String id) {
        log.debug("Kullanıcı verisi çekiliyor. ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Kullanıcı bulunamadı. ID: {}", id);
                    return new RuntimeException("Kullanıcı bulunamadı: " + id);
                });
        
        // Yetkili urun koleksiyonu, session kapanmadan once initialize edilir.
        int productCount = user.getAuthorizedProducts().size();
        log.debug("Kullanıcı çekildi: {}, Yetkili ürün sayısı: {}", user.getFullName(), productCount);
        
        return user;
    }

    @Transactional
    public User assignProductToUser(String userId, Long productId) {
        log.info("Ürün atama işlemi başlatıldı (Service). Kullanıcı: {}, Ürün ID: {}", userId, productId);
        User user = getUserById(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.error("Atanacak ürün bulunamadı. ID: {}", productId);
                    return new RuntimeException("Ürün bulunamadı: " + productId);
                });

        boolean alreadyHasProduct = user.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(productId));

        if (!alreadyHasProduct) {
            log.info("Yeni ürün yetkisi ekleniyor: {}", product.getName());
            user.getAuthorizedProducts().add(product);
            userRepository.save(user);
        } else {
            log.debug("Kullanıcı zaten bu ürüne yetkili. İşlem atlanıyor.");
        }

        return user;
    }

    @Transactional
    public User removeProductFromUser(String userId, Long productId) {
        log.info("Ürün yetki kaldırma işlemi başlatıldı (Service). Kullanıcı: {}, Ürün ID: {}", userId, productId);
        User user = getUserById(userId);

        log.debug("Kullanıcının ürün listesinden kaldırılıyor. Ürün ID: {}", productId);
        user.getAuthorizedProducts().removeIf(p -> p.getId().equals(productId));
        User savedUser = userRepository.save(user);
        
        log.info("Ürün yetkisi başarıyla kaldırıldı. Kullanıcı ID: {}", userId);
        return savedUser;
    }

    @Transactional(readOnly = true)
    public List<AgentCapacityDTO> getAgentsWithCapacity(Long productId) {
        log.info("Agent kapasite listesi istendi. Product ID: {}", productId);
        
        // 1. Belirtilen product'a yetkili tüm agent'ları çek
        List<User> agents = userRepository.findByRoleAndAuthorizedProductsId("AGENT", productId);
        log.debug("Toplam {} agent bulundu", agents.size());
        
        // 2. Product'ı yükle (default limit için)
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));
        
        // 3. Her agent için kapasite bilgisini hesapla
        return agents.stream().map(agent -> {
            // a. Effective limit hesapla (custom override varsa onu, yoksa product default'u)
            Integer effectiveLimit = product.getMaxActiveTickets();
            AgentProductLimit customLimit = agentProductLimitRepository
                    .findByAgentIdAndProductId(agent.getId(), productId)
                    .orElse(null);
            if (customLimit != null && Boolean.TRUE.equals(customLimit.getUseCustomLimit())) {
                effectiveLimit = customLimit.getMaxActiveTickets();
            }
            
            // b. Mevcut aktif bilet sayısını hesapla
            long currentActive = ticketClaimRepository
                    .countActiveTicketsByAgentAndProduct(agent.getId(), productId);
            
            // c. Limit doldu mu kontrol et
            boolean isFull = effectiveLimit != null && currentActive >= effectiveLimit;
            
            log.debug("Agent: {}, Aktif: {}, Limit: {}, Dolu: {}", 
                     agent.getFullName(), currentActive, effectiveLimit, isFull);
            
            return AgentCapacityDTO.builder()
                    .agentId(agent.getId())
                    .agentName(agent.getFullName())
                    .currentActiveTickets(currentActive)
                    .maxLimit(effectiveLimit)
                    .isFull(isFull)
                    .build();
        }).collect(Collectors.toList());
    }
}