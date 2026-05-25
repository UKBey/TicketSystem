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

/**
 * User-management flows and synchronization between the local {@code users} table
 * and Keycloak.
 *
 * <p>User creation happens in Keycloak first, then is synchronized to the local DB;
 * if the DB write fails, the Keycloak record is removed via a compensating action.
 * Role/profile/password changes are always written to Keycloak first, then mirrored
 * to the local DB. Activation status (soft-delete) writes {@code enabled/is_active}
 * to both sides.
 */
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
     * Creates a new user in Keycloak and synchronizes it with the local database.
     *
     * <p><b>Workflow:</b>
     * <ol>
     *   <li>Pre-check for email and username conflicts.</li>
     *   <li>Create the Keycloak user, set a temporary password, map roles.</li>
     *   <li>Persist a sync record into the local {@code users} table.</li>
     * </ol>
     *
     * <p><b>Compensating transaction:</b> If the Keycloak record is created but the
     * local DB write fails, the Keycloak user is deleted to avoid an inconsistent
     * state. Because Keycloak is external, true 2PC is not feasible.
     *
     * @param request user details and roles to assign
     * @return summary of the created user
     * @throws UserAlreadyExistsException if email or username already exists
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

        // 2. Keycloak'ta kullanıcı oluştur (rol ataması ayrı adımda yapılır)
        String keycloakId = keycloakAdminService.createUser(request);
        log.info("Keycloak kaydı başarılı. ID: {}", keycloakId);

        // 3. Yerel DB'ye senkronizasyon kaydı — başarısız olursa Keycloak kaydını geri al
        String resolvedRole = null;
        try {
            resolvedRole = resolveHighestRole(request.getRoles());

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
     * Resolves the highest-priority role from a list.
     * Priority order: AGENT_ADMIN > MANAGER > AGENT > CUSTOMER.
     * Returns null when the list is empty or null — CUSTOMER is not assumed as a default.
     */
    private String resolveHighestRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) return null;
        if (roles.contains("AGENT_ADMIN")) return "AGENT_ADMIN";
        if (roles.contains("MANAGER"))    return "MANAGER";
        if (roles.contains("AGENT"))      return "AGENT";
        if (roles.contains("CUSTOMER"))   return "CUSTOMER";
        return null;
    }

    /**
     * Keeps a user in sync with the local DB. If an existing record is found, its
     * email/full-name/role fields are updated; otherwise a new row is written. The
     * role from the JWT is always written through as-is (null clears the local role).
     *
     * @param user user representation to synchronize
     * @return the persisted user
     */
    @Transactional
    public User syncUser(User user) {
        log.info("Kullanıcı senkronizasyon işlemi (Service). ID: {}, Email: {}", user.getId(), user.getEmail());
        
        return userRepository.findById(user.getId()).map(existingUser -> {
            existingUser.setEmail(user.getEmail());
            existingUser.setFullName(user.getFullName());
            // Rol her zaman JWT'deki gerçek durumu yansıtır.
            // null gelirse DB'de de null yazılır — Keycloak'ta rol atanmamış demektir.
            existingUser.setRole(user.getRole());
            log.debug("Mevcut kullanıcı güncellendi. Rol: {}", existingUser.getRole());
            return userRepository.save(existingUser);
        }).orElseGet(() -> {
            log.debug("Yeni kullanıcı eklendi. Rol: {}", user.getRole());
            return userRepository.save(user);
        });
    }

    /**
     * Returns all users with the AGENT role in the local DB.
     *
     * @return list of AGENT users
     */
    public List<User> getAgents() {
        return userRepository.findByRole("AGENT");
    }

    /**
     * Returns every local user record. Caller authorization is enforced at the
     * controller layer.
     *
     * @return all users
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Returns a paginated user list filtered by search text and role.
     * Sorting is always applied as {@code full_name ASC} (native query).
     *
     * @param search name/email LIKE filter (ignored when null/blank)
     * @param roles active role filter (ignored when null/empty)
     * @param page 0-based page index
     * @param size records per page
     * @return paginated result
     */
    @Transactional(readOnly = true)
    public Page<User> getUsersFiltered(String search, java.util.List<String> roles, int page, int size) {
        String searchParam = (search == null || search.isBlank()) ? null : search.trim();
        boolean roleFilterActive = roles != null && !roles.isEmpty();
        java.util.List<String> roleList = roleFilterActive ? roles : java.util.List.of("__none__");
        // Native query kullandığımız için sort field adı SQL column adı olmalı
        Pageable pageable  = PageRequest.of(page, size, Sort.by("full_name").ascending());
        return userRepository.findFiltered(roleFilterActive, roleList, searchParam, pageable);
    }

    /**
     * Returns the user by ID and initializes the {@code authorizedProducts}
     * collection inside the transaction (avoids lazy-loading errors).
     *
     * @param id Keycloak subject (UUID)
     * @return the user (with authorized products eagerly loaded)
     * @throws RuntimeException if the user is not found
     */
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

    /**
     * Adds a product to the user's authorized list. Idempotent — the current
     * state is preserved if the user already has access.
     *
     * @param userId target user ID
     * @param productId product ID to assign
     * @return the updated user
     * @throws RuntimeException if user or product is not found
     */
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

    /**
     * Updates the user's Keycloak profile and synchronizes the local DB.
     * firstName + lastName are concatenated into the {@code users.full_name} column.
     */
    @Transactional
    public User updateProfile(String userId, String firstName, String lastName, String email) {
        log.info("Profil güncelleme işlemi başlatıldı. ID: {}", userId);

        keycloakAdminService.updateUserProfile(userId, firstName, lastName, email);

        User user = getUserById(userId);
        user.setFullName((firstName + " " + lastName).trim());
        user.setEmail(email);
        User saved = userRepository.save(user);

        log.info("Profil başarıyla güncellendi. ID: {}", userId);
        return saved;
    }

    /**
     * Updates the user's preferred language. Only {@code en} and {@code tr} are
     * supported; other values are rejected with 400.
     *
     * @param userId target user ID
     * @param lang language code (en/tr)
     * @return the updated user
     * @throws ResponseStatusException 400 on an unsupported language
     */
    @Transactional
    public User updatePreferredLanguage(String userId, String lang) {
        if (!"en".equals(lang) && !"tr".equals(lang)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported language code. Supported: en, tr");
        }
        User user = getUserById(userId);
        user.setPreferredLanguage(lang);
        log.debug("Kullanıcı dil tercihi güncellendi. ID: {}, Dil: {}", userId, lang);
        return userRepository.save(user);
    }

    /**
     * Updates the user's preferred theme. Only {@code light} and {@code dark} are
     * supported; other values are rejected with 400.
     *
     * @param userId target user ID
     * @param theme theme code (light/dark)
     * @return the updated user
     * @throws ResponseStatusException 400 on an unsupported theme
     */
    @Transactional
    public User updatePreferredTheme(String userId, String theme) {
        if (!"light".equals(theme) && !"dark".equals(theme)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported theme. Supported: light, dark");
        }
        User user = getUserById(userId);
        user.setPreferredTheme(theme);
        log.debug("Kullanıcı tema tercihi güncellendi. ID: {}, Tema: {}", userId, theme);
        return userRepository.save(user);
    }

    /**
     * Removes a product from the user's authorized list. Idempotent — does
     * nothing if the product is not in the list.
     *
     * @param userId target user ID
     * @param productId product ID to remove
     * @return the updated user
     */
    @Transactional
    public User removeProductFromUser(String userId, Long productId) {        log.info("Ürün yetki kaldırma işlemi başlatıldı (Service). Kullanıcı: {}, Ürün ID: {}", userId, productId);
        User user = getUserById(userId);

        log.debug("Kullanıcının ürün listesinden kaldırılıyor. Ürün ID: {}", productId);
        user.getAuthorizedProducts().removeIf(p -> p.getId().equals(productId));
        User savedUser = userRepository.save(user);
        
        log.info("Ürün yetkisi başarıyla kaldırıldı. Kullanıcı ID: {}", userId);
        return savedUser;
    }

    /**
     * Soft-deletes the user (deactivation).
     * Sets {@code enabled=false} in Keycloak and {@code is_active=false} in the local DB.
     * Tickets are left untouched.
     *
     * @param userId Keycloak UUID of the user to deactivate
     * @return the updated user
     */
    @Transactional
    public User deactivateUser(String userId) {
        log.info("Kullanıcı deaktivasyonu başlatıldı. ID: {}", userId);
        User user = getUserById(userId);
        keycloakAdminService.setUserEnabled(userId, false);
        user.setIsActive(false);
        User saved = userRepository.save(user);
        log.info("Kullanıcı deaktive edildi. ID: {}", userId);
        return saved;
    }

    /**
     * Reactivates a previously deactivated user.
     * Sets {@code enabled=true} in Keycloak and {@code is_active=true} in the local DB.
     *
     * @param userId Keycloak UUID of the user to reactivate
     * @return the updated user
     */
    @Transactional
    public User reactivateUser(String userId) {
        log.info("Kullanıcı reaktivasyonu başlatıldı. ID: {}", userId);
        User user = getUserById(userId);
        keycloakAdminService.setUserEnabled(userId, true);
        user.setIsActive(true);
        User saved = userRepository.save(user);
        log.info("Kullanıcı reaktive edildi. ID: {}", userId);
        return saved;
    }

    /**
     * Updates the user's roles in Keycloak and synchronizes the local database.
     *
     * @param userId   Keycloak UUID of the user to update
     * @param newRoles list of new role names to assign
     * @return the updated user record
     */
    @Transactional
    public User updateUserRoles(String userId, List<String> newRoles) {
        log.info("Kullanıcı rol güncelleme işlemi başlatıldı. ID: {}, Yeni roller: {}", userId, newRoles);

        // 1. Kullanıcının yerel DB'de var olduğunu doğrula
        User user = getUserById(userId);

        // 2. Keycloak'ta rolleri güncelle
        keycloakAdminService.updateUserRoles(userId, newRoles);

        // 3. Yerel DB'deki rolü en yüksek öncelikli rolle güncelle
        String resolvedRole = resolveHighestRole(newRoles);
        user.setRole(resolvedRole);
        User savedUser = userRepository.save(user);

        log.info("Kullanıcı rolleri başarıyla güncellendi. ID: {}, Yeni rol: {}", userId, resolvedRole);
        return savedUser;
    }

    /**
     * Returns a live capacity snapshot for every agent authorized on the product.
     *
     * <p>For each agent: the "effective limit" is the product-specific custom limit
     * when present, otherwise the product's default; the current active ticket
     * count is measured and {@code isFull} is computed.
     *
     * @param productId target product ID
     * @return per-agent capacity information
     * @throws EntityNotFoundException if the product is not found
     */
    @Transactional(readOnly = true)
    public List<AgentCapacityDTO> getAgentsWithCapacity(Long productId) {
        log.debug("Agent kapasite listesi istendi. Product ID: {}", productId);
        
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