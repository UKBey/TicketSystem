package com.ticketsystem.it_service_backend.config;

import lombok.extern.log4j.Log4j2;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keycloak Admin Client bean'ini yapılandırır.
 *
 * <p>Backend, kullanıcı oluşturma ve rol atama işlemleri için Keycloak Admin REST API'ye
 * {@code client_credentials} grant type ile erişir. Bu yaklaşım için {@code ticket-client}
 * üzerinde {@code serviceAccountsEnabled: true} ve {@code realm-management} client'ından
 * {@code manage-users} + {@code view-users} rollerinin atanmış olması gerekir.
 *
 * <p>Bu bean {@link com.ticketsystem.it_service_backend.service.KeycloakAdminService}
 * tarafından constructor injection ile kullanılır.
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
