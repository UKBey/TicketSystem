package com.ticketsystem.it_service_backend.entity;

/**
 * Which comment type a {@link CannedResponse} template suits, aligning it with the
 * {@link CommentType} an agent is composing.
 *
 * <ul>
 *   <li>{@link #EXTERNAL} — customer-facing reply.</li>
 *   <li>{@link #INTERNAL} — internal note.</li>
 *   <li>{@link #BOTH} — suits either side (the default).</li>
 * </ul>
 *
 * Stored as the enum name via {@code @Enumerated(EnumType.STRING)} on
 * {@link CannedResponse#getVisibility()}.
 */
public enum CannedResponseVisibility {
    EXTERNAL,
    INTERNAL,
    BOTH;

    /**
     * Parses a visibility from an HTTP request value. Returns {@code null} for {@code null},
     * blank or unrecognized input; whitespace is trimmed and matching is case-insensitive.
     * The caller decides how to react (default to {@link #BOTH} or reject with 400).
     *
     * @param value raw visibility string
     * @return the matching constant, or {@code null}
     */
    public static CannedResponseVisibility fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CannedResponseVisibility.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
