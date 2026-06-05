package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.KnownIssueDTO;
import com.ticketsystem.it_service_backend.entity.KnownIssue;
import com.ticketsystem.it_service_backend.service.KnownIssueService;
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
class KnownIssueControllerTest {

    @Mock private KnownIssueService knownIssueService;
    private KnownIssueController controller;

    @BeforeEach
    void setUp() {
        controller = new KnownIssueController(knownIssueService);
    }

    /** Builds a Keycloak-style JWT with the given subject and realm roles. */
    private Jwt jwt(String subject, String... roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .build();
    }

    private KnownIssue entity(long id) {
        return KnownIssue.builder()
                .id(id).productId(10L).topicId(5L)
                .title("VPN kopuyor").content("Ağ ayarlarını kontrol edin")
                .isActive(true).createdBy("lead-1").build();
    }

    @Test
    void listByProduct_defaultActiveOnly_passesActiveOnlyTrue() {
        when(knownIssueService.listByProduct(eq(10L), eq(null), eq(true), eq("agent-1"), any()))
                .thenReturn(List.of(entity(1L)));

        ResponseEntity<List<KnownIssueDTO>> res =
                controller.listByProduct(jwt("agent-1", "agent"), 10L, null, false);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).hasSize(1);
        assertThat(res.getBody().get(0).getTitle()).isEqualTo("VPN kopuyor");
    }

    @Test
    void listByProduct_includeInactive_passesActiveOnlyFalse_andTopicFilter() {
        when(knownIssueService.listByProduct(eq(10L), eq(7L), eq(false), eq("admin-1"), any()))
                .thenReturn(List.of());

        ResponseEntity<List<KnownIssueDTO>> res =
                controller.listByProduct(jwt("admin-1", "admin"), 10L, 7L, true);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).isEmpty();
        // JwtUtils normalizes "admin" -> "ADMIN"
        ArgumentCaptor<List<String>> rolesCaptor = ArgumentCaptor.forClass(List.class);
        verify(knownIssueService).listByProduct(eq(10L), eq(7L), eq(false), eq("admin-1"), rolesCaptor.capture());
        assertThat(rolesCaptor.getValue()).contains("ADMIN");
    }

    @Test
    void getOne_returnsDtoFromService() {
        when(knownIssueService.getById(eq(42L), eq("agent-1"), any())).thenReturn(entity(42L));

        ResponseEntity<KnownIssueDTO> res = controller.getOne(jwt("agent-1", "agent"), 42L);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().getId()).isEqualTo(42L);
        assertThat(res.getBody().getProductId()).isEqualTo(10L);
    }

    @Test
    void create_forwardsFieldsAndCreatedBy() {
        KnownIssueDTO body = KnownIssueDTO.builder()
                .topicId(5L).title("Yeni").content("İçerik").isActive(true).build();
        when(knownIssueService.create(10L, 5L, "Yeni", "İçerik", true, "lead-1"))
                .thenReturn(entity(7L));

        ResponseEntity<KnownIssueDTO> res = controller.create(jwt("lead-1", "lead_agent"), 10L, body);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().getId()).isEqualTo(7L);
        verify(knownIssueService).create(10L, 5L, "Yeni", "İçerik", true, "lead-1");
    }

    @Test
    void update_passesArgsAndReturnsDto() {
        KnownIssueDTO body = KnownIssueDTO.builder()
                .topicId(9L).title("Düzenli").content("Güncel").isActive(false).build();
        when(knownIssueService.update(5L, 9L, "Düzenli", "Güncel", false)).thenReturn(entity(5L));

        ResponseEntity<KnownIssueDTO> res = controller.update(5L, body);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().getId()).isEqualTo(5L);
        verify(knownIssueService).update(5L, 9L, "Düzenli", "Güncel", false);
    }

    @Test
    void delete_returnsNoContent() {
        ResponseEntity<Void> res = controller.delete(5L);

        assertThat(res.getStatusCode().value()).isEqualTo(204);
        verify(knownIssueService).delete(5L);
    }
}
