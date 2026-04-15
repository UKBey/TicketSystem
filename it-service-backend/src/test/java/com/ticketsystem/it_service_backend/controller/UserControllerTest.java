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
    void syncCurrentUser_mapsRoleAndReturnsDto() {
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
        when(userService.getAllUsers()).thenReturn(List.of(
                User.builder().id("u-1").fullName("User One").role("CUSTOMER").build(),
                User.builder().id("u-2").fullName("User Two").role("AGENT").build()
        ));

        ResponseEntity<List<UserDTO>> response = userController.getAllUsers();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
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
