package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.service.EmailService;
import com.ticketsystem.it_service_backend.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMailControllerTest {

    @Mock private EmailService emailService;
    @Mock private UserService userService;
    @Mock private Jwt jwt;

    private final User admin = User.builder().id("admin-1").email("admin@example.com").build();

    @Test
    void sendTestEmail_success_returnsSuccessTrueWithRecipient() {
        when(jwt.getSubject()).thenReturn("admin-1");
        when(userService.getUserById("admin-1")).thenReturn(admin);
        when(emailService.sendTestEmail(admin)).thenReturn(null); // null = başarı

        ResponseEntity<Map<String, Object>> res =
                new AdminMailController(emailService, userService).sendTestEmail(jwt);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsEntry("success", true);
        assertThat(res.getBody()).containsEntry("recipient", "admin@example.com");
        assertThat(res.getBody()).containsEntry("error", "");
    }

    @Test
    void sendTestEmail_whenSendFails_returnsSuccessFalseWithErrorReason() {
        when(jwt.getSubject()).thenReturn("admin-1");
        when(userService.getUserById("admin-1")).thenReturn(admin);
        when(emailService.sendTestEmail(admin)).thenReturn("Connection refused");

        ResponseEntity<Map<String, Object>> res =
                new AdminMailController(emailService, userService).sendTestEmail(jwt);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsEntry("success", false);
        assertThat(res.getBody()).containsEntry("error", "Connection refused");
    }
}
