package com.ticketsystem.it_service_backend.config;

import lombok.extern.log4j.Log4j2;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Keycloak Admin Client bean.
 *
 * <p>The backend talks to the Keycloak Admin REST API via the
 * {@code client_credentials} grant type to create users and assign roles.
 * This requires {@code serviceAccountsEnabled: true} on {@code ticket-client}
 * and the {@code manage-users} + {@code view-users} roles from the
 * {@code realm-management} client to be assigned.
 *
 * <p>The bean is consumed by
 * {@link com.ticketsystem.it_service_backend.service.KeycloakAdminService}
 * via constructor injection.
 */
@Configuration
@Log4j2
public class KeycloakAdminConfig {

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.client-id}")
    private String clientId;

    @Value("${keycloak.admin.client-secret}")
    private String clientSecret;

    /**
     * Builds the Keycloak Admin REST client with the
     * {@code client_credentials} grant.
     *
     * <p>An access token is fetched at startup to verify the connection;
     * issues such as a wrong secret or missing role are caught early during
     * boot. The application keeps starting even on failure — only the user
     * management endpoints are affected.
     *
     * @return the {@link Keycloak} client shared for the application's
     *         lifetime
     */
    @Bean
    public Keycloak keycloakAdminClient() {
        log.info("Keycloak Admin Client yapılandırılıyor. Server: {}, Realm: {}, ClientId: {}",
                serverUrl, realm, clientId);

        Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(serverUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .grantType("client_credentials")
                .build();

        // Uygulama açılışında bağlantıyı doğrula; hata olursa uyarı ver ama başlatmayı durdurma.
        try {
            // tokenManager().getAccessToken() çağrısı gerçek bir token alır;
            // bu sayede yanlış secret veya eksik rol gibi sorunlar erken yakalanır.
            String tokenType = keycloak.tokenManager().getAccessToken().getTokenType();
            log.info("Keycloak Admin Client bağlantısı başarılı. Token type: {}", tokenType);
        } catch (Exception e) {
            log.error("Keycloak Admin Client bağlantısı başarısız! " +
                    "Server: {} — Hata: {}", serverUrl, e.getMessage());
            log.warn("Uygulama başlatılmaya devam edecek, ancak kullanıcı yönetimi özellikleri çalışmayacak.");
        }

        return keycloak;
    }
}
