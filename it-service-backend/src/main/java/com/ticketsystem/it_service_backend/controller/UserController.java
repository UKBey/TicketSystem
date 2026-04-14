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
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Tag(name = "Kullanıcı Yönetimi", description = "Kullanıcı senkronizasyonu ve yetkilendirme işlemleri")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // UI girisinden sonra kullaniciyi yerel veritabaniyla esitlemek icin cagrilir.
    @Operation(summary = "Kullanıcı senkronizasyonu", description = "Keycloak'tan gelen kullanıcı bilgilerini yerel veritabanı ile eşitler.")
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

    @Operation(summary = "Tüm ajanları listele", description = "Sistemdeki tüm destek ajanlarını getirir.")
    @GetMapping("/agents")
    public ResponseEntity<List<UserDTO>> getAgents() {
        log.info("Tüm ajanları listeleme isteği.");
        
        List<User> agents = userService.getAgents();
        
        log.info("Toplam {} ajan listelendi.", agents.size());

        return ResponseEntity.ok(agents.stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Kullanıcı detayı getir", description = "ID ile kullanıcı bilgilerini çeker.")
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable String id) {
        log.info("Kullanıcı detayı isteği. Kullanıcı ID: {}", id);

        User user = userService.getUserById(id);
        
        log.info("Kullanıcı detayı çekildi: {} ({})", user.getFullName(), user.getRole());

        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    @Operation(summary = "Tüm kullanıcıları listele", description = "Sadece Yönetici (MANAGER) tüm kullanıcıları görebilir.")
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

    

    @Operation(summary = "Ajana ürün ata", description = "Belirli bir ürünün destek taleplerini görmesi için ajana yetki verir.")
    @PostMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<UserDTO> assignProductToUser(@PathVariable String userId, @PathVariable Long productId) {
        log.info("Ajan-Ürün atama isteği. Ajan: {}, Ürün: {}", userId, productId);

        User user = userService.assignProductToUser(userId, productId);
        
        log.info("Ürün başarıyla ajana atandı. Ajan: {}", userId);

        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }

    @Operation(summary = "Ajandan ürün yetkisi kaldır", description = "Ajanın belirli bir ürün üzerindeki yetkisini iptal eder.")
    @DeleteMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<UserDTO> removeProductFromUser(@PathVariable String userId, @PathVariable Long productId) {
        log.info("Ajan-Ürün yetki kaldırma isteği. Ajan: {}, Ürün: {}", userId, productId);

        User user = userService.removeProductFromUser(userId, productId);
        
        log.info("Ürün yetkisi ajandan başarıyla kaldırıldı. Ajan: {}", userId);

        return ResponseEntity.ok(UserDTO.fromEntity(user));
    }
}