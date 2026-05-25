package com.ticketsystem.generator.model;

import com.ticketsystem.generator.client.KeycloakTokenClient;

import java.io.IOException;

/**
 * Bir kullanıcının generator oturum bilgilerini tutar.
 *
 * <p>Username, rol ve token client'ı immutable; arka tarafta {@code /users/sync}
 * çağrısının döndürdüğü backend user ID setter ile sonradan atanır.
 * Token alımı {@link KeycloakTokenClient} üzerinden lazımsa yenilenerek yapılır.
 */
public class UserSession {

    private final String username;
    private final String role;
    private final KeycloakTokenClient tokenClient;
    private String userId;

    /**
     * @param username    Keycloak / backend kullanıcı adı
     * @param role        gösterim/akış amaçlı rol etiketi (örn. {@code AGENT}, {@code CUSTOMER})
     * @param tokenClient bu kullanıcıya bağlı, login edilmiş Keycloak token client
     */
    public UserSession(String username, String role, KeycloakTokenClient tokenClient) {
        this.username    = username;
        this.role        = role;
        this.tokenClient = tokenClient;
    }

    /**
     * @return geçerli access token; süresi dolmuşsa otomatik yenilenir
     * @throws IOException token yenileme/login başarısız olursa
     */
    public String getToken() throws IOException {
        return tokenClient.getToken();
    }

    /** @return Keycloak kullanıcı adı. */
    public String getUsername() { return username; }
    /** @return rol etiketi (örn. {@code AGENT}, {@code CUSTOMER}, {@code AGENT_ADMIN}). */
    public String getRole()     { return role; }
    /** @return backend tarafından atanmış user ID; sync edilmemişse {@code null}. */
    public String getUserId()   { return userId; }
    /** @param userId backend {@code /users/sync} yanıtından alınan user ID. */
    public void setUserId(String userId) { this.userId = userId; }

    @Override
    public String toString() {
        return username + " [" + role + "]";
    }
}
