package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AgentCapacityDTO;
import com.ticketsystem.it_service_backend.dto.ChangePasswordRequest;
import com.ticketsystem.it_service_backend.dto.PanelPreferencesDTO;
import com.ticketsystem.it_service_backend.dto.PdfPreferencesDTO;
import com.ticketsystem.it_service_backend.dto.CreateUserRequest;
import com.ticketsystem.it_service_backend.dto.TotpCredentialDTO;
import com.ticketsystem.it_service_backend.dto.UpdateProfileRequest;
import com.ticketsystem.it_service_backend.dto.UserCreationResponseDTO;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.exception.WrongCurrentPasswordException;
import com.ticketsystem.it_service_backend.service.EmailService;
import com.ticketsystem.it_service_backend.service.KeycloakAdminService;
import com.ticketsystem.it_service_backend.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.annotation.Validated;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.ticketsystem.it_service_backend.dto.UserDTO;
import com.ticketsystem.it_service_backend.util.JwtUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;

/**
 * Main REST controller for user management.
 *
 * <p>Hosts self-service flows (profile, password, 2FA, language/theme preference)
 * and admin operations (user creation, role updates, activate/deactivate, product
 * authorization assignments). Keycloak integration is delegated to
 * {@link KeycloakAdminService} and business rules to {@link UserService}.
 */
@Log4j2
@Tag(name = "Kullanıcı Yönetimi", description = "Keycloak senkronizasyonu, kullanıcı listeleme ve ürün yetki atamaları")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final KeycloakAdminService keycloakAdminService;
    private final EmailService emailService;

    /**
     * Synchronizes the identity claims from the JWT into the local users table after UI login.
     *
     * <p>Creates the user when absent and updates them otherwise; the role priority is
     * {@code ADMIN > MANAGER > LEAD_AGENT > AGENT > CUSTOMER}.
     *
     * @return DTO of the synchronized user
     */
    // UI girisinden sonra kullaniciyi yerel veritabaniyla esitlemek icin cagrilir.
    @Operation(summary = "Kullanıcı senkronizasyonu",
            description = """
                    Frontend giriş sonrası bu endpoint'i çağırarak JWT'deki kullanıcı bilgilerini yerel
                    PostgreSQL veritabanıyla eşitler. Kullanıcı yoksa oluşturulur, varsa bilgileri güncellenir.
                    
                    Rol öncelik sırası: MANAGER > AGENT > CUSTOMER
                    
                    **Senkronize edilen alanlar:** `email`, `fullName`, `role`, `isActive`
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kullanıcı başarıyla senkronize edildi",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @PostMapping("/sync")
    public ResponseEntity<UserDTO> syncCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        
        log.info("Kullanıcı senkronizasyon isteği. Keycloak ID: {}", userId);
        log.debug("Kullanıcı rolleri: {}", roles);

        // JWT'deki uygulama rollerini (sistem rolleri hariç) küme olarak al. Birincil/gösterim
        // rolü bundan türetilir; gerçek yetkilendirme zaten JWT authority'lerinden gelir.
        // Rol yoksa null — "CUSTOMER" varsayılanı yok; kullanıcı no-role sayfasında kalır.
        java.util.Set<String> appRoles = roles.stream()
                .map(String::toUpperCase)
                .filter(UserService.APP_ROLES::contains)
                .collect(Collectors.toSet());
        String assignedRole = UserService.resolveHighestRole(new java.util.ArrayList<>(appRoles));

        User user = User.builder()
                .id(userId)
                .email(jwt.getClaimAsString("email"))
                .fullName(buildFullName(jwt))
                .role(assignedRole)
                .roles(appRoles)
                .build();
        
        // syncUser bir upsert'tir (findById → yoksa insert). Ilk login'de aynı kullanıcı için
        // gelen iki eşzamanlı /sync isteği ikisi de "yok" görüp insert deneyebilir; biri PK
        // çakışmasıyla DataIntegrityViolationException alır. O işlemin transaction'ı geri
        // alındıktan sonra tek seferlik retry artık update yoluna düşer (satır artık vardır).
        User syncedUser;
        try {
            syncedUser = userService.syncUser(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("Eşzamanlı /sync yarışı tespit edildi (ID: {}), update olarak yeniden deneniyor.", userId);
            syncedUser = userService.syncUser(user);
        }

        log.info("Kullanıcı başarıyla senkronize edildi. Uygulama İçi Roller: {}", syncedUser.getRole());

        return ResponseEntity.ok(UserDTO.fromEntity(syncedUser));
    }

    /**
     * Builds the full name from the JWT. When {@code given_name} or {@code family_name}
     * is null, falls back to {@code preferred_username}.
     */
    private String buildFullName(Jwt jwt) {
        String given  = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        if (given != null && family != null) {
            return (given + " " + family).trim();
        }
        if (given != null) return given.trim();
        if (family != null) return family.trim();
        // Fallback: preferred_username veya email
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null) return username;
        String email = jwt.getClaimAsString("email");
        return email != null ? email : "Unknown";
    }

    /**
     * Returns all users in the {@code AGENT} role together with the products they are authorized for.
     *
     * @return list of agent user DTOs
     */
    @Operation(summary = "Tüm ajanları listele",
            description = "Sistemdeki `AGENT` rolündeki tüm kullanıcıları yetkili oldukları ürün bilgileriyle birlikte getirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajan listesi başarıyla döndü"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @GetMapping("/agents")
    public ResponseEntity<List<UserDTO>> getAgents() {
        log.debug("Tüm ajanları listeleme isteği.");

        List<User> agents = userService.getAgents();

        log.debug("Toplam {} ajan listelendi.", agents.size());

        return ResponseEntity.ok(agents.stream()
                .map(UserDTO::fromEntity)
                .toList());
    }

    /**
     * Returns the agents authorized for the given product, with their active ticket count and limit.
     *
     * <p>The agent selection list of the assignment UI is populated from this endpoint.
     *
     * @param productId product identifier
     * @return list of agent DTOs including capacity information
     */
    @Operation(summary = "Agent'ları kapasite bilgileriyle listele",
            description = "Belirtilen ürün için yetkili agent'ları, mevcut aktif bilet sayıları ve limitleriyle birlikte döner. Atama UI'ı için kullanılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent kapasite listesi başarıyla döndü"),
            @ApiResponse(responseCode = "403", description = "Yalnızca LEAD_AGENT veya ADMIN erişebilir")
    })
    @GetMapping("/agents/capacity")
    @PreAuthorize("hasAnyRole('LEAD_AGENT', 'ADMIN')")
    public ResponseEntity<List<AgentCapacityDTO>> getAgentsWithCapacity(
            @Parameter(description = "Ürün ID'si", required = true)
            @RequestParam Long productId) {
        log.debug("Agent kapasite listesi isteği. Product: {}", productId);

        List<AgentCapacityDTO> agents = userService.getAgentsWithCapacity(productId);

        log.debug("Toplam {} agent kapasite bilgisiyle döndü.", agents.size());
        return ResponseEntity.ok(agents);
    }

    /**
     * Returns detailed information about the user with the given Keycloak identifier.
     *
     * @param id Keycloak user identifier (UUID)
     * @return user DTO
     */
    @Operation(summary = "Kullanıcı detayı getir",
            description = "Belirtilen Keycloak ID'ye sahip kullanıcının detaylı bilgilerini döner.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kullanıcı detayı başarıyla döndü",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(
            @Parameter(description = "Keycloak kullanıcı ID'si (UUID)", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890", required = true)
            @PathVariable String id) {
        log.debug("Kullanıcı detayı isteği. Kullanıcı ID: {}", id);

        User user = userService.getUserById(id);

        log.debug("Kullanıcı detayı çekildi: {} ({})", user.getFullName(), user.getRole());

        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    /**
     * Lists users paginated, with optional name/email search and role filters.
     *
     * @param search free-text filter over name or email
     * @param role role filter (multiple)
     * @param page page index (0-based)
     * @param size page size (1-500)
     * @return map containing the {@code content}, {@code totalElements}, {@code totalPages}, {@code page} and {@code size} fields
     */
    @Operation(summary = "Tüm kullanıcıları listele (sayfalı + filtreli)",
            description = "Sistemdeki kullanıcıları isim/email araması ve rol filtresiyle sayfalı olarak getirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kullanıcı listesi başarıyla döndü"),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN veya MANAGER erişebilir")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> role,
            @RequestParam(defaultValue = "false") boolean excludeGlobalRoles,
            @RequestParam(required = false) List<Long> productId,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc")  String sortDir,
            @RequestParam(defaultValue = "0")  @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        log.debug("Kullanıcı listeleme isteği. search={}, roles={}, excludeGlobalRoles={}, productId={}, sortBy={}, sortDir={}, page={}, size={}",
                search, role, excludeGlobalRoles, productId, sortBy, sortDir, page, size);

        Page<User> userPage = userService.getUsersFiltered(search, role, excludeGlobalRoles, productId, sortBy, sortDir, page, size);

        log.debug("Toplam {} kullanıcı döndü (sayfa {}/{})", userPage.getNumberOfElements(), page, userPage.getTotalPages());

        return ResponseEntity.ok(Map.of(
                "content",       userPage.getContent().stream().map(UserDTO::fromEntity).toList(),
                "totalElements", userPage.getTotalElements(),
                "totalPages",    userPage.getTotalPages(),
                "page",          userPage.getNumber(),
                "size",          userPage.getSize()
        ));
    }

    /**
     * Updates the authenticated user's first name, last name and email in Keycloak and the local DB.
     *
     * @param request new first name, last name and email
     * @return DTO of the updated user
     */
    @Operation(summary = "Profil bilgilerini güncelle",
            description = """
                    Oturum açan kullanıcının ad, soyad ve e-posta bilgilerini Keycloak üzerinde günceller
                    ve yerel veritabanını senkronize eder. Email değişirse Keycloak'ta `emailVerified`
                    false'a çekilir, sonraki girişte doğrulama akışı tetiklenebilir.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Validasyon hatası"),
            @ApiResponse(responseCode = "409", description = "Bu email başka bir kullanıcıda kayıtlı")
    })
    @PutMapping("/me")
    public ResponseEntity<UserDTO> updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateProfileRequest request) {
        String userId = jwt.getSubject();
        log.info("Profil güncelleme isteği. Kullanıcı: {}", userId);
        User updated = userService.updateProfile(userId, request.getFirstName(), request.getLastName(), request.getEmail());
        return ResponseEntity.ok(UserDTO.fromEntity(updated));
    }

    /**
     * Changes the authenticated user's password in Keycloak; the current password is verified via direct-grant.
     *
     * @param request current and new password
     * @return {@code 204 No Content}
     * @throws WrongCurrentPasswordException if the current password is wrong
     */
    @Operation(summary = "Şifreyi değiştir",
            description = """
                    Oturum açan kullanıcının şifresini değiştirir. Önce mevcut şifre Keycloak token
                    endpoint'ine direct-grant ile doğrulanır; başarısız olursa 400 döner.
                    Doğrulama başarılıysa yeni şifre Keycloak Admin API üzerinden atanır. Realm
                    şifre politikası ihlal edilirse de 400 döner (`newPassword` field error ile).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Şifre başarıyla değiştirildi"),
            @ApiResponse(responseCode = "400", description = "Mevcut şifre yanlış veya yeni şifre politikaya uymuyor"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @PostMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @org.springframework.web.bind.annotation.RequestBody ChangePasswordRequest request) {
        String userId   = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        log.info("Şifre değiştirme isteği. Kullanıcı: {}", userId);

        if (username == null || !keycloakAdminService.verifyPassword(username, request.getCurrentPassword())) {
            throw new WrongCurrentPasswordException();
        }
        keycloakAdminService.changeUserPassword(userId, request.getNewPassword());

        // Şifre değişti — kullanıcıya bildirim maili gönder.
        // Kullanıcı oturum açmış olduğu için DB'deki dil/tema tercihleri güncel kabul edilir.
        userService.findById(userId).ifPresent(user ->
                emailService.sendPasswordChangedEmail(user, null, null));

        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the authenticated user's registered TOTP devices.
     *
     * @return list of device DTOs (id, label, created timestamp)
     */
    @Operation(summary = "2FA cihazlarını listele",
            description = """
                    Oturum açan kullanıcının kayıtlı TOTP (authenticator app) cihazlarını döner.
                    Frontend tarafında 2FA yönetim modal'ında listelenir.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "2FA cihaz listesi başarıyla döndü")
    })
    @GetMapping("/me/2fa")
    public ResponseEntity<List<TotpCredentialDTO>> listMyTotpDevices(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.debug("2FA cihazları listeleniyor. Kullanıcı: {}", userId);

        List<TotpCredentialDTO> devices = keycloakAdminService.listOtpCredentials(userId).stream()
                .map(c -> TotpCredentialDTO.builder()
                        .id(c.getId())
                        .userLabel(c.getUserLabel())
                        .createdDate(c.getCreatedDate())
                        .build())
                .toList();
        return ResponseEntity.ok(devices);
    }

    /**
     * Deletes a specific TOTP device of the authenticated user and sends a notification email.
     *
     * @param credentialId Keycloak credential identifier
     * @return {@code 204 No Content}
     */
    @Operation(summary = "2FA cihazı sil",
            description = """
                    Oturum açan kullanıcının belirli bir TOTP cihazını siler. Sonraki girişte
                    bu cihaz authenticator olarak kabul edilmez.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cihaz başarıyla silindi"),
            @ApiResponse(responseCode = "404", description = "Cihaz bulunamadı")
    })
    @DeleteMapping("/me/2fa/{credentialId}")
    public ResponseEntity<Void> deleteMyTotpDevice(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String credentialId) {
        String userId = jwt.getSubject();
        log.info("2FA cihazı silme isteği. Kullanıcı: {}, CredentialID: {}", userId, credentialId);

        // Silmeden önce cihaz etiketini yakala — silme sonrası bildirim mailinde geçecek.
        String deviceLabel = keycloakAdminService.listOtpCredentials(userId).stream()
                .filter(c -> credentialId.equals(c.getId()))
                .map(c -> c.getUserLabel())
                .findFirst()
                .orElse(null);

        keycloakAdminService.removeCredential(userId, credentialId);

        userService.findById(userId).ifPresent(user ->
                emailService.send2FADeviceRemovedEmail(user, deviceLabel));

        return ResponseEntity.noContent().build();
    }

    /**
     * Called by the frontend when the Keycloak {@code CONFIGURE_TOTP} flow completes;
     * triggers a notification email for the most recently added TOTP device.
     *
     * @return {@code 204 No Content}
     */
    @Operation(summary = "2FA cihaz eklendi bildirimi",
            description = """
                    Keycloak CONFIGURE_TOTP akışı tamamlandığında frontend bu endpoint'i
                    çağırır. Kullanıcının en son eklenen TOTP cihazı bulunup "cihaz eklendi"
                    bildirim maili gönderilir. Idempotency: endpoint zaten son cihaza göre
                    çalıştığı için yan yana çağrılarsa birden fazla mail tetiklenebilir; UI
                    bunu yalnızca `kc_action_status=success` sinyaliyle çağırmalı.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bildirim mailı kuyruğa alındı"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @PostMapping("/me/2fa/notify-added")
    public ResponseEntity<Void> notifyTotpDeviceAdded(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        log.info("2FA cihazı ekleme bildirimi istendi. Kullanıcı: {}", userId);

        String latestLabel = keycloakAdminService.listOtpCredentials(userId).stream()
                .max(java.util.Comparator.comparing(
                        c -> c.getCreatedDate() == null ? 0L : c.getCreatedDate()))
                .map(c -> c.getUserLabel())
                .orElse(null);

        userService.findById(userId).ifPresent(user ->
                emailService.send2FADeviceAddedEmail(user, latestLabel));

        return ResponseEntity.noContent().build();
    }

    /**
     * Updates the user's preferred UI/email language.
     *
     * @param lang language code ({@code en} or {@code tr})
     * @return DTO of the updated user
     */
    @Operation(summary = "Kullanıcı dil tercihini güncelle",
            description = "Kullanıcının tercih ettiği dili günceller. Desteklenen değerler: `en`, `tr`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dil tercihi başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz dil kodu"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @PutMapping("/me/language")
    public ResponseEntity<UserDTO> updateLanguage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String lang) {
        String userId = jwt.getSubject();
        log.debug("Dil tercihi güncelleme isteği. Kullanıcı: {}, Dil: {}", userId, lang);
        User user = userService.updatePreferredLanguage(userId, lang);
        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    /**
     * Updates the user's UI/email theme preference.
     *
     * @param theme theme value ({@code light} or {@code dark})
     * @return DTO of the updated user
     */
    @Operation(summary = "Kullanıcı tema tercihini güncelle",
            description = "Kullanıcının arayüz ve mail temasını günceller. Desteklenen değerler: `light`, `dark`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tema tercihi başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz tema değeri"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @PutMapping("/me/theme")
    public ResponseEntity<UserDTO> updateTheme(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String theme) {
        String userId = jwt.getSubject();
        log.debug("Tema tercihi güncelleme isteği. Kullanıcı: {}, Tema: {}", userId, theme);
        User user = userService.updatePreferredTheme(userId, theme);
        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    /**
     * Updates the user's preferred date display format.
     *
     * @param format date format preset key
     *               ({@code DMY_SLASH/MDY_SLASH/YMD_DASH/DMY_DOT/MED})
     * @return DTO of the updated user
     */
    @Operation(summary = "Kullanıcı tarih formatı tercihini güncelle",
            description = "Arayüzdeki tüm tarih gösterimleri için tek-tip formatı günceller. "
                    + "Desteklenen değerler: `DMY_SLASH`, `MDY_SLASH`, `YMD_DASH`, `DMY_DOT`, `MED`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tarih formatı tercihi başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz format değeri"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @PutMapping("/me/date-format")
    public ResponseEntity<UserDTO> updateDateFormat(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String format) {
        String userId = jwt.getSubject();
        log.debug("Tarih formatı tercihi güncelleme isteği. Kullanıcı: {}, Format: {}", userId, format);
        User user = userService.updatePreferredDateFormat(userId, format);
        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    /**
     * Returns the caller's last-used PDF export modal selections (sections, language,
     * theme) as an opaque JSON string, or null when none have been saved yet.
     *
     * @return DTO wrapping the stored preferences string (nullable)
     */
    @Operation(summary = "PDF dışa aktarma tercihlerini getir",
            description = "Kullanıcının PDF modalında en son kullandığı seçimleri (opak JSON string) döner; hiç kaydı yoksa null.")
    @GetMapping("/me/pdf-preferences")
    public ResponseEntity<PdfPreferencesDTO> getPdfPreferences(@AuthenticationPrincipal Jwt jwt) {
        PdfPreferencesDTO dto = new PdfPreferencesDTO();
        dto.setPreferences(userService.getPdfExportPreferences(jwt.getSubject()));
        return ResponseEntity.ok(dto);
    }

    /**
     * Persists the caller's PDF export modal selections. The value is stored verbatim as
     * an opaque JSON string (the frontend owns its shape); only the length is validated.
     *
     * @param body wrapper carrying the preferences JSON string (max 2000 chars)
     * @return {@code 204 No Content}
     */
    @Operation(summary = "PDF dışa aktarma tercihlerini güncelle",
            description = "Kullanıcının PDF modalındaki son seçimlerini saklar. Değer opak bir JSON string olarak tutulur.")
    @PutMapping("/me/pdf-preferences")
    public ResponseEntity<Void> updatePdfPreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @org.springframework.web.bind.annotation.RequestBody PdfPreferencesDTO body) {
        userService.updatePdfExportPreferences(jwt.getSubject(), body.getPreferences());
        return ResponseEntity.noContent().build();
    }

    /**
     * Persists the caller's sidebar ticket-panel visibility selections (workspace, pool,
     * history, team, all-tickets). The value is stored verbatim as an opaque JSON string
     * (the frontend owns its shape); only the length is validated. Hydrated back to the
     * client via {@code /users/sync} ({@link UserDTO#getPanelPreferences()}).
     *
     * @param body wrapper carrying the preferences JSON string (max 500 chars)
     * @return {@code 204 No Content}
     */
    @Operation(summary = "Ticket panel görünürlük tercihlerini güncelle",
            description = "Agent/lead kullanıcının sol menüdeki ticket panellerinin görünürlüğünü saklar. "
                    + "Değer opak bir JSON string olarak tutulur.")
    @PutMapping("/me/panel-preferences")
    public ResponseEntity<Void> updatePanelPreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @org.springframework.web.bind.annotation.RequestBody PanelPreferencesDTO body) {
        userService.updatePanelPreferences(jwt.getSubject(), body.getPreferences());
        return ResponseEntity.noContent().build();
    }

    /**
     * Marks the authenticated user's onboarding as completed. Idempotent.
     *
     * @return {@code 204 No Content}
     */
    @Operation(summary = "Onboarding'i tamamla",
            description = "Kullanıcının onboarding akışını gördüğünü ve tamamladığını işaretler. İdempotent.")
    @PutMapping("/me/onboarding-complete")
    public ResponseEntity<Void> completeOnboarding(@AuthenticationPrincipal Jwt jwt) {
        userService.completeOnboarding(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    /**
     * Grants the agent support authorization for the specified product (the {@code authorizedProducts} list is updated).
     *
     * @param userId Keycloak identifier of the target agent
     * @param productId identifier of the product to assign
     * @return DTO of the updated agent
     */
    @Operation(summary = "Ajana ürün ata",
            description = "Belirtilen ajana belirtilen ürün grubunun destek taleplerini görebilme ve sahiplenme yetkisi verir. "
                    + "Atama sonrası ajanın `authorizedProducts` listesi güncellenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün yetkisi başarıyla atandı",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN yetki atayabilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı veya ürün bulunamadı")
    })
    @PostMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> assignProductToUser(
            @Parameter(description = "Ajanın Keycloak ID'si", example = "f9e8d7c6-b5a4-3210-fedc-ba0987654321", required = true)
            @PathVariable String userId,
            @Parameter(description = "Atanacak ürünün ID'si", example = "1", required = true)
            @PathVariable Long productId) {
        log.info("Ajan-Ürün atama isteği. Ajan: {}, Ürün: {}", userId, productId);

        User user = userService.assignProductToUser(userId, productId);
        
        log.info("Ürün başarıyla ajana atandı. Ajan: {}", userId);

        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    /**
     * Revokes the agent's support authorization for the specified product.
     *
     * @param userId Keycloak identifier of the target agent
     * @param productId identifier of the product to remove authorization for
     * @return DTO of the updated agent
     */
    @Operation(summary = "Ajandan ürün yetkisi kaldır",
            description = "Ajanın belirtilen ürün grubu üzerindeki destek yetkisini iptal eder. "
                    + "Bu işlemden sonra ajan o ürüne ait yeni biletleri göremez ve sahiplenemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün yetkisi başarıyla kaldırıldı",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN yetki kaldırabilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı veya ürün bulunamadı")
    })
    @DeleteMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> removeProductFromUser(
            @Parameter(description = "Ajanın Keycloak ID'si", example = "f9e8d7c6-b5a4-3210-fedc-ba0987654321", required = true)
            @PathVariable String userId,
            @Parameter(description = "Kaldırılacak ürünün ID'si", example = "1", required = true)
            @PathVariable Long productId) {
        log.info("Ajan-Ürün yetki kaldırma isteği. Ajan: {}, Ürün: {}", userId, productId);

        User user = userService.removeProductFromUser(userId, productId);
        
        log.info("Ürün yetkisi ajandan başarıyla kaldırıldı. Ajan: {}", userId);

        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    // -------------------------------------------------------------------------
    // Admin — User creation & role management
    // -------------------------------------------------------------------------

    /**
     * Soft-deletes (deactivates) or reactivates the user; an admin cannot deactivate themselves.
     *
     * @param userId Keycloak identifier of the target user
     * @param active {@code true}: reactivate, {@code false}: deactivate
     * @return DTO of the updated user; {@code 400} if the admin tries to deactivate themselves
     */
    @Operation(
            summary = "Kullanıcı aktif/pasif durumunu güncelle (Admin)",
            description = """
                    Kullanıcıyı soft-delete ile deaktive eder veya yeniden aktive eder.
                    Deaktive edilen kullanıcı Keycloak'ta disabled olur (login yapamaz).
                    Ticket'lara dokunulmaz.

                    **Yetki:** Yalnızca `ADMIN` rolüne sahip kullanıcılar erişebilir.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            tags = {"Kullanıcı Yönetimi"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Durum başarıyla güncellendi",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN erişebilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> updateUserStatus(
            @Parameter(description = "Keycloak kullanıcı ID'si (UUID)", required = true)
            @PathVariable String userId,
            @RequestParam boolean active,
            @AuthenticationPrincipal Jwt jwt) {
        // Admin kendini deaktive edemez
        if (userId.equals(jwt.getSubject())) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Kullanıcı durum güncelleme isteği. ID: {}, active: {}", userId, active);
        User updated = active
                ? userService.reactivateUser(userId)
                : userService.deactivateUser(userId);
        return ResponseEntity.ok(UserDTO.fromEntity(updated));
    }

    /**
     * Replaces the user's Keycloak realm roles; existing roles are removed first.
     *
     * @param userId Keycloak identifier of the target user
     * @param roles list of roles to assign (cannot be empty)
     * @return DTO of the updated user; {@code 400} if the list is empty
     */
    @Operation(
            summary = "Kullanıcı rollerini güncelle (Admin)",
            description = """
                    Belirtilen kullanıcının Keycloak realm rollerini günceller ve yerel veritabanını senkronize eder.
                    Mevcut roller kaldırılır, yeni roller atanır.

                    **Yetki:** Yalnızca `ADMIN` rolüne sahip kullanıcılar erişebilir.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            tags = {"Kullanıcı Yönetimi"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roller başarıyla güncellendi",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek — roller listesi boş olamaz"),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN erişebilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> updateUserRoles(
            @Parameter(description = "Keycloak kullanıcı ID'si (UUID)", required = true)
            @PathVariable String userId,
            @org.springframework.web.bind.annotation.RequestBody List<String> roles,
            @AuthenticationPrincipal Jwt jwt) {
        log.info("Kullanıcı rol güncelleme isteği. Kullanıcı: {}, Roller: {}", userId, roles);

        if (roles == null || roles.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        User updatedUser = userService.updateUserRoles(userId, roles, jwt.getSubject());

        log.info("Kullanıcı rolleri başarıyla güncellendi. ID: {}", userId);
        return ResponseEntity.ok(UserDTO.fromEntity(updatedUser));
    }

    /**
     * Creates a new user in the Keycloak realm, sets a temporary password and syncs the local DB.
     *
     * @param request user information, temporary password and roles to assign
     * @return response containing the created user's information ({@code 201 Created})
     */
    @Operation(
            summary = "Yeni kullanıcı oluştur (Admin)",
            description = """
                    Keycloak realm'inde yeni bir kullanıcı oluşturur, geçici şifre atar ve
                    seçilen rolleri eşler. Başarılı Keycloak kaydının ardından yerel veritabanına
                    senkronizasyon kaydı atılır.

                    **Yetki:** Yalnızca `ADMIN` rolüne sahip kullanıcılar erişebilir.
                    
                    **Geçici şifre:** Oluşturulan kullanıcı ilk girişinde şifresini değiştirmek zorunda kalır.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            tags = {"Kullanıcı Yönetimi"}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Kullanıcı başarıyla oluşturuldu",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserCreationResponseDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validasyon hatası — eksik veya geçersiz alan",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(example = """
                                    {
                                      "status": 400,
                                      "message": "Validation failed",
                                      "fieldErrors": {
                                        "username": "Bu alan boş bırakılamaz.",
                                        "password": "Şifre en az 8 karakter olmalıdır.",
                                        "roles": "Bu alan zorunludur."
                                      },
                                      "timestamp": 1700000000000
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Yalnızca ADMIN erişebilir"
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email veya kullanıcı adı zaten kullanımda",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(example = """
                                    {
                                      "status": 409,
                                      "error": "USER_ALREADY_EXISTS",
                                      "message": "Bu email ile kayıtlı bir kullanıcı zaten mevcut",
                                      "fieldErrors": { "email": "Bu e-posta adresi zaten kullanımda." },
                                      "timestamp": 1700000000000
                                    }
                                    """)
                    )
            )
    })
    @PostMapping("/admin/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserCreationResponseDTO> createUser(
            @RequestBody(
                    description = "Oluşturulacak kullanıcının bilgileri ve atanacak roller",
                    required = true,
                    content = @Content(schema = @Schema(implementation = CreateUserRequest.class))
            )
            @Valid @org.springframework.web.bind.annotation.RequestBody CreateUserRequest request) {
        log.info("Admin kullanıcı oluşturma isteği. Username: {}, Email: {}",
                request.getUsername(), request.getEmail());

        UserCreationResponseDTO response = userService.createUserWithKeycloak(request);

        log.info("Kullanıcı başarıyla oluşturuldu. Keycloak ID: {}", response.getKeycloakId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Returns the list of roles assignable to users in the Keycloak realm; system roles are filtered out.
     *
     * @return list of role names
     */
    @Operation(
            summary = "Atanabilir rolleri listele (Admin)",
            description = """
                    Keycloak realm'indeki kullanıcıya atanabilir rolleri döner.
                    Sistem rolleri (`offline_access`, `uma_authorization`, `default-roles-*`) filtrelenir.

                    **Yetki:** Yalnızca `ADMIN` rolüne sahip kullanıcılar erişebilir.
                    
                    **Kullanım:** `POST /api/v1/users/admin/create` endpoint'inde `roles` alanını
                    doldurmak için bu endpoint'ten dinamik olarak rol listesi çekilir.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            tags = {"Kullanıcı Yönetimi"}
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Rol listesi başarıyla döndü",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(
                                    type = "string",
                                    example = "AGENT",
                                    allowableValues = {"CUSTOMER", "AGENT", "LEAD_AGENT", "ADMIN", "MANAGER"}
                            ))
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Yalnızca ADMIN erişebilir"
            )
    })
    @GetMapping("/admin/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> getAssignableRoles() {
        log.debug("Atanabilir roller listesi isteği.");

        List<String> roles = keycloakAdminService.getAssignableRoles()
                .stream()
                .map(role -> role.getName())
                .toList();

        log.debug("Toplam {} atanabilir rol döndü.", roles.size());
        return ResponseEntity.ok(roles);
    }
}