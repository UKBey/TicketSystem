package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.NotificationPreferenceResponse;
import com.ticketsystem.it_service_backend.dto.UpdateNotificationPreferenceRequest;
import com.ticketsystem.it_service_backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationPreferenceController preferenceController;

    @BeforeEach
    void setUp() {
        preferenceController = new NotificationPreferenceController(notificationService);
    }

    @Test
    void getPreferences_returns200WithDefaults() {
        NotificationPreferenceResponse defaults = NotificationPreferenceResponse.defaults();
        when(notificationService.getPreferences("user-1")).thenReturn(defaults);

        ResponseEntity<NotificationPreferenceResponse> response =
                preferenceController.getPreferences(jwtWithSubject("user-1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getEmailOnTicketCreated());
        assertTrue(response.getBody().getEmailOnSlaBreached());
        assertTrue(response.getBody().getEmailOnTicketResolved());
        verify(notificationService).getPreferences("user-1");
    }

    @Test
    void updatePreferences_returns200WithUpdatedValues() {
        UpdateNotificationPreferenceRequest req = UpdateNotificationPreferenceRequest.builder()
                .emailOnTicketCreated(false)
                .emailOnSlaBreached(false)
                .build();

        NotificationPreferenceResponse updated = NotificationPreferenceResponse.builder()
                .emailOnTicketCreated(false)
                .emailOnTicketAssigned(true)
                .emailOnStatusChanged(true)
                .emailOnCommentAdded(true)
                .emailOnSlaWarning(true)
                .emailOnSlaBreached(false)
                .emailOnTicketResolved(true)
                .build();

        when(notificationService.updatePreferences("user-1", req)).thenReturn(updated);

        ResponseEntity<NotificationPreferenceResponse> response =
                preferenceController.updatePreferences(jwtWithSubject("user-1"), req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().getEmailOnTicketCreated());
        assertFalse(response.getBody().getEmailOnSlaBreached());
        assertTrue(response.getBody().getEmailOnCommentAdded());
        verify(notificationService).updatePreferences("user-1", req);
    }

    @Test
    void updatePreferences_withNullFields_passesRequestAsIs() {
        UpdateNotificationPreferenceRequest req = new UpdateNotificationPreferenceRequest();
        when(notificationService.updatePreferences("user-1", req))
                .thenReturn(NotificationPreferenceResponse.defaults());

        ResponseEntity<NotificationPreferenceResponse> response =
                preferenceController.updatePreferences(jwtWithSubject("user-1"), req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(notificationService).updatePreferences("user-1", req);
    }

    private Jwt jwtWithSubject(String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        return jwt;
    }
}
