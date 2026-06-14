package com.ticketsystem.it_service_backend.entity;

/**
 * A user's preferred date display format — a preset key the frontend understands and
 * applies to every date rendered in the UI.
 *
 * <p>The constant names ARE the wire/persisted values (e.g. {@code DMY_SLASH}), so this
 * is a plain {@code @Enumerated(EnumType.STRING)} column on {@link User#getPreferredDateFormat()}
 * with no code mapping. The set MUST stay in sync with the frontend's known presets.
 */
public enum DateFormat {
    /** {@code 31/12/2026} */
    DMY_SLASH,
    /** {@code 12/31/2026} */
    MDY_SLASH,
    /** {@code 2026-12-31} */
    YMD_DASH,
    /** {@code 31.12.2026} */
    DMY_DOT,
    /** Localized medium form (e.g. {@code Dec 31, 2026}). */
    MED;

    /** The system default when none is set or a value cannot be parsed. */
    public static final DateFormat DEFAULT = DMY_SLASH;

    /**
     * Parses a preset key (exact match, whitespace trimmed). Returns {@code null} for
     * {@code null}, blank or unrecognized input — the caller decides how to react
     * (typically a 400). Matching is case-sensitive: the keys are fixed UI tokens.
     *
     * @param value raw preset key
     * @return the matching constant, or {@code null}
     */
    public static DateFormat fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DateFormat.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
