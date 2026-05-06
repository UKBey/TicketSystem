package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.NotificationPreferenceResponse;
import com.ticketsystem.it_service_backend.dto.UpdateNotificationPreferenceRequest;
import com.ticketsystem.it_service_backend.entity.*;
import com.ticketsystem.it_service_backend.repository.NotificationPreferenceRepository;
import com.ticketsystem.it_service_backend.repository.NotificationRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationPreferenceRepository preferenceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationService notificationService;

    private User customer;
    private User agent;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id("customer-1")
                .email("customer@example.com")
                .fullName("Ali Yılmaz")
                .role("CUSTOMER")
                .build();

        agent = User.builder()
                .id("agent-1")
                .email("agent@example.com")
                .fullName("Mehmet Kaya")
                .role("AGENT")
                .build();

        ticket = Ticket.builder()
                .id(10L)
                .title("VPN sorunu")
                .priority("HIGH")
                .status("IN_PROGRESS")
                .customerId("customer-1")
                .assigneeId("agent-1")
                .build();

        lenient().when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // -------------------------------------------------------------------------
    // notifyTicketCreated
    // -------------------------------------------------------------------------

    @Test
    void notifyTicketCreated_savesNotificationAndSendsEmail_whenBothPreferencesEnabled() {
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(preferenceRepository.findByUserId("customer-1")).thenReturn(Optional.empty());

        notificationService.notifyTicketCreated(ticket);

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendTicketCreatedEmail(customer, ticket);
    }

    @Test
    void notifyTicketCreated_savesNotificationButSkipsEmail_whenEmailPreferenceDisabled() {
        NotificationPreference pref = NotificationPreference.builder()
                .userId("customer-1")
                .emailOnTicketCreated(false)
                .build();

        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(preferenceRepository.findByUserId("customer-1")).thenReturn(Optional.of(pref));

        notificationService.notifyTicketCreated(ticket);

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService, never()).sendTicketCreatedEmail(any(), any());
    }

    @Test
    void notifyTicketCreated_skipsNotificationButSendsEmail_whenNotifyPreferenceDisabled() {
        NotificationPreference pref = NotificationPreference.builder()
                .userId("customer-1")
                .notifyOnTicketCreated(false)
                .build();

        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(preferenceRepository.findByUserId("customer-1")).thenReturn(Optional.of(pref));

        notificationService.notifyTicketCreated(ticket);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(emailService).sendTicketCreatedEmail(customer, ticket);
    }

    @Test
    void notifyTicketCreated_usesAllTrueDefaults_whenNoPreferenceFound() {
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(preferenceRepository.findByUserId("customer-1")).thenReturn(Optional.empty());

        notificationService.notifyTicketCreated(ticket);

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendTicketCreatedEmail(customer, ticket);
    }

    @Test
    void notifyTicketCreated_doesNothing_whenCustomerNotFound() {
        when(userRepository.findById("customer-1")).thenReturn(Optional.empty());

        notificationService.notifyTicketCreated(ticket);

        verify(notificationRepository, never()).save(any());
        verify(emailService, never()).sendTicketCreatedEmail(any(), any());
    }

    // -------------------------------------------------------------------------
    // notifyTicketAssigned
    // -------------------------------------------------------------------------

    @Test
    void notifyTicketAssigned_savesNotificationAndSendsEmail_whenBothPreferencesEnabled() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(preferenceRepository.findByUserId("agent-1")).thenReturn(Optional.empty());

        notificationService.notifyTicketAssigned(ticket);

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendTicketAssignedEmail(agent, ticket);
    }

    @Test
    void notifyTicketAssigned_doesNothing_whenAssigneeIdIsNull() {
        Ticket unassigned = Ticket.builder().id(11L).customerId("customer-1").build();

        notificationService.notifyTicketAssigned(unassigned);

        verify(notificationRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // notifyCommentAdded
    // -------------------------------------------------------------------------

    @Test
    void notifyCommentAdded_notifiesAgent_whenCustomerComments() {
        Comment comment = Comment.builder()
                .id(1L).authorId("customer-1").message("Sorun devam ediyor.").type("EXTERNAL").build();

        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(preferenceRepository.findByUserId("agent-1")).thenReturn(Optional.empty());

        notificationService.notifyCommentAdded(ticket, comment);

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendCommentAddedEmail(eq(agent), eq(ticket), any(), any());
    }

    @Test
    void notifyCommentAdded_notifiesCustomer_whenAgentComments() {
        Comment comment = Comment.builder()
                .id(2L).authorId("agent-1").message("İnceliyorum.").type("EXTERNAL").build();

        when(userRepository.findById("customer-1")).thenReturn(Optional.of(customer));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(preferenceRepository.findByUserId("customer-1")).thenReturn(Optional.empty());

        notificationService.notifyCommentAdded(ticket, comment);

        verify(notificationRepository).save(any(Notification.class));
        verify(emailService).sendCommentAddedEmail(eq(customer), eq(ticket), any(), any());
    }

    @Test
    void notifyCommentAdded_skipsNotification_whenCommentIsInternal() {
        Comment internal = Comment.builder()
                .id(3L).authorId("agent-1").message("Dahili not.").type("INTERNAL").build();

        notificationService.notifyCommentAdded(ticket, internal);

        verify(notificationRepository, never()).save(any());
        verify(emailService, never()).sendCommentAddedEmail(any(), any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // notifySlaBreached
    // -------------------------------------------------------------------------

    @Test
    void notifySlaBreached_notifiesAssigneeAndAllManagers() {
        User manager1 = User.builder().id("mgr-1").email("mgr1@example.com").fullName("Yönetici 1").role("MANAGER").build();
        User manager2 = User.builder().id("mgr-2").email("mgr2@example.com").fullName("Yönetici 2").role("MANAGER").build();

        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(preferenceRepository.findByUserId("agent-1")).thenReturn(Optional.empty());
        when(userRepository.findByRole("MANAGER")).thenReturn(List.of(manager1, manager2));
        when(preferenceRepository.findByUserId("mgr-1")).thenReturn(Optional.empty());
        when(preferenceRepository.findByUserId("mgr-2")).thenReturn(Optional.empty());

        notificationService.notifySlaBreached(ticket);

        // 1 assignee + 2 managers = 3 saves and 3 emails
        verify(notificationRepository, times(3)).save(any(Notification.class));
        verify(emailService, times(3)).sendSlaBreachedEmail(any(User.class), eq(ticket));
    }

    @Test
    void notifySlaBreached_skipsNotificationForAgent_whenNotifyPreferenceDisabled() {
        NotificationPreference agentPref = NotificationPreference.builder()
                .userId("agent-1")
                .notifyOnSlaBreached(false)
                .build();

        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(preferenceRepository.findByUserId("agent-1")).thenReturn(Optional.of(agentPref));
        when(userRepository.findByRole("MANAGER")).thenReturn(List.of());

        notificationService.notifySlaBreached(ticket);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(emailService).sendSlaBreachedEmail(agent, ticket);
    }

    // -------------------------------------------------------------------------
    // markAsRead
    // -------------------------------------------------------------------------

    @Test
    void markAsRead_throwsForbidden_whenNotificationBelongsToOtherUser() {
        Notification notification = Notification.builder()
                .id(99L).userId("user-2").isRead(false).build();

        when(notificationRepository.findById(99L)).thenReturn(Optional.of(notification));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.markAsRead(99L, "user-1"));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void markAsRead_throwsNotFound_whenNotificationDoesNotExist() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> notificationService.markAsRead(999L, "user-1"));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void markAsRead_setsIsReadTrue_whenOwnerRequests() {
        Notification notification = Notification.builder()
                .id(50L).userId("user-1").isRead(false).build();

        when(notificationRepository.findById(50L)).thenReturn(Optional.of(notification));

        notificationService.markAsRead(50L, "user-1");

        assertTrue(notification.getIsRead());
        verify(notificationRepository).save(notification);
    }

    // -------------------------------------------------------------------------
    // getPreferences / updatePreferences
    // -------------------------------------------------------------------------

    @Test
    void getPreferences_returnsDefaults_whenNoRowExists() {
        when(preferenceRepository.findByUserId("user-1")).thenReturn(Optional.empty());

        NotificationPreferenceResponse result = notificationService.getPreferences("user-1");

        assertTrue(result.getEmailOnTicketCreated());
        assertTrue(result.getEmailOnSlaBreached());
        assertTrue(result.getNotifyOnTicketCreated());
        assertTrue(result.getNotifyOnSlaBreached());
    }

    @Test
    void updatePreferences_persistsOnlyNonNullFields() {
        NotificationPreference existing = NotificationPreference.builder()
                .userId("user-1")
                .emailOnTicketCreated(true)
                .emailOnTicketAssigned(true)
                .emailOnStatusChanged(true)
                .emailOnCommentAdded(true)
                .emailOnSlaWarning(true)
                .emailOnSlaBreached(true)
                .emailOnTicketResolved(true)
                .notifyOnTicketCreated(true)
                .notifyOnTicketAssigned(true)
                .notifyOnStatusChanged(true)
                .notifyOnCommentAdded(true)
                .notifyOnSlaWarning(true)
                .notifyOnSlaBreached(true)
                .notifyOnTicketResolved(true)
                .build();

        when(preferenceRepository.findByUserId("user-1")).thenReturn(Optional.of(existing));
        when(preferenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateNotificationPreferenceRequest req = UpdateNotificationPreferenceRequest.builder()
                .emailOnTicketCreated(false)
                .notifyOnStatusChanged(false)
                .build();

        NotificationPreferenceResponse result = notificationService.updatePreferences("user-1", req);

        assertFalse(result.getEmailOnTicketCreated());
        assertFalse(result.getNotifyOnStatusChanged());
        // Null fields must stay unchanged
        assertTrue(result.getEmailOnSlaBreached());
        assertTrue(result.getNotifyOnTicketCreated());
        assertTrue(result.getNotifyOnCommentAdded());
    }

    // -------------------------------------------------------------------------
    // getNotificationsForUser / getUnreadCount
    // -------------------------------------------------------------------------

    @Test
    void getNotificationsForUser_returnsPagedResults() {
        Notification n = Notification.builder()
                .id(1L).userId("user-1").message("Test").isRead(false)
                .type(NotificationType.TICKET_CREATED).build();

        Page<Notification> page = new PageImpl<>(List.of(n));
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(eq("user-1"), any()))
                .thenReturn(page);

        Page<com.ticketsystem.it_service_backend.dto.NotificationResponse> result =
                notificationService.getNotificationsForUser("user-1", PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("Test", result.getContent().get(0).getMessage());
    }

    @Test
    void getUnreadCount_returnsCorrectCount() {
        when(notificationRepository.countByUserIdAndIsReadFalse("user-1")).thenReturn(5L);

        long count = notificationService.getUnreadCount("user-1");

        assertEquals(5L, count);
    }
}
