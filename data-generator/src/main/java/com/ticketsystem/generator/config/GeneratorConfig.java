package com.ticketsystem.generator.config;

/**
 * Generator runtime settings.
 *
 * <p>The user list and data content are now read from {@code src/main/resources/setup.json}
 * and {@code src/main/resources/tickets/*.json}.
 * Only a single <em>agent_admin</em> account and operational knobs are managed here.
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
    // Keycloak master realm admin — yalnızca kullanıcıların required-actions
    // alanını temizlemek için kullanılır (data-generator/ dışı bir şeye dokunmaz).
    // ---------------------------------------------------------------
    public static final String MASTER_ADMIN_USERNAME = "admin";
    public static final String MASTER_ADMIN_PASSWORD = "321654";
    public static final String MASTER_ADMIN_CLIENT   = "admin-cli";

    // ---------------------------------------------------------------
    // İstek temposu
    // ---------------------------------------------------------------
    /** Delay between two API requests (ms). */
    public static final long DELAY_MS = 600;

    /** Delay between comment rounds (ms) — backend comment cooldown is 5 sec. */
    public static final long COMMENT_DELAY_MS = 5500;

    /** Wait time after receiving a 429 (ms). */
    public static final long RATE_LIMIT_BACKOFF_MS = 6000;

    /** Number of retries after receiving a 429. */
    public static final int RATE_LIMIT_RETRY_COUNT = 3;

    /** Token refresh threshold (seconds). */
    public static final int TOKEN_REFRESH_THRESHOLD_SEC = 30;

    // ---------------------------------------------------------------
    // PostgreSQL (tarih backfill için doğrudan DB bağlantısı)
    // ---------------------------------------------------------------
    public static final String DB_URL      = "jdbc:postgresql://localhost:5432/ticketdb";
    public static final String DB_USER     = "ticketadmin";
    public static final String DB_PASSWORD = "321654";

    /** How many days back ticket creation dates should be spread across. */
    public static final int DATE_SPREAD_DAYS = 7;
}
