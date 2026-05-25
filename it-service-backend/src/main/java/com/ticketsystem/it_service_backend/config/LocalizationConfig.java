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
 * i18n yapilandirmasi — backend mesaj bundle'larinin ve istemci locale
 * cozumunun tek noktasi.
 *
 * <p>Locale {@code Accept-Language} header'indan okunur (frontend bunu
 * {@code users.preferred_language}'a gore set eder). Desteklenen diller
 * Ingilizce ve Turkce. {@code messages.properties} backend mesajlari,
 * {@code ValidationMessages.properties} ise {@code @Valid} kisitlama mesajlari
 * icindir. JVM locale'i goz ardi edilir; eslesmeyen istek deterministik olarak
 * Ingilizce'ye duser.
 */
@Configuration
public class LocalizationConfig {

    /**
     * {@code Accept-Language} header'ina dayanan {@link LocaleResolver}.
     *
     * <p>Default locale {@code en}; desteklenen liste {@code en} ve {@code tr}.
     * Cookie / session yerine header tercih edildi cunku frontend her istekte
     * kullanicinin tercih ettigi dili dogrudan gonderir.
     */
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        resolver.setSupportedLocales(List.of(Locale.ENGLISH, Locale.forLanguageTag("tr")));
        return resolver;
    }

    /**
     * Iki resource bundle'i tek {@link MessageSource} altinda toplar.
     *
     * <p>{@code useCodeAsDefaultMessage=true} ile bilinmeyen anahtarlarda
     * exception yerine anahtarin kendisi dondurulur — geriye donuk uyumluluk
     * icin. {@code fallbackToSystemLocale=false}, host JVM'inin locale'i ne
     * olursa olsun davranisi sabit tutar.
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
