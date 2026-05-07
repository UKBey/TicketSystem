package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Async
    public void sendTicketCreatedEmail(User customer, Ticket ticket) {
        String subject = "Your support ticket has been created: #" + ticket.getId();
        String body = buildHtml(
                "Support Ticket Created",
                "Hello " + customer.getFullName() + ",",
                "Your support ticket has been successfully created. Our team will get back to you as soon as possible.",
                ticket
        );
        send(customer.getEmail(), subject, body);
    }

    @Async
    public void sendTicketAssignedEmail(User agent, Ticket ticket) {
        String subject = "New ticket assigned to you: #" + ticket.getId();
        String body = buildHtml(
                "Ticket Assigned",
                "Hello " + agent.getFullName() + ",",
                "The following ticket has been assigned to you. Please review it.",
                ticket
        );
        send(agent.getEmail(), subject, body);
    }

    @Async
    public void sendStatusChangedEmail(User customer, Ticket ticket, String oldStatus, String newStatus) {
        String subject = "Ticket status updated: #" + ticket.getId();
        String body = buildHtml(
                "Ticket Status Changed",
                "Hello " + customer.getFullName() + ",",
                "Your ticket status has been updated from <strong>" + escapeHtml(oldStatus)
                        + "</strong> &rarr; <strong>" + escapeHtml(newStatus) + "</strong>.",
                ticket
        );
        send(customer.getEmail(), subject, body);
    }

    @Async
    public void sendCommentAddedEmail(User recipient, Ticket ticket, String commentMessage, String commenterName) {
        String subject = "New comment on ticket #" + ticket.getId();
        String body = buildHtml(
                "New Comment",
                "Hello " + recipient.getFullName() + ",",
                "<strong>" + escapeHtml(commenterName) + "</strong> wrote:<br><br>"
                        + "<blockquote style=\"border-left:4px solid #2563eb;margin:0;padding:8px 16px;color:#555;\">"
                        + escapeHtml(commentMessage) + "</blockquote>",
                ticket
        );
        send(recipient.getEmail(), subject, body);
    }

    @Async
    public void sendSlaWarningEmail(User recipient, Ticket ticket) {
        String subject = "SLA warning: Ticket #" + ticket.getId() + " approaching deadline";
        String body = buildHtml(
                "SLA Warning",
                "Hello " + recipient.getFullName() + ",",
                "The SLA deadline for the following ticket is <strong>approaching</strong>. Please take action immediately.",
                ticket
        );
        send(recipient.getEmail(), subject, body);
    }

    @Async
    public void sendSlaBreachedEmail(User recipient, Ticket ticket) {
        String subject = "SLA breached: Ticket #" + ticket.getId();
        String body = buildHtml(
                "SLA Breached",
                "Hello " + recipient.getFullName() + ",",
                "The SLA deadline for the following ticket has <strong style=\"color:#dc2626;\">expired</strong>. "
                        + "Immediate action is required.",
                ticket
        );
        send(recipient.getEmail(), subject, body);
    }

    @Async
    public void sendTicketResolvedEmail(User customer, Ticket ticket) {
        String subject = "Your support ticket has been resolved: #" + ticket.getId();
        String body = buildHtml(
                "Ticket Resolved",
                "Hello " + customer.getFullName() + ",",
                "Your support ticket has been resolved. If you are not satisfied, you can reopen the ticket.",
                ticket
        );
        send(customer.getEmail(), subject, body);
    }

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

    private String buildHtml(String title, String greeting, String bodyContent, Ticket ticket) {
        return """
                <!DOCTYPE html>
                <html lang="en">
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
                        <td style="padding:8px;font-weight:bold;width:40%%;">Ticket #</td>
                        <td style="padding:8px;">%d</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;font-weight:bold;">Title</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                      <tr style="background:#f3f4f6;">
                        <td style="padding:8px;font-weight:bold;">Priority</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;font-weight:bold;">Status</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                    </table>
                    <p style="margin-top:24px;font-size:12px;color:#6b7280;">
                      This message was automatically sent by IT Service Desk.
                    </p>
                  </div>
                </body>
                </html>
                """.formatted(
                title, greeting, bodyContent,
                ticket.getId(), escapeHtml(ticket.getTitle()),
                ticket.getPriority(), ticket.getStatus()
        );
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
