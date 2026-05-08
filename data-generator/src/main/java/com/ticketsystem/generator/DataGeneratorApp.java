package com.ticketsystem.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.client.ApiClient;
import com.ticketsystem.generator.client.KeycloakTokenClient;
import com.ticketsystem.generator.config.GeneratorConfig;
import com.ticketsystem.generator.generator.DateBackfiller;
import com.ticketsystem.generator.generator.SetupGenerator;
import com.ticketsystem.generator.generator.TicketGenerator;
import com.ticketsystem.generator.model.UserSession;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Giriş noktası.
 *
 * Çalıştırma:
 *   mvn package -q
 *   java -jar target/data-generator-1.0.0.jar
 *
 * Ayarlar için: src/main/java/com/ticketsystem/generator/config/GeneratorConfig.java
 */
public class DataGeneratorApp {

    private static final Logger log = LoggerFactory.getLogger(DataGeneratorApp.class);

    public static void main(String[] args) throws Exception {
        long startTime = System.currentTimeMillis();

        log.info("╔══════════════════════════════════════╗");
        log.info("║   Ticket System — Data Generator     ║");
        log.info("╚══════════════════════════════════════╝");
        log.info("Hedef: {}", GeneratorConfig.BASE_URL);

        OkHttpClient http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        ApiClient api = new ApiClient(http, mapper);

        // ---------------------------------------------------------------
        // 1. Kullanıcı oturumlarını başlat
        // ---------------------------------------------------------------
        log.info("Kullanıcı oturumları başlatılıyor...");

        List<UserSession> customers   = new ArrayList<>();
        List<UserSession> agents      = new ArrayList<>();
        List<UserSession> agentAdmins = new ArrayList<>();

        for (String[] creds : GeneratorConfig.CUSTOMERS) {
            UserSession session = createSession(http, mapper, creds[0], creds[1], "CUSTOMER");
            if (session != null) {
                syncUser(api, session);
                customers.add(session);
            }
        }

        for (String[] creds : GeneratorConfig.AGENTS) {
            UserSession session = createSession(http, mapper, creds[0], creds[1], "AGENT");
            if (session != null) {
                syncUser(api, session);
                agents.add(session);
            }
        }

        for (String[] creds : GeneratorConfig.AGENT_ADMINS) {
            UserSession session = createSession(http, mapper, creds[0], creds[1], "AGENT_ADMIN");
            if (session != null) {
                syncUser(api, session);
                agentAdmins.add(session);
            }
        }

        if (customers.isEmpty() || agents.isEmpty()) {
            log.error("En az bir CUSTOMER ve bir AGENT oturumu gerekli. Çıkılıyor.");
            System.exit(1);
        }

        log.info("Oturumlar: {} customer, {} agent, {} agent_admin",
                customers.size(), agents.size(), agentAdmins.size());

        // ---------------------------------------------------------------
        // 2. Setup: ürünleri oluştur, yetkileri ata
        // ---------------------------------------------------------------
        UserSession adminSession = agentAdmins.isEmpty() ? null : agentAdmins.get(0);
        if (adminSession == null) {
            log.error("En az bir AGENT_ADMIN gerekli. Çıkılıyor.");
            System.exit(1);
        }

        SetupGenerator setup = new SetupGenerator(api, adminSession, agents, customers);
        List<Long> productIds = setup.setup();

        if (productIds.isEmpty()) {
            log.error("Hiç ürün bulunamadı veya oluşturulamadı. Çıkılıyor.");
            System.exit(1);
        }

        // ---------------------------------------------------------------
        // 3. Biletleri üret
        // ---------------------------------------------------------------
        TicketGenerator generator = new TicketGenerator(api, mapper, customers, agents, productIds);
        List<Long> ticketIds = generator.generate();

        // ---------------------------------------------------------------
        // 4. Tarihleri geriye çek (doğrudan DB üzerinden)
        // ---------------------------------------------------------------
        new DateBackfiller().backfill(ticketIds);

        long elapsed = System.currentTimeMillis() - startTime;
        long minutes = elapsed / 60_000;
        long seconds = (elapsed % 60_000) / 1000;

        log.info("╔══════════════════════════════════════╗");
        log.info("║   ✓ Tüm işlemler tamamlandı          ║");
        log.info("║   Süre: {} dakika {} saniye           ║", minutes, seconds);
        log.info("║   Üretilen bilet: {}                  ║", ticketIds.size());
        log.info("╚══════════════════════════════════════╝");
    }

    // ---------------------------------------------------------------
    // Yardımcı metodlar
    // ---------------------------------------------------------------

    private static UserSession createSession(OkHttpClient http, ObjectMapper mapper,
                                              String username, String password, String role) {
        try {
            KeycloakTokenClient tokenClient = new KeycloakTokenClient(http, mapper);
            tokenClient.login(username, password);
            return new UserSession(username, role, tokenClient);
        } catch (Exception e) {
            log.warn("Oturum açılamadı ({} / {}): {}", username, role, e.getMessage());
            return null;
        }
    }

    private static void syncUser(ApiClient api, UserSession session) {
        try {
            JsonNode resp = api.post("/users/sync", null, session.getToken());
            if (resp.has("id")) {
                session.setUserId(resp.get("id").asText());
                log.debug("Kullanıcı senkronize edildi: {} → {}", session.getUsername(), session.getUserId());
            }
        } catch (Exception e) {
            log.warn("Kullanıcı senkronize edilemedi ({}): {}", session.getUsername(), e.getMessage());
        }
    }

}
