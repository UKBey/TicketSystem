package com.ticketsystem.it_service_backend.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuration that enables the STOMP / WebSocket infrastructure.
 *
 * <p>A single handshake endpoint ({@code /ws}) is exposed; the simple
 * (in-memory) broker fans out messages to subscribers on the
 * {@code /topic/**} prefix. Application messages from the client are
 * routed to controllers on the {@code /app/**} prefix.
 * {@link WebSocketAuthChannelInterceptor} is wired into the client-inbound
 * channel so CONNECT frames are validated against the Keycloak JWT.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authInterceptor;

    /**
     * Enables the simple (in-memory) STOMP broker.
     * {@code /topic/**} is used for published events and {@code /app/**}
     * for destinations from the client to a controller.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Registers the STOMP handshake endpoint under {@code /ws}. Because the
     * service runs behind an nginx reverse proxy
     * {@code allowedOriginPatterns("*")} is used.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    /**
     * Registers the JWT-validating
     * {@link WebSocketAuthChannelInterceptor} on the client-inbound channel
     * — STOMP CONNECT frames pass through here.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
