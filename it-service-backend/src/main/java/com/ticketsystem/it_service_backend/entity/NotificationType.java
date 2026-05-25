package com.ticketsystem.it_service_backend.entity;

/**
 * {@link Notification} olay türleri — e-posta şablonu, in-app ikon ve
 * {@link NotificationPreference} kanal seçimi bu değere göre belirlenir.
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
