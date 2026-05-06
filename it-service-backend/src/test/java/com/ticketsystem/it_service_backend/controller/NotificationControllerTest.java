package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.NotificationResponse;
import com.ticketsystem.it_service_backend.entity.NotificationType;
import com.ticketsystem.it_service_backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private NotificationController notificationController;

    @BeforeEach
    void setUp() {
        notificationController = new NotificationController(notificationService);
    }

    @Test
    void getNotifications_returns200WithPage() {
        NotificationResponse item = NotificationResponse.builder()
                .id(1L).userId("user-1").message("Bilet oluşturuldu.")
                .isRead(false).createdAt(ZonedDateTime.now())
                .type(NotificationType.TICKET_CREATED).referenceId(10L).referenceType("TICKET")
                .build();

        Page<NotificationResponse> page = new PageImpl<>(List.of(item));
        when(notificationService.getNotificationsForUser(eq("user-1"), any())).thenReturn(page);

        ResponseEntity<Page<NotificationResponse>> response =
                notificationController.getNotifications(jwtWithSubject("user-1"), 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals("Bilet oluşturuldu.", response.getBody().getContent().get(0).getMessage());
    }

    @Test
    void getNotifications_returnsEmptyPage_whenNoNotifications() {
        when(notificationService.getNotificationsForUser(eq("user-1"), any()))
                .thenReturn(Page.empty());

        ResponseEntity<Page<NotificationResponse>> response =
                notificationController.getNotifications(jwtWithSubject("user-1"), 0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().getTotalElements());
    }

    @Test
    void getUnreadCount_returns200WithCount() {
        when(notificationService.getUnreadCount("user-1")).thenReturn(7L);

        ResponseEntity<Map<String, Long>> response =
                notificationController.getUnreadCount(jwtWithSubject("user-1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(7L, response.getBody().get("count"));
    }

    @Test
    void getUnreadCount_returnsZero_whenAllRead() {
        when(notificationService.getUnreadCount("user-1")).thenReturn(0L);

        ResponseEntity<Map<String, Long>> response =
                notificationController.getUnreadCount(jwtWithSubject("user-1"));

        assertEquals(0L, response.getBody().get("count"));
    }

    @Test
    void markAsRead_returns204() {
        doNothing().when(notificationService).markAsRead(42L, "user-1");

        ResponseEntity<Void> response =
                notificationController.markAsRead(42L, jwtWithSubject("user-1"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationService).markAsRead(42L, "user-1");
    }

    @Test
    void markAllAsRead_returns204() {
        doNothing().when(notificationService).markAllAsRead("user-1");

        ResponseEntity<Void> response =
                notificationController.markAllAsRead(jwtWithSubject("user-1"));

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(notificationService).markAllAsRead("user-1");
    }

    private Jwt jwtWithSubject(String subject) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        return jwt;
    }
}
