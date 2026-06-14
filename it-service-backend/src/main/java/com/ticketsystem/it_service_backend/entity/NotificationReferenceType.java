package com.ticketsystem.it_service_backend.entity;

/**
 * The kind of record a {@link Notification} points to — used by the frontend to route
 * the click-through navigation (e.g. {@code TICKET} → the ticket detail page).
 *
 * <p>Stored as the enum name via {@code @Enumerated(EnumType.STRING)} on
 * {@link Notification#getReferenceType()}. Today every notification references a
 * {@link Ticket}; {@link #COMMENT} and {@link #CSAT} are reserved for forthcoming
 * notification sources so the column never has to widen back to a free string.
 */
public enum NotificationReferenceType {
    TICKET,
    COMMENT,
    CSAT;

    /**
     * Parses a reference type from a stored/request value. Returns {@code null} for
     * {@code null}, blank or unrecognized input; whitespace is trimmed and matching is
     * case-insensitive.
     *
     * @param value raw reference-type string
     * @return the matching constant, or {@code null}
     */
    public static NotificationReferenceType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return NotificationReferenceType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
