package com.ticketsystem.it_service_backend.entity;

/**
 * {@link Notification} event types — the email template, in-app icon, and
 * {@link NotificationPreference} channel selection are all chosen based on this value.
 */
public enum NotificationType {
    TICKET_CREATED,
    TICKET_ASSIGNED,
    TICKET_STATUS_CHANGED,
    COMMENT_ADDED,
    SLA_WARNING,
    SLA_BREACHED,
    TICKET_RESOLVED,
    GENERAL
}
