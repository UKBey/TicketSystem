package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentCapacityDTO;
import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private AgentProductLimitRepository agentProductLimitRepository;
    @Mock
    private TicketClaimRepository ticketClaimRepository;
    @Mock
    private KeycloakAdminService keycloakAdminService;

    @InjectMocks
    private UserService userService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(55L).name("Support").isActive(true).build();
        user = User.builder()
                .id("agent-1")
                .email("agent@example.com")
                .fullName("Agent One")
                .role("AGENT")
                .authorizedProducts(new ArrayList<>())
                .build();
    }

    @Test
    void syncUser_savesAndReturnsUser() {
        when(userRepository.save(any(User.class))).thenReturn(user);

        User result = userService.syncUser(user);

        assertEquals("agent-1", result.getId());
        verify(userRepository).save(user);
    }

    @Test
    void assignProductToUser_addsProductWhenMissing() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(productRepository.findById(55L)).thenReturn(Optional.of(product));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.assignProductToUser("agent-1", 55L);

        assertEquals(1, result.getAuthorizedProducts().size());
        assertEquals(55L, result.getAuthorizedProducts().get(0).getId());
        verify(userRepository).save(user);
    }

    @Test
    void assignProductToUser_doesNotDuplicateProduct() {
        user.setAuthorizedProducts(new ArrayList<>(List.of(product)));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(productRepository.findById(55L)).thenReturn(Optional.of(product));

        User result = userService.assignProductToUser("agent-1", 55L);

        assertEquals(1, result.getAuthorizedProducts().size());
    }

    @Test
    void removeProductFromUser_removesMatchingProduct() {
        user.setAuthorizedProducts(new ArrayList<>(List.of(product)));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.removeProductFromUser("agent-1", 55L);

        assertEquals(0, result.getAuthorizedProducts().size());
        verify(userRepository).save(user);
    }

    @Test
    void getUserById_whenMissing_throwsRuntimeException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.getUserById("missing"));
    }

    // -------------------------------------------------------------------------
    // syncUser — existing user (update path)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("syncUser mevcut kullanıcıyı günceller (update path)")
    void syncUser_whenUserExists_updatesFields() {
        User existing = User.builder().id("agent-1").email("old@example.com").fullName("Old Name").role("AGENT").build();
        User incoming = User.builder().id("agent-1").email("new@example.com").fullName("New Name").role("ADMIN").build();

        when(userRepository.findById("agent-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.syncUser(incoming);

        assertEquals("new@example.com", result.getEmail());
        assertEquals("New Name", result.getFullName());
        assertEquals("ADMIN", result.getRole());
        verify(userRepository).save(existing);
    }

    // -------------------------------------------------------------------------
    // getAgents / getAllUsers
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getAgents → AGENT rolündeki kullanıcıları döner")
    void getAgents_returnsAgentList() {
        when(userRepository.findByRole("AGENT")).thenReturn(List.of(user));

        List<User> result = userService.getAgents();

        assertThat(result).hasSize(1).extracting(User::getId).containsExactly("agent-1");
    }

    @Test
    @DisplayName("getAllUsers → tüm kullanıcıları döner")
    void getAllUsers_returnsAllUsers() {
        User customer = User.builder().id("customer-1").role("CUSTOMER").build();
        when(userRepository.findAll()).thenReturn(List.of(user, customer));

        List<User> result = userService.getAllUsers();

        assertThat(result).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // getUsersFiltered
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getUsersFiltered → null search ve null role trim edilir")
    void getUsersFiltered_nullParams_usesNullInQuery() {
        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findFiltered(eq(false), eq(List.of("__none__")), isNull(), eq(false), any(Pageable.class))).thenReturn(page);

        Page<User> result = userService.getUsersFiltered(null, null, false, 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("getUsersFiltered → search ve role trim edilir ve sorguya geçirilir")
    void getUsersFiltered_withParams_passesTrimmmedValues() {
        Page<User> page = new PageImpl<>(List.of(user));
        when(userRepository.findFiltered(eq(true), eq(List.of("AGENT")), eq("agent"), eq(false), any(Pageable.class))).thenReturn(page);

        Page<User> result = userService.getUsersFiltered("  agent  ", List.of("AGENT"), false, 0, 10);

        assertThat(result.getContent()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // getAgentsWithCapacity
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getAgentsWithCapacity → agent yok → boş liste")
    void getAgentsWithCapacity_noAgents_returnsEmpty() {
        when(userRepository.findByRoleAndAuthorizedProductsId("AGENT", 10L)).thenReturn(List.of());
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        List<AgentCapacityDTO> result = userService.getAgentsWithCapacity(10L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAgentsWithCapacity → ürün bulunamadıysa EntityNotFoundException")
    void getAgentsWithCapacity_productNotFound_throws() {
        when(userRepository.findByRoleAndAuthorizedProductsId("AGENT", 99L)).thenReturn(List.of());
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getAgentsWithCapacity(99L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("getAgentsWithCapacity → limitsiz product, isFull=false döner")
    void getAgentsWithCapacity_noLimit_isFullFalse() {
        Product unlimited = Product.builder().id(10L).maxActiveTickets(null).build();
        when(userRepository.findByRoleAndAuthorizedProductsId("AGENT", 10L)).thenReturn(List.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(unlimited));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L)).thenReturn(Optional.empty());
        when(ticketClaimRepository.countActiveTicketsByAgentAndProduct("agent-1", 10L)).thenReturn(3L);

        List<AgentCapacityDTO> result = userService.getAgentsWithCapacity(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getIsFull()).isFalse();
        assertThat(result.get(0).getCurrentActiveTickets()).isEqualTo(3L);
    }

    @Test
    @DisplayName("getAgentsWithCapacity → custom limit dolu, isFull=true döner")
    void getAgentsWithCapacity_customLimitReached_isFullTrue() {
        Product prod = Product.builder().id(10L).maxActiveTickets(10).build();
        AgentProductLimit customLimit = AgentProductLimit.builder()
                .agentId("agent-1").maxActiveTickets(2).useCustomLimit(true).build();

        when(userRepository.findByRoleAndAuthorizedProductsId("AGENT", 10L)).thenReturn(List.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(prod));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L))
                .thenReturn(Optional.of(customLimit));
        when(ticketClaimRepository.countActiveTicketsByAgentAndProduct("agent-1", 10L)).thenReturn(2L);

        List<AgentCapacityDTO> result = userService.getAgentsWithCapacity(10L);

        assertThat(result.get(0).getIsFull()).isTrue();
        assertThat(result.get(0).getMaxLimit()).isEqualTo(2);
    }

    @Test
    @DisplayName("getAgentsWithCapacity → custom limit var ama useCustomLimit=false → product limit kullanılır")
    void getAgentsWithCapacity_customLimitDisabled_usesProductLimit() {
        Product prod = Product.builder().id(10L).maxActiveTickets(5).build();
        AgentProductLimit customLimit = AgentProductLimit.builder()
                .agentId("agent-1").maxActiveTickets(2).useCustomLimit(false).build();

        when(userRepository.findByRoleAndAuthorizedProductsId("AGENT", 10L)).thenReturn(List.of(user));
        when(productRepository.findById(10L)).thenReturn(Optional.of(prod));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L))
                .thenReturn(Optional.of(customLimit));
        when(ticketClaimRepository.countActiveTicketsByAgentAndProduct("agent-1", 10L)).thenReturn(3L);

        List<AgentCapacityDTO> result = userService.getAgentsWithCapacity(10L);

        assertThat(result.get(0).getMaxLimit()).isEqualTo(5);
        assertThat(result.get(0).getIsFull()).isFalse();
    }

    // -------------------------------------------------------------------------
    // createUserWithKeycloak — happy + conflict + compensating action
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createUserWithKeycloak → happy path → keycloak create + db sync + dto")
    void createUserWithKeycloak_happyPath_syncsLocalDb() {
        com.ticketsystem.it_service_backend.dto.CreateUserRequest req = new com.ticketsystem.it_service_backend.dto.CreateUserRequest();
        req.setUsername("john"); req.setEmail("john@x.com"); req.setFirstName("John"); req.setLastName("Doe");
        req.setPassword("Temp1234!"); req.setRoles(List.of("AGENT"));

        when(keycloakAdminService.existsByEmail("john@x.com")).thenReturn(false);
        when(keycloakAdminService.existsByUsername("john")).thenReturn(false);
        when(keycloakAdminService.createUser(req)).thenReturn("kc-id");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        var dto = userService.createUserWithKeycloak(req);

        assertThat(dto.getKeycloakId()).isEqualTo("kc-id");
        assertThat(dto.getFullName()).isEqualTo("John Doe");
        assertThat(dto.getAssignedRoles()).containsExactly("AGENT");
    }

    @Test
    @DisplayName("createUserWithKeycloak → email çakışması → UserAlreadyExistsException ve keycloak çağrılmaz")
    void createUserWithKeycloak_emailConflict_throws() {
        com.ticketsystem.it_service_backend.dto.CreateUserRequest req = new com.ticketsystem.it_service_backend.dto.CreateUserRequest();
        req.setUsername("john"); req.setEmail("john@x.com");
        when(keycloakAdminService.existsByEmail("john@x.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUserWithKeycloak(req))
                .isInstanceOf(com.ticketsystem.it_service_backend.exception.UserAlreadyExistsException.class)
                .extracting("field").isEqualTo("email");
        verify(keycloakAdminService, org.mockito.Mockito.never()).createUser(any());
    }

    @Test
    @DisplayName("createUserWithKeycloak → username çakışması → UserAlreadyExistsException")
    void createUserWithKeycloak_usernameConflict_throws() {
        com.ticketsystem.it_service_backend.dto.CreateUserRequest req = new com.ticketsystem.it_service_backend.dto.CreateUserRequest();
        req.setUsername("john"); req.setEmail("john@x.com");
        when(keycloakAdminService.existsByEmail("john@x.com")).thenReturn(false);
        when(keycloakAdminService.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUserWithKeycloak(req))
                .isInstanceOf(com.ticketsystem.it_service_backend.exception.UserAlreadyExistsException.class)
                .extracting("field").isEqualTo("username");
        verify(keycloakAdminService, org.mockito.Mockito.never()).createUser(any());
    }

    @Test
    @DisplayName("createUserWithKeycloak → DB sync hatası → compensating delete keycloak ve RuntimeException")
    void createUserWithKeycloak_dbFailure_compensatesAndRethrows() {
        com.ticketsystem.it_service_backend.dto.CreateUserRequest req = new com.ticketsystem.it_service_backend.dto.CreateUserRequest();
        req.setUsername("john"); req.setEmail("john@x.com"); req.setFirstName("J"); req.setLastName("D");
        req.setPassword("p"); req.setRoles(List.of("AGENT"));

        when(keycloakAdminService.existsByEmail("john@x.com")).thenReturn(false);
        when(keycloakAdminService.existsByUsername("john")).thenReturn(false);
        when(keycloakAdminService.createUser(req)).thenReturn("kc-id");
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> userService.createUserWithKeycloak(req))
                .isInstanceOf(RuntimeException.class);
        verify(keycloakAdminService).deleteUser("kc-id");
    }

    // -------------------------------------------------------------------------
    // resolveHighestRole — indirect via createUserWithKeycloak/updateUserRoles
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateUserRoles → ADMIN > MANAGER > LEAD_AGENT > AGENT > CUSTOMER önceliği (legacy ADMIN → ADMIN)")
    void updateUserRoles_resolvesAgentAdminAsHighest() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserRoles("agent-1", List.of("CUSTOMER", "ADMIN", "AGENT"), "actor-admin");

        // Legacy ADMIN now resolves to the new ADMIN role (highest priority).
        assertThat(result.getRole()).isEqualTo("ADMIN");
        verify(keycloakAdminService).updateUserRoles("agent-1", List.of("CUSTOMER", "ADMIN", "AGENT"));
    }

    @Test
    @DisplayName("updateUserRoles → BAŞKA admin'in rolleri 403 (admin başka admin'i düzenleyemez)")
    void updateUserRoles_otherAdmin_forbidden() {
        User adminTarget = User.builder().id("admin-9").role("ADMIN")
                .roles(new java.util.HashSet<>(List.of("ADMIN")))
                .build();
        when(userRepository.findById("admin-9")).thenReturn(Optional.of(adminTarget));

        org.springframework.web.server.ResponseStatusException ex = org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> userService.updateUserRoles("admin-9", List.of("AGENT"), "actor-admin"));

        assertThat(ex.getStatusCode().value()).isEqualTo(403);
        verify(keycloakAdminService, org.mockito.Mockito.never()).updateUserRoles(any(), any());
    }

    @Test
    @DisplayName("updateUserRoles → admin KENDİ rollerini düzenleyebilir (self)")
    void updateUserRoles_selfAdmin_allowed() {
        User adminSelf = User.builder().id("admin-9").role("ADMIN")
                .roles(new java.util.HashSet<>(List.of("ADMIN")))
                .build();
        when(userRepository.findById("admin-9")).thenReturn(Optional.of(adminSelf));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserRoles("admin-9", List.of("ADMIN", "MANAGER"), "admin-9");

        assertThat(result.getRole()).isEqualTo("ADMIN");
        verify(keycloakAdminService).updateUserRoles("admin-9", List.of("ADMIN", "MANAGER"));
    }

    @Test
    @DisplayName("updateUserRoles → MANAGER → MANAGER")
    void updateUserRoles_resolvesManager() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserRoles("agent-1", List.of("MANAGER", "CUSTOMER"), "actor-admin");

        assertThat(result.getRole()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("updateUserRoles → boş liste → role null")
    void updateUserRoles_emptyList_nullsRole() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserRoles("agent-1", List.of(), "actor-admin");

        assertThat(result.getRole()).isNull();
    }

    @Test
    @DisplayName("updateUserRoles → CUSTOMER → CUSTOMER")
    void updateUserRoles_customerOnly() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserRoles("agent-1", List.of("CUSTOMER"), "actor-admin");

        assertThat(result.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("updateUserRoles → bilinmeyen rol → role null")
    void updateUserRoles_unknownRole_nullsRole() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updateUserRoles("agent-1", List.of("UNKNOWN"), "actor-admin");

        assertThat(result.getRole()).isNull();
    }

    // -------------------------------------------------------------------------
    // getUserById
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getUserById → kullanıcı yok → RuntimeException")
    void getUserById_missing_throws() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById("ghost"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    @DisplayName("getUserById → bulundu → yetkili ürün koleksiyonu initialize edilir")
    void getUserById_existing_returnsUser() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));

        User result = userService.getUserById("agent-1");

        assertThat(result).isSameAs(user);
    }

    // -------------------------------------------------------------------------
    // assignProductToUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("assignProductToUser → yeni ürün → ekler ve save eder")
    void assignProductToUser_newProduct_addsToList() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(productRepository.findById(55L)).thenReturn(Optional.of(product));

        userService.assignProductToUser("agent-1", 55L);

        assertThat(user.getAuthorizedProducts()).containsExactly(product);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("assignProductToUser → zaten yetkili → save edilmez")
    void assignProductToUser_alreadyAssigned_skipsSave() {
        user.getAuthorizedProducts().add(product);
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(productRepository.findById(55L)).thenReturn(Optional.of(product));

        userService.assignProductToUser("agent-1", 55L);

        verify(userRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("assignProductToUser → ürün yok → RuntimeException")
    void assignProductToUser_productMissing_throws() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.assignProductToUser("agent-1", 99L))
                .isInstanceOf(RuntimeException.class);
    }

    // -------------------------------------------------------------------------
    // removeProductFromUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("removeProductFromUser → ürünü listeden kaldırır")
    void removeProductFromUser_removesAndSaves() {
        user.getAuthorizedProducts().add(product);
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        userService.removeProductFromUser("agent-1", 55L);

        assertThat(user.getAuthorizedProducts()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // updatePreferredLanguage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updatePreferredLanguage → en → user.preferredLanguage=en")
    void updatePreferredLanguage_en_saves() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updatePreferredLanguage("agent-1", "en");

        assertThat(result.getPreferredLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("updatePreferredLanguage → tr → user.preferredLanguage=tr")
    void updatePreferredLanguage_tr_saves() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updatePreferredLanguage("agent-1", "tr");

        assertThat(result.getPreferredLanguage()).isEqualTo("tr");
    }

    @Test
    @DisplayName("updatePreferredLanguage → desteklenmeyen dil → ResponseStatusException 400")
    void updatePreferredLanguage_invalidLang_throws() {
        assertThatThrownBy(() -> userService.updatePreferredLanguage("agent-1", "de"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    // -------------------------------------------------------------------------
    // updatePreferredTheme
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updatePreferredTheme → dark → user.preferredTheme=dark")
    void updatePreferredTheme_dark_saves() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updatePreferredTheme("agent-1", "dark");

        assertThat(result.getPreferredTheme()).isEqualTo("dark");
    }

    @Test
    @DisplayName("updatePreferredTheme → light → user.preferredTheme=light")
    void updatePreferredTheme_light_saves() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.updatePreferredTheme("agent-1", "light");

        assertThat(result.getPreferredTheme()).isEqualTo("light");
    }

    @Test
    @DisplayName("updatePreferredTheme → geçersiz değer → ResponseStatusException 400")
    void updatePreferredTheme_invalidTheme_throws() {
        assertThatThrownBy(() -> userService.updatePreferredTheme("agent-1", "neon"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    // -------------------------------------------------------------------------
    // deactivate / reactivate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("deactivateUser → keycloak disable + isActive=false")
    void deactivateUser_disablesUser() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.deactivateUser("agent-1");

        assertThat(result.getIsActive()).isFalse();
        verify(keycloakAdminService).setUserEnabled("agent-1", false);
    }

    @Test
    @DisplayName("reactivateUser → keycloak enable + isActive=true")
    void reactivateUser_enablesUser() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = userService.reactivateUser("agent-1");

        assertThat(result.getIsActive()).isTrue();
        verify(keycloakAdminService).setUserEnabled("agent-1", true);
    }
}
