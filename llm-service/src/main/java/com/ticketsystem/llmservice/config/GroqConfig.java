package com.ticketsystem.llmservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Groq API erişim ayarları — {@code application.yml} içindeki {@code groq.api.*}
 * altında tanımlanır.
 *
 * <p>Endpoint URL'i, API anahtarı, kullanılacak model adı, maksimum üretilecek
 * token sayısı ve HTTP zaman aşımı bu sınıf üzerinden okunur.
 */
@Configuration
@ConfigurationProperties(prefix = "groq.api")
@Getter
@Setter
public class GroqConfig {

    private String url;
    private String key;
    private String model;
    private int maxTokens;
    private int timeoutSeconds;
}
