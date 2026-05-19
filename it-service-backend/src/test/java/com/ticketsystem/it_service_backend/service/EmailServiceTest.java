package com.ticketsystem.it_service_backend.service;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
                .priority("HIGH")
                .status("NEW")
                .customerId("customer-1")
                .build();

        when(mailSender.createMimeMessage())
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
                .preferredTheme("dark")
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
}
