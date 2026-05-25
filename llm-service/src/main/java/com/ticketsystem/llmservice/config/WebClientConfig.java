package com.ticketsystem.llmservice.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Provides {@link WebClient} beans for the outbound HTTP calls the service makes.
 *
 * <p>Two separate clients are defined:
 * <ul>
 *   <li>{@code groqWebClient} — targets the Groq API with an Authorization
 *       header and custom timeouts.</li>
 *   <li>{@code ticketServiceWebClient} — targets the internal endpoints of
 *       it-service-backend with the {@code X-Internal-Token} header.</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

    private final GroqConfig groqConfig;

    @Value("${ticket-service.base-url}")
    private String ticketServiceBaseUrl;

    @Value("${ticket-service.internal-token}")
    private String internalToken;

    /**
     * WebClient for Groq API calls.
     * The Authorization header is added automatically.
     */
    @Bean("groqWebClient")
    public WebClient groqWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(groqConfig.getTimeoutSeconds()))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(groqConfig.getTimeoutSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        return WebClient.builder()
                .baseUrl(groqConfig.getUrl())
                .defaultHeader("Authorization", "Bearer " + groqConfig.getKey())
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    /**
     * WebClient for internal calls to it-service-backend.
     */
    @Bean("ticketServiceWebClient")
    public WebClient ticketServiceWebClient() {
        return WebClient.builder()
                .baseUrl(ticketServiceBaseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }
}
