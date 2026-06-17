package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Language;
import com.ticketsystem.it_service_backend.entity.Theme;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import com.ticketsystem.it_service_backend.entity.TicketStatus;
import com.ticketsystem.it_service_backend.entity.Priority;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MessageSource messageSource;

    private MeterRegistry meterRegistry;

    private EmailService emailService;

    private User customer;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        emailService = new EmailService(mailSender, messageSource, meterRegistry);
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@ticketsystem.local");

        // MessageSource: her key için key'in kendisini döndür (test ortamında yeterli)
        lenient().when(messageSource.getMessage(anyString(), any(), anyString(), any()))
                .thenAnswer(inv -> inv.getArgument(2)); // defaultMessage (3. arg) = key itself

        customer = User.builder()
                .id("customer-1")
                .email("customer@example.com")
                .fullName("Ali Yılmaz")
                .build();

        ticket = Ticket.builder()
                .id(42L)
                .title("VPN bağlanamıyorum")
                .priority(Priority.HIGH)
                .status(TicketStatus.NEW)
                .customerId("customer-1")
                .build();

        // lenient: blank-recipient testinde send() erken döner, createMimeMessage çağrılmaz.
        lenient().when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getDefaultInstance(new Properties())));
    }

    @Test
    void sendTicketCreatedEmail_callsMailSenderSend() {
        emailService.sendTicketCreatedEmail(customer, ticket);
        verify(mailSender).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "ticket_created", "status", "success").count());
    }

    @Test
    void sendTicketAssignedEmail_callsMailSenderSend() {
        User agent = User.builder().id("agent-1").email("agent@example.com").fullName("Mehmet Kaya").build();
        emailService.sendTicketAssignedEmail(agent, ticket);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendStatusChangedEmail_callsMailSenderSend() {
        emailService.sendStatusChangedEmail(customer, ticket, "NEW", "IN_PROGRESS");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendCommentAddedEmail_callsMailSenderSend() {
        emailService.sendCommentAddedEmail(customer, ticket, "Sorun devam ediyor.", "Destek Ajanı");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendSlaWarningEmail_callsMailSenderSend() {
        emailService.sendSlaWarningEmail(customer, ticket);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendSlaBreachedEmail_callsMailSenderSend() {
        emailService.sendSlaBreachedEmail(customer, ticket);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendTicketResolvedEmail_callsMailSenderSend() {
        emailService.sendTicketResolvedEmail(customer, ticket);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_whenMailSenderThrowsException_doesNotPropagate() {
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        assertDoesNotThrow(() -> emailService.sendTicketCreatedEmail(customer, ticket));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "ticket_created", "status", "failure").count());
    }

    @Test
    void sendTicketCreatedEmail_darkThemeUser_stillSends() {
        User darkUser = User.builder()
                .id("dark-1")
                .email("dark@example.com")
                .fullName("Dark Mode User")
                .preferredTheme(Theme.DARK)
                .build();

        emailService.sendTicketCreatedEmail(darkUser, ticket);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendTicketCreatedEmail_nullThemeFallsBackToLight() {
        User noThemeUser = User.builder()
                .id("nt-1")
                .email("nt@example.com")
                .fullName("No Theme User")
                .preferredTheme(null)
                .build();

        emailService.sendTicketCreatedEmail(noThemeUser, ticket);

        verify(mailSender).send(any(MimeMessage.class));
    }

    // -------------------------------------------------------------------------
    // Password reset / security notification e-postaları
    // -------------------------------------------------------------------------

    @Test
    void sendPasswordResetEmail_withOverrides_usesOverrideLocaleAndDarkPalette() {
        emailService.sendPasswordResetEmail(customer, "https://app/reset?token=abc&x=1", 60, "tr", "dark");
        verify(mailSender).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "password_reset", "status", "success").count());
    }

    @Test
    void sendPasswordResetEmail_nullOverrides_fallsBackToUserPreference() {
        User trUser = User.builder().id("tr-1").email("tr@example.com").fullName("Türk Kullanıcı")
                .preferredLanguage(Language.TR).preferredTheme(Theme.LIGHT).build();
        emailService.sendPasswordResetEmail(trUser, "https://app/reset?token=xyz", 30, null, null);
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendPasswordChangedEmail_buildsSecurityNotificationAndSends() {
        emailService.sendPasswordChangedEmail(customer, null, null);
        verify(mailSender).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "password_changed", "status", "success").count());
    }

    @Test
    void send2FADeviceAddedEmail_withLabel_sends() {
        emailService.send2FADeviceAddedEmail(customer, "iPhone 15");
        verify(mailSender).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "twofa_device_added", "status", "success").count());
    }

    @Test
    void send2FADeviceAddedEmail_blankLabel_usesUnnamedFallback() {
        emailService.send2FADeviceAddedEmail(customer, "   ");
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send2FADeviceRemovedEmail_nullLabel_usesUnnamedFallback() {
        emailService.send2FADeviceRemovedEmail(customer, null);
        verify(mailSender).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "twofa_device_removed", "status", "success").count());
    }

    @Test
    void send_blankRecipient_skipsAndRecordsSkippedMetric() {
        User noEmail = User.builder().id("ne-1").email("  ").fullName("No Email").build();
        emailService.sendPasswordChangedEmail(noEmail, null, null);
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "password_changed", "status", "skipped").count());
    }

    @Test
    void send_firstAttemptFailsThenSucceeds_recordsSuccess() {
        doThrow(new MailSendException("transient")).doNothing()
                .when(mailSender).send(any(MimeMessage.class));

        emailService.sendTicketCreatedEmail(customer, ticket);

        verify(mailSender, times(2)).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "ticket_created", "status", "success").count());
    }

    @Test
    void priorityBadge_coversAllPriorityBranches() {
        for (String priority : new String[]{"CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN", null}) {
            Ticket t = Ticket.builder().id(7L).title("x").priority(Priority.fromNullable(priority)).status(TicketStatus.NEW)
                    .customerId("customer-1").build();
            emailService.sendTicketCreatedEmail(customer, t);
        }
        // 6 mail = 6 başarılı gönderim (her priority dalı + null dalı).
        assertEquals(6.0, meterRegistry.counter(
                "mail_send_total", "category", "ticket_created", "status", "success").count());
    }

    @Test
    void sendTicketCreatedEmail_nullStatusAndTitle_handledByEscapeAndBadge() {
        Ticket t = Ticket.builder().id(9L).title(null).priority(null).status(null)
                .customerId("customer-1").build();
        assertDoesNotThrow(() -> emailService.sendTicketCreatedEmail(customer, t));
        verify(mailSender).send(any(MimeMessage.class));
    }

    // -------------------------------------------------------------------------
    // sendTestEmail — senkron admin SMTP testi
    // -------------------------------------------------------------------------

    @Test
    void sendTestEmail_success_returnsNullAndRecordsSuccess() {
        String result = emailService.sendTestEmail(customer);

        assertNull(result); // null = başarı
        verify(mailSender).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "test", "status", "success").count());
    }

    @Test
    void sendTestEmail_whenSmtpFails_returnsErrorMessageAndRecordsFailure() {
        doThrow(new MailSendException("SMTP connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        String result = emailService.sendTestEmail(customer);

        assertEquals("SMTP connection refused", result);
        // Test gönderimi tek denemedir — retry yok.
        verify(mailSender, times(1)).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "test", "status", "failure").count());
    }

    @Test
    void sendTestEmail_blankRecipient_skipsAndReturnsMarker() {
        User noEmail = User.builder().id("ne-1").email("  ").fullName("No Email").build();

        String result = emailService.sendTestEmail(noEmail);

        assertEquals("no-recipient", result);
        verify(mailSender, never()).send(any(MimeMessage.class));
        assertEquals(1.0, meterRegistry.counter(
                "mail_send_total", "category", "test", "status", "skipped").count());
    }
}
