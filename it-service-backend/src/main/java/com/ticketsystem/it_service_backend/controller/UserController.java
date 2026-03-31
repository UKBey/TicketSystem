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

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Frontend login olduktan hemen sonra Keycloak Token'ı ile bu endpoint'e vurur.
    @PostMapping("/sync")
    public ResponseEntity<UserDTO> syncCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = JwtUtils.extractRoles(jwt);
        String assignedRole = "CUSTOMER"; // Varsayılan

        if (roles.contains("MANAGER")) {
            assignedRole = "MANAGER";
        } else if (roles.contains("AGENT")) {
            assignedRole = "AGENT";
        }

        User user = User.builder()
                .id(jwt.getSubject()) // Keycloak UUID
                .email(jwt.getClaimAsString("email"))
                .fullName(jwt.getClaimAsString("given_name") + " " + jwt.getClaimAsString("family_name"))
                .role(assignedRole) 
                .build();
        
        return ResponseEntity.ok(UserDTO.fromEntity(userService.syncUser(user)));
    }

    @GetMapping("/agents")
    public ResponseEntity<List<UserDTO>> getAgents() {
        return ResponseEntity.ok(userService.getAgents().stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable String id) {
        return ResponseEntity.ok(UserDTO.fromEntity(userService.getUserById(id)));
    }

    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers().stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    

    @PostMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<UserDTO> assignProductToUser(@PathVariable String userId, @PathVariable Long productId) {
        return ResponseEntity.ok(UserDTO.fromEntity(userService.assignProductToUser(userId, productId)));
    }

    @DeleteMapping("/{userId}/products/{productId}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<UserDTO> removeProductFromUser(@PathVariable String userId, @PathVariable Long productId) {
        return ResponseEntity.ok(UserDTO.fromEntity(userService.removeProductFromUser(userId, productId)));
    }
}