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
        String subject = "Destek kaydınız oluşturuldu: #" + ticket.getId();
        String body = buildHtml(
                "Destek Kaydı Oluşturuldu",
                "Merhaba " + customer.getFullName() + ",",
                "Destek kaydınız başarıyla oluşturuldu. Ekibimiz en kısa sürede size dönecektir.",
                ticket
        );
        send(customer.getEmail(), subject, body);
    }

    @Async
    public void sendTicketAssignedEmail(User agent, Ticket ticket) {
        String subject = "Yeni bilet atandı: #" + ticket.getId();
        String body = buildHtml(
                "Bilet Atandı",
                "Merhaba " + agent.getFullName() + ",",
                "Aşağıdaki bilet üzerinize atanmıştır. Lütfen inceleyiniz.",
                ticket
        );
        send(agent.getEmail(), subject, body);
    }

    @Async
    public void sendStatusChangedEmail(User customer, Ticket ticket, String oldStatus, String newStatus) {
        String subject = "Bilet durumu güncellendi: #" + ticket.getId();
        String body = buildHtml(
                "Bilet Durumu Değişti",
                "Merhaba " + customer.getFullName() + ",",
                "Biletinizin durumu <strong>" + escapeHtml(oldStatus)
                        + "</strong> &rarr; <strong>" + escapeHtml(newStatus) + "</strong> olarak güncellendi.",
                ticket
        );
        send(customer.getEmail(), subject, body);
    }

    @Async
    public void sendCommentAddedEmail(User recipient, Ticket ticket, String commentMessage, String commenterName) {
        String subject = "Bilet #" + ticket.getId() + " için yeni yorum eklendi";
        String body = buildHtml(
                "Yeni Yorum",
                "Merhaba " + recipient.getFullName() + ",",
                "<strong>" + escapeHtml(commenterName) + "</strong> şunu yazdı:<br><br>"
                        + "<blockquote style=\"border-left:4px solid #2563eb;margin:0;padding:8px 16px;color:#555;\">"
                        + escapeHtml(commentMessage) + "</blockquote>",
                ticket
        );
        send(recipient.getEmail(), subject, body);
    }

    @Async
    public void sendSlaWarningEmail(User recipient, Ticket ticket) {
        String subject = "SLA uyarısı: Bilet #" + ticket.getId() + " yaklaşıyor";
        String body = buildHtml(
                "SLA Uyarısı",
                "Merhaba " + recipient.getFullName() + ",",
                "Aşağıdaki biletin SLA süresi <strong>dolmak üzere</strong>. Lütfen acilen müdahale ediniz.",
                ticket
        );
        send(recipient.getEmail(), subject, body);
    }

    @Async
    public void sendSlaBreachedEmail(User recipient, Ticket ticket) {
        String subject = "SLA ihlali: Bilet #" + ticket.getId();
        String body = buildHtml(
                "SLA İhlali",
                "Merhaba " + recipient.getFullName() + ",",
                "Aşağıdaki biletin SLA süresi <strong style=\"color:#dc2626;\">dolmuştur</strong>. "
                        + "Acil müdahale gerekmektedir.",
                ticket
        );
        send(recipient.getEmail(), subject, body);
    }

    @Async
    public void sendTicketResolvedEmail(User customer, Ticket ticket) {
        String subject = "Destek kaydınız çözüldü: #" + ticket.getId();
        String body = buildHtml(
                "Bilet Çözüldü",
                "Merhaba " + customer.getFullName() + ",",
                "Destek kaydınız çözüme kavuşturulmuştur. Memnun kalmadıysanız bileti yeniden "
                        + "açabilirsiniz.",
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
            log.debug("Mail gönderildi: to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("Mail gönderilemedi: to={}, subject={}, hata={}", to, subject, e.getMessage());
        }
    }

    private String buildHtml(String title, String greeting, String bodyContent, Ticket ticket) {
        return """
                <!DOCTYPE html>
                <html lang="tr">
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
                        <td style="padding:8px;font-weight:bold;width:40%%;">Bilet #</td>
                        <td style="padding:8px;">%d</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;font-weight:bold;">Başlık</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                      <tr style="background:#f3f4f6;">
                        <td style="padding:8px;font-weight:bold;">Öncelik</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                      <tr>
                        <td style="padding:8px;font-weight:bold;">Durum</td>
                        <td style="padding:8px;">%s</td>
                      </tr>
                    </table>
                    <p style="margin-top:24px;font-size:12px;color:#6b7280;">
                      Bu mesaj IT Service Desk tarafından otomatik gönderilmiştir.
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
