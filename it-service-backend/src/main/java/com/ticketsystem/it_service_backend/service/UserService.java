package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentCapacityDTO;
import com.ticketsystem.it_service_backend.dto.CreateUserRequest;
import com.ticketsystem.it_service_backend.dto.UserCreationResponseDTO;
import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.exception.UserAlreadyExistsException;
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
    private final KeycloakAdminService keycloakAdminService;

    /**
     * Keycloak'ta yeni bir kullanıcı oluşturur ve yerel veritabanıyla senkronize eder.
     *
     * <p><b>İş akışı:</b>
     * <ol>
     *   <li>Email ve username çakışması ön kontrolü yapılır.</li>
     *   <li>Keycloak'ta kullanıcı oluşturulur, geçici şifre atanır, roller eşlenir.</li>
     *   <li>Yerel {@code users} tablosuna senkronizasyon kaydı atılır.</li>
     * </ol>
     *
     * <p><b>Compensating transaction:</b> Keycloak kaydı başarılı olup yerel DB kaydı
     * başarısız olursa Keycloak'taki kullanıcı silinerek tutarsız durum önlenir.
     * Keycloak harici bir sistem olduğundan tam 2PC mümkün değildir.
     *
     * @param request kullanıcı bilgileri ve atanacak roller
     * @return oluşturulan kullanıcının özet bilgileri
     * @throws UserAlreadyExistsException email veya username zaten mevcutsa
     */
    public UserCreationResponseDTO createUserWithKeycloak(CreateUserRequest request) {
        log.info("Kullanıcı oluşturma işlemi başlatıldı. Username: {}, Email: {}",
                request.getUsername(), request.getEmail());

        // 1. Ön kontrol — Keycloak çağrısından önce çakışmayı yakala
        if (keycloakAdminService.existsByEmail(request.getEmail())) {
            log.warn("Email çakışması tespit edildi: {}", request.getEmail());
            throw new UserAlreadyExistsException("email", request.getEmail());
        }
        if (keycloakAdminService.existsByUsername(request.getUsername())) {
            log.warn("Username çakışması tespit edildi: {}", request.getUsername());
            throw new UserAlreadyExistsException("username", request.getUsername());
        }

        // 2. Keycloak'ta kullanıcı oluştur
        String keycloakId = keycloakAdminService.createUser(request);
        log.info("Keycloak kaydı başarılı. ID: {}", keycloakId);

        // 3. Yerel DB'ye senkronizasyon kaydı — başarısız olursa Keycloak kaydını geri al
        try {
            String resolvedRole = resolveHighestRole(request.getRoles());

            User userToSync = User.builder()
                    .id(keycloakId)
                    .email(request.getEmail())
                    .fullName(request.getFirstName() + " " + request.getLastName())
                    .role(resolvedRole)
                    .build();

            syncUser(userToSync);
            log.info("Yerel DB senkronizasyonu tamamlandı. ID: {}, Rol: {}", keycloakId, resolvedRole);

        } catch (Exception e) {
            log.error("Yerel DB kaydı başarısız! Keycloak compensating action başlatılıyor. ID: {}", keycloakId);
            keycloakAdminService.deleteUser(keycloakId);
            throw new RuntimeException("error.user.creation.db.failed", e);
        }

        return UserCreationResponseDTO.builder()
                .keycloakId(keycloakId)
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFirstName() + " " + request.getLastName())
                .assignedRoles(request.getRoles())
                .build();
    }

    /**
     * Rol listesinden en yüksek öncelikli rolü belirler.
     * Öncelik sırası: AGENT_ADMIN > MANAGER > AGENT > CUSTOMER
     */
    private String resolveHighestRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) return "CUSTOMER";
        if (roles.contains("AGENT_ADMIN")) return "AGENT_ADMIN";
        if (roles.contains("MANAGER"))    return "MANAGER";
        if (roles.contains("AGENT"))      return "AGENT";
        return "CUSTOMER";
    }

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
    public User updatePreferredLanguage(String userId, String lang) {
        if (!"en".equals(lang) && !"tr".equals(lang)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported language code. Supported: en, tr");
        }
        User user = getUserById(userId);
        user.setPreferredLanguage(lang);
        log.info("Kullanıcı dil tercihi güncellendi. ID: {}, Dil: {}", userId, lang);
        return userRepository.save(user);
    }

    @Transactional
    public User removeProductFromUser(String userId, Long productId) {        log.info("Ürün yetki kaldırma işlemi başlatıldı (Service). Kullanıcı: {}, Ürün ID: {}", userId, productId);
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