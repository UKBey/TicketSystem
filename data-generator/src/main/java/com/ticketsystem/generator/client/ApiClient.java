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
 * Backend API'ye JWT ile istek atan yardımcı sınıf.
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    public ApiClient(OkHttpClient http, ObjectMapper mapper) {
        this.http    = http;
        this.mapper  = mapper;
        this.baseUrl = GeneratorConfig.BASE_URL + "/api/v1";
    }

    // ---------------------------------------------------------------
    // Genel HTTP metodları
    // ---------------------------------------------------------------

    public JsonNode get(String path, String token) throws IOException {
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .get()
                .build();
        return execute(request);
    }

    public JsonNode post(String path, Object body, String token) throws IOException {
        String json = mapper.writeValueAsString(body);
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .post(RequestBody.create(json, JSON))
                .build();
        return execute(request);
    }

    public JsonNode put(String path, Object body, String token) throws IOException {
        String json = mapper.writeValueAsString(body);
        Request request = new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", "Bearer " + token)
                .put(RequestBody.create(json, JSON))
                .build();
        return execute(request);
    }

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

        public ApiException(int statusCode, String message) {
            super("HTTP " + statusCode + ": " + message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() { return statusCode; }
    }
}
