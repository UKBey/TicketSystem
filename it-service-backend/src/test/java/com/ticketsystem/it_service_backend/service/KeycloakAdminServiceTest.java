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

    // =====================================================================
    // changeUserPassword — başarı / politika ihlali / diğer hata dalları
    // =====================================================================

    @Test
    void changeUserPassword_success_callsResetPassword() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("kc-1")).thenReturn(userResource);

        service.changeUserPassword("kc-1", "NewPass1!");

        verify(userResource).resetPassword(any(CredentialRepresentation.class));
    }

    @Test
    void changeUserPassword_policyViolation400_throwsInvalidPassword() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("kc-1")).thenReturn(userResource);
        jakarta.ws.rs.WebApplicationException ex =
                new jakarta.ws.rs.WebApplicationException(Response.status(400).entity("too weak").build());
        org.mockito.Mockito.doThrow(ex).when(userResource).resetPassword(any(CredentialRepresentation.class));

        assertThatThrownBy(() -> service.changeUserPassword("kc-1", "weak"))
                .isInstanceOf(com.ticketsystem.it_service_backend.exception.InvalidPasswordException.class);
    }

    @Test
    void changeUserPassword_otherError500_rethrows() {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("kc-1")).thenReturn(userResource);
        jakarta.ws.rs.WebApplicationException ex =
                new jakarta.ws.rs.WebApplicationException(Response.status(500).entity("boom").build());
        org.mockito.Mockito.doThrow(ex).when(userResource).resetPassword(any(CredentialRepresentation.class));

        assertThatThrownBy(() -> service.changeUserPassword("kc-1", "x"))
                .isInstanceOf(jakarta.ws.rs.WebApplicationException.class);
    }

    // =====================================================================
    // verifyPassword — 200 / non-200 / exception (httpClient mock)
    // =====================================================================

    @SuppressWarnings("unchecked")
    private void injectHttpClient(int status, boolean throwError) throws Exception {
        java.net.http.HttpClient mockClient = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        if (throwError) {
            when(mockClient.send(any(), any(java.net.http.HttpResponse.BodyHandler.class)))
                    .thenThrow(new java.io.IOException("connection refused"));
        } else {
            java.net.http.HttpResponse<String> resp = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
            when(resp.statusCode()).thenReturn(status);
            when(mockClient.send(any(), any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn(resp);
        }
        ReflectionTestUtils.setField(service, "httpClient", mockClient);
        ReflectionTestUtils.setField(service, "serverUrl", "http://keycloak:8080/auth");
        ReflectionTestUtils.setField(service, "userClientId", "ticket-frontend");
    }

    @Test
    void verifyPassword_status200_true() throws Exception {
        injectHttpClient(200, false);
        assertThat(service.verifyPassword("john", "pass")).isTrue();
    }

    @Test
    void verifyPassword_status401_false() throws Exception {
        injectHttpClient(401, false);
        assertThat(service.verifyPassword("john", "wrong")).isFalse();
    }

    @Test
    void verifyPassword_exception_false() throws Exception {
        injectHttpClient(0, true);
        assertThat(service.verifyPassword("john", "pass")).isFalse();
    }

    // =====================================================================
    // updateUserProfile — email değişimi / çakışma dalları
    // =====================================================================

    private UserRepresentation rep(String id, String email) {
        UserRepresentation u = new UserRepresentation();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private void stubProfileChain(UserRepresentation current) {
        when(keycloakAdminClient.realm("TicketSystemRealm")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get("kc-1")).thenReturn(userResource);
        when(userResource.toRepresentation()).thenReturn(current);
    }

    @Test
    void updateUserProfile_emailUnchanged_updatesWithoutSearch() {
        stubProfileChain(rep("kc-1", "same@example.com"));

        service.updateUserProfile("kc-1", "John", "Doe", "same@example.com");

        verify(userResource).update(any(UserRepresentation.class));
        verify(usersResource, never()).searchByEmail(any(), any());
    }

    @Test
    void updateUserProfile_emailNull_updatesWithoutSearch() {
        stubProfileChain(rep("kc-1", "old@example.com"));

        service.updateUserProfile("kc-1", "John", "Doe", null);

        verify(userResource).update(any(UserRepresentation.class));
        verify(usersResource, never()).searchByEmail(any(), any());
    }

    @Test
    void updateUserProfile_emailChangedNoConflict_updates() {
        stubProfileChain(rep("kc-1", "old@example.com"));
        // searchByEmail başka kullanıcı dönmez (yalnızca kendisi)
        when(usersResource.searchByEmail("new@example.com", true)).thenReturn(List.of(rep("kc-1", "new@example.com")));

        service.updateUserProfile("kc-1", "John", "Doe", "new@example.com");

        verify(userResource).update(any(UserRepresentation.class));
    }

    @Test
    void updateUserProfile_emailChangedConflict_throwsUserAlreadyExists() {
        stubProfileChain(rep("kc-1", "old@example.com"));
        when(usersResource.searchByEmail("taken@example.com", true))
                .thenReturn(List.of(rep("other-id", "taken@example.com")));

        assertThatThrownBy(() -> service.updateUserProfile("kc-1", "John", "Doe", "taken@example.com"))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userResource, never()).update(any());
    }
}
