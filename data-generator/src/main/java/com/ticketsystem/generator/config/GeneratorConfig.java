package com.ticketsystem.generator.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Generator runtime settings.
 *
 * <p>Credentials (admin, master, DB and the seed agents/customers) are loaded once at
 * class init from {@code data-generator/users.json} when that file is present. Anything
 * not overridden there falls back to the hardcoded defaults below. The user list and
 * structural data still live in {@code src/main/resources/setup.json}.
 */
public class GeneratorConfig {

    /** Single source of truth for all login credentials (admin + master + DB + agents/customers). */
    public static final Path USERS_FILE_NAME = Paths.get("users.json");

    private static final UsersFile USERS = UsersFile.load();

    // ---------------------------------------------------------------
    // Server address
    // ---------------------------------------------------------------
    public static final String BASE_URL = "http://localhost";

    // ---------------------------------------------------------------
    // Keycloak
    // ---------------------------------------------------------------
    public static final String KEYCLOAK_URL    = BASE_URL + "/auth";
    public static final String KEYCLOAK_REALM  = "TicketSystemRealm";
    public static final String KEYCLOAK_CLIENT = "ticket-frontend";

    // ---------------------------------------------------------------
    // Agent admin (single account) — creates the remaining users,
    // adds products/topics/issues and authorizes them.
    // ---------------------------------------------------------------
    public static final String ADMIN_AGENT_USERNAME = USERS.username("adminAgent", "aatest");
    public static final String ADMIN_AGENT_PASSWORD = USERS.password("adminAgent", "321654");

    // ---------------------------------------------------------------
    // Keycloak master realm admin — used only to clear required-actions
    // on freshly created users (touches nothing outside data-generator).
    // ---------------------------------------------------------------
    public static final String MASTER_ADMIN_USERNAME = USERS.username("keycloakAdmin", "admin");
    public static final String MASTER_ADMIN_PASSWORD = USERS.password("keycloakAdmin", "321654");
    public static final String MASTER_ADMIN_CLIENT   = "admin-cli";

    // ---------------------------------------------------------------
    // Request cadence
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
    // PostgreSQL (direct DB connection for date backfill)
    // ---------------------------------------------------------------
    public static final String DB_URL      = "jdbc:postgresql://localhost:5432/ticketdb";
    public static final String DB_USER     = USERS.username("database", "ticketadmin");
    public static final String DB_PASSWORD = USERS.password("database", "321654");

    /** How many days back ticket creation dates should be spread across. */
    public static final int DATE_SPREAD_DAYS = 7;

    /**
     * Looks up the password for a seed user (agent or customer) listed in {@code users.json}.
     * Returns {@code null} when the user is not mapped — callers should fall back to the
     * {@code password} field in {@code setup.json}.
     *
     * @param username the username to resolve
     * @return the password from {@code users.json}, or {@code null} if not configured
     */
    public static String passwordForUser(String username) {
        return USERS.passwordForUser(username);
    }

    private GeneratorConfig() {
        // Utility class — no instances.
    }

    // -----------------------------------------------------------------
    // users.json loader
    // -----------------------------------------------------------------

    /**
     * Parsed contents of {@code users.json}. Missing file or malformed JSON degrades to an empty
     * mapping so the generator can still run with the hardcoded defaults.
     */
    private static final class UsersFile {
        private final Map<String, Map<String, String>> namedAccounts;
        private final Map<String, String> seedUsers;

        private UsersFile(Map<String, Map<String, String>> namedAccounts,
                          Map<String, String> seedUsers) {
            this.namedAccounts = namedAccounts;
            this.seedUsers     = seedUsers;
        }

        static UsersFile load() {
            // Try the file next to the working directory first (so `cd data-generator` works),
            // then under data-generator/ for invocations from the repo root (e.g. `make gen`).
            Path[] candidates = {
                    USERS_FILE_NAME,
                    Paths.get("data-generator").resolve(USERS_FILE_NAME)
            };
            for (Path p : candidates) {
                if (!Files.isRegularFile(p)) continue;
                try {
                    JsonNode root = new ObjectMapper().readTree(p.toFile());
                    return parse(root);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read " + p, e);
                }
            }
            return new UsersFile(Collections.emptyMap(), Collections.emptyMap());
        }

        private static UsersFile parse(JsonNode root) {
            Map<String, Map<String, String>> named = new HashMap<>();
            Map<String, String> seed = new HashMap<>();

            // Named single accounts: adminAgent / keycloakAdmin / database (object with username + password).
            for (String key : new String[] {"adminAgent", "keycloakAdmin", "database"}) {
                JsonNode node = root.path(key);
                if (!node.isObject()) continue;
                Map<String, String> entry = new HashMap<>();
                entry.put("username", node.path("username").asText(null));
                entry.put("password", node.path("password").asText(null));
                named.put(key, entry);
            }

            // Per-username password maps for seed users (agents / customers).
            for (String key : new String[] {"agents", "customers"}) {
                JsonNode node = root.path(key);
                if (!node.isObject()) continue;
                Iterator<Map.Entry<String, JsonNode>> it = node.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String username = e.getKey();
                    String password = e.getValue().asText(null);
                    if (username != null && password != null) seed.put(username, password);
                }
            }
            return new UsersFile(named, seed);
        }

        String username(String accountKey, String fallback) {
            Map<String, String> entry = namedAccounts.get(accountKey);
            String value = entry == null ? null : entry.get("username");
            return (value != null && !value.isEmpty()) ? value : fallback;
        }

        String password(String accountKey, String fallback) {
            Map<String, String> entry = namedAccounts.get(accountKey);
            String value = entry == null ? null : entry.get("password");
            return (value != null && !value.isEmpty()) ? value : fallback;
        }

        String passwordForUser(String username) {
            return seedUsers.get(username);
        }
    }
}
