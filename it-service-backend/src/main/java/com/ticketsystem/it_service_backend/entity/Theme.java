package com.ticketsystem.it_service_backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * A user's preferred UI/email color theme.
 *
 * <p>The persisted/wire form is the lower-case token ({@code light}/{@code dark}), not the
 * constant name — kept stable for the frontend and the {@code preferred_theme} column.
 * {@link #getCode()} ({@code @JsonValue}) drives JSON output and
 * {@link com.ticketsystem.it_service_backend.converter.ThemeConverter} drives DB storage.
 * Email templates pick a light or dark palette from this value.
 */
public enum Theme {
    LIGHT("light"),
    DARK("dark");

    /** The system default when none is set or a value cannot be parsed. */
    public static final Theme DEFAULT = LIGHT;

    private final String code;

    Theme(String code) {
        this.code = code;
    }

    /** Lower-case token — the persisted and JSON value (e.g. {@code "dark"}). */
    @JsonValue
    public String getCode() {
        return code;
    }

    /**
     * Parses a theme token (case-insensitive, whitespace trimmed). Returns {@code null}
     * for {@code null}, blank or unrecognized input.
     *
     * @param value raw theme token
     * @return the matching constant, or {@code null}
     */
    @JsonCreator
    public static Theme fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (Theme theme : values()) {
            if (normalized.equals(theme.code)) {
                return theme;
            }
        }
        return null;
    }
}
