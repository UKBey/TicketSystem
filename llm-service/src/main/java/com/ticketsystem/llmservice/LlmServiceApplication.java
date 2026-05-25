package com.ticketsystem.llmservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * llm-service mikroservisinin Spring Boot giriş noktası.
 *
 * <p>Bu servis it-service-backend'den ticket verisini çekip Groq LLM API'ye
 * gönderir, üretilen özetleri paylaşılan {@code ticketdb} veritabanına yazar.
 * Backend ile aynı şemayı kullanır ancak ayrı bir Flyway tarihçe tablosuna
 * sahiptir.
 */
@SpringBootApplication
public class LlmServiceApplication {

    /**
     * Spring Boot uygulamasını başlatır.
     *
     * @param args komut satırı argümanları
     */
    public static void main(String[] args) {
        SpringApplication.run(LlmServiceApplication.class, args);
    }
}
