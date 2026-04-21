package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import com.ticketsystem.it_service_backend.dto.UserDTO;
import com.ticketsystem.it_service_backend.util.JwtUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Tag(name = "Kullanıcı Yönetimi", description = "Keycloak senkronizasyonu, kullanıcı listeleme ve ürün yetki atamaları")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

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

        String assignedRole = "CUSTOMER"; 

        if (roles.contains("MANAGER")) {
            assignedRole = "MANAGER";
        } else if (roles.contains("AGENT")) {
            assignedRole = "AGENT";
        }

        User user = User.builder()
                .id(userId)
                .email(jwt.getClaimAsString("email"))
                .fullName(jwt.getClaimAsString("given_name") + " " + jwt.getClaimAsString("family_name"))
                .role(assignedRole) 
                .build();
        
        User syncedUser = userService.syncUser(user);
        
        log.info("Kullanıcı başarıyla senkronize edildi. Uygulama İçi Roller: {}", syncedUser.getRole());

        return ResponseEntity.ok(UserDTO.fromEntity(syncedUser));
    }

    @Operation(summary = "Tüm ajanları listele",
            description = "Sistemdeki `AGENT` rolündeki tüm kullanıcıları yetkili oldukları ürün bilgileriyle birlikte getirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ajan listesi başarıyla döndü"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @GetMapping("/agents")
    public ResponseEntity<List<UserDTO>> getAgents() {
        log.info("Tüm ajanları listeleme isteği.");
        
        List<User> agents = userService.getAgents();
        
        log.info("Toplam {} ajan listelendi.", agents.size());

        return ResponseEntity.ok(agents.stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList()));
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
        log.info("Kullanıcı detayı isteği. Kullanıcı ID: {}", id);

        User user = userService.getUserById(id);
        
        log.info("Kullanıcı detayı çekildi: {} ({})", user.getFullName(), user.getRole());

        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    @Operation(summary = "Tüm kullanıcıları listele",
            description = "Sistemdeki tüm kullanıcıları (CUSTOMER, AGENT, MANAGER) yetkili ürün bilgileriyle birlikte getirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Kullanıcı listesi başarıyla döndü"),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER erişebilir")
    })
    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        log.info("Tüm kullanıcıları listeleme isteği (Yönetici).");

        List<User> users = userService.getAllUsers();
        
        log.info("Sistemdeki toplam {} kullanıcı listelendi.", users.size());

        return ResponseEntity.ok(users.stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    

    @Operation(summary = "Ajana ürün ata",
            description = "Belirtilen ajana belirtilen ürün grubunun destek taleplerini görebilme ve sahiplenme yetkisi verir. "
                    + "Atama sonrası ajanın `authorizedProducts` listesi güncellenir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün yetkisi başarıyla atandı",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER yetki atayabilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı veya ürün bulunamadı")
    })
    @PostMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasRole('MANAGER')")
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
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER yetki kaldırabilir"),
            @ApiResponse(responseCode = "404", description = "Kullanıcı veya ürün bulunamadı")
    })
    @DeleteMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasRole('MANAGER')")
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
}