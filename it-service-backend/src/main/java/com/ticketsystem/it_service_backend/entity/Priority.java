package com.ticketsystem.it_service_backend.entity;

/**
 * Ticket priority levels — drive the SLA target duration (see
 * {@code SlaPolicyService}). Ordered low→high so {@link #ordinal()} reflects
 * severity, though SQL sort order is still expressed explicitly in the
 * repository CASE expressions. Stored as the enum name via
 * {@code @Enumerated(EnumType.STRING)} on {@link Ticket#getPriority()}.
 */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * Parses an exact priority name (e.g. an HTTP request value). Returns
     * {@code null} for {@code null}, blank or unrecognized input — the caller
     * decides how to react (typically a 400). Whitespace is trimmed; matching is
     * case-sensitive to mirror the previously hand-rolled validation.
     *
     * @param value raw priority string
     * @return the matching constant, or {@code null}
     */
    public static Priority fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Priority.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
