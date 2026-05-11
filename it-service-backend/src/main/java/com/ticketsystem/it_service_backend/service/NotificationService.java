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
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

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

    public void notifyTicketCreated(Ticket ticket) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnTicketCreated())) {
                saveNotification(customer.getId(), NotificationType.TICKET_CREATED,
                        msg(customer, "notification.ticket.created", ticket.getId(), ticket.getTitle()),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnTicketCreated())) {
                emailService.sendTicketCreatedEmail(customer, ticket);
            }
        });
    }

    /**
     * Claim alan ajana bildirim gönderir. Çok-agentli yapıda her yeni claimer için ayrı çağrılır.
     */
    public void notifyTicketClaimed(Ticket ticket, String agentId) {
        userRepository.findById(agentId).ifPresent(agent -> {
            NotificationPreference pref = getOrDefaultPreference(agent.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnTicketAssigned())) {
                saveNotification(agent.getId(), NotificationType.TICKET_ASSIGNED,
                        msg(agent, "notification.ticket.assigned.agent", ticket.getId(), ticket.getTitle()),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnTicketAssigned())) {
                emailService.sendTicketAssignedEmail(agent, ticket);
            }
        });
    }

    /**
     * Agent Admin tarafından manuel atama yapıldığında çağrılır.
     * Hedef agent'a ve müşteriye ayrı bildirimler gönderir.
     */
    public void notifyTicketAssigned(Ticket ticket, String targetAgentId, String adminId) {
        // 1. Hedef agent'a bildirim gönder
        userRepository.findById(targetAgentId).ifPresent(agent -> {
            NotificationPreference pref = getOrDefaultPreference(agent.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnTicketAssigned())) {
                saveNotification(agent.getId(), NotificationType.TICKET_ASSIGNED,
                        msg(agent, "notification.ticket.assigned.agent", ticket.getId(), ticket.getTitle()),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnTicketAssigned())) {
                emailService.sendTicketAssignedEmail(agent, ticket);
            }
        });

        // 2. Müşteriye bildirim gönder
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnStatusChanged())) {
                saveNotification(customer.getId(), NotificationType.TICKET_ASSIGNED,
                        msg(customer, "notification.ticket.assigned.customer", ticket.getId(), ticket.getTitle()),
                        ticket.getId());
            }
        });

        log.info("Atama bildirimleri gönderildi. Bilet: {}, Agent: {}", ticket.getId(), targetAgentId);
    }

    public void notifyStatusChanged(Ticket ticket, String oldStatus) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnStatusChanged())) {
                saveNotification(customer.getId(), NotificationType.TICKET_STATUS_CHANGED,
                        msg(customer, "notification.ticket.status.changed",
                                ticket.getId(), oldStatus, ticket.getStatus()),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnStatusChanged())) {
                emailService.sendStatusChangedEmail(customer, ticket, oldStatus, ticket.getStatus());
            }
        });
    }

    public void notifyCommentAdded(Ticket ticket, Comment comment) {
        if (!"EXTERNAL".equals(comment.getType())) return;

        boolean authorIsCustomer = comment.getAuthorId().equals(ticket.getCustomerId());

        if (authorIsCustomer) {
            // Müşteri yazdı → tüm claim sahiplerini bildir
            ticketClaimRepository.findByTicketId(ticket.getId()).forEach(claim ->
                userRepository.findById(claim.getAgentId()).ifPresent(agent -> {
                    NotificationPreference pref = getOrDefaultPreference(agent.getId());
                    if (Boolean.TRUE.equals(pref.getNotifyOnCommentAdded())) {
                        saveNotification(agent.getId(), NotificationType.COMMENT_ADDED,
                                msg(agent, "notification.comment.added.agent", ticket.getId()),
                                ticket.getId());
                    }
                    if (Boolean.TRUE.equals(pref.getEmailOnCommentAdded())) {
                        userRepository.findById(comment.getAuthorId()).ifPresent(author ->
                                emailService.sendCommentAddedEmail(agent, ticket,
                                        comment.getMessage(), author.getFullName()));
                    }
                })
            );
        } else {
            // Ajan yazdı → müşteriye bildir
            userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
                NotificationPreference pref = getOrDefaultPreference(customer.getId());
                if (Boolean.TRUE.equals(pref.getNotifyOnCommentAdded())) {
                    saveNotification(customer.getId(), NotificationType.COMMENT_ADDED,
                            msg(customer, "notification.comment.added.customer", ticket.getId()),
                            ticket.getId());
                }
                if (Boolean.TRUE.equals(pref.getEmailOnCommentAdded())) {
                    userRepository.findById(comment.getAuthorId()).ifPresent(author ->
                            emailService.sendCommentAddedEmail(customer, ticket,
                                    comment.getMessage(), author.getFullName()));
                }
            });
        }
    }

    public void notifySlaWarning(Ticket ticket) {
        notifyStaffAboutSla(ticket, NotificationType.SLA_WARNING,
                "notification.sla.warning", true);
    }

    public void notifySlaBreached(Ticket ticket) {
        notifyStaffAboutSla(ticket, NotificationType.SLA_BREACHED,
                "notification.sla.breached", false);
    }

    public void notifyTicketResolved(Ticket ticket) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnTicketResolved())) {
                saveNotification(customer.getId(), NotificationType.TICKET_RESOLVED,
                        msg(customer, "notification.ticket.resolved", ticket.getId(), ticket.getTitle()),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnTicketResolved())) {
                emailService.sendTicketResolvedEmail(customer, ticket);
            }
        });
    }

    // -------------------------------------------------------------------------
    // API sorgu metotları (NotificationController ve NotificationPreferenceController çağırır)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationsForUser(String userId, Pageable pageable) {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

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

    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public void deleteNotification(Long notificationId, String userId) {
        int deleted = notificationRepository.deleteByIdAndUserId(notificationId, userId);
        if (deleted == 0) {
            // Ya bulunamadı ya da başkasına ait — ikisi de 404 döner
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "error.notification.not.found");
        }
    }

    @Transactional
    public void deleteAllNotifications(String userId) {
        notificationRepository.deleteAllByUserId(userId);
    }

    /**
     * Otomatik temizlik:
     *  - Okunmuş bildirimler: 48 saat sonra silinir
     *  - Okunmamış bildirimler: 240 saat (10 gün) sonra silinir
     * Her gece 02:00'de çalışır.
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

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(String userId) {
        return preferenceRepository.findByUserId(userId)
                .map(NotificationPreferenceResponse::fromEntity)
                .orElse(NotificationPreferenceResponse.defaults());
    }

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
                        msg(agent, messageKey, ticket.getId(), ticket.getTitle()), ticket.getId());
                if (shouldEmail) {
                    if (isWarning) emailService.sendSlaWarningEmail(agent, ticket);
                    else           emailService.sendSlaBreachedEmail(agent, ticket);
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
                    msg(manager, messageKey, ticket.getId(), ticket.getTitle()), ticket.getId());
            if (shouldEmail) {
                if (isWarning) emailService.sendSlaWarningEmail(manager, ticket);
                else           emailService.sendSlaBreachedEmail(manager, ticket);
            }
        }
    }

    private NotificationPreference getOrDefaultPreference(String userId) {
        return preferenceRepository.findByUserId(userId)
                .orElse(NotificationPreference.builder().userId(userId).build());
    }

    /** Resolves a message key using the user's stored language preference. */
    private String msg(User user, String key, Object... args) {
        String lang = user.getPreferredLanguage();
        Locale locale = (lang == null || lang.isBlank()) ? Locale.ENGLISH : Locale.forLanguageTag(lang);
        return messageSource.getMessage(key, args, key, locale);
    }

    private void saveNotification(String userId, NotificationType type,
                                  String message, Long referenceId) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .message(message)
                .referenceId(referenceId)
                .referenceType("TICKET")
                .isRead(false)
                .emailSent(false)
                .build();
        notificationRepository.save(notification);
    }
}
