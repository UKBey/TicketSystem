package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.PdfPreferencesDTO;
import com.ticketsystem.it_service_backend.dto.UserDTO;
import com.ticketsystem.it_service_backend.entity.Language;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Theme;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.service.EmailService;
import com.ticketsystem.it_service_backend.service.KeycloakAdminService;
import com.ticketsystem.it_service_backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private EmailService emailService;

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController(userService, keycloakAdminService, emailService);
    }

    @Test
    void syncCurrentUser_mapsAgentAdminRoleAndReturnsDto() {
        User synced = User.builder()
                .id("admin-1")
                .email("admin@example.com")
                .fullName("Ada Admin")
                .role("ADMIN")
                .build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);

        Jwt jwt = jwtWithRoles("admin-1", List.of("ADMIN"));
        when(jwt.getClaimAsString("email")).thenReturn("admin@example.com");
        when(jwt.getClaimAsString("given_name")).thenReturn("Ada");
        when(jwt.getClaimAsString("family_name")).thenReturn("Admin");

        ResponseEntity<UserDTO> response = userController.syncCurrentUser(jwt);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("admin-1", response.getBody().getId());
        assertEquals("ADMIN", response.getBody().getRole());
    }

    @Test
    void syncCurrentUser_mapsManagerRoleAndReturnsDto() {
        User synced = User.builder()
                .id("manager-1")
                .email("manager@example.com")
                .fullName("Ada Manager")
                .role("MANAGER")
                .build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);

        Jwt jwt = jwtWithRoles("manager-1", List.of("MANAGER"));
        when(jwt.getClaimAsString("email")).thenReturn("manager@example.com");
        when(jwt.getClaimAsString("given_name")).thenReturn("Ada");
        when(jwt.getClaimAsString("family_name")).thenReturn("Manager");

        ResponseEntity<UserDTO> response = userController.syncCurrentUser(jwt);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("manager-1", response.getBody().getId());
        assertEquals("MANAGER", response.getBody().getRole());
    }

    @Test
    void syncCurrentUser_mapsAgentRoleAndReturnsDto() {
        User synced = User.builder()
                .id("agent-1")
                .email("agent@example.com")
                .fullName("Ada Agent")
                .role("AGENT")
                .build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);

        Jwt jwt = jwtWithRoles("agent-1", List.of("AGENT"));
        when(jwt.getClaimAsString("email")).thenReturn("agent@example.com");
        when(jwt.getClaimAsString("given_name")).thenReturn("Ada");
        when(jwt.getClaimAsString("family_name")).thenReturn("Agent");

        ResponseEntity<UserDTO> response = userController.syncCurrentUser(jwt);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("AGENT", response.getBody().getRole());
    }

    @Test
    void syncCurrentUser_mapsCustomerRoleAndReturnsDto() {
        User synced = User.builder()
                .id("customer-1")
                .email("customer@example.com")
                .fullName("Ada Customer")
                .role("CUSTOMER")
                .build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);

        Jwt jwt = jwtWithRoles("customer-1", List.of("CUSTOMER"));
        when(jwt.getClaimAsString("email")).thenReturn("customer@example.com");
        when(jwt.getClaimAsString("given_name")).thenReturn("Ada");
        when(jwt.getClaimAsString("family_name")).thenReturn("Customer");

        ResponseEntity<UserDTO> response = userController.syncCurrentUser(jwt);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("CUSTOMER", response.getBody().getRole());
    }

    @Test
    void getAgents_returnsDtoList() {
        User agent = User.builder().id("agent-1").fullName("Agent One").role("AGENT").build();
        when(userService.getAgents()).thenReturn(List.of(agent));

        ResponseEntity<List<UserDTO>> response = userController.getAgents();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("agent-1", response.getBody().get(0).getId());
    }

    @Test
    void getUser_returnsSingleDto() {
        User user = User.builder().id("u-1").fullName("User One").role("CUSTOMER").build();
        when(userService.getUserById("u-1")).thenReturn(user);

        ResponseEntity<UserDTO> response = userController.getUser("u-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("u-1", response.getBody().getId());
    }

    @Test
    void getAllUsers_returnsAllDtos() {
        org.springframework.data.domain.Page<User> page =
                new org.springframework.data.domain.PageImpl<>(List.of(
                        User.builder().id("u-1").fullName("User One").role("CUSTOMER").build(),
                        User.builder().id("u-2").fullName("User Two").role("AGENT").build()
                ));
        when(userService.getUsersFiltered(null, null, false, null, "name", "asc", 0, 20)).thenReturn(page);

        ResponseEntity<java.util.Map<String, Object>> response = userController.getAllUsers(null, null, false, null, "name", "asc", 0, 20);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        java.util.List<?> content = (java.util.List<?>) response.getBody().get("content");
        assertEquals(2, content.size());
    }

    @Test
    void assignProductToUser_returnsUpdatedUser() {
        Product product = Product.builder().id(55L).nameEn("Support").build();
        User user = User.builder().id("agent-1").fullName("Agent One").role("AGENT").authorizedProducts(List.of(product)).build();
        when(userService.assignProductToUser("agent-1", 55L)).thenReturn(user);

        ResponseEntity<UserDTO> response = userController.assignProductToUser("agent-1", 55L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getAuthorizedProducts().size());
    }

    @Test
    void removeProductFromUser_returnsUpdatedUser() {
        User user = User.builder().id("agent-1").fullName("Agent One").role("AGENT").authorizedProducts(List.of()).build();
        when(userService.removeProductFromUser("agent-1", 55L)).thenReturn(user);

        ResponseEntity<UserDTO> response = userController.removeProductFromUser("agent-1", 55L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("agent-1", response.getBody().getId());
        verify(userService).removeProductFromUser("agent-1", 55L);
    }

    private Jwt jwtWithRoles(String subject, List<String> roles) {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", roles));
        return jwt;
    }

    // -------------------------------------------------------------------------
    // updateLanguage
    // -------------------------------------------------------------------------

    @Test
    void updateLanguage_returnsUpdatedDto() {
        User updated = User.builder().id("u-1").fullName("N").role("CUSTOMER").preferredLanguage(Language.TR).build();
        when(userService.updatePreferredLanguage("u-1", "tr")).thenReturn(updated);
        Jwt jwt = jwtWithRoles("u-1", List.of("CUSTOMER"));

        ResponseEntity<UserDTO> response = userController.updateLanguage(jwt, "tr");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Language.TR, response.getBody().getPreferredLanguage());
    }

    // -------------------------------------------------------------------------
    // updateTheme
    // -------------------------------------------------------------------------

    @Test
    void updateTheme_returnsUpdatedDto() {
        User updated = User.builder().id("u-1").fullName("N").role("CUSTOMER").preferredTheme(Theme.DARK).build();
        when(userService.updatePreferredTheme("u-1", "dark")).thenReturn(updated);
        Jwt jwt = jwtWithRoles("u-1", List.of("CUSTOMER"));

        ResponseEntity<UserDTO> response = userController.updateTheme(jwt, "dark");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Theme.DARK, response.getBody().getPreferredTheme());
    }

    // -------------------------------------------------------------------------
    // getPdfPreferences / updatePdfPreferences
    // -------------------------------------------------------------------------

    @Test
    void getPdfPreferences_returnsStoredString() {
        when(userService.getPdfExportPreferences("u-1")).thenReturn("{\"theme\":\"dark\"}");
        Jwt jwt = jwtWithRoles("u-1", List.of("CUSTOMER"));

        ResponseEntity<PdfPreferencesDTO> response = userController.getPdfPreferences(jwt);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("{\"theme\":\"dark\"}", response.getBody().getPreferences());
    }

    @Test
    void getPdfPreferences_returnsNullWhenNoneSaved() {
        when(userService.getPdfExportPreferences("u-1")).thenReturn(null);
        Jwt jwt = jwtWithRoles("u-1", List.of("CUSTOMER"));

        ResponseEntity<PdfPreferencesDTO> response = userController.getPdfPreferences(jwt);

        assertEquals(200, response.getStatusCode().value());
        assertNull(response.getBody().getPreferences());
    }

    @Test
    void updatePdfPreferences_persistsAndReturnsNoContent() {
        PdfPreferencesDTO body = new PdfPreferencesDTO();
        body.setPreferences("{\"language\":\"tr\"}");
        Jwt jwt = jwtWithRoles("u-1", List.of("AGENT"));

        ResponseEntity<Void> response = userController.updatePdfPreferences(jwt, body);

        assertEquals(204, response.getStatusCode().value());
        verify(userService).updatePdfExportPreferences("u-1", "{\"language\":\"tr\"}");
    }

    // -------------------------------------------------------------------------
    // getAgents / getAgentsWithCapacity / getUser
    // -------------------------------------------------------------------------

    @Test
    void getAgents_returnsList() {
        when(userService.getAgents()).thenReturn(List.of(
                User.builder().id("a-1").fullName("Agent 1").role("AGENT").build()
        ));

        ResponseEntity<List<UserDTO>> response = userController.getAgents();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getAgentsWithCapacity_delegatesToService() {
        com.ticketsystem.it_service_backend.dto.AgentCapacityDTO cap =
                com.ticketsystem.it_service_backend.dto.AgentCapacityDTO.builder()
                        .agentId("a-1").agentName("A").currentActiveTickets(2L).maxLimit(5).isFull(false).build();
        when(userService.getAgentsWithCapacity(10L)).thenReturn(List.of(cap));

        ResponseEntity<List<com.ticketsystem.it_service_backend.dto.AgentCapacityDTO>> response =
                userController.getAgentsWithCapacity(10L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getUser_returnsDto() {
        User u = User.builder().id("u-1").fullName("X").role("AGENT").build();
        when(userService.getUserById("u-1")).thenReturn(u);

        ResponseEntity<UserDTO> response = userController.getUser("u-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("u-1", response.getBody().getId());
    }

    // -------------------------------------------------------------------------
    // updateUserStatus
    // -------------------------------------------------------------------------

    @Test
    void updateUserStatus_activeTrue_callsReactivate() {
        User u = User.builder().id("u-2").fullName("X").role("AGENT").isActive(true).build();
        when(userService.reactivateUser("u-2")).thenReturn(u);
        Jwt jwt = jwtWithRoles("admin-1", List.of("ADMIN"));

        ResponseEntity<UserDTO> response = userController.updateUserStatus("u-2", true, jwt);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).reactivateUser("u-2");
    }

    @Test
    void updateUserStatus_activeFalse_callsDeactivate() {
        User u = User.builder().id("u-2").fullName("X").role("AGENT").isActive(false).build();
        when(userService.deactivateUser("u-2")).thenReturn(u);
        Jwt jwt = jwtWithRoles("admin-1", List.of("ADMIN"));

        ResponseEntity<UserDTO> response = userController.updateUserStatus("u-2", false, jwt);

        assertEquals(200, response.getStatusCode().value());
        verify(userService).deactivateUser("u-2");
    }

    @Test
    void updateUserStatus_selfDeactivation_returnsBadRequest() {
        Jwt jwt = jwtWithRoles("admin-1", List.of("ADMIN"));

        ResponseEntity<UserDTO> response = userController.updateUserStatus("admin-1", false, jwt);

        assertEquals(400, response.getStatusCode().value());
        verify(userService, org.mockito.Mockito.never()).deactivateUser(org.mockito.ArgumentMatchers.any());
    }

    // -------------------------------------------------------------------------
    // updateUserRoles
    // -------------------------------------------------------------------------

    @Test
    void updateUserRoles_validRoles_returnsUpdatedDto() {
        User u = User.builder().id("u-2").fullName("X").role("AGENT").build();
        when(userService.updateUserRoles("u-2", List.of("AGENT"), "admin-1")).thenReturn(u);

        ResponseEntity<UserDTO> response = userController.updateUserRoles("u-2", List.of("AGENT"), jwtWithRoles("admin-1", List.of("ADMIN")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("AGENT", response.getBody().getRole());
    }

    @Test
    void updateUserRoles_emptyList_returnsBadRequest() {
        ResponseEntity<UserDTO> response = userController.updateUserRoles("u-2", List.of(), null);

        assertEquals(400, response.getStatusCode().value());
        verify(userService, org.mockito.Mockito.never()).updateUserRoles(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateUserRoles_nullList_returnsBadRequest() {
        ResponseEntity<UserDTO> response = userController.updateUserRoles("u-2", null, null);

        assertEquals(400, response.getStatusCode().value());
    }

    // -------------------------------------------------------------------------
    // createUser / getAssignableRoles (admin)
    // -------------------------------------------------------------------------

    @Test
    void syncCurrentUser_onlyGivenName_buildsFromIt() {
        User synced = User.builder().id("u").fullName("OnlyGiven").role("CUSTOMER").build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);
        Jwt jwt = jwtWithRoles("u", List.of("CUSTOMER"));
        lenient().when(jwt.getClaimAsString("given_name")).thenReturn("OnlyGiven");
        lenient().when(jwt.getClaimAsString("family_name")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("email")).thenReturn(null);

        ResponseEntity<UserDTO> response = userController.syncCurrentUser(jwt);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void syncCurrentUser_onlyFamilyName_buildsFromIt() {
        User synced = User.builder().id("u").fullName("OnlyFam").role("CUSTOMER").build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);
        Jwt jwt = jwtWithRoles("u", List.of("CUSTOMER"));
        lenient().when(jwt.getClaimAsString("given_name")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("family_name")).thenReturn("OnlyFam");
        lenient().when(jwt.getClaimAsString("email")).thenReturn(null);

        userController.syncCurrentUser(jwt);

        org.mockito.Mockito.verify(userService).syncUser(
                org.mockito.ArgumentMatchers.argThat(u -> "OnlyFam".equals(u.getFullName())));
    }

    @Test
    void syncCurrentUser_fallsBackToPreferredUsername() {
        User synced = User.builder().id("u").fullName("preferred").role("CUSTOMER").build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);
        Jwt jwt = jwtWithRoles("u", List.of("CUSTOMER"));
        lenient().when(jwt.getClaimAsString("given_name")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("family_name")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("preferred_username")).thenReturn("preferred");
        lenient().when(jwt.getClaimAsString("email")).thenReturn(null);

        userController.syncCurrentUser(jwt);

        org.mockito.Mockito.verify(userService).syncUser(
                org.mockito.ArgumentMatchers.argThat(u -> "preferred".equals(u.getFullName())));
    }

    @Test
    void syncCurrentUser_fallsBackToEmail() {
        User synced = User.builder().id("u").fullName("x@y").role("CUSTOMER").build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);
        Jwt jwt = jwtWithRoles("u", List.of("CUSTOMER"));
        lenient().when(jwt.getClaimAsString("given_name")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("family_name")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("preferred_username")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("email")).thenReturn("x@y");

        userController.syncCurrentUser(jwt);

        org.mockito.Mockito.verify(userService).syncUser(
                org.mockito.ArgumentMatchers.argThat(u -> "x@y".equals(u.getFullName())));
    }

    @Test
    void syncCurrentUser_unknownFallback() {
        User synced = User.builder().id("u").fullName("Unknown").role("CUSTOMER").build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);
        Jwt jwt = jwtWithRoles("u", List.of("CUSTOMER"));
        lenient().when(jwt.getClaimAsString("given_name")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("family_name")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("preferred_username")).thenReturn(null);
        lenient().when(jwt.getClaimAsString("email")).thenReturn(null);

        userController.syncCurrentUser(jwt);

        org.mockito.Mockito.verify(userService).syncUser(
                org.mockito.ArgumentMatchers.argThat(u -> "Unknown".equals(u.getFullName())));
    }

    @Test
    void syncCurrentUser_agentRoleMapping() {
        User synced = User.builder().id("u").fullName("Agent").role("AGENT").build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);
        Jwt jwt = jwtWithRoles("u", List.of("AGENT"));
        lenient().when(jwt.getClaimAsString("given_name")).thenReturn("A");
        lenient().when(jwt.getClaimAsString("family_name")).thenReturn("B");
        lenient().when(jwt.getClaimAsString("email")).thenReturn("e@x");

        userController.syncCurrentUser(jwt);

        org.mockito.Mockito.verify(userService).syncUser(
                org.mockito.ArgumentMatchers.argThat(u -> "AGENT".equals(u.getRole())));
    }

    @Test
    void syncCurrentUser_customerRoleMapping() {
        User synced = User.builder().id("u").fullName("Customer").role("CUSTOMER").build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);
        Jwt jwt = jwtWithRoles("u", List.of("CUSTOMER"));
        lenient().when(jwt.getClaimAsString("given_name")).thenReturn("A");
        lenient().when(jwt.getClaimAsString("family_name")).thenReturn("B");
        lenient().when(jwt.getClaimAsString("email")).thenReturn("e@x");

        userController.syncCurrentUser(jwt);

        org.mockito.Mockito.verify(userService).syncUser(
                org.mockito.ArgumentMatchers.argThat(u -> "CUSTOMER".equals(u.getRole())));
    }

    @Test
    void syncCurrentUser_noRecognizedRole_assignsNull() {
        User synced = User.builder().id("u").fullName("Null").role(null).build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);
        Jwt jwt = jwtWithRoles("u", List.of("RANDOM_OTHER"));
        lenient().when(jwt.getClaimAsString("given_name")).thenReturn("A");
        lenient().when(jwt.getClaimAsString("family_name")).thenReturn("B");
        lenient().when(jwt.getClaimAsString("email")).thenReturn("e@x");

        userController.syncCurrentUser(jwt);

        org.mockito.Mockito.verify(userService).syncUser(
                org.mockito.ArgumentMatchers.argThat(u -> u.getRole() == null));
    }

    @Test
    void createUser_returnsCreatedDto() {
        com.ticketsystem.it_service_backend.dto.CreateUserRequest req =
                new com.ticketsystem.it_service_backend.dto.CreateUserRequest();
        req.setUsername("u"); req.setEmail("e@e.com");
        req.setFirstName("F"); req.setLastName("L"); req.setPassword("Temp1234!");
        req.setRoles(List.of("AGENT"));
        com.ticketsystem.it_service_backend.dto.UserCreationResponseDTO dto =
                com.ticketsystem.it_service_backend.dto.UserCreationResponseDTO.builder()
                        .keycloakId("kc-id").username("u").email("e@e.com").fullName("F L")
                        .assignedRoles(List.of("AGENT")).build();
        when(userService.createUserWithKeycloak(req)).thenReturn(dto);

        ResponseEntity<com.ticketsystem.it_service_backend.dto.UserCreationResponseDTO> response =
                userController.createUser(req);

        assertEquals(201, response.getStatusCode().value());
        assertEquals("kc-id", response.getBody().getKeycloakId());
    }

    @Test
    void getAssignableRoles_returnsNames() {
        org.keycloak.representations.idm.RoleRepresentation r1 = new org.keycloak.representations.idm.RoleRepresentation();
        r1.setName("AGENT");
        org.keycloak.representations.idm.RoleRepresentation r2 = new org.keycloak.representations.idm.RoleRepresentation();
        r2.setName("CUSTOMER");
        when(keycloakAdminService.getAssignableRoles()).thenReturn(List.of(r1, r2));

        ResponseEntity<List<String>> response = userController.getAssignableRoles();

        assertEquals(200, response.getStatusCode().value());
        org.assertj.core.api.Assertions.assertThat(response.getBody()).containsExactly("AGENT", "CUSTOMER");
    }
}
