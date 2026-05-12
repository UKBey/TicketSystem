package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.CreateUserRequest;
import com.ticketsystem.it_service_backend.exception.UserAlreadyExistsException;
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

import java.util.List;
import java.util.Set;

/**
 * Keycloak Admin REST API üzerinden kullanıcı ve rol yönetimi işlemlerini gerçekleştirir.
 *
 * <p>Bu servis yalnızca Keycloak tarafındaki işlemleri kapsar. Yerel veritabanı
 * senkronizasyonu {@link UserService#createUserWithKeycloak} tarafından yönetilir.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final Keycloak keycloakAdminClient;

    @Value("${keycloak.admin.realm}")
    private String realm;

    /**
     * Sistem rolleri — atanabilir rol listesinden filtrelenir.
     * Bu roller Keycloak'ın iç işleyişine aittir, kullanıcıya atanmamalıdır.
     */
    private static final Set<String> SYSTEM_ROLES = Set.of(
            "offline_access",
            "uma_authorization"
    );

    // -------------------------------------------------------------------------
    // Kullanıcı oluşturma
    // -------------------------------------------------------------------------

    /**
     * Keycloak realm'inde yeni bir kullanıcı oluşturur, geçici şifre atar ve
     * istenen realm rollerini eşler.
     *
     * @param request kullanıcı bilgileri ve atanacak roller
     * @return oluşturulan kullanıcının Keycloak UUID'si
     * @throws UserAlreadyExistsException email veya username çakışması durumunda
     * @throws RuntimeException           Keycloak API beklenmedik hata döndürdüğünde
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

        // 3. Geçici şifre ata (kullanıcı ilk girişte değiştirmek zorunda kalır)
        setTemporaryPassword(usersResource, keycloakId, request.getPassword());

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
     * Realm'deki kullanıcıya atanabilir rolleri döner.
     * Keycloak sistem rolleri ({@code offline_access}, {@code uma_authorization},
     * {@code default-roles-*}) listeden çıkarılır.
     *
     * @return atanabilir {@link RoleRepresentation} listesi
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
     * Verilen email adresine sahip bir kullanıcının Keycloak'ta mevcut olup olmadığını kontrol eder.
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
     * Verilen username'e sahip bir kullanıcının Keycloak'ta mevcut olup olmadığını kontrol eder.
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
     * Kullanıcının Keycloak realm rollerini günceller.
     *
     * <p>Mevcut tüm atanabilir roller önce kaldırılır, ardından yeni roller atanır.
     * Bu yaklaşım idempotent bir güncelleme sağlar.
     *
     * @param keycloakId güncellenecek kullanıcının Keycloak UUID'si
     * @param newRoles   atanacak yeni rol isimleri listesi
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
     * Keycloak'ta kullanıcının {@code enabled} durumunu günceller.
     * Disabled kullanıcı login yapamaz; mevcut oturumları sonraki token
     * yenilemesinde geçersiz kalır.
     *
     * @param keycloakId güncellenecek kullanıcının Keycloak UUID'si
     * @param enabled    {@code true} → aktif, {@code false} → pasif
     */
    public void setUserEnabled(String keycloakId, boolean enabled) {
        log.info("Keycloak kullanıcı durumu güncelleniyor. ID: {}, enabled: {}", keycloakId, enabled);
        UserRepresentation userRep = new UserRepresentation();
        userRep.setEnabled(enabled);
        keycloakAdminClient.realm(realm).users().get(keycloakId).update(userRep);
        log.info("Keycloak kullanıcı durumu güncellendi. ID: {}, enabled: {}", keycloakId, enabled);
    }

    // -------------------------------------------------------------------------
    // Kullanıcı silme (compensating action)
    // -------------------------------------------------------------------------

    /**
     * Keycloak'tan kullanıcıyı siler.
     *
     * <p>Yerel DB kaydı başarısız olduğunda Keycloak tarafındaki kaydı geri almak
     * (compensating transaction) için {@link UserService} tarafından çağrılır.
     *
     * @param keycloakId silinecek kullanıcının Keycloak UUID'si
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

    private void setTemporaryPassword(UsersResource usersResource, String keycloakId, String password) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(password);
        credential.setTemporary(true); // Kullanıcı ilk girişte şifresini değiştirmek zorunda kalır.

        usersResource.get(keycloakId).resetPassword(credential);
        log.debug("Geçici şifre atandı. Kullanıcı ID: {}", keycloakId);
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
     * Keycloak 409 Conflict yanıtında hangi alanın çakıştığını belirler ve
     * uygun {@link UserAlreadyExistsException} fırlatır.
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
