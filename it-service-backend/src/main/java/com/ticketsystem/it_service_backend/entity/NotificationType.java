package com.ticketsystem.it_service_backend.entity;

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
