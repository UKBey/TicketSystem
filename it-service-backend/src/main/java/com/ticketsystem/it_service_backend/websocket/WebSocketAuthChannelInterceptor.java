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
 * Channel interceptor that authenticates STOMP {@code CONNECT} frames using
 * the Keycloak JWT.
 *
 * <p>The HTTP upgrade step is marked permit-all for {@code /ws/**} by
 * {@link com.ticketsystem.it_service_backend.config.SecurityConfig}; the
 * real authentication happens here. The native {@code Authorization: Bearer
 * <token>} header is read, validated through {@link JwtDecoder} and, on
 * success, set as the STOMP session principal. Missing or invalid tokens
 * are rejected with a {@link MessagingException} and the client receives a
 * frame-level error.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    /**
     * Engages only on {@link StompCommand#CONNECT} frames; other STOMP
     * commands (SUBSCRIBE, SEND, etc.) already arrive on an authenticated
     * session.
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
