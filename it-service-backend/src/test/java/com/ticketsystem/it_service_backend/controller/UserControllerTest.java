package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.UserDTO;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.User;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController(userService);
    }

    @Test
    void syncCurrentUser_mapsAgentAdminRoleAndReturnsDto() {
        User synced = User.builder()
                .id("admin-1")
                .email("admin@example.com")
                .fullName("Ada Admin")
                .role("AGENT_ADMIN")
                .build();
        when(userService.syncUser(org.mockito.ArgumentMatchers.any(User.class))).thenReturn(synced);

        Jwt jwt = jwtWithRoles("admin-1", List.of("AGENT_ADMIN"));
        when(jwt.getClaimAsString("email")).thenReturn("admin@example.com");
        when(jwt.getClaimAsString("given_name")).thenReturn("Ada");
        when(jwt.getClaimAsString("family_name")).thenReturn("Admin");

        ResponseEntity<UserDTO> response = userController.syncCurrentUser(jwt);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("admin-1", response.getBody().getId());
        assertEquals("AGENT_ADMIN", response.getBody().getRole());
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
        when(userService.getUsersFiltered(null, null, 0, 20)).thenReturn(page);

        ResponseEntity<java.util.Map<String, Object>> response = userController.getAllUsers(null, null, 0, 20);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        java.util.List<?> content = (java.util.List<?>) response.getBody().get("content");
        assertEquals(2, content.size());
    }

    @Test
    void assignProductToUser_returnsUpdatedUser() {
        Product product = Product.builder().id(55L).name("Support").build();
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
}
