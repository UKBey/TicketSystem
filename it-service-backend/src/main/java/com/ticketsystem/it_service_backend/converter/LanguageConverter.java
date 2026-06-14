package com.ticketsystem.it_service_backend.converter;

import com.ticketsystem.it_service_backend.entity.Language;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link Language} as its lower-case ISO code ({@code en}/{@code tr}) rather than
 * the upper-case constant name, so the {@code preferred_language} column keeps the values
 * the frontend and pre-existing rows use. Unknown/blank stored values map to
 * {@link Language#DEFAULT} on read so a stray value never fails a query.
 */
@Converter
public class LanguageConverter implements AttributeConverter<Language, String> {

    @Override
    public String convertToDatabaseColumn(Language attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public Language convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return Language.fromCodeOrDefault(dbData);
    }
}
