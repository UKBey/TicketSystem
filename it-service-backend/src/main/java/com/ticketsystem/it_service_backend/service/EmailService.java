package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Log4j2
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;

    @Value("${app.mail.from}")
    private String fromAddress;

    // -------------------------------------------------------------------------
    // Public send methods — each resolves messages using the recipient's locale
    // -------------------------------------------------------------------------

    @Async
    public void sendTicketCreatedEmail(User customer, Ticket ticket) {
        Locale locale = localeOf(customer);
        String subject = msg(locale, "email.subject.ticket.created", ticket.getId());
        String body = buildHtml(locale,
                msg(locale, "email.title.ticket.created"),
                msg(locale, "email.greeting", customer.getFullName()),
                msg(locale, "email.body.ticket.created"),
                ticket);
        send(customer.getEmail(), subject, body);
    }

    @Async
    public void sendTicketAssignedEmail(User agent, Ticket ticket) {
        Locale locale = localeOf(agent);
        String subject = msg(locale, "email.subject.ticket.assigned", ticket.getId());
        String body = buildHtml(locale,
                msg(locale, "email.title.ticket.assigned"),
                msg(locale, "email.greeting", agent.getFullName()),
                msg(locale, "email.body.ticket.assigned"),
                ticket);
        send(agent.getEmail(), subject, body);
    }

    @Async
    public void sendStatusChangedEmail(User customer, Ticket ticket, String oldStatus, String newStatus) {
        Locale locale = localeOf(customer);
        String subject = msg(locale, "email.subject.status.changed", ticket.getId());
        String body = buildHtml(locale,
                msg(locale, "email.title.status.changed"),
                msg(locale, "email.greeting", customer.getFullName()),
                msg(locale, "email.body.status.changed", escapeHtml(oldStatus), escapeHtml(newStatus)),
                ticket);
        send(customer.getEmail(), subject, body);
    }

    @Async
    public void sendCommentAddedEmail(User recipient, Ticket ticket, String commentMessage, String commenterName) {
        Locale locale = localeOf(recipient);
        String subject = msg(locale, "email.subject.comment.added", ticket.getId());
        String body = buildHtml(locale,
                msg(locale, "email.title.comment.added"),
                msg(locale, "email.greeting", recipient.getFullName()),
                msg(locale, "email.body.comment.added", escapeHtml(commenterName), escapeHtml(commentMessage)),
                ticket);
        send(recipient.getEmail(), subject, body);
    }

    @Async
    public void sendSlaWarningEmail(User recipient, Ticket ticket) {
        Locale locale = localeOf(recipient);
        String subject = msg(locale, "email.subject.sla.warning", ticket.getId());
        String body = buildHtml(locale,
                msg(locale, "email.title.sla.warning"),
                msg(locale, "email.greeting", recipient.getFullName()),
                msg(locale, "email.body.sla.warning"),
                ticket);
        send(recipient.getEmail(), subject, body);
    }

    @Async
    public void sendSlaBreachedEmail(User recipient, Ticket ticket) {
        Locale locale = localeOf(recipient);
        String subject = msg(locale, "email.subject.sla.breached", ticket.getId());
        String body = buildHtml(locale,
                msg(locale, "email.title.sla.breached"),
                msg(locale, "email.greeting", recipient.getFullName()),
                msg(locale, "email.body.sla.breached"),
                ticket);
        send(recipient.getEmail(), subject, body);
    }

    @Async
    public void sendTicketResolvedEmail(User customer, Ticket ticket) {
        Locale locale = localeOf(customer);
        String subject = msg(locale, "email.subject.ticket.resolved", ticket.getId());
        String body = buildHtml(locale,
                msg(locale, "email.title.ticket.resolved"),
                msg(locale, "email.greeting", customer.getFullName()),
                msg(locale, "email.body.ticket.resolved"),
                ticket);
        send(customer.getEmail(), subject, body);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.debug("Mail sent: to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("Mail could not be sent: to={}, subject={}, error={}", to, subject, e.getMessage());
        }
    }

    private String buildHtml(Locale locale, String title, String greeting, String bodyContent, Ticket ticket) {
        String labelTicket   = msg(locale, "email.label.ticket.number");
        String labelTitle    = msg(locale, "email.label.title");
        String labelPriority = msg(locale, "email.label.priority");
        String labelStatus   = msg(locale, "email.label.status");
        String footer        = msg(locale, "email.footer");

        return """
                <!DOCTYPE html>
                <html lang="%s">
                <body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;">
                  <div style="background:#2563eb;padding:20px;border-radius:8px 8px 0 0;">
                    <h2 style="color:#fff;margin:0;">IT Service Desk</h2>
                  </div>
                  <div style="padding:24px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 8px 8px;">
                    <h3 style="color:#1e40af;">%s</h3>
                    <p>%s</p>
                    <p>%s</p>
                    <table style="width:100%%;border-collapse:collapse;margin-top:16px;">
                      <tr style="background:#f3f4f6;">
                        <td style="padding:8px;font-weight:bold;width:40%%;">%s</td>
                        <td style="padding:8px;">%d</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;font-weight:bold;">%s</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                      <tr style="background:#f3f4f6;">
                        <td style="padding:8px;font-weight:bold;">%s</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;font-weight:bold;">%s</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                    </table>
                    <p style="margin-top:24px;font-size:12px;color:#6b7280;">
                      %s
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(
                locale.getLanguage(),
                title, greeting, bodyContent,
                labelTicket, ticket.getId(),
                labelTitle, escapeHtml(ticket.getTitle()),
                labelPriority, ticket.getPriority(),
                labelStatus, ticket.getStatus(),
                footer
        );
    }

    /** Resolves a message key with optional arguments for the given locale. */
    private String msg(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

    /** Returns the Locale matching the user's stored language preference. */
    private Locale localeOf(User user) {
        String lang = user.getPreferredLanguage();
        if (lang == null || lang.isBlank()) return Locale.ENGLISH;
        return Locale.forLanguageTag(lang);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
