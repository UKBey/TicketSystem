package com.ticketsystem.llmservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Groq API access settings — defined under {@code groq.api.*} in
 * {@code application.yml}.
 *
 * <p>The endpoint URL, API key, model name to use, maximum number of tokens
 * to generate and the HTTP timeout are all read through this class.
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
