package com.ticketsystem.generator.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.model.SeedUser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    // Host'tan `java -jar` ile çalışınca localhost (nginx :80). Container içinde
    // GEN_BASE_URL=http://nginx-proxy ile compose servis adına yönlendirilir.
    public static final String BASE_URL = envOrDefault("GEN_BASE_URL", "http://localhost");

    // ---------------------------------------------------------------
    // Keycloak
    // ---------------------------------------------------------------
    public static final String KEYCLOAK_URL    = BASE_URL + "/auth";
    public static final String KEYCLOAK_REALM  = "TicketSystemRealm";
    public static final String KEYCLOAK_CLIENT = "ticket-frontend";

    // ---------------------------------------------------------------
    // Bootstrap admin (single account) — creates the remaining users,
    // adds products/topics/issues and authorizes them. Defaults to the
    // `superadmin` seed user (ADMIN + LEAD_AGENT + MANAGER); replaces the
    // deprecated `aatest`/admin account.
    // ---------------------------------------------------------------
    public static final String ADMIN_AGENT_USERNAME = USERS.username("adminAgent", "superadmin");
    public static final String ADMIN_AGENT_PASSWORD = USERS.password("adminAgent", "321654");

    // ---------------------------------------------------------------
    // Keycloak master realm admin — used only to clear required-actions
    // on freshly created users (touches nothing outside data-generator).
    // ---------------------------------------------------------------
    public static final String MASTER_ADMIN_USERNAME = USERS.username("keycloakAdmin", "admin");
    public static final String MASTER_ADMIN_PASSWORD = USERS.password("keycloakAdmin", "321654");
    public static final String MASTER_ADMIN_CLIENT   = "admin-cli";

    // ---------------------------------------------------------------
    // Seed user provisioning
    // ---------------------------------------------------------------
    /**
     * Temporary password assigned at creation time (Agent Admin API). Keycloak forces the
     * user to change it on first login; the generator then sets each user's final password
     * (from users.json) and clears the required action. Must satisfy the realm password policy.
     */
    public static final String TEMP_PASSWORD = "Temp321654!";

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
    // DB_URL container içinde GEN_DB_URL ile it-service-db servisine yönlendirilir.
    // DB_USER/DB_PASSWORD önceliği: env (GEN_DB_*) > users.json > hardcoded fallback.
    public static final String DB_URL      = envOrDefault("GEN_DB_URL", "jdbc:postgresql://localhost:5432/ticketdb");
    public static final String DB_USER     = envOrDefault("GEN_DB_USER", USERS.username("database", "ticketadmin"));
    public static final String DB_PASSWORD = envOrDefault("GEN_DB_PASSWORD", USERS.password("database", "321654"));

    /** How many days back ticket creation dates should be spread across. */
    public static final int DATE_SPREAD_DAYS = 7;

    /**
     * Seed agent definitions from {@code users.json} (empty if none configured).
     *
     * @return the list of agents the generator should create and sign in
     */
    public static List<SeedUser> agents() {
        return USERS.agents;
    }

    /**
     * Seed customer definitions from {@code users.json} (empty if none configured).
     *
     * @return the list of customers the generator should create and sign in
     */
    public static List<SeedUser> customers() {
        return USERS.customers;
    }

    /**
     * Seed lead-agent definitions from {@code users.json} (empty if none configured).
     * Leads are created with the LEAD_AGENT realm role (a composite that includes AGENT),
     * so they also operate as agents.
     *
     * @return the list of lead agents the generator should create and sign in
     */
    public static List<SeedUser> leads() {
        return USERS.leads;
    }

    private GeneratorConfig() {
        // Utility class — no instances.
    }

    /**
     * Returns the value of environment variable {@code key} when it is set and non-blank,
     * otherwise {@code def}.
     *
     * <p>Lets a containerized run override the host-oriented defaults — e.g.
     * {@code GEN_BASE_URL=http://nginx-proxy} and
     * {@code GEN_DB_URL=jdbc:postgresql://it-service-db:5432/ticketdb} — while a plain
     * {@code java -jar} on the host keeps using {@code localhost}.
     *
     * @param key environment variable name
     * @param def fallback used when the variable is unset or blank
     * @return the environment value or {@code def}
     */
    private static String envOrDefault(String key, String def) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : def;
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
        private final List<SeedUser> agents;
        private final List<SeedUser> leads;
        private final List<SeedUser> customers;

        private UsersFile(Map<String, Map<String, String>> namedAccounts,
                          List<SeedUser> agents, List<SeedUser> leads, List<SeedUser> customers) {
            this.namedAccounts = namedAccounts;
            this.agents        = agents;
            this.leads         = leads;
            this.customers     = customers;
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
            return new UsersFile(Collections.emptyMap(), List.of(), List.of(), List.of());
        }

        private static UsersFile parse(JsonNode root) {
            Map<String, Map<String, String>> named = new HashMap<>();

            // Named single accounts: adminAgent / keycloakAdmin / database (object with username + password).
            for (String key : new String[] {"adminAgent", "keycloakAdmin", "database"}) {
                JsonNode node = root.path(key);
                if (!node.isObject()) continue;
                Map<String, String> entry = new HashMap<>();
                entry.put("username", node.path("username").asText(null));
                entry.put("password", node.path("password").asText(null));
                named.put(key, entry);
            }

            // Seed users: arrays of full user objects (username/email/firstName/lastName/password).
            return new UsersFile(named, parseSeedUsers(root.path("agents")),
                    parseSeedUsers(root.path("leads")), parseSeedUsers(root.path("customers")));
        }

        private static List<SeedUser> parseSeedUsers(JsonNode node) {
            List<SeedUser> list = new ArrayList<>();
            if (node == null || !node.isArray()) return list;
            for (JsonNode u : node) {
                String username = u.path("username").asText(null);
                String password = u.path("password").asText(null);
                if (username == null || username.isBlank() || password == null || password.isBlank()) continue;
                list.add(new SeedUser(
                        username,
                        u.path("email").asText(null),
                        u.path("firstName").asText(""),
                        u.path("lastName").asText(""),
                        password));
            }
            return list;
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
    }
}
