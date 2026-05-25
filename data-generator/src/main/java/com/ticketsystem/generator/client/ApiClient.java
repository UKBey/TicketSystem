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
 * Helper class that sends requests to the backend API via OkHttp + Keycloak JWT.
 *
 * <p>JSON marshal/unmarshal is handled by Jackson; failed responses are thrown
 * as {@link ApiException}. All of the generator's REST calls go through this
 * class and the {@code /api/v1} prefix is automatically appended to the base URL.
 */
public class ApiClient {

    private static final Logger log = LoggerFactory.getLogger(ApiClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;

    /**
     * Creates a new API client.
     *
     * @param http   shared OkHttp client (timeouts configured at app level)
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
     * Performs a GET call and returns the JSON response.
     *
     * @param path  the path after {@code /api/v1}
     * @param token JWT access token (written into the Bearer header)
     * @return the parsed JSON response; an empty ObjectNode for an empty body
     * @throws IOException on a network error or {@link ApiException} (HTTP 4xx/5xx)
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
     * Performs a POST call; the body is serialized to JSON via Jackson.
     *
     * @param path  the path after {@code /api/v1}
     * @param body  the request body to serialize ({@code null} → {@code "null"})
     * @param token JWT access token
     * @return the parsed JSON response
     * @throws IOException on a network error or {@link ApiException}
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
     * Performs a PUT call; the body is serialized to JSON via Jackson.
     *
     * @param path  the path after {@code /api/v1}
     * @param body  the request body to serialize
     * @param token JWT access token
     * @return the parsed JSON response
     * @throws IOException on a network error or {@link ApiException}
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
     * Performs a DELETE call; may optionally send a body.
     *
     * @param path  the path after {@code /api/v1}
     * @param body  optional request body; if {@code null}, performs a DELETE without a body
     * @param token JWT access token
     * @return the parsed JSON response (usually an empty ObjectNode)
     * @throws IOException on a network error or {@link ApiException}
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

    /** Exception thrown together with the API error code. */
    public static class ApiException extends IOException {
        private final int statusCode;

        /**
         * @param statusCode HTTP status code (4xx/5xx)
         * @param message    response body or error description
         */
        public ApiException(int statusCode, String message) {
            super("HTTP " + statusCode + ": " + message);
            this.statusCode = statusCode;
        }

        /** @return the HTTP status code of the response. */
        public int getStatusCode() { return statusCode; }
    }
}
