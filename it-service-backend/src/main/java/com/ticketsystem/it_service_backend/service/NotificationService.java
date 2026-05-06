package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.NotificationPreferenceResponse;
import com.ticketsystem.it_service_backend.dto.NotificationResponse;
import com.ticketsystem.it_service_backend.dto.UpdateNotificationPreferenceRequest;
import com.ticketsystem.it_service_backend.entity.*;
import com.ticketsystem.it_service_backend.repository.NotificationPreferenceRepository;
import com.ticketsystem.it_service_backend.repository.NotificationRepository;
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
    private final EmailService emailService;

    // -------------------------------------------------------------------------
    // Bildirim tetikleyicileri (TicketService, CommentService, Scheduler çağırır)
    // -------------------------------------------------------------------------

    public void notifyTicketCreated(Ticket ticket) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            saveNotification(customer.getId(), NotificationType.TICKET_CREATED,
                    "Bilet #" + ticket.getId() + " başarıyla oluşturuldu: " + ticket.getTitle(),
                    ticket.getId());
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getEmailOnTicketCreated())) {
                emailService.sendTicketCreatedEmail(customer, ticket);
            }
        });
    }

    public void notifyTicketAssigned(Ticket ticket) {
        if (ticket.getAssigneeId() == null) return;
        userRepository.findById(ticket.getAssigneeId()).ifPresent(agent -> {
            saveNotification(agent.getId(), NotificationType.TICKET_ASSIGNED,
                    "Bilet #" + ticket.getId() + " üzerinize atandı: " + ticket.getTitle(),
                    ticket.getId());
            NotificationPreference pref = getOrDefaultPreference(agent.getId());
            if (Boolean.TRUE.equals(pref.getEmailOnTicketAssigned())) {
                emailService.sendTicketAssignedEmail(agent, ticket);
            }
        });
    }

    public void notifyStatusChanged(Ticket ticket, String oldStatus) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            saveNotification(customer.getId(), NotificationType.TICKET_STATUS_CHANGED,
                    "Bilet #" + ticket.getId() + " durumu güncellendi: "
                            + oldStatus + " → " + ticket.getStatus(),
                    ticket.getId());
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
            if (Boolean.TRUE.equals(pref.getEmailOnStatusChanged())) {
                emailService.sendStatusChangedEmail(customer, ticket, oldStatus, ticket.getStatus());
            }
        });
    }

    public void notifyCommentAdded(Ticket ticket, Comment comment) {
        if (!"EXTERNAL".equals(comment.getType())) return;

        boolean authorIsCustomer = comment.getAuthorId().equals(ticket.getCustomerId());

        if (authorIsCustomer) {
            // Müşteri yazdı → ajana bildir
            if (ticket.getAssigneeId() == null) return;
            userRepository.findById(ticket.getAssigneeId()).ifPresent(agent -> {
                saveNotification(agent.getId(), NotificationType.COMMENT_ADDED,
                        "Bilet #" + ticket.getId() + " için yeni müşteri yorumu eklendi.",
                        ticket.getId());
                NotificationPreference pref = getOrDefaultPreference(agent.getId());
                if (Boolean.TRUE.equals(pref.getEmailOnCommentAdded())) {
                    userRepository.findById(comment.getAuthorId()).ifPresent(author ->
                            emailService.sendCommentAddedEmail(agent, ticket,
                                    comment.getMessage(), author.getFullName()));
                }
            });
        } else {
            // Ajan yazdı → müşteriye bildir
            userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
                saveNotification(customer.getId(), NotificationType.COMMENT_ADDED,
                        "Bilet #" + ticket.getId() + " için yeni bir yanıt eklendi.",
                        ticket.getId());
                NotificationPreference pref = getOrDefaultPreference(customer.getId());
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
                "Bilet #" + ticket.getId() + " için SLA süresi dolmak üzere: " + ticket.getTitle(),
                true);
    }

    public void notifySlaBreached(Ticket ticket) {
        notifyStaffAboutSla(ticket, NotificationType.SLA_BREACHED,
                "Bilet #" + ticket.getId() + " için SLA süresi doldu: " + ticket.getTitle(),
                false);
    }

    public void notifyTicketResolved(Ticket ticket) {
        userRepository.findById(ticket.getCustomerId()).ifPresent(customer -> {
            saveNotification(customer.getId(), NotificationType.TICKET_RESOLVED,
                    "Bilet #" + ticket.getId() + " çözüme kavuşturuldu: " + ticket.getTitle(),
                    ticket.getId());
            NotificationPreference pref = getOrDefaultPreference(customer.getId());
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
                        "Bildirim bulunamadı: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Bu bildirimi okuma yetkiniz yok.");
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

        if (req.getEmailOnTicketCreated() != null)  pref.setEmailOnTicketCreated(req.getEmailOnTicketCreated());
        if (req.getEmailOnTicketAssigned() != null) pref.setEmailOnTicketAssigned(req.getEmailOnTicketAssigned());
        if (req.getEmailOnStatusChanged() != null)  pref.setEmailOnStatusChanged(req.getEmailOnStatusChanged());
        if (req.getEmailOnCommentAdded() != null)   pref.setEmailOnCommentAdded(req.getEmailOnCommentAdded());
        if (req.getEmailOnSlaWarning() != null)     pref.setEmailOnSlaWarning(req.getEmailOnSlaWarning());
        if (req.getEmailOnSlaBreached() != null)    pref.setEmailOnSlaBreached(req.getEmailOnSlaBreached());
        if (req.getEmailOnTicketResolved() != null) pref.setEmailOnTicketResolved(req.getEmailOnTicketResolved());

        return NotificationPreferenceResponse.fromEntity(preferenceRepository.save(pref));
    }

    // -------------------------------------------------------------------------
    // Yardımcı metotlar
    // -------------------------------------------------------------------------

    private void notifyStaffAboutSla(Ticket ticket, NotificationType type,
                                     String message, boolean isWarning) {
        // Atanan ajan varsa bildir
        if (ticket.getAssigneeId() != null) {
            userRepository.findById(ticket.getAssigneeId()).ifPresent(agent -> {
                saveNotification(agent.getId(), type, message, ticket.getId());
                NotificationPreference pref = getOrDefaultPreference(agent.getId());
                boolean shouldEmail = isWarning
                        ? Boolean.TRUE.equals(pref.getEmailOnSlaWarning())
                        : Boolean.TRUE.equals(pref.getEmailOnSlaBreached());
                if (shouldEmail) {
                    if (isWarning) emailService.sendSlaWarningEmail(agent, ticket);
                    else           emailService.sendSlaBreachedEmail(agent, ticket);
                }
            });
        }

        // Tüm yöneticileri bildir
        List<User> managers = userRepository.findByRole("MANAGER");
        for (User manager : managers) {
            saveNotification(manager.getId(), type, message, ticket.getId());
            NotificationPreference pref = getOrDefaultPreference(manager.getId());
            boolean shouldEmail = isWarning
                    ? Boolean.TRUE.equals(pref.getEmailOnSlaWarning())
                    : Boolean.TRUE.equals(pref.getEmailOnSlaBreached());
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
