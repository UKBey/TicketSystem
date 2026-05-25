package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.CreateUserRequest;
import com.ticketsystem.it_service_backend.exception.InvalidPasswordException;
import com.ticketsystem.it_service_backend.exception.UserAlreadyExistsException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Performs user and role management operations via the Keycloak Admin REST API.
 *
 * <p>This service covers only Keycloak-side operations. Local database
 * synchronization is handled by {@link UserService#createUserWithKeycloak}.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final Keycloak keycloakAdminClient;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    /**
     * Public client used to verify passwords via direct-grant. This is the
     * clientId used by the frontend — {@code directAccessGrantsEnabled: true} must be set.
     */
    @Value("${keycloak.user-client-id:ticket-frontend}")
    private String userClientId;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * System roles — filtered out from the assignable role list.
     * These roles belong to Keycloak internals and must not be assigned to users.
     */
    private static final Set<String> SYSTEM_ROLES = Set.of(
            "offline_access",
            "uma_authorization"
    );

    // -------------------------------------------------------------------------
    // Kullanıcı oluşturma
    // -------------------------------------------------------------------------

    /**
     * Creates a new user in the Keycloak realm, assigns a temporary password,
     * and maps the requested realm roles.
     *
     * @param request user details and roles to assign
     * @return the Keycloak UUID of the created user
     * @throws UserAlreadyExistsException when email or username conflicts
     * @throws RuntimeException           when the Keycloak API returns an unexpected error
     */
    public String createUser(CreateUserRequest request) {
        log.info("Keycloak'ta kullanıcı oluşturuluyor. Username: {}, Email: {}",
                request.getUsername(), request.getEmail());

        RealmResource realmResource = keycloakAdminClient.realm(realm);
        UsersResource usersResource = realmResource.users();

        // 1. UserRepresentation oluştur
        UserRepresentation userRep = buildUserRepresentation(request);

        // 2. Kullanıcıyı Keycloak'a kaydet
        String keycloakId;
        try (Response response = usersResource.create(userRep)) {
            int status = response.getStatus();
            log.debug("Keycloak kullanıcı oluşturma yanıtı: HTTP {}", status);

            if (status == 409) {
                // Keycloak hangi alanın çakıştığını response body'de belirtmez;
                // email ve username ayrı ayrı kontrol edilerek doğru alan belirlenir.
                log.warn("Keycloak 409 Conflict. Username: {}, Email: {}",
                        request.getUsername(), request.getEmail());
                resolveConflict(usersResource, request);
                // resolveConflict her zaman exception fırlatır; bu satıra ulaşılmaz.
                throw new RuntimeException("Conflict could not be resolved");
            }

            if (status != 201) {
                String body = response.readEntity(String.class);
                log.error("Keycloak kullanıcı oluşturma başarısız. HTTP {}: {}", status, body);
                throw new RuntimeException(
                        "Keycloak user creation failed with status " + status + ": " + body);
            }

            // Location header: .../users/{uuid}
            String location = response.getLocation().toString();
            keycloakId = location.substring(location.lastIndexOf('/') + 1);
            log.info("Keycloak kullanıcısı oluşturuldu. ID: {}", keycloakId);
        }

        // 3. Şifre ata — varsayılan geçici (ilk girişte değişmek zorunda).
        //    request.temporaryPassword == false ise kalıcı atanır (data-generator akışı).
        boolean temporary = request.getTemporaryPassword() == null || request.getTemporaryPassword();
        setUserPassword(usersResource, keycloakId, request.getPassword(), temporary);

        // 4. Realm rollerini ata — başarısız olursa kullanıcı rollsuz kalır, DB kaydı yine yapılır
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            try {
                assignRealmRoles(realmResource, keycloakId, request.getRoles());
            } catch (Exception e) {
                log.error("Rol ataması başarısız. Kullanıcı Keycloak'ta oluşturuldu ancak rol atanamadı. ID: {}, Hata: {}",
                        keycloakId, e.getMessage());
                // Exception yutulur; kullanıcı null role ile DB'ye kaydedilir.
                // Admin daha sonra Edit Role ile düzeltebilir.
            }
        }

        return keycloakId;
    }

    // -------------------------------------------------------------------------
    // Rol listeleme
    // -------------------------------------------------------------------------

    /**
     * Returns the roles in the realm that can be assigned to users.
     * Keycloak system roles ({@code offline_access}, {@code uma_authorization},
     * {@code default-roles-*}) are excluded from the list.
     *
     * @return list of assignable {@link RoleRepresentation} entries
     */
    public List<RoleRepresentation> getAssignableRoles() {
        log.debug("Atanabilir roller çekiliyor. Realm: {}", realm);

        List<RoleRepresentation> allRoles = keycloakAdminClient
                .realm(realm)
                .roles()
                .list();

        List<RoleRepresentation> assignable = allRoles.stream()
                .filter(role -> !SYSTEM_ROLES.contains(role.getName()))
                .filter(role -> !role.getName().startsWith("default-roles-"))
                .toList();

        log.debug("Toplam {} rol bulundu, {} tanesi atanabilir.", allRoles.size(), assignable.size());
        return assignable;
    }

    // -------------------------------------------------------------------------
    // Çakışma kontrolü
    // -------------------------------------------------------------------------

    /**
     * Checks whether a user with the given email already exists in Keycloak.
     */
    public boolean existsByEmail(String email) {
        boolean exists = !keycloakAdminClient.realm(realm)
                .users()
                .searchByEmail(email, true)
                .isEmpty();
        log.debug("Email çakışma kontrolü. Email: {}, Mevcut: {}", email, exists);
        return exists;
    }

    /**
     * Checks whether a user with the given username already exists in Keycloak.
     */
    public boolean existsByUsername(String username) {
        boolean exists = !keycloakAdminClient.realm(realm)
                .users()
                .searchByUsername(username, true)
                .isEmpty();
        log.debug("Username çakışma kontrolü. Username: {}, Mevcut: {}", username, exists);
        return exists;
    }

    // -------------------------------------------------------------------------
    // Rol güncelleme
    // -------------------------------------------------------------------------

    /**
     * Updates the user's Keycloak realm roles.
     *
     * <p>All currently assignable roles are removed first, then the new roles are
     * assigned. This approach ensures an idempotent update.
     *
     * @param keycloakId Keycloak UUID of the user to update
     * @param newRoles   list of new role names to assign
     */
    public void updateUserRoles(String keycloakId, List<String> newRoles) {
        log.info("Kullanıcı rolleri güncelleniyor. ID: {}, Yeni roller: {}", keycloakId, newRoles);

        RealmResource realmResource = keycloakAdminClient.realm(realm);

        // 1. Mevcut realm rollerini çek
        List<RoleRepresentation> currentRoles = realmResource
                .users().get(keycloakId)
                .roles().realmLevel().listAll();

        // 2. Sistem rolleri hariç mevcut rolleri kaldır
        List<RoleRepresentation> rolesToRemove = currentRoles.stream()
                .filter(role -> !SYSTEM_ROLES.contains(role.getName()))
                .filter(role -> !role.getName().startsWith("default-roles-"))
                .toList();

        if (!rolesToRemove.isEmpty()) {
            realmResource.users().get(keycloakId).roles().realmLevel().remove(rolesToRemove);
            log.debug("Mevcut roller kaldırıldı. ID: {}, Kaldırılan: {}",
                    keycloakId, rolesToRemove.stream().map(RoleRepresentation::getName).toList());
        }

        // 3. Yeni rolleri ata
        if (newRoles != null && !newRoles.isEmpty()) {
            assignRealmRoles(realmResource, keycloakId, newRoles);
        }

        log.info("Kullanıcı rolleri başarıyla güncellendi. ID: {}", keycloakId);
    }

    // -------------------------------------------------------------------------
    // Kullanıcı aktif/pasif durumu
    // -------------------------------------------------------------------------

    /**
     * Updates the user's {@code enabled} status in Keycloak.
     * A disabled user cannot log in, and existing sessions are invalidated
     * on the next token refresh.
     *
     * @param keycloakId Keycloak UUID of the user to update
     * @param enabled    {@code true} to activate, {@code false} to deactivate
     */
    public void setUserEnabled(String keycloakId, boolean enabled) {
        log.info("Keycloak kullanıcı durumu güncelleniyor. ID: {}, enabled: {}", keycloakId, enabled);
        UserRepresentation userRep = new UserRepresentation();
        userRep.setEnabled(enabled);
        keycloakAdminClient.realm(realm).users().get(keycloakId).update(userRep);
        log.info("Keycloak kullanıcı durumu güncellendi. ID: {}, enabled: {}", keycloakId, enabled);
    }

    // -------------------------------------------------------------------------
    // Profil güncelleme (self-service)
    // -------------------------------------------------------------------------

    /**
     * Updates the user's identity attributes on the Keycloak side (firstName, lastName, email).
     * If the email changes, {@code emailVerified} is reset to false so Keycloak
     * triggers its verification flow on the next login.
     *
     * @throws UserAlreadyExistsException if the new email is already registered to another user
     */
    public void updateUserProfile(String keycloakId, String firstName, String lastName, String email) {
        log.info("Keycloak profil güncelleniyor. ID: {}", keycloakId);

        UserRepresentation current = keycloakAdminClient.realm(realm).users().get(keycloakId).toRepresentation();

        boolean emailChanged = email != null && !email.equalsIgnoreCase(current.getEmail());
        if (emailChanged) {
            // Aynı email başka kullanıcıda mevcut mu?
            List<UserRepresentation> matches = keycloakAdminClient.realm(realm).users().searchByEmail(email, true);
            boolean conflict = matches.stream().anyMatch(u -> !u.getId().equals(keycloakId));
            if (conflict) {
                log.warn("Email çakışması: {} başka bir kullanıcıda kayıtlı.", email);
                throw new UserAlreadyExistsException("email", email);
            }
        }

        current.setFirstName(firstName);
        current.setLastName(lastName);
        current.setEmail(email);
        if (emailChanged) {
            current.setEmailVerified(false);
        }

        keycloakAdminClient.realm(realm).users().get(keycloakId).update(current);
        log.info("Keycloak profil güncellendi. ID: {}, emailChanged: {}", keycloakId, emailChanged);
    }

    // -------------------------------------------------------------------------
    // Self-service şifre değiştirme
    // -------------------------------------------------------------------------

    /**
     * Verifies the given username / password combination by sending a direct-grant
     * request to Keycloak's token endpoint. A 200 response means the password is
     * correct; 401/400 means it is invalid.
     */
    public boolean verifyPassword(String username, String password) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";
        String body = "grant_type=password" +
                "&client_id=" + URLEncoder.encode(userClientId, StandardCharsets.UTF_8) +
                "&username="  + URLEncoder.encode(username,     StandardCharsets.UTF_8) +
                "&password="  + URLEncoder.encode(password,     StandardCharsets.UTF_8) +
                "&scope=openid";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() == 200;
            log.debug("Şifre doğrulaması. Kullanıcı: {}, Sonuç: {}", username, ok ? "OK" : "RED");
            return ok;
        } catch (Exception e) {
            log.error("Şifre doğrulama isteği başarısız. Kullanıcı: {}", username, e);
            return false;
        }
    }

    /**
     * Changes the user's password permanently (non-temporary).
     * Throws {@link InvalidPasswordException} if the new password violates the
     * realm-level password policy in Keycloak.
     */
    public void changeUserPassword(String keycloakId, String newPassword) {
        log.info("Şifre güncelleniyor. ID: {}", keycloakId);
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(newPassword);
        credential.setTemporary(false);
        try {
            keycloakAdminClient.realm(realm).users().get(keycloakId).resetPassword(credential);
            log.info("Şifre başarıyla güncellendi. ID: {}", keycloakId);
        } catch (WebApplicationException e) {
            Response r = e.getResponse();
            int status = r != null ? r.getStatus() : -1;
            String responseBody = r != null ? r.readEntity(String.class) : "";
            if (status == 400) {
                log.warn("Keycloak şifre politikası ihlali. ID: {}, Body: {}", keycloakId, responseBody);
                throw new InvalidPasswordException(responseBody);
            }
            log.error("Keycloak şifre güncelleme başarısız. HTTP {}, Body: {}", status, responseBody);
            throw e;
        }
    }

    // -------------------------------------------------------------------------
    // TOTP credentials (self-service 2FA management)
    // -------------------------------------------------------------------------

    /**
     * Returns all TOTP (authenticator app) credentials registered for the user.
     * Other credential types (password, recovery-code, etc.) are filtered out.
     */
    public List<CredentialRepresentation> listOtpCredentials(String keycloakId) {
        log.debug("TOTP credential listesi isteniyor. ID: {}", keycloakId);
        return keycloakAdminClient.realm(realm).users().get(keycloakId).credentials().stream()
                .filter(c -> "otp".equalsIgnoreCase(c.getType()))
                .toList();
    }

    /**
     * Removes a specific credential from the user. Typical use case: removing a TOTP device.
     */
    public void removeCredential(String keycloakId, String credentialId) {
        log.info("Credential siliniyor. ID: {}, CredentialID: {}", keycloakId, credentialId);
        keycloakAdminClient.realm(realm).users().get(keycloakId).removeCredential(credentialId);
        log.info("Credential silindi. ID: {}, CredentialID: {}", keycloakId, credentialId);
    }

    // -------------------------------------------------------------------------
    // Kullanıcı silme (compensating action)
    // -------------------------------------------------------------------------

    /**
     * Deletes the user from Keycloak.
     *
     * <p>Called by {@link UserService} as a compensating transaction to roll back
     * the Keycloak-side record when the local DB insert fails.
     *
     * @param keycloakId Keycloak UUID of the user to delete
     */
    public void deleteUser(String keycloakId) {
        log.warn("Keycloak kullanıcısı siliniyor (compensating action). ID: {}", keycloakId);
        try {
            keycloakAdminClient.realm(realm).users().delete(keycloakId);
            log.info("Keycloak kullanıcısı başarıyla silindi. ID: {}", keycloakId);
        } catch (Exception e) {
            log.error("Keycloak kullanıcısı silinemedi! ID: {} — Hata: {}", keycloakId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private yardımcı metodlar
    // -------------------------------------------------------------------------

    private UserRepresentation buildUserRepresentation(CreateUserRequest request) {
        UserRepresentation userRep = new UserRepresentation();
        userRep.setUsername(request.getUsername());
        userRep.setEmail(request.getEmail());
        userRep.setFirstName(request.getFirstName());
        userRep.setLastName(request.getLastName());
        userRep.setEnabled(true);
        userRep.setEmailVerified(true);
        return userRep;
    }

    private void setUserPassword(UsersResource usersResource, String keycloakId,
                                  String password, boolean temporary) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(temporary);

        usersResource.get(keycloakId).resetPassword(credential);
        log.debug("Şifre atandı. Kullanıcı ID: {}, temporary={}", keycloakId, temporary);
    }

    private void assignRealmRoles(RealmResource realmResource, String keycloakId, List<String> roleNames) {
        List<RoleRepresentation> rolesToAssign = roleNames.stream()
                .map(roleName -> {
                    try {
                        return realmResource.roles().get(roleName).toRepresentation();
                    } catch (Exception e) {
                        log.error("Rol bulunamadı veya atanamadı: '{}' — Hata: {}", roleName, e.getMessage());
                        throw new RuntimeException("Keycloak'ta rol bulunamadı: " + roleName, e);
                    }
                })
                .toList();

        if (!rolesToAssign.isEmpty()) {
            realmResource.users().get(keycloakId).roles().realmLevel().add(rolesToAssign);
            log.info("Roller atandı. Kullanıcı ID: {}, Roller: {}", keycloakId,
                    rolesToAssign.stream().map(RoleRepresentation::getName).toList());
        }
    }

    /**
     * Determines which field caused a Keycloak 409 Conflict response and
     * throws the appropriate {@link UserAlreadyExistsException}.
     */
    private void resolveConflict(UsersResource usersResource, CreateUserRequest request) {
        // Email çakışmasını önce kontrol et (daha kritik)
        if (existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("email", request.getEmail());
        }
        if (existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("username", request.getUsername());
        }
        // Keycloak 409 döndü ama hangi alan olduğu belirlenemedi — genel hata
        throw new UserAlreadyExistsException("username", request.getUsername());
    }
}
