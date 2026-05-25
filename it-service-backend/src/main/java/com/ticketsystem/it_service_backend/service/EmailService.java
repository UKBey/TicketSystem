package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import io.micrometer.core.instrument.MeterRegistry;
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

/**
 * Builds and sends HTML emails for ticket and account-security events.
 *
 * <p>All {@code send*} methods run {@code @Async}; messages are localized and a
 * light/dark color palette is applied based on the recipient's
 * {@code preferredLanguage} and {@code preferredTheme}. Delivery goes through
 * {@link JavaMailSender} (Mailpit in dev) with up to 3 retry attempts on transient
 * failures. Success / failure / skipped outcomes are tracked per category in the
 * {@code mail_send_total} counter.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;
    private final MeterRegistry meterRegistry;

    @Value("${app.mail.from}")
    private String fromAddress;

    private static final String METRIC_NAME      = "mail_send_total";
    private static final String TAG_CATEGORY     = "category";
    private static final String TAG_STATUS       = "status";
    private static final String STATUS_SUCCESS   = "success";
    private static final String STATUS_FAILURE   = "failure";
    private static final String STATUS_SKIPPED   = "skipped";

    static final class Category {
        static final String TICKET_CREATED      = "ticket_created";
        static final String TICKET_ASSIGNED     = "ticket_assigned";
        static final String TICKET_RESOLVED     = "ticket_resolved";
        static final String STATUS_CHANGED      = "status_changed";
        static final String COMMENT_ADDED       = "comment_added";
        static final String SLA_WARNING         = "sla_warning";
        static final String SLA_BREACHED        = "sla_breached";
        static final String PASSWORD_RESET      = "password_reset";
        static final String PASSWORD_CHANGED    = "password_changed";
        static final String TWOFA_DEVICE_ADDED  = "twofa_device_added";
        static final String TWOFA_DEVICE_REMOVED = "twofa_device_removed";

        private Category() {}
    }

    // -------------------------------------------------------------------------
    // Public send methods — each resolves messages using the recipient's locale
    // -------------------------------------------------------------------------

    /**
     * Sends a confirmation email to the customer when a new ticket is created (async).
     *
     * @param customer recipient customer (language/theme preferences are read from this)
     * @param ticket the related ticket
     */
    @Async
    public void sendTicketCreatedEmail(User customer, Ticket ticket) {
        Locale locale = localeOf(customer);
        String subject = msg(locale, "email.subject.ticket.created", ticket.getId());
        String body = buildHtml(locale, customer,
                msg(locale, "email.title.ticket.created"),
                msg(locale, "email.greeting", customer.getFullName()),
                msg(locale, "email.body.ticket.created"),
                ticket);
        send(customer.getEmail(), subject, body, Category.TICKET_CREATED);
    }

    /**
     * Sends a manual-assignment notification email to the agent (async).
     *
     * @param agent the assigned agent
     * @param ticket the assigned ticket
     */
    @Async
    public void sendTicketAssignedEmail(User agent, Ticket ticket) {
        Locale locale = localeOf(agent);
        String subject = msg(locale, "email.subject.ticket.assigned", ticket.getId());
        String body = buildHtml(locale, agent,
                msg(locale, "email.title.ticket.assigned"),
                msg(locale, "email.greeting", agent.getFullName()),
                msg(locale, "email.body.ticket.assigned"),
                ticket);
        send(agent.getEmail(), subject, body, Category.TICKET_ASSIGNED);
    }

    /**
     * Sends a ticket status-change notification email to the customer (async).
     *
     * @param customer recipient customer
     * @param ticket ticket reference
     * @param oldStatus previous status
     * @param newStatus new status
     */
    @Async
    public void sendStatusChangedEmail(User customer, Ticket ticket, String oldStatus, String newStatus) {
        Locale locale = localeOf(customer);
        String subject = msg(locale, "email.subject.status.changed", ticket.getId());
        String body = buildHtml(locale, customer,
                msg(locale, "email.title.status.changed"),
                msg(locale, "email.greeting", customer.getFullName()),
                msg(locale, "email.body.status.changed", escapeHtml(oldStatus), escapeHtml(newStatus)),
                ticket);
        send(customer.getEmail(), subject, body, Category.STATUS_CHANGED);
    }

    /**
     * Sends a notification email to the other party when a new comment is added (async).
     *
     * @param recipient recipient user
     * @param ticket ticket reference
     * @param commentMessage comment text (HTML-escaped)
     * @param commenterName commenter display name (HTML-escaped)
     */
    @Async
    public void sendCommentAddedEmail(User recipient, Ticket ticket, String commentMessage, String commenterName) {
        Locale locale = localeOf(recipient);
        String subject = msg(locale, "email.subject.comment.added", ticket.getId());
        String body = buildHtml(locale, recipient,
                msg(locale, "email.title.comment.added"),
                msg(locale, "email.greeting", recipient.getFullName()),
                msg(locale, "email.body.comment.added", escapeHtml(commenterName), escapeHtml(commentMessage)),
                ticket);
        send(recipient.getEmail(), subject, body, Category.COMMENT_ADDED);
    }

    /**
     * Sends a warning email for an upcoming SLA breach (warning threshold) (async).
     *
     * @param recipient recipient (agent or manager)
     * @param ticket the related ticket
     */
    @Async
    public void sendSlaWarningEmail(User recipient, Ticket ticket) {
        Locale locale = localeOf(recipient);
        String subject = msg(locale, "email.subject.sla.warning", ticket.getId());
        String body = buildHtml(locale, recipient,
                msg(locale, "email.title.sla.warning"),
                msg(locale, "email.greeting", recipient.getFullName()),
                msg(locale, "email.body.sla.warning"),
                ticket);
        send(recipient.getEmail(), subject, body, Category.SLA_WARNING);
    }

    /**
     * Sends an alert email when an SLA breach has occurred (async).
     *
     * @param recipient recipient (agent or manager)
     * @param ticket the breached ticket
     */
    @Async
    public void sendSlaBreachedEmail(User recipient, Ticket ticket) {
        Locale locale = localeOf(recipient);
        String subject = msg(locale, "email.subject.sla.breached", ticket.getId());
        String body = buildHtml(locale, recipient,
                msg(locale, "email.title.sla.breached"),
                msg(locale, "email.greeting", recipient.getFullName()),
                msg(locale, "email.body.sla.breached"),
                ticket);
        send(recipient.getEmail(), subject, body, Category.SLA_BREACHED);
    }

    /**
     * Sends an informational email to the customer when a ticket transitions to RESOLVED (async).
     *
     * @param customer recipient customer
     * @param ticket the resolved ticket
     */
    @Async
    public void sendTicketResolvedEmail(User customer, Ticket ticket) {
        Locale locale = localeOf(customer);
        String subject = msg(locale, "email.subject.ticket.resolved", ticket.getId());
        String body = buildHtml(locale, customer,
                msg(locale, "email.title.ticket.resolved"),
                msg(locale, "email.greeting", customer.getFullName()),
                msg(locale, "email.body.ticket.resolved"),
                ticket);
        send(customer.getEmail(), subject, body, Category.TICKET_RESOLVED);
    }

    /**
     * Password-reset email containing the reset link. Unlike the other ticket-scoped
     * emails there is no ticket reference, so this uses a separate, simpler HTML template.
     *
     * <p>If {@code languageOverride} / {@code themeOverride} is supplied, the email is
     * rendered using whichever language/theme the user is currently running in their
     * browser session. When null/blank, the user's stored DB preference is used.
     *
     * @param recipient recipient user
     * @param resetUrl reset link (with the token query parameter)
     * @param ttlMinutes link validity period (minutes), displayed in the body
     * @param languageOverride the client's current language (en/tr), or {@code null}
     * @param themeOverride the client's current theme (light/dark), or {@code null}
     */
    @Async
    public void sendPasswordResetEmail(User recipient, String resetUrl, int ttlMinutes,
                                       String languageOverride, String themeOverride) {
        Locale locale = resolveLocale(recipient, languageOverride);
        Palette palette = resolvePalette(recipient, themeOverride);
        String subject = msg(locale, "email.subject.password.reset");
        String body = buildPasswordResetHtml(locale, palette, recipient, resetUrl, ttlMinutes);
        send(recipient.getEmail(), subject, body, Category.PASSWORD_RESET);
    }

    /**
     * Security notification sent when the password has been successfully changed.
     * Triggered at the end of both the profile-page "change password" and the
     * forgot-password reset flows. Lets the user spot unauthorized changes — prevents
     * silent password changes.
     *
     * @param recipient recipient user
     * @param languageOverride client's current language, or {@code null} (DB preference)
     * @param themeOverride client's current theme, or {@code null} (DB preference)
     */
    @Async
    public void sendPasswordChangedEmail(User recipient, String languageOverride, String themeOverride) {
        Locale locale = resolveLocale(recipient, languageOverride);
        Palette palette = resolvePalette(recipient, themeOverride);
        String subject = msg(locale, "email.subject.password.changed");
        String title   = msg(locale, "email.title.password.changed");
        String body    = msg(locale, "email.body.password.changed");
        String warning = msg(locale, "email.warning.password.changed");
        String html    = buildSecurityNotificationHtml(locale, palette, recipient, title, body, warning);
        send(recipient.getEmail(), subject, html, Category.PASSWORD_CHANGED);
    }

    /**
     * Security notification sent when a new 2FA device is added to the account.
     *
     * @param recipient recipient user
     * @param deviceLabel device label; localized "unnamed device" when null/blank
     */
    @Async
    public void send2FADeviceAddedEmail(User recipient, String deviceLabel) {
        Locale locale = localeOf(recipient);
        Palette palette = paletteOf(recipient);
        String label = (deviceLabel == null || deviceLabel.isBlank())
                ? msg(locale, "email.body.twofa.device.unnamed")
                : deviceLabel;
        String subject = msg(locale, "email.subject.twofa.added");
        String title   = msg(locale, "email.title.twofa.added");
        String body    = msg(locale, "email.body.twofa.added", escapeHtml(label));
        String warning = msg(locale, "email.warning.twofa.added");
        String html    = buildSecurityNotificationHtml(locale, palette, recipient, title, body, warning);
        send(recipient.getEmail(), subject, html, Category.TWOFA_DEVICE_ADDED);
    }

    /**
     * Security notification sent when a 2FA device is removed from the account.
     *
     * @param recipient recipient user
     * @param deviceLabel device label; localized "unnamed device" when null/blank
     */
    @Async
    public void send2FADeviceRemovedEmail(User recipient, String deviceLabel) {
        Locale locale = localeOf(recipient);
        Palette palette = paletteOf(recipient);
        String label = (deviceLabel == null || deviceLabel.isBlank())
                ? msg(locale, "email.body.twofa.device.unnamed")
                : deviceLabel;
        String subject = msg(locale, "email.subject.twofa.removed");
        String title   = msg(locale, "email.title.twofa.removed");
        String body    = msg(locale, "email.body.twofa.removed", escapeHtml(label));
        String warning = msg(locale, "email.warning.twofa.removed");
        String html    = buildSecurityNotificationHtml(locale, palette, recipient, title, body, warning);
        send(recipient.getEmail(), subject, html, Category.TWOFA_DEVICE_REMOVED);
    }

    /**
     * Shared security-notification template — for emails without a ticket
     * reference whose body is a single paragraph plus a red warning box
     * (password changed, 2FA added/removed).
     */
    private String buildSecurityNotificationHtml(Locale locale, Palette p, User recipient,
                                                 String title, String body, String warning) {
        String greeting = msg(locale, "email.greeting", recipient.getFullName());
        String footer   = msg(locale, "email.footer");

        return """
                <!DOCTYPE html>
                <html lang="%s">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <meta name="color-scheme" content="%s">
                </head>
                <body style="margin:0;padding:0;background:%s;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:%s;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;background:%s;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                        <tr><td style="background:linear-gradient(135deg,%s 0%%,%s 100%%);padding:32px;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                              <td style="vertical-align:middle;">
                                <div style="display:inline-block;width:44px;height:44px;line-height:44px;text-align:center;background:rgba(255,255,255,0.18);border-radius:12px;font-size:22px;color:#fff;font-weight:700;">IT</div>
                              </td>
                              <td style="vertical-align:middle;padding-left:14px;">
                                <div style="color:rgba(255,255,255,0.75);font-size:12px;letter-spacing:1.5px;text-transform:uppercase;font-weight:600;">IT Service Desk</div>
                                <div style="color:#ffffff;font-size:20px;font-weight:700;margin-top:2px;">%s</div>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <p style="margin:0 0 12px 0;font-size:15px;color:%s;">%s</p>
                          <p style="margin:0 0 24px 0;font-size:15px;line-height:1.6;color:%s;">%s</p>
                          <div style="padding:14px 18px;border-radius:10px;background:rgba(239,68,68,0.08);border:1px solid rgba(239,68,68,0.25);font-size:13px;line-height:1.5;color:#b91c1c;">
                            %s
                          </div>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px 32px;">
                          <div style="border-top:1px solid %s;padding-top:18px;font-size:12px;color:%s;text-align:center;line-height:1.5;">%s</div>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                locale.getLanguage(),
                p.colorScheme,
                p.bgBody, p.textPrimary,
                p.bgBody,
                p.bgCard,
                p.headerStart, p.headerEnd,
                title,
                p.textPrimary, greeting,
                p.textSecondary, body,
                warning,
                p.border, p.textMuted, footer
        );
    }

    private Locale resolveLocale(User user, String override) {
        if (override != null && !override.isBlank()) {
            return supportedLocale(override);
        }
        return localeOf(user);
    }

    private Palette resolvePalette(User user, String override) {
        if (override != null && !override.isBlank()) {
            return "dark".equalsIgnoreCase(override) ? Palette.DARK : Palette.LIGHT;
        }
        return paletteOf(user);
    }

    private String buildPasswordResetHtml(Locale locale, Palette p, User recipient, String resetUrl, int ttlMinutes) {
        String title    = msg(locale, "email.title.password.reset");
        String greeting = msg(locale, "email.greeting", recipient.getFullName());
        String body     = msg(locale, "email.body.password.reset", ttlMinutes);
        String cta      = msg(locale, "email.cta.password.reset");
        String fallback = msg(locale, "email.fallback.password.reset");
        String ignore   = msg(locale, "email.ignore.password.reset");
        String footer   = msg(locale, "email.footer");

        String safeUrl = escapeHtml(resetUrl);

        return """
                <!DOCTYPE html>
                <html lang="%s">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <meta name="color-scheme" content="%s">
                </head>
                <body style="margin:0;padding:0;background:%s;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:%s;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;background:%s;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                        <tr><td style="background:linear-gradient(135deg,%s 0%%,%s 100%%);padding:32px;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                              <td style="vertical-align:middle;">
                                <div style="display:inline-block;width:44px;height:44px;line-height:44px;text-align:center;background:rgba(255,255,255,0.18);border-radius:12px;font-size:22px;color:#fff;font-weight:700;">IT</div>
                              </td>
                              <td style="vertical-align:middle;padding-left:14px;">
                                <div style="color:rgba(255,255,255,0.75);font-size:12px;letter-spacing:1.5px;text-transform:uppercase;font-weight:600;">IT Service Desk</div>
                                <div style="color:#ffffff;font-size:20px;font-weight:700;margin-top:2px;">%s</div>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <p style="margin:0 0 12px 0;font-size:15px;color:%s;">%s</p>
                          <p style="margin:0 0 24px 0;font-size:15px;line-height:1.6;color:%s;">%s</p>
                          <p style="margin:0 0 24px 0;text-align:center;">
                            <a href="%s" style="display:inline-block;padding:14px 28px;background:%s;color:#ffffff;text-decoration:none;border-radius:10px;font-size:15px;font-weight:600;">%s</a>
                          </p>
                          <p style="margin:0 0 8px 0;font-size:13px;color:%s;">%s</p>
                          <p style="margin:0 0 24px 0;font-size:12px;color:%s;word-break:break-all;">%s</p>
                          <p style="margin:0;font-size:13px;color:%s;line-height:1.5;">%s</p>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px 32px;">
                          <div style="border-top:1px solid %s;padding-top:18px;font-size:12px;color:%s;text-align:center;line-height:1.5;">%s</div>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                locale.getLanguage(),
                p.colorScheme,
                p.bgBody, p.textPrimary,
                p.bgBody,
                p.bgCard,
                p.headerStart, p.headerEnd,
                title,
                p.textPrimary, greeting,
                p.textSecondary, body,
                safeUrl, p.headerStart, cta,
                p.textMuted, fallback,
                p.textMuted, safeUrl,
                p.textMuted, ignore,
                p.border, p.textMuted, footer
        );
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final long SEND_RETRY_DELAY_MS = 200L;

    private void send(String to, String subject, String htmlBody, String category) {
        // Boş alıcı: silent fail değil, açık atlama logu — kaynak debug edilebilir kalır.
        if (to == null || to.isBlank()) {
            log.warn("Mail atlandı (boş alıcı): subject={}", subject);
            recordMetric(category, STATUS_SKIPPED);
            return;
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_SEND_ATTEMPTS; attempt++) {
            try {
                MimeMessage msg = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
                helper.setFrom(fromAddress);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                mailSender.send(msg);
                if (attempt > 1) {
                    log.info("Mail sent on retry attempt {}/{}: to={}, subject={}",
                            attempt, MAX_SEND_ATTEMPTS, to, subject);
                } else {
                    log.debug("Mail sent: to={}, subject={}", to, subject);
                }
                recordMetric(category, STATUS_SUCCESS);
                return;
            } catch (Exception e) {
                lastError = e;
                if (attempt < MAX_SEND_ATTEMPTS) {
                    log.warn("Mail attempt {}/{} failed (will retry): to={}, subject={}, error={}",
                            attempt, MAX_SEND_ATTEMPTS, to, subject, e.getMessage());
                    try {
                        Thread.sleep(SEND_RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("Mail could not be sent after {} attempts: to={}, subject={}, error={}",
                MAX_SEND_ATTEMPTS, to, subject,
                lastError == null ? "unknown" : lastError.getMessage());
        recordMetric(category, STATUS_FAILURE);
    }

    private void recordMetric(String category, String status) {
        meterRegistry.counter(METRIC_NAME, TAG_CATEGORY, category, TAG_STATUS, status).increment();
    }

    private String buildHtml(Locale locale, User recipient, String title, String greeting,
                             String bodyContent, Ticket ticket) {
        String labelTicket   = msg(locale, "email.label.ticket.number");
        String labelTitle    = msg(locale, "email.label.title");
        String labelPriority = msg(locale, "email.label.priority");
        String labelStatus   = msg(locale, "email.label.status");
        String footer        = msg(locale, "email.footer");

        Palette p = paletteOf(recipient);
        String priorityBadge = priorityBadge(ticket.getPriority(), p);
        String statusBadge   = statusBadge(ticket.getStatus(), p);

        return """
                <!DOCTYPE html>
                <html lang="%s">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <meta name="color-scheme" content="%s">
                </head>
                <body style="margin:0;padding:0;background:%s;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:%s;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;background:%s;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">
                        <tr><td style="background:linear-gradient(135deg,%s 0%%,%s 100%%);padding:32px 32px 28px 32px;">
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                            <tr>
                              <td style="vertical-align:middle;">
                                <div style="display:inline-block;width:44px;height:44px;line-height:44px;text-align:center;background:rgba(255,255,255,0.18);border-radius:12px;font-size:22px;color:#fff;font-weight:700;">IT</div>
                              </td>
                              <td style="vertical-align:middle;padding-left:14px;">
                                <div style="color:rgba(255,255,255,0.75);font-size:12px;letter-spacing:1.5px;text-transform:uppercase;font-weight:600;">IT Service Desk</div>
                                <div style="color:#ffffff;font-size:20px;font-weight:700;margin-top:2px;">%s</div>
                              </td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <p style="margin:0 0 8px 0;font-size:15px;color:%s;">%s</p>
                          <p style="margin:0 0 24px 0;font-size:15px;line-height:1.6;color:%s;">%s</p>
                          <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s;border:1px solid %s;border-radius:12px;overflow:hidden;">
                            <tr>
                              <td style="padding:14px 18px;border-bottom:1px solid %s;font-size:13px;color:%s;width:35%%;">%s</td>
                              <td style="padding:14px 18px;border-bottom:1px solid %s;font-size:14px;color:%s;font-weight:600;">#%d</td>
                            </tr>
                            <tr>
                              <td style="padding:14px 18px;border-bottom:1px solid %s;font-size:13px;color:%s;">%s</td>
                              <td style="padding:14px 18px;border-bottom:1px solid %s;font-size:14px;color:%s;font-weight:600;">%s</td>
                            </tr>
                            <tr>
                              <td style="padding:14px 18px;border-bottom:1px solid %s;font-size:13px;color:%s;">%s</td>
                              <td style="padding:14px 18px;border-bottom:1px solid %s;">%s</td>
                            </tr>
                            <tr>
                              <td style="padding:14px 18px;font-size:13px;color:%s;">%s</td>
                              <td style="padding:14px 18px;">%s</td>
                            </tr>
                          </table>
                        </td></tr>
                        <tr><td style="padding:0 32px 28px 32px;">
                          <div style="border-top:1px solid %s;padding-top:18px;font-size:12px;color:%s;text-align:center;line-height:1.5;">%s</div>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(
                locale.getLanguage(),
                p.colorScheme,
                p.bgBody, p.textPrimary,
                p.bgBody,
                p.bgCard,
                p.headerStart, p.headerEnd,
                title,
                p.textPrimary, greeting,
                p.textSecondary, bodyContent,
                p.bgPanel, p.border,
                p.border, p.textMuted, labelTicket,
                p.border, p.textPrimary, ticket.getId(),
                p.border, p.textMuted, labelTitle,
                p.border, p.textPrimary, escapeHtml(ticket.getTitle()),
                p.border, p.textMuted, labelPriority,
                p.border, priorityBadge,
                p.textMuted, labelStatus,
                statusBadge,
                p.border, p.textMuted, footer
        );
    }

    /** Resolves a message key with optional arguments for the given locale. */
    private String msg(Locale locale, String key, Object... args) {
        return messageSource.getMessage(key, args, key, locale);
    }

    /** Returns the Locale matching the user's stored language preference. */
    private Locale localeOf(User user) {
        return supportedLocale(user.getPreferredLanguage());
    }

    /**
     * Maps any raw language input to a supported, fully-translated Locale (en/tr).
     * Guards against malformed values that {@link Locale#forLanguageTag} would
     * otherwise turn into {@code Locale.ROOT} — which renders emails as raw keys.
     */
    private Locale supportedLocale(String lang) {
        if (lang == null || lang.isBlank()) return Locale.ENGLISH;
        String normalized = lang.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("tr") ? Locale.forLanguageTag("tr") : Locale.ENGLISH;
    }

    /** Returns the color palette matching the user's stored theme preference. */
    private Palette paletteOf(User user) {
        return "dark".equalsIgnoreCase(user.getPreferredTheme()) ? Palette.DARK : Palette.LIGHT;
    }

    private String priorityBadge(String priority, Palette p) {
        String key = priority == null ? "" : priority.toUpperCase(Locale.ROOT);
        String bg; String fg;
        switch (key) {
            case "CRITICAL" -> { bg = p.badgeCriticalBg; fg = p.badgeCriticalFg; }
            case "HIGH"     -> { bg = p.badgeHighBg;     fg = p.badgeHighFg; }
            case "MEDIUM"   -> { bg = p.badgeMediumBg;   fg = p.badgeMediumFg; }
            case "LOW"      -> { bg = p.badgeLowBg;      fg = p.badgeLowFg; }
            default          -> { bg = p.badgeNeutralBg; fg = p.badgeNeutralFg; }
        }
        return badge(escapeHtml(priority == null ? "-" : priority), bg, fg);
    }

    private String statusBadge(String status, Palette p) {
        return badge(escapeHtml(status == null ? "-" : status), p.badgeNeutralBg, p.badgeNeutralFg);
    }

    private String badge(String text, String bg, String fg) {
        return "<span style=\"display:inline-block;padding:4px 12px;border-radius:999px;"
                + "font-size:12px;font-weight:700;letter-spacing:0.4px;background:" + bg
                + ";color:" + fg + ";\">" + text + "</span>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Color palette — injected into buildHtml based on the user's theme preference.
     * Inline CSS is required because most mail clients strip {@code <style>} blocks
     * and CSS variables.
     */
    private enum Palette {
        LIGHT(
                "light only",
                "#f3f4f6",   // bgBody — sayfa arkaplanı
                "#ffffff",   // bgCard — ana kart
                "#f9fafb",   // bgPanel — bilet detay tablosu
                "#e5e7eb",   // border
                "#2563eb",   // headerStart — gradient sol
                "#1e40af",   // headerEnd   — gradient sağ
                "#111827",   // textPrimary
                "#374151",   // textSecondary
                "#6b7280",   // textMuted
                // priority badges
                "#fee2e2", "#991b1b",  // critical
                "#ffedd5", "#9a3412",  // high
                "#fef3c7", "#92400e",  // medium
                "#dcfce7", "#166534",  // low
                "#e5e7eb", "#374151"   // neutral
        ),
        DARK(
                "dark only",
                "#0b1220",   // bgBody — koyu lacivert
                "#111827",   // bgCard
                "#1f2937",   // bgPanel
                "#374151",   // border
                "#3b82f6",   // headerStart
                "#1e3a8a",   // headerEnd
                "#f3f4f6",   // textPrimary
                "#d1d5db",   // textSecondary
                "#9ca3af",   // textMuted
                "#7f1d1d", "#fecaca",  // critical
                "#7c2d12", "#fed7aa",  // high
                "#78350f", "#fde68a",  // medium
                "#14532d", "#bbf7d0",  // low
                "#374151", "#e5e7eb"   // neutral
        );

        final String colorScheme;
        final String bgBody, bgCard, bgPanel, border;
        final String headerStart, headerEnd;
        final String textPrimary, textSecondary, textMuted;
        final String badgeCriticalBg, badgeCriticalFg;
        final String badgeHighBg, badgeHighFg;
        final String badgeMediumBg, badgeMediumFg;
        final String badgeLowBg, badgeLowFg;
        final String badgeNeutralBg, badgeNeutralFg;

        Palette(String colorScheme,
                String bgBody, String bgCard, String bgPanel, String border,
                String headerStart, String headerEnd,
                String textPrimary, String textSecondary, String textMuted,
                String badgeCriticalBg, String badgeCriticalFg,
                String badgeHighBg, String badgeHighFg,
                String badgeMediumBg, String badgeMediumFg,
                String badgeLowBg, String badgeLowFg,
                String badgeNeutralBg, String badgeNeutralFg) {
            this.colorScheme = colorScheme;
            this.bgBody = bgBody; this.bgCard = bgCard; this.bgPanel = bgPanel; this.border = border;
            this.headerStart = headerStart; this.headerEnd = headerEnd;
            this.textPrimary = textPrimary; this.textSecondary = textSecondary; this.textMuted = textMuted;
            this.badgeCriticalBg = badgeCriticalBg; this.badgeCriticalFg = badgeCriticalFg;
            this.badgeHighBg = badgeHighBg; this.badgeHighFg = badgeHighFg;
            this.badgeMediumBg = badgeMediumBg; this.badgeMediumFg = badgeMediumFg;
            this.badgeLowBg = badgeLowBg; this.badgeLowFg = badgeLowFg;
            this.badgeNeutralBg = badgeNeutralBg; this.badgeNeutralFg = badgeNeutralFg;
        }
    }
}
