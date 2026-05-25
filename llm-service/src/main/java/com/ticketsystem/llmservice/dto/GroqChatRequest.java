package com.ticketsystem.llmservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Groq API chat completion request.
 * Reference: https://console.groq.com/docs/api-reference#chat-create
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroqChatRequest {

    /** Model name (e.g. llama3-8b-8192) */
    private String model;

    /** Message sequence */
    private List<Message> messages;

    /** Maximum number of tokens to generate */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** Temperature (0.0 - 2.0, default: 1.0) */
    private Double temperature;

    /**
     * Groq chat message — carries the role and content pair.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /** Role: system, user, assistant */
        private String role;

        /** Message content */
        private String content;
    }
}
