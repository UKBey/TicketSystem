package com.ticketsystem.generator.config;

/**
 * Generator çalışma ayarları.
 *
 * <p>Kullanıcı listesi ve veri içeriği artık {@code src/main/resources/setup.json}
 * ve {@code src/main/resources/tickets/*.json} dosyalarından okunur.
 * Buradan yalnızca tek bir <em>agent_admin</em> hesabı ve operasyonel knob'lar yönetilir.
 */
public class GeneratorConfig {

    // ---------------------------------------------------------------
    // Sunucu adresi
    // ---------------------------------------------------------------
    public static final String BASE_URL = "http://localhost";

    // ---------------------------------------------------------------
    // Keycloak
    // ---------------------------------------------------------------
    public static final String KEYCLOAK_URL    = BASE_URL + "/auth";
    public static final String KEYCLOAK_REALM  = "TicketSystemRealm";
    public static final String KEYCLOAK_CLIENT = "ticket-frontend";

    // ---------------------------------------------------------------
    // Yönetici (tek hesap) — bu kullanıcı diğer kullanıcıları oluşturur,
    // ürün/topic/sorun bilgilerini ekler ve yetkilendirme yapar.
    // ---------------------------------------------------------------
    public static final String ADMIN_AGENT_USERNAME = "aatest";
    public static final String ADMIN_AGENT_PASSWORD = "321654";

    // ---------------------------------------------------------------
    // İstek temposu
    // ---------------------------------------------------------------
    /** İki API isteği arasındaki bekleme (ms). */
    public static final long DELAY_MS = 600;

    /** Yorum turları arası bekleme (ms) — backend yorum cooldown'ı 5 sn. */
    public static final long COMMENT_DELAY_MS = 5500;

    /** 429 alındığında bekleme süresi (ms). */
    public static final long RATE_LIMIT_BACKOFF_MS = 6000;

    /** 429 alındığında deneme sayısı. */
    public static final int RATE_LIMIT_RETRY_COUNT = 3;

    /** Token yenileme eşiği (saniye). */
    public static final int TOKEN_REFRESH_THRESHOLD_SEC = 30;

    // ---------------------------------------------------------------
    // PostgreSQL (tarih backfill için doğrudan DB bağlantısı)
    // ---------------------------------------------------------------
    public static final String DB_URL      = "jdbc:postgresql://localhost:5432/ticketdb";
    public static final String DB_USER     = "ticketadmin";
    public static final String DB_PASSWORD = "321654";

    /** Biletlerin oluşturulma tarihleri kaç gün geriye yayılsın. */
    public static final int DATE_SPREAD_DAYS = 7;
}
