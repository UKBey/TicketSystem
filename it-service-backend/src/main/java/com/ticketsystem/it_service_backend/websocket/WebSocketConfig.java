package com.ticketsystem.it_service_backend.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP / WebSocket altyapisini etkinlestiren yapilandirma.
 *
 * <p>Tek bir handshake endpoint'i ({@code /ws}) acilir; basit (in-memory) broker
 * {@code /topic/**} prefixiyle dinleyicilere mesaj yayinlar. Istemciden gelen
 * uygulama mesajlari {@code /app/**} prefixiyle controller'lara yonlendirilir.
 * {@link WebSocketAuthChannelInterceptor} client-inbound channel'a baglanir;
 * boylece CONNECT frame'leri Keycloak JWT'si ile dogrulanir.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authInterceptor;

    /**
     * Basit (in-memory) STOMP broker'i etkinlestirir.
     * {@code /topic/**} yayinlanan event'ler icin, {@code /app/**} ise istemciden
     * controller'a giden hedefler icin kullanilir.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    /**
     * {@code /ws} altinda STOMP handshake endpoint'ini kaydeder. Nginx reverse
     * proxy ardinda calistigi icin {@code allowedOriginPatterns("*")} kullanilir.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*");
    }

    /**
     * JWT kontrol eden {@link WebSocketAuthChannelInterceptor}'i client-inbound
     * channel'a kayit eder — STOMP CONNECT frame'leri buradan gecer.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
