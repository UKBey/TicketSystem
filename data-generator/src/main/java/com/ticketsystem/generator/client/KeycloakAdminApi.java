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
 * Keycloak Admin REST API'ye doğrudan erişim — yalnızca data-generator akışı içinde
 * required-action temizleme gibi düşük seviye operasyonlar için kullanılır.
 *
 * <p>Master realm üzerinde {@code admin-cli} ile password grant alarak token alır,
 * hedef realm'in {@code /admin/realms/.../users} endpoint'lerini çağırır. Token
 * basitçe ilk kullanımda alınır ve cache'lenir; bu generator akışı dakikalar sürmez,
 * refresh stratejisine ihtiyaç yoktur.
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
     * Admin client'ı oluşturur; token URL'i master realm'den, kullanıcı URL'i
     * ise hedef realm'den ({@link GeneratorConfig#KEYCLOAK_REALM}) türetilir.
     *
     * @param http   paylaşılan OkHttp client
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
     * Kullanıcının {@code requiredActions} listesini temizler. Kullanıcı yoksa veya
     * admin REST çağrısı başarısız olursa {@code false} döner — login fallback'i
     * çalışmaz, bu durumda kullanıcı setup'tan atlanır.
     *
     * @param username temizlenecek kullanıcının Keycloak username'i
     * @return işlem başarılıysa {@code true}; aksi halde {@code false}
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
     * Master realm üzerinden admin token alır. Tek seferlik — generator akışı kısa.
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
