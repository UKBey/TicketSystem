package com.ticketsystem.it_service_backend.entity;

/**
 * Ticket lifecycle states.
 *
 * <p>The authoritative state machine lives in the jBPM BPMN
 * ({@code ticket-lifecycle.bpmn2}); these constant names MUST stay in lock-step
 * with the BPMN {@code transition_<NAME>} signal targets and with the persisted
 * column values. Stored as the enum name via {@code @Enumerated(EnumType.STRING)}
 * on {@link Ticket#getStatus()} — never as the ordinal, so reordering is safe.
 */
public enum TicketStatus {
    NEW,
    IN_PROGRESS,
    WAITING_FOR_CUSTOMER,
    RESOLVED,
    CLOSED;

    /**
     * Parses an exact status name (e.g. an HTTP request value). Returns {@code null}
     * for {@code null}, blank or unrecognized input — the caller decides how to react
     * (typically a 400). Whitespace is trimmed; matching is case-sensitive to mirror
     * the previously hand-rolled validation.
     *
     * @param value raw status string
     * @return the matching constant, or {@code null}
     */
    public static TicketStatus fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return TicketStatus.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
