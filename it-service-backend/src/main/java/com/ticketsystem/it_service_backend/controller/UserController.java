package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AgentCapacityDTO;
import com.ticketsystem.it_service_backend.dto.ChangePasswordRequest;
import com.ticketsystem.it_service_backend.dto.CreateUserRequest;
import com.ticketsystem.it_service_backend.dto.TotpCredentialDTO;
import com.ticketsystem.it_service_backend.dto.UpdateProfileRequest;
import com.ticketsystem.it_service_backend.dto.UserCreationResponseDTO;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.exception.WrongCurrentPasswordException;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.service.EmailService;
import com.ticketsystem.it_service_backend.service.KeycloakAdminService;
import com.ticketsystem.it_service_backend.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
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

@Log4j2
@Tag(name = "Kullanıcı Yönetimi", description = "Keycloak senkronizasyonu, kullanıcı listeleme ve ürün yetki atamaları")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;
    private final KeycloakAdminService keycloakAdminService;
    private final UserRepository userRepository;
    private final EmailService emailService;

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

        // JWT'de tanınan uygulama rolü yoksa null — "CUSTOMER" varsayılanı kullanılmaz.
        // Rol ataması admin tarafından yapılana kadar kullanıcı no-role sayfasında kalır.
        String assignedRole = null;

        if (roles.contains("AGENT_ADMIN")) {
            assignedRole = "AGENT_ADMIN";
        } else if (roles.contains("MANAGER")) {
            assignedRole = "MANAGER";
        } else if (roles.contains("AGENT")) {
            assignedRole = "AGENT";
        } else if (roles.contains("CUSTOMER")) {
            assignedRole = "CUSTOMER";
        }

        User user = User.builder()
                .id(userId)
                .email(jwt.getClaimAsString("email"))
                .fullName(buildFullName(jwt))
                .role(assignedRole) 
                .build();
        
        User syncedUser = userService.syncUser(user);
        
        log.info("Kullanıcı başarıyla senkronize edildi. Uygulama İçi Roller: {}", syncedUser.getRole());

        return ResponseEntity.ok(UserDTO.fromEntity(syncedUser));
    }

    /**
     * JWT'den fullName oluşturur. given_name veya family_name null ise
     * preferred_username'i fallback olarak kullanır.
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
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Agent'ları kapasite bilgileriyle listele",
            description = "Belirtilen ürün için yetkili agent'ları, mevcut aktif bilet sayıları ve limitleriyle birlikte döner. Atama UI'ı için kullanılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent kapasite listesi başarıyla döndü"),
            @ApiResponse(responseCode = "403", description = "Yalnızca AGENT_ADMIN erişebilir")
    })
    @GetMapping("/agents/capacity")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<List<AgentCapacityDTO>> getAgentsWithCapacity(
            @Parameter(description = "Ürün ID'si", required = true)
            @RequestParam Long productId) {
        log.debug("Agent kapasite listesi isteği. Product: {}", productId);

        List<AgentCapacityDTO> agents = userService.getAgentsWithCapacity(productId);

        log.debug("Toplam {} agent kapasite bilgisiyle döndü.", agents.size());
        return ResponseEntity.ok(agents);
    }

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

    @Operation(summary = "Tüm kullanıcıları listele (sayfalı + filtreli)",
            description = "Sistemdeki kullanıcıları isim/email araması ve rol filtresiyle sayfalı olarak getirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kullanıcı listesi başarıyla döndü"),
            @ApiResponse(responseCode = "403", description = "Yalnızca AGENT_ADMIN erişebilir")
    })
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<String> role,
            @RequestParam(defaultValue = "0")  @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(500) int size) {
        log.debug("Kullanıcı listeleme isteği. search={}, roles={}, page={}, size={}", search, role, page, size);

        Page<User> userPage = userService.getUsersFiltered(search, role, page, size);

        log.debug("Toplam {} kullanıcı döndü (sayfa {}/{})", userPage.getNumberOfElements(), page, userPage.getTotalPages());

        return ResponseEntity.ok(Map.of(
                "content",       userPage.getContent().stream().map(UserDTO::fromEntity).collect(Collectors.toList()),
                "totalElements", userPage.getTotalElements(),
                "totalPages",    userPage.getTotalPages(),
                "page",          userPage.getNumber(),
                "size",          userPage.getSize()
        ));
    }

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
        userRepository.findById(userId).ifPresent(user ->
                emailService.sendPasswordChangedEmail(user, null, null));

        return ResponseEntity.noContent().build();
    }

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

        userRepository.findById(userId).ifPresent(user ->
                emailService.send2FADeviceRemovedEmail(user, deviceLabel));

        return ResponseEntity.noContent().build();
    }

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

        userRepository.findById(userId).ifPresent(user ->
                emailService.send2FADeviceAddedEmail(user, latestLabel));

        return ResponseEntity.noContent().build();
    }

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

    @Operation(summary = "Ajana ürün ata",
            description = "Belirtilen ajana belirtilen ürün grubunun destek taleplerini görebilme ve sahiplenme yetkisi verir. "
                    + "Atama sonrası ajanın `authorizedProducts` listesi güncellenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün yetkisi başarıyla atandı",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca AGENT_ADMIN yetki atayabilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı veya ürün bulunamadı")
    })
    @PostMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
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

    @Operation(summary = "Ajandan ürün yetkisi kaldır",
            description = "Ajanın belirtilen ürün grubu üzerindeki destek yetkisini iptal eder. "
                    + "Bu işlemden sonra ajan o ürüne ait yeni biletleri göremez ve sahiplenemez.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün yetkisi başarıyla kaldırıldı",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca AGENT_ADMIN yetki kaldırabilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı veya ürün bulunamadı")
    })
    @DeleteMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
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
    // Admin — Kullanıcı Oluşturma & Rol Yönetimi
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Kullanıcı aktif/pasif durumunu güncelle (Admin)",
            description = """
                    Kullanıcıyı soft-delete ile deaktive eder veya yeniden aktive eder.
                    Deaktive edilen kullanıcı Keycloak'ta disabled olur (login yapamaz).
                    Ticket'lara dokunulmaz.
                    
                    **Yetki:** Yalnızca `AGENT_ADMIN` rolüne sahip kullanıcılar erişebilir.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            tags = {"Kullanıcı Yönetimi"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Durum başarıyla güncellendi",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca AGENT_ADMIN erişebilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
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

    @Operation(
            summary = "Kullanıcı rollerini güncelle (Admin)",
            description = """
                    Belirtilen kullanıcının Keycloak realm rollerini günceller ve yerel veritabanını senkronize eder.
                    Mevcut roller kaldırılır, yeni roller atanır.
                    
                    **Yetki:** Yalnızca `AGENT_ADMIN` rolüne sahip kullanıcılar erişebilir.
                    """,
            security = @SecurityRequirement(name = "bearerAuth"),
            tags = {"Kullanıcı Yönetimi"}
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Roller başarıyla güncellendi",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz istek — roller listesi boş olamaz"),
            @ApiResponse(responseCode = "403", description = "Yalnızca AGENT_ADMIN erişebilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı bulunamadı")
    })
    @PutMapping("/{userId}/roles")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<UserDTO> updateUserRoles(
            @Parameter(description = "Keycloak kullanıcı ID'si (UUID)", required = true)
            @PathVariable String userId,
            @org.springframework.web.bind.annotation.RequestBody List<String> roles) {
        log.info("Kullanıcı rol güncelleme isteği. Kullanıcı: {}, Roller: {}", userId, roles);

        if (roles == null || roles.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        User updatedUser = userService.updateUserRoles(userId, roles);

        log.info("Kullanıcı rolleri başarıyla güncellendi. ID: {}", userId);
        return ResponseEntity.ok(UserDTO.fromEntity(updatedUser));
    }

    @Operation(
            summary = "Yeni kullanıcı oluştur (Admin)",
            description = """
                    Keycloak realm'inde yeni bir kullanıcı oluşturur, geçici şifre atar ve
                    seçilen rolleri eşler. Başarılı Keycloak kaydının ardından yerel veritabanına
                    senkronizasyon kaydı atılır.
                    
                    **Yetki:** Yalnızca `AGENT_ADMIN` rolüne sahip kullanıcılar erişebilir.
                    
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
                    description = "Yalnızca AGENT_ADMIN erişebilir"
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
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
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

    @Operation(
            summary = "Atanabilir rolleri listele (Admin)",
            description = """
                    Keycloak realm'indeki kullanıcıya atanabilir rolleri döner.
                    Sistem rolleri (`offline_access`, `uma_authorization`, `default-roles-*`) filtrelenir.
                    
                    **Yetki:** Yalnızca `AGENT_ADMIN` rolüne sahip kullanıcılar erişebilir.
                    
                    **Kullanım:** `POST /api/users/admin/create` endpoint'inde `roles` alanını
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
                                    allowableValues = {"CUSTOMER", "AGENT", "AGENT_ADMIN", "MANAGER"}
                            ))
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Yalnızca AGENT_ADMIN erişebilir"
            )
    })
    @GetMapping("/admin/roles")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<List<String>> getAssignableRoles() {
        log.debug("Atanabilir roller listesi isteği.");

        List<String> roles = keycloakAdminService.getAssignableRoles()
                .stream()
                .map(role -> role.getName())
                .collect(Collectors.toList());

        log.debug("Toplam {} atanabilir rol döndü.", roles.size());
        return ResponseEntity.ok(roles);
    }
}