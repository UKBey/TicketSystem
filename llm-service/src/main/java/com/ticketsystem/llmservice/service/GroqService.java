package com.ticketsystem.llmservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.llmservice.config.GroqConfig;
import com.ticketsystem.llmservice.dto.GroqChatRequest;
import com.ticketsystem.llmservice.dto.GroqChatResponse;
import com.ticketsystem.llmservice.exception.GroqRateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that handles communication with the Groq API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroqService {

    @Qualifier("groqWebClient")
    private final WebClient groqWebClient;

    private final GroqConfig groqConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // "Please try again in 5.51s" → 5.51 çıkarmak için
    private static final Pattern RETRY_AFTER_PATTERN =
            Pattern.compile("try again in ([\\d.]+)s", Pattern.CASE_INSENSITIVE);

    /**
     * Sends a chat completion request to the Groq API and returns the response.
     *
     * @param systemPrompt System message defining the LLM's role
     * @param userPrompt   Ticket data to be summarized
     * @return Groq API response
     * @throws GroqRateLimitException when the token limit is exceeded
     */
    public GroqChatResponse complete(String systemPrompt, String userPrompt) {
        GroqChatRequest request = GroqChatRequest.builder()
                .model(groqConfig.getModel())
                .maxTokens(groqConfig.getMaxTokens())
                .temperature(0.3)
                .messages(List.of(
                        GroqChatRequest.Message.builder()
                                .role("system")
                                .content(systemPrompt)
                                .build(),
                        GroqChatRequest.Message.builder()
                                .role("user")
                                .content(userPrompt)
                                .build()
                ))
                .build();

        int promptChars = systemPrompt.length() + userPrompt.length();
        log.debug("Groq API isteği gönderiliyor. Model: {}, MaxTokens: {}, PromptChars: {}",
                groqConfig.getModel(), groqConfig.getMaxTokens(), promptChars);
        long start = System.currentTimeMillis();

        try {
            GroqChatResponse response = groqWebClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.warn("Groq API 4xx hatası. Status: {}, Body: {}",
                                        clientResponse.statusCode(), body);
                                RuntimeException ex = parseGroqError(body);
                                return reactor.core.publisher.Mono.error(ex);
                            })
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class).flatMap(body -> {
                                log.error("Groq API 5xx hatası. Status: {}, Body: {}",
                                        clientResponse.statusCode(), body);
                                return reactor.core.publisher.Mono.error(
                                        new RuntimeException("Groq API sunucu hatası. Lütfen daha sonra tekrar deneyin."));
                            })
                    )
                    .bodyToMono(GroqChatResponse.class)
                    .block();

            long elapsedMs = System.currentTimeMillis() - start;

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                log.error("Groq API boş yanıt döndü. Süre: {}ms", elapsedMs);
                throw new RuntimeException("Groq API boş yanıt döndü");
            }

            log.info("Groq API yanıtı alındı. Model: {}, PromptTokens: {}, CompletionTokens: {}, Süre: {}ms",
                    response.getModel(),
                    response.getUsage() != null ? response.getUsage().getPromptTokens() : "?",
                    response.getUsage() != null ? response.getUsage().getCompletionTokens() : "?",
                    elapsedMs);

            return response;

        } catch (GroqRateLimitException e) {
            log.warn("Groq rate limit istisnası fırlatıldı. RetryAfter: {}s", e.getRetryAfterSeconds());
            throw e; // zaten doğru tip, tekrar wrap etme
        } catch (WebClientResponseException e) {
            log.error("Groq API HTTP hatası. Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw parseGroqError(e.getResponseBodyAsString());
        }
    }

    /**
     * Parses the Groq error body.
     * Returns GroqRateLimitException for rate_limit_exceeded, RuntimeException otherwise.
     */
    private RuntimeException parseGroqError(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            String code = error.path("code").asText("");
            String message = error.path("message").asText(body);

            if ("rate_limit_exceeded".equals(code) || "tokens".equals(error.path("type").asText(""))) {
                // "Please try again in 5.51s" → retryAfterSeconds çıkar
                double retryAfterSeconds = 10.0;
                Matcher m = RETRY_AFTER_PATTERN.matcher(message);
                if (m.find()) {
                    retryAfterSeconds = Double.parseDouble(m.group(1));
                }
                log.warn("Groq rate limit aşıldı. Retry after: {}s", retryAfterSeconds);
                return new GroqRateLimitException(retryAfterSeconds, message);
            }

            return new RuntimeException("Groq API hatası: " + message);
        } catch (Exception parseEx) {
            return new RuntimeException("Groq API hatası: " + body);
        }
    }
}
