package com.ticketsystem.it_service_backend.converter;

import com.ticketsystem.it_service_backend.entity.Theme;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link Theme} as its lower-case token ({@code light}/{@code dark}) rather than
 * the upper-case constant name, keeping the {@code preferred_theme} column aligned with the
 * frontend and pre-existing rows. Unknown/blank stored values map to {@link Theme#DEFAULT}
 * on read.
 */
@Converter
public class ThemeConverter implements AttributeConverter<Theme, String> {

    @Override
    public String convertToDatabaseColumn(Theme attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public Theme convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        Theme parsed = Theme.fromCode(dbData);
        return parsed != null ? parsed : Theme.DEFAULT;
    }
}
