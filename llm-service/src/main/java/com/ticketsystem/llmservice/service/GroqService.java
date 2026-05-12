package com.ticketsystem.llmservice.service;

import com.ticketsystem.llmservice.config.GroqConfig;
import com.ticketsystem.llmservice.dto.GroqChatRequest;
import com.ticketsystem.llmservice.dto.GroqChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

/**
 * Groq API ile iletişimi yöneten servis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroqService {

    @Qualifier("groqWebClient")
    private final WebClient groqWebClient;

    private final GroqConfig groqConfig;

    /**
     * Groq API'ye chat completion isteği gönderir ve yanıtı döner.
     *
     * @param systemPrompt LLM'e rolünü tanımlayan sistem mesajı
     * @param userPrompt   Özetlenecek ticket verisi
     * @return Groq API yanıtı
     */
    public GroqChatResponse complete(String systemPrompt, String userPrompt) {
        GroqChatRequest request = GroqChatRequest.builder()
                .model(groqConfig.getModel())
                .maxTokens(groqConfig.getMaxTokens())
                .temperature(0.3) // Tutarlı, düşük yaratıcılıklı çıktı için
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

        log.debug("Groq API isteği gönderiliyor. Model: {}, MaxTokens: {}",
                groqConfig.getModel(), groqConfig.getMaxTokens());

        try {
            GroqChatResponse response = groqWebClient.post()
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse ->
                            clientResponse.bodyToMono(String.class).map(body -> {
                                log.error("Groq API 4xx hatası: {}", body);
                                return new RuntimeException("Groq API istemci hatası: " + body);
                            })
                    )
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                            clientResponse.bodyToMono(String.class).map(body -> {
                                log.error("Groq API 5xx hatası: {}", body);
                                return new RuntimeException("Groq API sunucu hatası: " + body);
                            })
                    )
                    .bodyToMono(GroqChatResponse.class)
                    .block();

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                throw new RuntimeException("Groq API boş yanıt döndü");
            }

            log.info("Groq API yanıtı alındı. Model: {}, PromptTokens: {}, CompletionTokens: {}",
                    response.getModel(),
                    response.getUsage() != null ? response.getUsage().getPromptTokens() : "?",
                    response.getUsage() != null ? response.getUsage().getCompletionTokens() : "?");

            return response;

        } catch (WebClientResponseException e) {
            log.error("Groq API HTTP hatası. Status: {}, Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Groq API isteği başarısız: " + e.getMessage(), e);
        }
    }
}
