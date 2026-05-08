package com.ticketsystem.generator.config;

/**
 * Tüm ayarlar buradan yönetilir.
 * Çalıştırmadan önce Keycloak kullanıcı bilgilerini ve
 * üretilecek veri miktarlarını buradan düzenleyin.
 */
public class GeneratorConfig {

    // ---------------------------------------------------------------
    // Sunucu adresi
    // ---------------------------------------------------------------
    public static final String BASE_URL = "http://localhost";

    // ---------------------------------------------------------------
    // Keycloak bağlantı bilgileri
    // ---------------------------------------------------------------
    public static final String KEYCLOAK_URL      = BASE_URL + "/auth";
    public static final String KEYCLOAK_REALM    = "TicketSystemRealm";
    public static final String KEYCLOAK_CLIENT   = "ticket-frontend";

    // ---------------------------------------------------------------
    // Kullanıcı kimlik bilgileri
    // Her rol için en az bir kullanıcı tanımlayın.
    // ---------------------------------------------------------------

    /** CUSTOMER rolündeki kullanıcılar — bilet oluşturur, CSAT gönderir */
    public static final String[][] CUSTOMERS = {
        {"ctest", "321654"},
        {"ctest2", "321654"},
        {"ctest3", "321654"},
    };

    /** AGENT rolündeki kullanıcılar — bilet claim alır, çözer */
    public static final String[][] AGENTS = {
        {"atest", "321654"},
        {"atest2", "321654"},
    };

    /** AGENT_ADMIN rolündeki kullanıcı — atama yapar, yönetir */
    public static final String[][] AGENT_ADMINS = {
        {"aatest", "321654"},
    };

    // ---------------------------------------------------------------
    // Üretilecek veri miktarları
    // ---------------------------------------------------------------

    /** Toplam kaç bilet oluşturulsun */
    public static final int TICKET_COUNT = 150;

    /**
     * Biletlerin durum dağılımı (yüzde olarak, toplamı 100 olmalı).
     * NEW: havuzda bekleyen
     * IN_PROGRESS: agent üzerinde
     * RESOLVED: çözüldü, CSAT bekliyor
     * CLOSED: tamamen kapandı
     */
    public static final int PCT_NEW        = 20;
    public static final int PCT_IN_PROGRESS = 30;
    public static final int PCT_RESOLVED   = 20;
    public static final int PCT_CLOSED     = 30;

    /** İstekler arasındaki bekleme süresi (ms) — rate limit aşımını önler */
    public static final long DELAY_MS = 800;

    /**
     * Yorum istekleri arasındaki bekleme süresi (ms).
     * Backend'de yorum endpoint'i için 5 saniyelik cooldown var.
     */
    public static final long COMMENT_DELAY_MS = 5500;

    /** 429 alındığında kaç ms beklensin */
    public static final long RATE_LIMIT_BACKOFF_MS = 6000;

    /** 429 alındığında kaç kez tekrar denensin */
    public static final int RATE_LIMIT_RETRY_COUNT = 3;

    /** Token yenileme eşiği (saniye) — bu süre kalmadan token yenilenir */
    public static final int TOKEN_REFRESH_THRESHOLD_SEC = 30;

    // ---------------------------------------------------------------
    // PostgreSQL bağlantısı — tarih geriye çekme için
    // ---------------------------------------------------------------
    public static final String DB_URL      = "jdbc:postgresql://localhost:5432/ticketdb";
    public static final String DB_USER     = "ticketadmin";
    public static final String DB_PASSWORD = "321654";

    /**
     * Biletlerin tarihleri kaç gün geriye yayılsın.
     * Örn: 90 → son 90 gün içinde rastgele dağıtılır.
     */
    public static final int DATE_SPREAD_DAYS = 3;
}
