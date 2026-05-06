package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private User customer;
    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromAddress", "noreply@ticketsystem.local");

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
    }
}
