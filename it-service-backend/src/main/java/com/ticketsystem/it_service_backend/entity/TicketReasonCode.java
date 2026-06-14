package com.ticketsystem.it_service_backend.entity;

import java.util.Locale;
import java.util.Set;

/**
 * Canonical reason codes an actor (or the system) attaches to a ticket mutation —
 * the typed source of truth for what used to be bare string literals shared only with
 * the frontend. Grouped by the {@link AuditAction} they accompany; {@link #OTHER} is the
 * shared free-text escape hatch (a note is then required).
 *
 * <p><b>Why this is not an {@code @Enumerated} column.</b> Reason codes are written into
 * the append-only {@code ticket_audit_logs} table alongside {@code action_type}; older rows
 * may carry codes no longer offered by current code, and reads
 * ({@link TicketAuditLog#getReasonCode()}) must never fail on an unmapped value. As with
 * {@link AuditAction}, the column therefore stays a plain {@code String} and this enum is
 * used only at the write/validation boundary. The constant names ARE the stored/wire values.
 *
 * <p>The per-action sets below MUST stay in sync with the frontend's {@code reasonCodes.js}.
 */
public enum TicketReasonCode {

    // --- UNCLAIM ---
    INSUFFICIENT_KNOWLEDGE,
    WORKLOAD,
    OUT_OF_SCOPE,
    REASSIGNMENT_NEEDED,

    // --- RESOLVE ---
    SOLUTION_PROVIDED,
    INFORMATION_GIVEN,
    WORKAROUND_PROVIDED,
    CONFIGURATION_CHANGE,
    ROOT_CAUSE_FIXED,

    // --- CLOSE ---
    RESOLVED_CONFIRMED,
    NO_RESPONSE,
    DUPLICATE,
    INVALID,
    CUSTOMER_REQUEST,
    /** System-generated CLOSE reason stamped when a customer submits CSAT (no manual input). */
    CSAT_SUBMITTED,

    // --- PRIORITY_CHANGE ---
    CUSTOMER_IMPACT,
    BUSINESS_URGENCY,
    SLA_REASSESSMENT,
    INCORRECT_INITIAL,

    // --- TOPIC_CHANGE ---
    MISCATEGORIZED,
    SCOPE_REFINED,
    ROOT_CAUSE_DIFFERENT,
    CUSTOMER_CLARIFIED,

    /** Shared escape hatch — requires an explanatory note. */
    OTHER;

    /** Reason codes offered for {@link AuditAction#UNCLAIM}. */
    public static final Set<TicketReasonCode> FOR_UNCLAIM = Set.of(
            INSUFFICIENT_KNOWLEDGE, WORKLOAD, OUT_OF_SCOPE, REASSIGNMENT_NEEDED, OTHER);

    /** Reason codes offered for {@link AuditAction#RESOLVE}. */
    public static final Set<TicketReasonCode> FOR_RESOLVE = Set.of(
            SOLUTION_PROVIDED, INFORMATION_GIVEN, WORKAROUND_PROVIDED,
            CONFIGURATION_CHANGE, ROOT_CAUSE_FIXED, OTHER);

    /** Reason codes valid for {@link AuditAction#CLOSE} (incl. the system {@link #CSAT_SUBMITTED}). */
    public static final Set<TicketReasonCode> FOR_CLOSE = Set.of(
            RESOLVED_CONFIRMED, NO_RESPONSE, DUPLICATE, INVALID, CUSTOMER_REQUEST, CSAT_SUBMITTED, OTHER);

    /** Reason codes offered for {@link AuditAction#PRIORITY_CHANGE}. */
    public static final Set<TicketReasonCode> FOR_PRIORITY_CHANGE = Set.of(
            CUSTOMER_IMPACT, BUSINESS_URGENCY, SLA_REASSESSMENT, INCORRECT_INITIAL, OTHER);

    /** Reason codes offered for {@link AuditAction#TOPIC_CHANGE}. */
    public static final Set<TicketReasonCode> FOR_TOPIC_CHANGE = Set.of(
            MISCATEGORIZED, SCOPE_REFINED, ROOT_CAUSE_DIFFERENT, CUSTOMER_CLARIFIED, OTHER);

    /**
     * Parses an exact reason code (whitespace trimmed, case-sensitive — the codes are fixed
     * tokens shared with the frontend). Returns {@code null} for {@code null}, blank or
     * unrecognized input.
     *
     * @param value raw reason code
     * @return the matching constant, or {@code null}
     */
    public static TicketReasonCode fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TicketReasonCode.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Whether the raw code denotes {@link #OTHER} (the only code that requires a note).
     * Trimmed and case-insensitive so it mirrors lenient frontend input.
     *
     * @param value raw reason code
     * @return {@code true} if the value is the OTHER code
     */
    public static boolean isOther(String value) {
        return value != null && OTHER.name().equals(value.trim().toUpperCase(Locale.ROOT));
    }
}
