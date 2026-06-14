package com.ticketsystem.it_service_backend.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * A user's preferred UI/email language (ISO 639-1).
 *
 * <p>Unlike most enums in this package the persisted/wire form is the lower-case ISO
 * code ({@code en}/{@code tr}), not the constant name — kept stable for the frontend and
 * the {@code preferred_language} column. {@link #getCode()} ({@code @JsonValue}) drives
 * JSON output and {@link com.ticketsystem.it_service_backend.converter.LanguageConverter}
 * drives DB storage, so neither sees the upper-case constant name.
 */
public enum Language {
    EN("en", Locale.ENGLISH),
    TR("tr", Locale.forLanguageTag("tr"));

    /** The system default when none is set or a value cannot be parsed. */
    public static final Language DEFAULT = EN;

    private final String code;
    private final Locale locale;

    Language(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    /** Lower-case ISO 639-1 code — the persisted and JSON value (e.g. {@code "tr"}). */
    @JsonValue
    public String getCode() {
        return code;
    }

    /** The fully-translated {@link Locale} this language maps to (for MessageSource/email). */
    public Locale toLocale() {
        return locale;
    }

    /**
     * Parses a language code (case-insensitive, whitespace trimmed). Accepts the bare ISO
     * code and tolerates region suffixes (e.g. {@code "tr-TR"} → {@link #TR}). Returns
     * {@code null} for {@code null}, blank or unrecognized input.
     *
     * @param value raw language code
     * @return the matching constant, or {@code null}
     */
    @JsonCreator
    public static Language fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (normalized.equals(language.code) || normalized.startsWith(language.code + "-")) {
                return language;
            }
        }
        return null;
    }

    /** Like {@link #fromCode(String)} but falls back to {@link #DEFAULT} instead of {@code null}. */
    public static Language fromCodeOrDefault(String value) {
        Language parsed = fromCode(value);
        return parsed != null ? parsed : DEFAULT;
    }
}
