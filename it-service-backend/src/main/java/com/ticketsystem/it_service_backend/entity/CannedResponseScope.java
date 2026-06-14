package com.ticketsystem.it_service_backend.entity;

/**
 * Ownership scope of a {@link CannedResponse}.
 *
 * <ul>
 *   <li>{@link #PERSONAL} — owned by a single agent ({@link CannedResponse#getOwnerAgentId()});
 *       the least-privileged default.</li>
 *   <li>{@link #SHARED} — team-wide, managed only by {@code LEAD_AGENT}/{@code ADMIN}.</li>
 * </ul>
 *
 * Stored as the enum name via {@code @Enumerated(EnumType.STRING)} on
 * {@link CannedResponse#getScope()}.
 */
public enum CannedResponseScope {
    PERSONAL,
    SHARED;

    /**
     * Parses a scope from an HTTP request value. Returns {@code null} for {@code null},
     * blank or unrecognized input; whitespace is trimmed and matching is case-insensitive.
     * The caller decides how to react (default to {@link #PERSONAL} or reject with 400).
     *
     * @param value raw scope string
     * @return the matching constant, or {@code null}
     */
    public static CannedResponseScope fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CannedResponseScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
