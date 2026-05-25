package com.ticketsystem.llmservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot entry point for the llm-service microservice.
 *
 * <p>This service fetches ticket data from it-service-backend, sends it to the
 * Groq LLM API and writes the generated summaries to the shared {@code ticketdb}
 * database. It uses the same schema as the backend but has its own Flyway
 * history table.
 */
@SpringBootApplication
public class LlmServiceApplication {

    /**
     * Starts the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(LlmServiceApplication.class, args);
    }
}
