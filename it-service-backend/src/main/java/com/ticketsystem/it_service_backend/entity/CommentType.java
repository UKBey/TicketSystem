package com.ticketsystem.it_service_backend.entity;

/**
 * Visibility of a ticket {@link Comment}.
 *
 * <ul>
 *   <li>{@link #EXTERNAL} — visible to both agents and the customer (the default).</li>
 *   <li>{@link #INTERNAL} — visible only to agents/admins.</li>
 * </ul>
 *
 * Stored as the enum name via {@code @Enumerated(EnumType.STRING)} on
 * {@link Comment#getType()}.
 */
public enum CommentType {
    EXTERNAL,
    INTERNAL;

    /**
     * Parses a comment type from an HTTP request value. Returns {@code null} for
     * {@code null}, blank or unrecognized input; whitespace is trimmed and matching
     * is case-insensitive. Callers typically fall back to {@link #EXTERNAL}.
     *
     * @param value raw type string
     * @return the matching constant, or {@code null}
     */
    public static CommentType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CommentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
