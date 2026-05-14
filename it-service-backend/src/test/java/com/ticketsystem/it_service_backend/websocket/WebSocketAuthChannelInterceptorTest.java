package com.ticketsystem.it_service_backend.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock private JwtDecoder jwtDecoder;
    @Mock private MessageChannel channel;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(jwtDecoder);
    }

    private Message<byte[]> connectMessage(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribeMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt sampleJwt() {
        return Jwt.withTokenValue("token-value")
                .header("alg", "RS256")
                .subject("user-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(Map.of("preferred_username", "alice")))
                .build();
    }

    @Test
    void preSend_nonConnectFrame_passesThroughUnmodified() {
        Message<byte[]> msg = subscribeMessage();

        Message<?> result = interceptor.preSend(msg, channel);

        assertThat(result).isSameAs(msg);
    }

    @Test
    void preSend_connectWithValidBearer_attachesAuthenticatedUser() {
        when(jwtDecoder.decode("good-token")).thenReturn(sampleJwt());
        Message<byte[]> msg = connectMessage("Bearer good-token");

        Message<?> result = interceptor.preSend(msg, channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) accessor.getUser();
        assertThat(((Jwt) auth.getPrincipal()).getSubject()).isEqualTo("user-123");
    }

    @Test
    void preSend_connectMissingAuthHeader_throws() {
        Message<byte[]> msg = connectMessage(null);

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("missing Bearer token");
    }

    @Test
    void preSend_connectNonBearerScheme_throws() {
        Message<byte[]> msg = connectMessage("Basic abc==");

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("missing Bearer token");
    }

    @Test
    void preSend_connectInvalidJwt_throws() {
        when(jwtDecoder.decode("broken")).thenThrow(new BadJwtException("bad sig"));
        Message<byte[]> msg = connectMessage("Bearer broken");

        assertThatThrownBy(() -> interceptor.preSend(msg, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("invalid JWT");
    }

    @Test
    void preSend_messageWithoutStompAccessor_passesThrough() {
        Message<byte[]> msg = MessageBuilder.withPayload(new byte[0]).build();

        Message<?> result = interceptor.preSend(msg, channel);

        assertThat(result).isSameAs(msg);
    }
}
