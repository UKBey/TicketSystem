package com.ticketsystem.generator.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.config.GeneratorConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;

/**
 * Keycloak'tan kullanıcı adı/şifre ile token alır ve otomatik yeniler.
 */
public class KeycloakTokenClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTokenClient.class);

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String tokenUrl;

    private String username;
    private String password;
    private String accessToken;
    private String refreshToken;
    private Instant expiresAt;

    public KeycloakTokenClient(OkHttpClient http, ObjectMapper mapper) {
        this.http    = http;
        this.mapper  = mapper;
        this.tokenUrl = GeneratorConfig.KEYCLOAK_URL
                + "/realms/" + GeneratorConfig.KEYCLOAK_REALM
                + "/protocol/openid-connect/token";
    }

    /** Belirtilen kullanıcı için token alır. */
    public void login(String username, String password) throws IOException {
        this.username = username;
        this.password = password;
        fetchToken();
        log.info("Token alındı: {}", username);
    }

    /** Geçerli access token'ı döner; gerekirse yeniler. */
    public String getToken() throws IOException {
        if (accessToken == null || isExpiringSoon()) {
            if (refreshToken != null) {
                try {
                    refreshAccessToken();
                } catch (IOException e) {
                    log.warn("Token yenileme başarısız, yeniden giriş yapılıyor: {}", username);
                    fetchToken();
                }
            } else {
                fetchToken();
            }
        }
        return accessToken;
    }

    private boolean isExpiringSoon() {
        return expiresAt != null &&
               Instant.now().isAfter(expiresAt.minusSeconds(GeneratorConfig.TOKEN_REFRESH_THRESHOLD_SEC));
    }

    private void fetchToken() throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "password")
                .add("client_id", GeneratorConfig.KEYCLOAK_CLIENT)
                .add("username", username)
                .add("password", password)
                .build();

        parseTokenResponse(executePost(body));
    }

    private void refreshAccessToken() throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", GeneratorConfig.KEYCLOAK_CLIENT)
                .add("refresh_token", refreshToken)
                .build();

        parseTokenResponse(executePost(body));
        log.debug("Token yenilendi: {}", username);
    }

    private String executePost(RequestBody body) throws IOException {
        Request request = new Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("Token isteği başarısız [" + response.code() + "]: " + responseBody);
            }
            return responseBody;
        }
    }

    private void parseTokenResponse(String json) throws IOException {
        JsonNode node = mapper.readTree(json);
        this.accessToken  = node.get("access_token").asText();
        this.refreshToken = node.has("refresh_token") ? node.get("refresh_token").asText() : null;
        int expiresIn     = node.has("expires_in") ? node.get("expires_in").asInt(300) : 300;
        this.expiresAt    = Instant.now().plusSeconds(expiresIn);
    }
}
