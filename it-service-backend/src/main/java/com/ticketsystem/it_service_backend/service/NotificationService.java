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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final TicketClaimRepository ticketClaimRepository;
    private final EmailService emailService;

    // -------------------------------------------------------------------------
    // Bildirim tetikleyicileri (TicketService, CommentService, Scheduler çağırır)
    // -------------------------------------------------------------------------

    public void notifyTicketCreated(Ticket ticket) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnTicketCreated())) {
                saveNotification(customer.getId(), NotificationType.TICKET_CREATED,
                        "Ticket #" + ticket.getId() + " has been successfully created: " + ticket.getTitle(),
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
                        "Ticket #" + ticket.getId() + " has been assigned to you: " + ticket.getTitle(),
                        ticket.getId());
            }
            if (Boolean.TRUE.equals(pref.getEmailOnTicketAssigned())) {
                emailService.sendTicketAssignedEmail(agent, ticket);
            }
        });
    }

    public void notifyStatusChanged(Ticket ticket, String oldStatus) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnStatusChanged())) {
                saveNotification(customer.getId(), NotificationType.TICKET_STATUS_CHANGED,
                        "Ticket #" + ticket.getId() + " status updated: "
                                + oldStatus + " → " + ticket.getStatus(),
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
                                "New customer comment on ticket #" + ticket.getId() + ".",
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
                            "A new reply has been added to ticket #" + ticket.getId() + ".",
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
                "SLA deadline approaching for ticket #" + ticket.getId() + ": " + ticket.getTitle(),
                true);
    }

    public void notifySlaBreached(Ticket ticket) {
        notifyStaffAboutSla(ticket, NotificationType.SLA_BREACHED,
                "SLA breached for ticket #" + ticket.getId() + ": " + ticket.getTitle(),
                false);
    }

    public void notifyTicketResolved(Ticket ticket) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getNotifyOnTicketResolved())) {
                saveNotification(customer.getId(), NotificationType.TICKET_RESOLVED,
                        "Ticket #" + ticket.getId() + " has been resolved: " + ticket.getTitle(),
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
                        "Notification not found: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not have permission to read this notification.");
        }
        notification.setIsRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsReadByUserId(userId);
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
                                     String message, boolean isWarning) {
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
                if (shouldNotify) saveNotification(agent.getId(), type, message, ticket.getId());
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
            if (shouldNotify) saveNotification(manager.getId(), type, message, ticket.getId());
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
