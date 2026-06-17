package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.NotificationPreferenceResponse;
import com.ticketsystem.it_service_backend.dto.NotificationResponse;
import com.ticketsystem.it_service_backend.dto.UpdateNotificationPreferenceRequest;
import com.ticketsystem.it_service_backend.entity.*;
import com.ticketsystem.it_service_backend.repository.NotificationPreferenceRepository;
import com.ticketsystem.it_service_backend.repository.NotificationRepository;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Notification orchestration for ticket events.
 *
 * <p>For every event, three channels are evaluated against the recipient's
 * {@link com.ticketsystem.it_service_backend.entity.NotificationPreference} settings:
 * the in-app feed (DB row), email (via {@link EmailService} over SMTP), and the
 * STOMP WebSocket broadcast (consumed by
 * {@link com.ticketsystem.it_service_backend.websocket.WebSocketNotificationListener}).
 * Email triggers fired inside an open transaction are deferred to the
 * {@code afterCommit} phase — so emails do not leak on rollback. A nightly job
 * cleans up notifications based on read/unread status and age thresholds.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final EmailService emailService;
    private final MessageSource messageSource;

    // -------------------------------------------------------------------------
    // Bildirim tetikleyicileri (TicketService, CommentService, Scheduler çağırır)
    // -------------------------------------------------------------------------

    /**
     * Triggers in-app and optional email notification for the customer (owner)
     * when a new ticket is created. User preferences are checked independently
     * for each channel.
     *
     * @param ticket the newly created ticket
     */
    public void notifyTicketCreated(Ticket ticket) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnTicketCreated())) {
                saveNotification(customer.getId(), NotificationType.TICKET_CREATED,
                        "notification.ticket.created", args(ticket.getId(), ticket.getTitle()),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnTicketCreated())) {
                runAfterCommit(() -> emailService.sendTicketCreatedEmail(customer, ticket));
            }
        });
    }

    /**
     * Called when an Agent Admin manually assigns a ticket.
     * Sends separate notifications to the target agent and the customer.
     *
     * @param ticket the assigned ticket
     * @param targetAgentId ID of the assigned agent
     * @param adminId ID of the ADMIN making the assignment
     */
    public void notifyTicketAssigned(Ticket ticket, String targetAgentId, String adminId) {
        // 1. Hedef agent'a bildirim gönder
        userRepository.findById(targetAgentId).ifPresent(agent -> {
            NotificationPreference pref = getOrDefaultPreference(agent.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnTicketAssigned())) {
                saveNotification(agent.getId(), NotificationType.TICKET_ASSIGNED,
                        "notification.ticket.assigned.agent", args(ticket.getId(), ticket.getTitle()),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnTicketAssigned())) {
                runAfterCommit(() -> emailService.sendTicketAssignedEmail(agent, ticket));
            }
        });

        // 2. Müşteriye bildirim gönder
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnStatusChanged())) {
                saveNotification(customer.getId(), NotificationType.TICKET_ASSIGNED,
                        "notification.ticket.assigned.customer", args(ticket.getId(), ticket.getTitle()),
                        ticket.getId());
            }
        });

        log.info("Atama bildirimleri gönderildi. Bilet: {}, Agent: {}", ticket.getId(), targetAgentId);
    }

    /**
     * Triggers an in-app and optional email status-change notification to the
     * customer (excluding CLOSE/RESOLVE, which have their own paths).
     *
     * @param ticket the ticket whose status changed (the new status is on {@code ticket.getStatus()})
     * @param oldStatus previous status
     */
    public void notifyStatusChanged(Ticket ticket, String oldStatus) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnStatusChanged())) {
                saveNotification(customer.getId(), NotificationType.TICKET_STATUS_CHANGED,
                        "notification.ticket.status.changed",
                        args(ticket.getId(), oldStatus, ticket.getStatus().name()),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnStatusChanged())) {
                runAfterCommit(() ->
                        emailService.sendStatusChangedEmail(customer, ticket, oldStatus, ticket.getStatus().name()));
            }
        });
    }

    /**
     * Notifies the other party after an EXTERNAL comment is added. When the author
     * is the customer, all claim holders are notified; when the author is an agent,
     * the customer is notified. INTERNAL comments produce no notifications.
     *
     * @param ticket the ticket the comment belongs to
     * @param comment the new comment
     */
    public void notifyCommentAdded(Ticket ticket, Comment comment) {
        if (comment.getType() != CommentType.EXTERNAL) return;

        boolean authorIsCustomer = comment.getAuthorId().equals(ticket.getCustomerId());

        if (authorIsCustomer) {
            // Müşteri yazdı → tüm claim sahiplerini bildir
            ticketClaimRepository.findByTicketId(ticket.getId()).forEach(claim ->
                userRepository.findById(claim.getAgentId()).ifPresent(agent -> {
                    NotificationPreference pref = getOrDefaultPreference(agent.getId());
                    if (Boolean.TRUE.equals(pref.getNotifyOnCommentAdded())) {
                        saveNotification(agent.getId(), NotificationType.COMMENT_ADDED,
                                "notification.comment.added.agent", args(ticket.getId()),
                                ticket.getId());
                    }
                    if (Boolean.TRUE.equals(pref.getEmailOnCommentAdded())) {
                        userRepository.findById(comment.getAuthorId()).ifPresent(author ->
                                runAfterCommit(() -> emailService.sendCommentAddedEmail(agent, ticket,
                                        comment.getMessage(), author.getFullName())));
                    }
                })
            );
        } else {
            // Ajan yazdı → müşteriye bildir
            userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
                NotificationPreference pref = getOrDefaultPreference(customer.getId());
                if (Boolean.TRUE.equals(pref.getNotifyOnCommentAdded())) {
                    saveNotification(customer.getId(), NotificationType.COMMENT_ADDED,
                            "notification.comment.added.customer", args(ticket.getId()),
                            ticket.getId());
                }
                if (Boolean.TRUE.equals(pref.getEmailOnCommentAdded())) {
                    userRepository.findById(comment.getAuthorId()).ifPresent(author ->
                            runAfterCommit(() -> emailService.sendCommentAddedEmail(customer, ticket,
                                    comment.getMessage(), author.getFullName())));
                }
            });
        }
    }

    /**
     * Sends an SLA warning notification to every claim holder agent and every
     * manager when the warning threshold is crossed.
     *
     * @param ticket the ticket that entered the warning window
     */
    public void notifySlaWarning(Ticket ticket) {
        notifyStaffAboutSla(ticket, NotificationType.SLA_WARNING,
                "notification.sla.warning", true);
    }

    /**
     * Sends an SLA-breached notification to every claim holder agent and every
     * manager when an SLA breach occurs.
     *
     * @param ticket the ticket that breached its SLA
     */
    public void notifySlaBreached(Ticket ticket) {
        notifyStaffAboutSla(ticket, NotificationType.SLA_BREACHED,
                "notification.sla.breached", false);
    }

    /**
     * Sends an in-app and optional email notification to the customer when a
     * ticket transitions to RESOLVED.
     *
     * @param ticket the resolved ticket
     */
    public void notifyTicketResolved(Ticket ticket) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnTicketResolved())) {
                saveNotification(customer.getId(), NotificationType.TICKET_RESOLVED,
                        "notification.ticket.resolved", args(ticket.getId(), ticket.getTitle()),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnTicketResolved())) {
                runAfterCommit(() -> emailService.sendTicketResolvedEmail(customer, ticket));
            }
        });
    }

    // -------------------------------------------------------------------------
    // API sorgu metotları (NotificationController ve NotificationPreferenceController çağırır)
    // -------------------------------------------------------------------------

    /**
     * Returns the user's notifications newest-to-oldest, paginated. Messages are
     * rendered at read time from the i18n key + args according to the user's
     * language preference; pre-V33 rows fall back to the stored {@code message}
     * column.
     *
     * @param userId recipient user ID
     * @param pageable pagination
     * @return localized notification page
     */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationsForUser(String userId, Pageable pageable) {
        // The requester IS the recipient (userId is the JWT subject), so notifications
        // are rendered at read time in the requester's current language preference.
        Locale locale = resolveUserLocale(userId);
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(n -> NotificationResponse.fromEntity(n, renderMessage(n, locale)));
    }

    /**
     * Returns the user's unread notification count (for the badge).
     *
     * @param userId target user
     * @return unread record count
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /**
     * Marks a single notification as read.
     *
     * @param notificationId target notification
     * @param userId requesting user (must be the owner)
     * @throws ResponseStatusException 404 if not found, 403 if owned by another user
     */
    @Transactional
    public void markAsRead(Long notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "error.notification.not.found"));
        if (!notification.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "error.notification.access.forbidden");
        }
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    /**
     * Marks every notification belonging to the user as read in a single SQL statement.
     *
     * @param userId target user
     */
    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    /**
     * Deletes a notification; records belonging to another user return 404
     * (ownership is checked in the same DELETE statement).
     *
     * @param notificationId notification to delete
     * @param userId requesting user
     * @throws ResponseStatusException 404 if not found or not authorized
     */
    @Transactional
    public void deleteNotification(Long notificationId, String userId) {
        int deleted = notificationRepository.deleteByIdAndUserId(notificationId, userId);
        if (deleted == 0) {
            // Ya bulunamadı ya da başkasına ait — ikisi de 404 döner
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "error.notification.not.found");
        }
    }

    /**
     * Deletes every notification belonging to the user (the "clear all" flow).
     *
     * @param userId target user
     */
    @Transactional
    public void deleteAllNotifications(String userId) {
        notificationRepository.deleteAllByUserId(userId);
    }

    /**
     * Automatic cleanup:
     *  - Read notifications: deleted after 48 hours
     *  - Unread notifications: deleted after 240 hours (10 days)
     * Runs every night at 02:00.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredNotifications() {
        ZonedDateTime readCutoff   = ZonedDateTime.now().minusHours(48);
        ZonedDateTime unreadCutoff = ZonedDateTime.now().minusHours(240);
        int deletedRead   = notificationRepository.deleteReadBefore(readCutoff);
        int deletedUnread = notificationRepository.deleteUnreadBefore(unreadCutoff);
        log.info("Bildirim temizliği tamamlandı. Silinen okunmuş: {}, okunmamış: {}",
                deletedRead, deletedUnread);
    }

    /**
     * Returns the user's notification preferences; falls back to the default
     * (all enabled) DTO when no row exists.
     *
     * @param userId target user
     * @return notification preferences DTO
     */
    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(String userId) {
        return preferenceRepository.findByUserId(userId)
                .map(NotificationPreferenceResponse::fromEntity)
                .orElse(NotificationPreferenceResponse.defaults());
    }

    /**
     * Partially updates the user's notification preferences. Only the fields
     * supplied in the request are changed; the rest are preserved. A new row is
     * created if none exists.
     *
     * @param userId target user
     * @param req DTO carrying the fields to update ({@code null} field = no-op)
     * @return DTO reflecting the final state
     */
    @Transactional
    public NotificationPreferenceResponse updatePreferences(String userId,
                                                            UpdateNotificationPreferenceRequest req) {
        NotificationPreference pref = preferenceRepository.findByUserId(userId)
                .orElse(NotificationPreference.builder().userId(userId).build());

        if (req.getEmailOnTicketCreated() != null)   pref.setEmailOnTicketCreated(req.getEmailOnTicketCreated());
        if (req.getEmailOnTicketAssigned() != null)  pref.setEmailOnTicketAssigned(req.getEmailOnTicketAssigned());
        if (req.getEmailOnStatusChanged() != null)   pref.setEmailOnStatusChanged(req.getEmailOnStatusChanged());
        if (req.getEmailOnCommentAdded() != null)    pref.setEmailOnCommentAdded(req.getEmailOnCommentAdded());
        if (req.getEmailOnSlaWarning() != null)      pref.setEmailOnSlaWarning(req.getEmailOnSlaWarning());
        if (req.getEmailOnSlaBreached() != null)     pref.setEmailOnSlaBreached(req.getEmailOnSlaBreached());
        if (req.getEmailOnTicketResolved() != null)  pref.setEmailOnTicketResolved(req.getEmailOnTicketResolved());

        if (req.getNotifyOnTicketCreated() != null)  pref.setNotifyOnTicketCreated(req.getNotifyOnTicketCreated());
        if (req.getNotifyOnTicketAssigned() != null) pref.setNotifyOnTicketAssigned(req.getNotifyOnTicketAssigned());
        if (req.getNotifyOnStatusChanged() != null)  pref.setNotifyOnStatusChanged(req.getNotifyOnStatusChanged());
        if (req.getNotifyOnCommentAdded() != null)   pref.setNotifyOnCommentAdded(req.getNotifyOnCommentAdded());
        if (req.getNotifyOnSlaWarning() != null)     pref.setNotifyOnSlaWarning(req.getNotifyOnSlaWarning());
        if (req.getNotifyOnSlaBreached() != null)    pref.setNotifyOnSlaBreached(req.getNotifyOnSlaBreached());
        if (req.getNotifyOnTicketResolved() != null) pref.setNotifyOnTicketResolved(req.getNotifyOnTicketResolved());

        return NotificationPreferenceResponse.fromEntity(preferenceRepository.save(pref));
    }

    // -------------------------------------------------------------------------
    // Yardımcı metotlar
    // -------------------------------------------------------------------------

    private void notifyStaffAboutSla(Ticket ticket, NotificationType type,
                                     String messageKey, boolean isWarning) {
        // Tüm claim sahiplerini SLA uyarısı hakkında bilgilendir.
        ticketClaimRepository.findByTicketId(ticket.getId()).forEach(claim ->
            userRepository.findById(claim.getAgentId()).ifPresent(agent -> {
                NotificationPreference pref = getOrDefaultPreference(agent.getId());
                boolean shouldNotify = isWarning
                        ? Boolean.TRUE.equals(pref.getNotifyOnSlaWarning())
                        : Boolean.TRUE.equals(pref.getNotifyOnSlaBreached());
                boolean shouldEmail = isWarning
                        ? Boolean.TRUE.equals(pref.getEmailOnSlaWarning())
                        : Boolean.TRUE.equals(pref.getEmailOnSlaBreached());
                if (shouldNotify) saveNotification(agent.getId(), type,
                        messageKey, args(ticket.getId(), ticket.getTitle()), ticket.getId());
                if (shouldEmail) {
                    if (isWarning) runAfterCommit(() -> emailService.sendSlaWarningEmail(agent, ticket));
                    else           runAfterCommit(() -> emailService.sendSlaBreachedEmail(agent, ticket));
                }
            })
        );

        List<User> managers = userRepository.findByRole("MANAGER");
        for (User manager : managers) {
            NotificationPreference pref = getOrDefaultPreference(manager.getId());
            boolean shouldNotify = isWarning
                    ? Boolean.TRUE.equals(pref.getNotifyOnSlaWarning())
                    : Boolean.TRUE.equals(pref.getNotifyOnSlaBreached());
            boolean shouldEmail = isWarning
                    ? Boolean.TRUE.equals(pref.getEmailOnSlaWarning())
                    : Boolean.TRUE.equals(pref.getEmailOnSlaBreached());
            if (shouldNotify) saveNotification(manager.getId(), type,
                    messageKey, args(ticket.getId(), ticket.getTitle()), ticket.getId());
            if (shouldEmail) {
                if (isWarning) runAfterCommit(() -> emailService.sendSlaWarningEmail(manager, ticket));
                else           runAfterCommit(() -> emailService.sendSlaBreachedEmail(manager, ticket));
            }
        }
    }

    /**
     * Runs email triggers AFTER a transaction commit — when an active transaction
     * exists. This guarantees that no email goes out if the parent transaction
     * rolls back; the side effect of the @Async method is not triggered for an
     * uncommitted DB change.
     *
     * <p>When no active transaction exists (e.g. a transactionless flow outside the
     * scheduler), the task runs immediately — identical to the previous behavior.
     */
    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.error("Post-commit email dispatch failed: {}", e.getMessage(), e);
                    }
                }
            });
        } else {
            task.run();
        }
    }

    private NotificationPreference getOrDefaultPreference(String userId) {
        return preferenceRepository.findByUserId(userId)
                .orElse(NotificationPreference.builder().userId(userId).build());
    }

    /**
     * Builds the structured argument list for a notification message key. Every
     * argument is converted to its String form so {@link java.text.MessageFormat}
     * renders numeric IDs verbatim (e.g. ticket id 1234, not the locale-grouped
     * "1,234") and so the list serializes cleanly to the {@code message_args} JSONB
     * column. A {@code null} argument becomes an empty string.
     */
    private List<String> args(Object... values) {
        return Arrays.stream(values)
                .map(v -> v == null ? "" : String.valueOf(v))
                .toList();
    }

    /**
     * Resolves a user's {@link Locale} from their stored {@code preferredLanguage}.
     * Safe mapping: blank → English; starts with "tr" → Turkish; otherwise English.
     */
    private Locale resolveUserLocale(String userId) {
        return userRepository.findById(userId)
                .map(User::getPreferredLanguage)
                .map(Language::toLocale)
                .orElse(Locale.ENGLISH);
    }

    /**
     * Renders a notification's text in the given locale. Key-bearing rows (V33+)
     * render the stored {@code messageKey} + {@code messageArgs}; pre-V33 rows fall
     * back to their frozen legacy {@code message} text.
     */
    private String renderMessage(Notification n, Locale locale) {
        if (n.getMessageKey() != null) {
            Object[] argsArray = n.getMessageArgs() == null
                    ? new Object[0]
                    : n.getMessageArgs().toArray();
            return messageSource.getMessage(n.getMessageKey(), argsArray, n.getMessageKey(), locale);
        }
        return n.getMessage();
    }

    /**
     * Persists an in-app notification storing a localizable message KEY + ARGS
     * (rendered at read time per the recipient's language). The legacy {@code message}
     * column is left {@code null} for these V33+ rows.
     */
    private void saveNotification(String userId, NotificationType type,
                                  String messageKey, List<String> args, Long referenceId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .message(null)
                .messageKey(messageKey)
                .messageArgs(args)
                .referenceId(referenceId)
                .referenceType(NotificationReferenceType.TICKET)
                .isRead(false)
                .emailSent(false)
                .build();
        notificationRepository.save(notification);
    }
}
