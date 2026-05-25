package com.ticketsystem.generator.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ticketsystem.generator.config.GeneratorConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Direct access to the Keycloak Admin REST API — used only inside the data-generator
 * flow for low-level operations such as clearing required actions.
 *
 * <p>Obtains a token via password grant against the master realm with {@code admin-cli},
 * then calls the target realm's {@code /admin/realms/.../users} endpoints. The token
 * is simply fetched on first use and cached; the generator flow does not run for many
 * minutes, so no refresh strategy is needed.
 */
public class KeycloakAdminApi {

    private static final Logger log = LoggerFactory.getLogger(KeycloakAdminApi.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String tokenUrl;
    private final String adminUsersUrl;
    private String cachedToken;

    /**
     * Creates the admin client; the token URL is derived from the master realm and the
     * user URL from the target realm ({@link GeneratorConfig#KEYCLOAK_REALM}).
     *
     * @param http   shared OkHttp client
     * @param mapper Jackson mapper
     */
    public KeycloakAdminApi(OkHttpClient http, ObjectMapper mapper) {
        this.http   = http;
        this.mapper = mapper;
        this.tokenUrl       = GeneratorConfig.KEYCLOAK_URL + "/realms/master/protocol/openid-connect/token";
        this.adminUsersUrl  = GeneratorConfig.KEYCLOAK_URL + "/admin/realms/"
                            + GeneratorConfig.KEYCLOAK_REALM + "/users";
    }

    /**
     * Clears the user's {@code requiredActions} list. Returns {@code false} if the
     * user does not exist or the admin REST call fails — the login fallback will not
     * run and the user is skipped during setup.
     *
     * @param username Keycloak username of the user to clear
     * @return {@code true} if the operation succeeded; {@code false} otherwise
     */
    public boolean clearRequiredActions(String username) {
        try {
            String userId = findUserIdByUsername(username);
            if (userId == null) {
                log.warn("Admin REST: kullanıcı bulunamadı ({})", username);
                return false;
            }

            // PUT /users/{id} — Keycloak partial update'i destekler, sadece requiredActions
            // göndermek diğer alanları korur ama yine de örnek bir representation hazırlıyoruz.
            ObjectNode body = mapper.createObjectNode();
            body.putArray("requiredActions"); // boş array

            Request req = new Request.Builder()
                    .url(adminUsersUrl + "/" + userId)
                    .header("Authorization", "Bearer " + token())
                    .put(RequestBody.create(mapper.writeValueAsString(body), JSON))
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                if (!resp.isSuccessful()) {
                    log.warn("Admin REST: requiredActions temizlenemedi ({}, HTTP {}): {}",
                            username, resp.code(),
                            resp.body() != null ? resp.body().string() : "");
                    return false;
                }
            }
            log.info("Admin REST: required-actions temizlendi → {}", username);
            return true;
        } catch (Exception e) {
            log.warn("Admin REST: clearRequiredActions hata ({}): {}", username, e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------
    // Yardımcılar
    // -----------------------------------------------------------------

    private String findUserIdByUsername(String username) throws IOException {
        Request req = new Request.Builder()
                .url(adminUsersUrl + "?exact=true&username="
                        + URLEncoder.encode(username, StandardCharsets.UTF_8))
                .header("Authorization", "Bearer " + token())
                .get()
                .build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Kullanıcı arama başarısız HTTP " + resp.code());
            }
            JsonNode arr = mapper.readTree(resp.body() != null ? resp.body().string() : "[]");
            if (arr.isArray() && arr.size() > 0) {
                return arr.get(0).path("id").asText();
            }
            return null;
        }
    }

    /**
     * Obtains an admin token via the master realm. One-shot — the generator flow is short.
     */
    private synchronized String token() throws IOException {
        if (cachedToken != null) return cachedToken;

        FormBody form = new FormBody.Builder()
                .add("grant_type", "password")
                .add("client_id",  GeneratorConfig.MASTER_ADMIN_CLIENT)
                .add("username",   GeneratorConfig.MASTER_ADMIN_USERNAME)
                .add("password",   GeneratorConfig.MASTER_ADMIN_PASSWORD)
                .build();
        Request req = new Request.Builder().url(tokenUrl).post(form).build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IOException("Master admin token isteği başarısız HTTP " + resp.code() + ": " + body);
            }
            JsonNode node = mapper.readTree(body);
            cachedToken = node.path("access_token").asText();
            log.debug("Master admin token alındı.");
            return cachedToken;
        }
    }
}
