package com.ticketsystem.it_service_backend.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * i18n configuration — the single entry point for backend message bundles and
 * client locale resolution.
 *
 * <p>The locale is read from the {@code Accept-Language} header (the frontend
 * sets it based on {@code users.preferred_language}). Supported languages are
 * English and Turkish. {@code messages.properties} holds backend messages and
 * {@code ValidationMessages.properties} holds {@code @Valid} constraint
 * messages. The JVM locale is ignored; an unmatched request falls back
 * deterministically to English.
 */
@Configuration
public class LocalizationConfig {

    /**
     * A {@link LocaleResolver} backed by the {@code Accept-Language} header.
     *
     * <p>The default locale is {@code en}; the supported list is {@code en}
     * and {@code tr}. The header was preferred over cookies or sessions
     * because the frontend sends the user's preferred language directly on
     * every request.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("tr")));
        return resolver;
    }

    /**
     * Combines two resource bundles under a single {@link MessageSource}.
     *
     * <p>With {@code useCodeAsDefaultMessage=true} unknown keys return the key
     * itself instead of throwing an exception — kept for backwards
     * compatibility. {@code fallbackToSystemLocale=false} keeps behaviour
     * stable regardless of the host JVM's locale.
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        // "messages" → messages_en/tr.properties; "ValidationMessages" → @Valid constraint messages
        source.setBasenames("messages", "ValidationMessages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true);
        // Deterministic fallback: never consult the host JVM locale — any unmatched
        // locale resolves to messages_en.properties regardless of the server's locale.
        source.setDefaultLocale(Locale.ENGLISH);
        source.setFallbackToSystemLocale(false);
        return source;
    }
}
