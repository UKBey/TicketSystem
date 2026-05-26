package com.ticketsystem.generator.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Generator runtime settings.
 *
 * <p>Values are resolved in priority order:
 * <ol>
 *   <li>OS environment variable (e.g. {@code ADMIN_AGENT_PASSWORD})</li>
 *   <li>Property from {@code data-generator/.env} (or {@code ./.env} when run from {@code data-generator/})</li>
 *   <li>Hardcoded fallback (the value shipped in this file)</li>
 * </ol>
 *
 * <p>The user list and data content are read from {@code src/main/resources/setup.json}
 * and {@code src/main/resources/tickets/*.json}. Only a single <em>agent_admin</em>
 * account and operational knobs are managed here.
 */
public class GeneratorConfig {

    private static final Properties DOT_ENV = loadDotEnv();

    // ---------------------------------------------------------------
    // Server address
    // ---------------------------------------------------------------
    public static final String BASE_URL = env("BASE_URL", "http://localhost");

    // ---------------------------------------------------------------
    // Keycloak
    // ---------------------------------------------------------------
    public static final String KEYCLOAK_URL    = env("KEYCLOAK_URL",    BASE_URL + "/auth");
    public static final String KEYCLOAK_REALM  = env("KEYCLOAK_REALM",  "TicketSystemRealm");
    public static final String KEYCLOAK_CLIENT = env("KEYCLOAK_CLIENT", "ticket-frontend");

    // ---------------------------------------------------------------
    // Agent admin (single account) — creates the remaining users,
    // adds products/topics/issues and authorizes them.
    // ---------------------------------------------------------------
    public static final String ADMIN_AGENT_USERNAME = env("ADMIN_AGENT_USERNAME", "aatest");
    public static final String ADMIN_AGENT_PASSWORD = env("ADMIN_AGENT_PASSWORD", "321654");

    // ---------------------------------------------------------------
    // Keycloak master realm admin — used only to clear required-actions
    // on freshly created users (touches nothing outside data-generator).
    // ---------------------------------------------------------------
    public static final String MASTER_ADMIN_USERNAME = env("MASTER_ADMIN_USERNAME", "admin");
    public static final String MASTER_ADMIN_PASSWORD = env("MASTER_ADMIN_PASSWORD", "321654");
    public static final String MASTER_ADMIN_CLIENT   = env("MASTER_ADMIN_CLIENT",   "admin-cli");

    // ---------------------------------------------------------------
    // Request cadence
    // ---------------------------------------------------------------
    /** Delay between two API requests (ms). */
    public static final long DELAY_MS = envLong("DELAY_MS", 600L);

    /** Delay between comment rounds (ms) — backend comment cooldown is 5 sec. */
    public static final long COMMENT_DELAY_MS = envLong("COMMENT_DELAY_MS", 5500L);

    /** Wait time after receiving a 429 (ms). */
    public static final long RATE_LIMIT_BACKOFF_MS = envLong("RATE_LIMIT_BACKOFF_MS", 6000L);

    /** Number of retries after receiving a 429. */
    public static final int RATE_LIMIT_RETRY_COUNT = envInt("RATE_LIMIT_RETRY_COUNT", 3);

    /** Token refresh threshold (seconds). */
    public static final int TOKEN_REFRESH_THRESHOLD_SEC = envInt("TOKEN_REFRESH_THRESHOLD_SEC", 30);

    // ---------------------------------------------------------------
    // PostgreSQL (direct DB connection for date backfill)
    // ---------------------------------------------------------------
    public static final String DB_URL      = env("DB_URL",      "jdbc:postgresql://localhost:5432/ticketdb");
    public static final String DB_USER     = env("DB_USER",     "ticketadmin");
    public static final String DB_PASSWORD = env("DB_PASSWORD", "321654");

    /** How many days back ticket creation dates should be spread across. */
    public static final int DATE_SPREAD_DAYS = envInt("DATE_SPREAD_DAYS", 7);

    // ---------------------------------------------------------------
    // .env loader
    // ---------------------------------------------------------------

    private static Properties loadDotEnv() {
        Properties props = new Properties();
        // Try both common working-directory layouts so the file is found whether
        // the JAR runs from the repo root (via `make gen`) or from data-generator/.
        Path[] candidates = {
                Paths.get(".env"),
                Paths.get("data-generator", ".env")
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                try (var reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    props.load(reader);
                    return props;
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read " + p, e);
                }
            }
        }
        return props;
    }

    private static String env(String key, String defaultValue) {
        // OS env vars win so they can be supplied without editing the .env file.
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) return value;
        value = DOT_ENV.getProperty(key);
        if (value != null && !value.isEmpty()) return value;
        return defaultValue;
    }

    private static long envLong(String key, long defaultValue) {
        String raw = env(key, null);
        if (raw == null) return defaultValue;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static int envInt(String key, int defaultValue) {
        String raw = env(key, null);
        if (raw == null) return defaultValue;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private GeneratorConfig() {
        // Utility class — no instances.
    }
}
