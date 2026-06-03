package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CannedResponseDTO;
import com.ticketsystem.it_service_backend.service.CannedResponseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CannedResponseControllerTest {

    @Mock private CannedResponseService service;
    private CannedResponseController controller;

    @BeforeEach
    void setUp() {
        controller = new CannedResponseController(service);
    }

    /** Builds a Keycloak-style JWT with the given subject and realm roles. */
    private Jwt jwt(String subject, String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }

    @Test
    void list_passesSubjectAndFiltersToService() {
        when(service.listVisible("agent-1", 10L, "SHARED", "EXTERNAL", "vpn"))
                .thenReturn(List.of(CannedResponseDTO.builder().id(1L).title("VPN").build()));

        ResponseEntity<List<CannedResponseDTO>> res =
                controller.list(jwt("agent-1", "agent"), 10L, "SHARED", "EXTERNAL", "vpn");

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).hasSize(1);
    }

    @Test
    void create_forwardsUserIdAndRoles() {
        CannedResponseDTO body = CannedResponseDTO.builder().title("T").scope("SHARED").contentEn("x").build();
        CannedResponseDTO created = CannedResponseDTO.builder().id(7L).title("T").scope("SHARED").build();
        when(service.create(eq(body), eq("admin-1"), any())).thenReturn(created);

        ResponseEntity<CannedResponseDTO> res = controller.create(jwt("admin-1", "admin"), body);

        assertThat(res.getBody().getId()).isEqualTo(7L);
        ArgumentCaptor<List<String>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        verify(service).create(eq(body), eq("admin-1"), rolesCaptor.capture());
        // JwtUtils normalizes "admin" -> "ADMIN"
        assertThat(rolesCaptor.getValue()).contains("ADMIN");
    }

    @Test
    void update_forwardsArgs() {
        CannedResponseDTO body = CannedResponseDTO.builder().title("T").scope("PERSONAL").contentTr("x").build();
        CannedResponseDTO updated = CannedResponseDTO.builder().id(5L).title("T").build();
        when(service.update(eq(5L), eq(body), eq("agent-1"), any())).thenReturn(updated);

        ResponseEntity<CannedResponseDTO> res = controller.update(jwt("agent-1", "agent"), 5L, body);

        assertThat(res.getBody().getId()).isEqualTo(5L);
    }

    @Test
    void delete_returnsNoContent() {
        ResponseEntity<Void> res = controller.delete(jwt("agent-1", "agent"), 5L);

        assertThat(res.getStatusCode().value()).isEqualTo(204);
        verify(service).delete(eq(5L), eq("agent-1"), any());
    }

    @Test
    void addFavorite_delegatesAndReturnsNoContent() {
        ResponseEntity<Void> res = controller.addFavorite(jwt("agent-1", "agent"), 3L);

        assertThat(res.getStatusCode().value()).isEqualTo(204);
        verify(service).addFavorite(3L, "agent-1");
    }

    @Test
    void removeFavorite_delegatesAndReturnsNoContent() {
        ResponseEntity<Void> res = controller.removeFavorite(jwt("agent-1", "agent"), 3L);

        assertThat(res.getStatusCode().value()).isEqualTo(204);
        verify(service).removeFavorite(3L, "agent-1");
    }
}
