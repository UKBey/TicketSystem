package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.CreateUserRequest;
import com.ticketsystem.it_service_backend.exception.UserAlreadyExistsException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakAdminServiceTest {

    @Mock private Keycloak keycloakAdminClient;
    @Mock private RealmResource realmResource;
    @Mock private UsersResource usersResource;
    @Mock private UserResource userResource;
    @Mock private RolesResource rolesResource;
    @Mock private RoleResource roleResource;
    @Mock private RoleScopeResource roleScopeResource;
    @Mock private Response response;

    private KeycloakAdminService service;

    @BeforeEach
    void setUp() {
        service = new KeycloakAdminService(keycloakAdminClient);
        ReflectionTestUtils.setField(service, "realm", "TicketSystemRealm");
    }

    private CreateUserRequest buildRequest() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("john.doe");
        req.setEmail("john@example.com");
        req.setFirstName("John");
        req.setLastName("Doe");
        req.setPassword("Temp1234!");
        req.setRoles(List.of("AGENT"));
        return req;
    }

    private void stubUserCreationSuccess(String keycloakId) {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(201);
        when(response.getLocation()).thenReturn(URI.create("http://keycloak/admin/realms/r/users/" + keycloakId));
        when(usersResource.get(keycloakId)).thenReturn(userResource);
    }

    // ---- createUser -----------------------------------------------------------

    @Test
    void createUser_success_returnsIdAndAssignsRolesAndPassword() {
        stubUserCreationSuccess("abc-123");

        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("AGENT")).thenReturn(roleResource);
        RoleRepresentation agent = new RoleRepresentation();
        agent.setName("AGENT");
        when(roleResource.toRepresentation()).thenReturn(agent);
        when(userResource.roles()).thenReturn(org.mockito.Mockito.mock(org.keycloak.admin.client.resource.RoleMappingResource.class, withRealm()));
        when(userResource.roles().realmLevel()).thenReturn(roleScopeResource);

        String id = service.createUser(buildRequest());

        assertThat(id).isEqualTo("abc-123");

        ArgumentCaptor<UserRepresentation> userCaptor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(usersResource).create(userCaptor.capture());
        UserRepresentation captured = userCaptor.getValue();
        assertThat(captured.getUsername()).isEqualTo("john.doe");
        assertThat(captured.getEmail()).isEqualTo("john@example.com");
        assertThat(captured.isEnabled()).isTrue();
        assertThat(captured.isEmailVerified()).isTrue();

        ArgumentCaptor<CredentialRepresentation> credCaptor = ArgumentCaptor.forClass(CredentialRepresentation.class);
        verify(userResource).resetPassword(credCaptor.capture());
        assertThat(credCaptor.getValue().getValue()).isEqualTo("Temp1234!");
        assertThat(credCaptor.getValue().isTemporary()).isTrue();

        verify(roleScopeResource).add(anyList());
    }

    @Test
    void createUser_emptyRoles_skipsRoleAssignment() {
        stubUserCreationSuccess("abc-empty");

        CreateUserRequest req = buildRequest();
        req.setRoles(List.of());

        String id = service.createUser(req);

        assertThat(id).isEqualTo("abc-empty");
        verify(realmResource, never()).roles();
    }

    @Test
    void createUser_nullRoles_skipsRoleAssignment() {
        stubUserCreationSuccess("abc-null");
        CreateUserRequest req = buildRequest();
        req.setRoles(null);

        service.createUser(req);

        verify(realmResource, never()).roles();
    }

    @Test
    void createUser_roleAssignmentFails_swallowsExceptionAndReturnsId() {
        stubUserCreationSuccess("abc-roleboom");

        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("AGENT")).thenThrow(new RuntimeException("role missing"));

        String id = service.createUser(buildRequest());

        assertThat(id).isEqualTo("abc-roleboom");
    }

    @Test
    void createUser_returns409_emailConflict_throwsUserAlreadyExists() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(409);
        UserRepresentation emailHit = new UserRepresentation();
        emailHit.setEmail("john@example.com");
        when(usersResource.searchByEmail("john@example.com", true)).thenReturn(List.of(emailHit));

        assertThatThrownBy(() -> service.createUser(buildRequest()))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("email");
    }

    @Test
    void createUser_returns409_usernameConflict_throwsUserAlreadyExists() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(409);
        when(usersResource.searchByEmail("john@example.com", true)).thenReturn(List.of());
        UserRepresentation usernameHit = new UserRepresentation();
        usernameHit.setUsername("john.doe");
        when(usersResource.searchByUsername("john.doe", true)).thenReturn(List.of(usernameHit));

        assertThatThrownBy(() -> service.createUser(buildRequest()))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("username");
    }

    @Test
    void createUser_returns409_unknownField_throwsGenericConflict() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(409);
        when(usersResource.searchByEmail("john@example.com", true)).thenReturn(List.of());
        when(usersResource.searchByUsername("john.doe", true)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createUser(buildRequest()))
                .isInstanceOf(UserAlreadyExistsException.class);
    }

    @Test
    void createUser_returns500_throwsRuntimeException() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any(UserRepresentation.class))).thenReturn(response);
        when(response.getStatus()).thenReturn(500);
        when(response.readEntity(String.class)).thenReturn("internal error");

        assertThatThrownBy(() -> service.createUser(buildRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500");
    }

    // ---- getAssignableRoles ---------------------------------------------------

    @Test
    void getAssignableRoles_filtersSystemAndDefaultRoles() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.roles()).thenReturn(rolesResource);

        RoleRepresentation agent = role("AGENT");
        RoleRepresentation customer = role("CUSTOMER");
        RoleRepresentation offline = role("offline_access");
        RoleRepresentation uma = role("uma_authorization");
        RoleRepresentation defaultRoles = role("default-roles-TicketSystemRealm");
        when(rolesResource.list()).thenReturn(List.of(agent, customer, offline, uma, defaultRoles));

        List<RoleRepresentation> result = service.getAssignableRoles();

        assertThat(result).extracting(RoleRepresentation::getName)
                .containsExactlyInAnyOrder("AGENT", "CUSTOMER");
    }

    // ---- existsByEmail / existsByUsername -------------------------------------

    @Test
    void existsByEmail_returnsTrueWhenFound() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.searchByEmail("x@y", true)).thenReturn(List.of(new UserRepresentation()));

        assertThat(service.existsByEmail("x@y")).isTrue();
    }

    @Test
    void existsByEmail_returnsFalseWhenEmpty() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.searchByEmail("missing@y", true)).thenReturn(List.of());

        assertThat(service.existsByEmail("missing@y")).isFalse();
    }

    @Test
    void existsByUsername_returnsTrueWhenFound() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.searchByUsername("u", true)).thenReturn(List.of(new UserRepresentation()));

        assertThat(service.existsByUsername("u")).isTrue();
    }

    @Test
    void existsByUsername_returnsFalseWhenEmpty() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.searchByUsername("u", true)).thenReturn(List.of());

        assertThat(service.existsByUsername("u")).isFalse();
    }

    // ---- updateUserRoles ------------------------------------------------------

    @Test
    void updateUserRoles_removesAssignableAndAddsNewRoles() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("abc-id")).thenReturn(userResource);

        org.keycloak.admin.client.resource.RoleMappingResource roleMapping =
                org.mockito.Mockito.mock(org.keycloak.admin.client.resource.RoleMappingResource.class);
        when(userResource.roles()).thenReturn(roleMapping);
        when(roleMapping.realmLevel()).thenReturn(roleScopeResource);

        RoleRepresentation oldAgent = role("AGENT");
        RoleRepresentation offline = role("offline_access");
        when(roleScopeResource.listAll()).thenReturn(List.of(oldAgent, offline));

        when(realmResource.roles()).thenReturn(rolesResource);
        RoleRepresentation customer = role("CUSTOMER");
        when(rolesResource.get("CUSTOMER")).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(customer);

        service.updateUserRoles("abc-id", List.of("CUSTOMER"));

        ArgumentCaptor<List<RoleRepresentation>> removed = removeCaptor();
        verify(roleScopeResource).remove(removed.capture());
        assertThat(removed.getValue()).extracting(RoleRepresentation::getName).containsExactly("AGENT");

        ArgumentCaptor<List<RoleRepresentation>> added = removeCaptor();
        verify(roleScopeResource).add(added.capture());
        assertThat(added.getValue()).extracting(RoleRepresentation::getName).containsExactly("CUSTOMER");
    }

    @Test
    void updateUserRoles_emptyNewRoles_removesButDoesNotAdd() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("abc-id")).thenReturn(userResource);

        org.keycloak.admin.client.resource.RoleMappingResource roleMapping =
                org.mockito.Mockito.mock(org.keycloak.admin.client.resource.RoleMappingResource.class);
        when(userResource.roles()).thenReturn(roleMapping);
        when(roleMapping.realmLevel()).thenReturn(roleScopeResource);

        when(roleScopeResource.listAll()).thenReturn(List.of(role("AGENT")));

        service.updateUserRoles("abc-id", List.of());

        verify(roleScopeResource).remove(anyList());
        verify(roleScopeResource, never()).add(anyList());
    }

    @Test
    void updateUserRoles_noExistingAssignableRoles_skipsRemove() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("abc-id")).thenReturn(userResource);

        org.keycloak.admin.client.resource.RoleMappingResource roleMapping =
                org.mockito.Mockito.mock(org.keycloak.admin.client.resource.RoleMappingResource.class);
        when(userResource.roles()).thenReturn(roleMapping);
        when(roleMapping.realmLevel()).thenReturn(roleScopeResource);

        when(roleScopeResource.listAll()).thenReturn(List.of(role("offline_access")));

        when(realmResource.roles()).thenReturn(rolesResource);
        when(rolesResource.get("AGENT")).thenReturn(roleResource);
        when(roleResource.toRepresentation()).thenReturn(role("AGENT"));

        service.updateUserRoles("abc-id", List.of("AGENT"));

        verify(roleScopeResource, never()).remove(anyList());
        verify(roleScopeResource).add(anyList());
    }

    // ---- setUserEnabled -------------------------------------------------------

    @Test
    void setUserEnabled_callsUpdateWithFlag() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("u-1")).thenReturn(userResource);

        service.setUserEnabled("u-1", false);

        ArgumentCaptor<UserRepresentation> captor = ArgumentCaptor.forClass(UserRepresentation.class);
        verify(userResource).update(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
    }

    // ---- deleteUser -----------------------------------------------------------

    @Test
    void deleteUser_success_invokesDelete() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);

        service.deleteUser("u-2");

        verify(usersResource).delete("u-2");
    }

    @Test
    void deleteUser_swallowsException() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        org.mockito.Mockito.doThrow(new RuntimeException("kc down")).when(usersResource).delete("u-3");

        // Should NOT throw — exception is logged and swallowed
        service.deleteUser("u-3");
    }

    // ---- helpers --------------------------------------------------------------

    private RoleRepresentation role(String name) {
        RoleRepresentation r = new RoleRepresentation();
        r.setName(name);
        return r;
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<RoleRepresentation>> removeCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }

    private static org.mockito.stubbing.Answer<org.keycloak.admin.client.resource.RoleMappingResource> withRealm() {
        return invocation -> null;
    }
}
