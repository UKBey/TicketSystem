package com.ticketsystem.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.client.ApiClient;
import com.ticketsystem.generator.client.KeycloakTokenClient;
import com.ticketsystem.generator.config.GeneratorConfig;
import com.ticketsystem.generator.generator.DateBackfiller;
import com.ticketsystem.generator.generator.SetupGenerator;
import com.ticketsystem.generator.generator.TicketGenerator;
import com.ticketsystem.generator.model.SetupResult;
import com.ticketsystem.generator.model.UserSession;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Entry point.
 *
 * <p>The following steps are executed with a single admin account:
 * <ol>
 *   <li>Agent and customer users from users.json are created via the Agent Admin API (temporary
 *       password → forced first-login change, then final password set); existing ones are reused.</li>
 *   <li>A 5 product x 5 topic x 2+ known-issue ecosystem is set up idempotently, along with
 *       shared canned responses (10 global + 5 per product).</li>
 *   <li>50 tickets are generated declaratively from tickets/*.json files; for each template
 *       the full lifecycle is played out based on its status (claim/comment/worklog/resolve/csat).</li>
 *   <li>Ticket dates and SLA fields are written directly to the DB so they spread across the last N days.</li>
 * </ol>
 *
 * Run:
 *   mvn package -q
 *   java -jar target/data-generator-1.0.0.jar
 */
public class DataGeneratorApp {

    private static final Logger log = LoggerFactory.getLogger(DataGeneratorApp.class);

    /**
     * Runs the generator flow end to end: admin login, setup,
     * ticket generation, and then date/SLA backfill against the DB.
     *
     * @param args command-line arguments (unused)
     * @throws Exception any error originating from infrastructure calls
     */
    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();

        log.info("╔══════════════════════════════════════╗");
        log.info("║   Ticket System — Data Generator     ║");
        log.info("╚══════════════════════════════════════╝");
        log.info("Target: {}", GeneratorConfig.BASE_URL);

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        ApiClient api = new ApiClient(http, mapper);

        // ---------------------------------------------------------------
        // 1. Sadece admin oturumu — diğer her şeyi setup üstlenir
        // ---------------------------------------------------------------
        UserSession adminAgent = loginAdmin(http, mapper);
        if (adminAgent == null) {
            log.error("Admin login failed. Check credentials and Keycloak configuration.");
            System.exit(1);
        }
        syncUser(api, adminAgent);
        log.info("Admin logged in: {}", adminAgent.getUsername());

        // ---------------------------------------------------------------
        // 2. Setup — kullanıcılar, ürünler, topic'ler, sıkça karşılaşılan sorunlar, hazır yanıtlar
        // ---------------------------------------------------------------
        SetupGenerator setup = new SetupGenerator(api, mapper, http, adminAgent);
        SetupResult result = setup.setup();

        // ---------------------------------------------------------------
        // 3. JSON şablonlarından bilet üretimi
        // ---------------------------------------------------------------
        TicketGenerator generator = new TicketGenerator(api, mapper, result);
        List<Long> ticketIds = generator.generate();

        // ---------------------------------------------------------------
        // 4. Tarihleri geriye çek — generator'un çalıştığı saate göre
        // ---------------------------------------------------------------
        new DateBackfiller().backfill(ticketIds);

        long elapsed = System.currentTimeMillis() - startTime;
        long minutes = elapsed / 60_000;
        long seconds = (elapsed % 60_000) / 1000;

        log.info("╔══════════════════════════════════════╗");
        log.info("║   ✓ All done                          ║");
        log.info("║   Elapsed: {} min {} sec              ║", minutes, seconds);
        log.info("║   Tickets created: {}                 ║", ticketIds.size());
        log.info("╚══════════════════════════════════════╝");
    }

    private static UserSession loginAdmin(OkHttpClient http, ObjectMapper mapper) {
        try {
            KeycloakTokenClient tokenClient = new KeycloakTokenClient(http, mapper);
            tokenClient.login(GeneratorConfig.ADMIN_AGENT_USERNAME, GeneratorConfig.ADMIN_AGENT_PASSWORD);
            return new UserSession(GeneratorConfig.ADMIN_AGENT_USERNAME, "ADMIN", tokenClient);
        } catch (Exception e) {
            log.error("Admin login error: {}", e.getMessage());
            return null;
        }
    }

    private static void syncUser(ApiClient api, UserSession session) {
        try {
            JsonNode resp = api.post("/users/sync", null, session.getToken());
            if (resp.has("id")) session.setUserId(resp.get("id").asText());
        } catch (Exception e) {
            log.warn("Sync failed ({}): {}", session.getUsername(), e.getMessage());
        }
    }
}
