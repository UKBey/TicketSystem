package com.ticketsystem.generator.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.config.GeneratorConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

/**
 * Backend API'ye OkHttp + Keycloak JWT ile istek atan yardımcı sınıf.
 *
 * <p>JSON marshal/unmarshal Jackson üzerinden; başarısız yanıtlar
 * {@link ApiException} olarak fırlatılır. Generator'un tüm REST
 * çağrıları bu sınıf üzerinden geçer ve {@code /api/v1} prefix'i
 * baz URL'e otomatik eklenir.
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    /**
     * Yeni bir API istemcisi oluşturur.
     *
     * @param http   paylaşılan OkHttp client (timeout'lar app seviyesinde ayarlı)
     * @param mapper Jackson {@link ObjectMapper}
     */
    public ApiClient(OkHttpClient http, ObjectMapper mapper) {
        this.http    = http;
        this.mapper  = mapper;
        this.baseUrl = GeneratorConfig.BASE_URL + "/api/v1";
    }

    // ---------------------------------------------------------------
    // Genel HTTP metodları
    // ---------------------------------------------------------------

    /**
     * GET çağrısı yapar ve JSON yanıtı döndürür.
     *
     * @param path  {@code /api/v1} sonrasındaki path
     * @param token JWT access token (Bearer header'a yazılır)
     * @return ayrıştırılmış JSON yanıt; boş gövde için boş ObjectNode
     * @throws IOException ağ hatası veya {@link ApiException} (HTTP 4xx/5xx)
     */
    public JsonNode get(String path, String token) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        return execute(request);
    }

    /**
     * POST çağrısı yapar; gövde Jackson ile JSON'a serileştirilir.
     *
     * @param path  {@code /api/v1} sonrasındaki path
     * @param body  serileştirilecek istek gövdesi ({@code null} → {@code "null"})
     * @param token JWT access token
     * @return ayrıştırılmış JSON yanıt
     * @throws IOException ağ hatası veya {@link ApiException}
     */
    public JsonNode post(String path, Object body, String token) throws IOException {
        String json = mapper.writeValueAsString(body);
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(json, JSON))
                .build();
        return execute(request);
    }

    /**
     * PUT çağrısı yapar; gövde Jackson ile JSON'a serileştirilir.
     *
     * @param path  {@code /api/v1} sonrasındaki path
     * @param body  serileştirilecek istek gövdesi
     * @param token JWT access token
     * @return ayrıştırılmış JSON yanıt
     * @throws IOException ağ hatası veya {@link ApiException}
     */
    public JsonNode put(String path, Object body, String token) throws IOException {
        String json = mapper.writeValueAsString(body);
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .put(RequestBody.create(json, JSON))
                .build();
        return execute(request);
    }

    /**
     * DELETE çağrısı yapar; isteğe bağlı gövde gönderebilir.
     *
     * @param path  {@code /api/v1} sonrasındaki path
     * @param body  opsiyonel istek gövdesi; {@code null} ise gövdesiz DELETE
     * @param token JWT access token
     * @return ayrıştırılmış JSON yanıt (genellikle boş ObjectNode)
     * @throws IOException ağ hatası veya {@link ApiException}
     */
    public JsonNode delete(String path, Object body, String token) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token);
        if (body != null) {
            String json = mapper.writeValueAsString(body);
            builder.delete(RequestBody.create(json, JSON));
        } else {
            builder.delete();
        }
        return execute(builder.build());
    }

    // ---------------------------------------------------------------
    // Yardımcı metodlar
    // ---------------------------------------------------------------

    private JsonNode execute(Request request) throws IOException {
        try (Response response = http.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                log.warn("API hatası [{} {}] → {}: {}",
                        request.method(), request.url().encodedPath(),
                        response.code(), body);
                throw new ApiException(response.code(), body);
            }
            return body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
        }
    }

    /** API hata kodu ile birlikte fırlatılan exception. */
    public static class ApiException extends IOException {
        private final int statusCode;

        /**
         * @param statusCode HTTP durum kodu (4xx/5xx)
         * @param message    yanıt gövdesi veya hata açıklaması
         */
        public ApiException(int statusCode, String message) {
            super("HTTP " + statusCode + ": " + message);
            this.statusCode = statusCode;
        }

        /** @return yanıtın HTTP durum kodu. */
        public int getStatusCode() { return statusCode; }
    }
}
