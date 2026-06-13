package com.ticketsystem.it_service_backend.util;

/**
 * Canonical ticket audit-log action types, centralizing what used to be string
 * literals scattered across the mutation services.
 *
 * <p>Deliberately String constants rather than an enum: {@code ticket_audit_logs}
 * is an append-only historical log, so older rows may carry action values no longer
 * emitted by current code. Reads ({@code TicketAuditLog#getActionType()}) must never
 * fail on an unmapped value, which an {@code @Enumerated} field would. The values
 * here are the closed set the code currently <em>writes</em>.
 */
public final class AuditAction {

    private AuditAction() {
    }

    public static final String CREATE = "CREATE";
    public static final String CLAIM = "CLAIM";
    public static final String UNCLAIM = "UNCLAIM";
    public static final String ASSIGN = "ASSIGN";
    public static final String CLOSE = "CLOSE";
    public static final String RESOLVE = "RESOLVE";
    public static final String REOPEN = "REOPEN";
    public static final String WAITING = "WAITING";
    public static final String RESUME = "RESUME";
    public static final String STATUS_CHANGE = "STATUS_CHANGE";
    public static final String PRIORITY_CHANGE = "PRIORITY_CHANGE";
    public static final String TOPIC_CHANGE = "TOPIC_CHANGE";
    public static final String CSAT_SUBMITTED = "CSAT_SUBMITTED";
}
