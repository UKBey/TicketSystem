package com.ticketsystem.it_service_backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * STOMP {@code CONNECT} frame'lerini Keycloak JWT'si ile dogrulayan kanal
 * interceptor'i.
 *
 * <p>HTTP upgrade asamasi {@link com.ticketsystem.it_service_backend.config.SecurityConfig}
 * tarafindan {@code /ws/**} icin permit-all olarak isaretlenmistir; gercek
 * kimlik dogrulama burada gerceklesir. {@code Authorization: Bearer <token>}
 * native header okunur, {@link JwtDecoder} ile dogrulanir ve gecerli ise
 * STOMP session principal'i set edilir. Eksik veya gecersiz token
 * {@link MessagingException} ile reddedilir; istemci frame seviyesinde hata
 * alir.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    /**
     * Yalnizca {@link StompCommand#CONNECT} frame'lerinde devreye girer; diger
     * STOMP komutlari (SUBSCRIBE, SEND vs.) zaten authenticated session
     * uzerinden gelir.
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("WebSocket CONNECT rejected: missing/invalid Authorization header");
            throw new MessagingException("WebSocket authentication failed: missing Bearer token");
        }

        String token = authHeader.substring(7);
        try {
            Jwt jwt = jwtDecoder.decode(token);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(jwt, null, List.of());
            accessor.setUser(auth);
            log.debug("WebSocket CONNECT authenticated for subject: {}", jwt.getSubject());
        } catch (Exception e) {
            log.warn("WebSocket CONNECT rejected: JWT decode failed ({})", e.getMessage());
            throw new MessagingException("WebSocket authentication failed: invalid JWT");
        }

        return message;
    }
}
