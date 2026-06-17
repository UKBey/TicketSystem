package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentCapacityDTO;
import com.ticketsystem.it_service_backend.dto.CreateUserRequest;
import com.ticketsystem.it_service_backend.dto.UserCreationResponseDTO;
import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.DateFormat;
import com.ticketsystem.it_service_backend.entity.Language;
import com.ticketsystem.it_service_backend.entity.Theme;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.exception.UserAlreadyExistsException;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.util.AuthRoles;
import com.ticketsystem.it_service_backend.util.LocalizedText;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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

        // Atanacak rolleri normalize et: geçersizleri ele, lead_agent varsa redundant
        // agent'ı düşür (lead zaten agent'ı kapsar). Hem Keycloak hem DB bunu kullanır.
        request.setRoles(normalizeAssignableRoles(request.getRoles()));

        // 2. Keycloak'ta kullanıcı oluştur (rol ataması ayrı adımda yapılır)
        String keycloakId = keycloakAdminService.createUser(request);
        log.info("Keycloak kaydı başarılı. ID: {}", keycloakId);

        // 3. Yerel DB'ye senkronizasyon kaydı — başarısız olursa Keycloak kaydını geri al
        String resolvedRole = null;
        try {
            java.util.List<String> requestedRoles = request.getRoles() == null ? java.util.List.of()
                    : request.getRoles().stream().map(String::toUpperCase).toList();
            resolvedRole = resolveHighestRole(requestedRoles);

            User userToSync = User.builder()
                    .id(keycloakId)
                    .email(request.getEmail())
                    .fullName(request.getFirstName() + " " + request.getLastName())
                    .role(resolvedRole)
                    .roles(new java.util.HashSet<>(requestedRoles))
                    .build();

            // Yeni kullanıcı → doğrudan insert. (syncUser self-invocation'ı @Transactional
            // proxy'sini atlardı — S2229; ön-kontrol çakışmayı zaten engelledi.)
            userRepository.save(userToSync);
            log.info("Yerel DB senkronizasyonu tamamlandı. ID: {}, Rol: {}", keycloakId, resolvedRole);

        } catch (Exception e) {
            log.error("Yerel DB kaydı başarısız! Keycloak compensating action başlatılıyor. ID: {}", keycloakId);
            keycloakAdminService.deleteUser(keycloakId);
            throw new IllegalStateException("error.user.creation.db.failed", e);
        }

        return UserCreationResponseDTO.builder()
                .keycloakId(keycloakId)
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFirstName() + " " + request.getLastName())
                .assignedRoles(request.getRoles())
                .build();
    }

    /** Uygulama rolleri (Keycloak realm rolleri). */
    public static final java.util.Set<String> APP_ROLES =
            java.util.Set.of(AuthRoles.CUSTOMER, AuthRoles.AGENT, AuthRoles.LEAD_AGENT, AuthRoles.ADMIN, AuthRoles.MANAGER);

    /**
     * Normalizes a requested role list for assignment: upper-cases, keeps only valid
     * {@link #APP_ROLES}, de-duplicates, and drops the redundant {@code AGENT} when
     * {@code LEAD_AGENT} is present — LEAD_AGENT is a Keycloak composite that already
     * includes AGENT, so the two are never assigned together.
     *
     * <p>{@code CUSTOMER} is a <b>singleton</b> role: an end-user who opens tickets can never
     * also be staff (agent/lead/admin/manager). Combining {@code CUSTOMER} with any other role
     * is rejected with {@code 400} so the two identity contexts (customer vs. staff) stay
     * mutually exclusive.
     */
    public static List<String> normalizeAssignableRoles(List<String> roles) {
        if (roles == null) return java.util.List.of();
        List<String> valid = roles.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::toUpperCase)
                .filter(APP_ROLES::contains)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        if (valid.contains(AuthRoles.LEAD_AGENT)) {
            valid.removeIf(AuthRoles.AGENT::equals);
        }
        if (valid.contains(AuthRoles.CUSTOMER) && valid.size() > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.role.customer.exclusive");
        }
        return valid;
    }

    /**
     * Resolves the display/primary role from a list. Used ONLY for the legacy single-role
     * column and the frontend landing default — real authorization uses the full role set
     * (JWT authorities + {@code user_roles}). Priority:
     * ADMIN > MANAGER > LEAD_AGENT > AGENT > CUSTOMER.
     * Returns null when the list is empty or null — CUSTOMER is not assumed as a default.
     */
    public static String resolveHighestRole(List<String> roles) {
        if (roles == null || roles.isEmpty()) return null;
        if (roles.contains(AuthRoles.ADMIN)) return AuthRoles.ADMIN;
        if (roles.contains(AuthRoles.MANAGER))    return AuthRoles.MANAGER;
        if (roles.contains(AuthRoles.LEAD_AGENT)) return AuthRoles.LEAD_AGENT;
        if (roles.contains(AuthRoles.AGENT))      return AuthRoles.AGENT;
        if (roles.contains(AuthRoles.CUSTOMER))   return AuthRoles.CUSTOMER;
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
            // Rol her zaman JWT'deki gerçek durumu yansıtır (hem birincil hem küme).
            // boş gelirse temizlenir — Keycloak'ta rol atanmamış demektir.
            existingUser.setRole(user.getRole());
            existingUser.setRoles(user.getRoles() != null ? user.getRoles() : new java.util.HashSet<>());
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
        return userRepository.findByRole(AuthRoles.AGENT);
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
     * Maps a frontend sort field name to its underlying SQL column. Whitelist-only —
     * the value is interpolated into the native query's ORDER BY by Spring Data, so it
     * must never be raw user input (prevents SQL injection). Unknown fields fall back
     * to {@code full_name}.
     */
    private static String sortColumn(String sortBy) {
        if (sortBy == null) return "full_name";
        return switch (sortBy) {
            case "email"     -> "email";
            case "role"      -> "role";
            case "createdAt" -> "created_at";
            default           -> "full_name"; // "name" ve bilinmeyen alanlar
        };
    }

    /**
     * Returns a paginated user list filtered by search text and role, sorted by the
     * given (whitelisted) column. Defaults to {@code full_name ASC}.
     *
     * @param search name/email LIKE filter (ignored when null/blank)
     * @param roles active role filter (ignored when null/empty)
     * @param productIds authorized-product filter (ignored when null/empty); a user matches
     *                   if they are authorized on ANY of the given products
     * @param sortBy frontend sort field (name|email|role|createdAt); unknown → name
     * @param sortDir {@code "asc"} or {@code "desc"} (anything else → asc)
     * @param page 0-based page index
     * @param size records per page
     * @return paginated result
     */
    @Transactional(readOnly = true)
    public Page<User> getUsersFiltered(String search, java.util.List<String> roles,
                                       boolean excludeGlobalRoles, java.util.List<Long> productIds,
                                       String sortBy, String sortDir, int page, int size) {
        String searchParam = (search == null || search.isBlank()) ? null : search.trim();
        boolean roleFilterActive = roles != null && !roles.isEmpty();
        java.util.List<String> roleList = roleFilterActive ? roles : java.util.List.of("__none__");
        boolean productFilterActive = productIds != null && !productIds.isEmpty();
        // IN (:productIds) boş listeyle geçersiz; filtre kapalıyken sentinel liste gönderilir.
        java.util.List<Long> productList = productFilterActive ? productIds : java.util.List.of(-1L);
        // Native query kullandığımız için sort field adı SQL column adı olmalı (whitelist'ten gelir).
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortColumn(sortBy)));
        return userRepository.findFiltered(roleFilterActive, roleList, searchParam, excludeGlobalRoles,
                productFilterActive, productList, pageable);
    }

    /**
     * Returns the user by ID as an {@link Optional}, without throwing on absence and
     * without initializing lazy collections. Intended for display/side-effect flows
     * (e.g. "send email if the user still exists") that must not fail when the row
     * is missing.
     *
     * @param id Keycloak subject (UUID)
     * @return the user if present, otherwise empty
     */
    @Transactional(readOnly = true)
    public Optional<User> findById(String id) {
        return userRepository.findById(id);
    }

    /**
     * Batch-resolves user IDs to their display names in a single query (no N+1).
     * Null IDs are ignored and unknown IDs are simply absent from the result, so
     * callers decide their own fallback (e.g. "Unknown" or the raw ID).
     *
     * @param userIds user IDs to resolve (nulls/duplicates tolerated)
     * @return map of user ID to {@code fullName} for the IDs that exist
     */
    @Transactional(readOnly = true)
    public Map<String, String> getDisplayNames(Collection<String> userIds) {
        if (userIds == null) return Map.of();
        List<String> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
    }

    /**
     * Batch-resolves user IDs to their {@link User} entities in a single query (no
     * N+1). Use when callers need more than the display name (e.g. name + role).
     * Lazy collections are not initialized — touch only simple columns after return.
     *
     * @param userIds user IDs to resolve (nulls/duplicates tolerated)
     * @return map of user ID to the {@link User} for the IDs that exist
     */
    @Transactional(readOnly = true)
    public Map<String, User> getUsersByIds(Collection<String> userIds) {
        if (userIds == null) return Map.of();
        List<String> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
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
            log.info("Yeni ürün yetkisi ekleniyor: {}", LocalizedText.label(product.getNameTr(), product.getNameEn()));
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
        Language language = Language.fromCode(lang);
        if (language == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported language code. Supported: en, tr");
        }
        User user = getUserById(userId);
        user.setPreferredLanguage(language);
        log.debug("Kullanıcı dil tercihi güncellendi. ID: {}, Dil: {}", userId, language.getCode());
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
        Theme parsedTheme = Theme.fromCode(theme);
        if (parsedTheme == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported theme. Supported: light, dark");
        }
        User user = getUserById(userId);
        user.setPreferredTheme(parsedTheme);
        log.debug("Kullanıcı tema tercihi güncellendi. ID: {}, Tema: {}", userId, parsedTheme.getCode());
        return userRepository.save(user);
    }

    /**
     * Updates the user's preferred date format. Only the known preset keys
     * ({@link DateFormat}) are accepted; other values are rejected with 400.
     *
     * @param userId target user ID
     * @param format date format preset key
     * @return the updated user
     * @throws ResponseStatusException 400 on an unsupported format
     */
    @Transactional
    public User updatePreferredDateFormat(String userId, String format) {
        DateFormat parsedFormat = DateFormat.fromNullable(format);
        if (parsedFormat == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unsupported date format. Supported: " + java.util.Arrays.toString(DateFormat.values()));
        }
        User user = getUserById(userId);
        user.setPreferredDateFormat(parsedFormat);
        log.debug("Kullanıcı tarih formatı tercihi güncellendi. ID: {}, Format: {}", userId, parsedFormat);
        return userRepository.save(user);
    }

    /**
     * Returns the user's last-used PDF export preferences (opaque JSON string defined by
     * the frontend), or {@code null} when the user has never generated a PDF.
     *
     * @param userId target user ID
     * @return the stored preferences string, or null
     */
    @Transactional(readOnly = true)
    public String getPdfExportPreferences(String userId) {
        return getUserById(userId).getPdfExportPreferences();
    }

    /**
     * Persists the user's PDF export preferences. The backend treats the value as an
     * opaque blob (the frontend owns its shape); only the length is bounded by the
     * column ({@code VARCHAR(2000)}), enforced again here.
     *
     * @param userId target user ID
     * @param preferences JSON string from the PDF export modal (max 2000 chars)
     * @return the updated user
     * @throws ResponseStatusException 400 when the payload exceeds the length cap
     */
    @Transactional
    public User updatePdfExportPreferences(String userId, String preferences) {
        if (preferences != null && preferences.length() > 2000) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "PDF preferences payload too large (max 2000 characters)");
        }
        User user = getUserById(userId);
        user.setPdfExportPreferences(preferences);
        log.debug("Kullanıcı PDF tercihleri güncellendi. ID: {}", userId);
        return userRepository.save(user);
    }

    /**
     * Persists the user's sidebar ticket-panel visibility preferences. The backend treats
     * the value as an opaque blob (the frontend owns its shape); only the length is bounded
     * by the column ({@code VARCHAR(500)}), enforced again here.
     *
     * @param userId target user ID
     * @param preferences JSON string from the panel-preferences modal (max 500 chars)
     * @return the updated user
     * @throws ResponseStatusException 400 when the payload exceeds the length cap
     */
    @Transactional
    public User updatePanelPreferences(String userId, String preferences) {
        if (preferences != null && preferences.length() > 500) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Panel preferences payload too large (max 500 characters)");
        }
        User user = getUserById(userId);
        user.setPanelPreferences(preferences);
        log.debug("Kullanıcı panel tercihleri güncellendi. ID: {}", userId);
        return userRepository.save(user);
    }

    /**
     * Marks the user's onboarding as completed. Idempotent — safe to call multiple times.
     *
     * @param userId target user ID
     * @return the updated user
     */
    @Transactional
    public User completeOnboarding(String userId) {
        User user = getUserById(userId);
        user.setOnboardingCompleted(true);
        log.debug("Kullanıcı onboarding tamamlandı olarak işaretlendi. ID: {}", userId);
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
     * @param userId       Keycloak UUID of the user to update
     * @param newRoles     list of new role names to assign
     * @param actingUserId Keycloak UUID of the admin performing the change (the JWT subject)
     * @return the updated user record
     */
    @Transactional
    public User updateUserRoles(String userId, List<String> newRoles, String actingUserId) {
        log.info("Kullanıcı rol güncelleme işlemi başlatıldı. ID: {}, Yeni roller: {}", userId, newRoles);

        // 1. Kullanıcının yerel DB'de var olduğunu doğrula
        User user = getUserById(userId);

        // 1b. Bir admin KENDİ rollerini düzenleyebilir, ancak BAŞKA bir admin'in rollerini
        // bu panelden değiştiremez. (Hedef admin + kendisi değilse reddet.)
        boolean targetIsAdmin = AuthRoles.isAdmin(user.getRoles());
        boolean editingSelf = userId.equals(actingUserId);
        if (targetIsAdmin && !editingSelf) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.user.modify.admin.forbidden");
        }

        // 2. Rolleri normalize et (geçersizleri ele, lead varsa redundant agent'ı düşür),
        //    sonra aynı kümeyi hem Keycloak'ta hem yerel DB'de uygula.
        java.util.List<String> normalized = normalizeAssignableRoles(newRoles);
        keycloakAdminService.updateUserRoles(userId, normalized);

        // 3. Yerel DB'deki rol bilgisini güncelle (hem küme hem birincil/gösterim rolü)
        user.setRoles(new java.util.HashSet<>(normalized));
        String resolvedRole = resolveHighestRole(normalized);
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
        List<User> agents = userRepository.findByRoleAndAuthorizedProductsId(AuthRoles.AGENT, productId);
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