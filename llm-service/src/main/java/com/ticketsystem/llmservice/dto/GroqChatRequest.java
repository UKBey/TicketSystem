package com.ticketsystem.llmservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Groq API chat completion isteği.
 * Referans: https://console.groq.com/docs/api-reference#chat-create
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroqChatRequest {

    /** Model adı (ör: llama3-8b-8192) */
    private String model;

    /** Mesaj dizisi */
    private List<Message> messages;

    /** Maksimum üretilecek token sayısı */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** Sıcaklık (0.0 - 2.0, varsayılan: 1.0) */
    private Double temperature;

    /**
     * Groq sohbet mesajı — rol ve içerik çiftini taşır.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /** Rol: system, user, assistant */
        private String role;

        /** Mesaj içeriği */
        private String content;
    }
}
