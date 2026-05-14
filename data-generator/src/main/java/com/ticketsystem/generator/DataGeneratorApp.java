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
 * Giriş noktası.
 *
 * <p>Tek bir agent_admin hesabı ile aşağıdaki adımlar uygulanır:
 * <ol>
 *   <li>setup.json'daki agent + customer kullanıcıları yoksa oluşturulur (varsa atlanır).</li>
 *   <li>5 ürün × 5 topic × 2+ sıkça karşılaşılan sorun ekosistemi idempotent şekilde kurulur.</li>
 *   <li>tickets/*.json dosyalarından 50 bilet deklaratif olarak üretilir; her şablonun
 *       statüsüne göre tam yaşam döngüsü oynatılır (claim/comment/worklog/resolve/csat).</li>
 *   <li>Bilet tarihleri ve SLA alanları doğrudan DB'ye yazılarak son N gün içine dağıtılır.</li>
 * </ol>
 *
 * Çalıştırma:
 *   mvn package -q
 *   java -jar target/data-generator-1.0.0.jar
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
        // 1. Sadece agent_admin oturumu — diğer her şeyi setup üstlenir
        // ---------------------------------------------------------------
        UserSession adminAgent = loginAdmin(http, mapper);
        if (adminAgent == null) {
            log.error("agent_admin oturumu açılamadı. Kullanıcı/şifre ve Keycloak ayarlarını kontrol et.");
            System.exit(1);
        }
        syncUser(api, adminAgent);
        log.info("agent_admin oturum açıldı: {}", adminAgent.getUsername());

        // ---------------------------------------------------------------
        // 2. Setup — kullanıcılar, ürünler, topic'ler, sıkça karşılaşılan sorunlar
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
        log.info("║   ✓ Tüm işlemler tamamlandı          ║");
        log.info("║   Süre: {} dakika {} saniye           ║", minutes, seconds);
        log.info("║   Üretilen bilet: {}                  ║", ticketIds.size());
        log.info("╚══════════════════════════════════════╝");
    }

    private static UserSession loginAdmin(OkHttpClient http, ObjectMapper mapper) {
        try {
            KeycloakTokenClient tokenClient = new KeycloakTokenClient(http, mapper);
            tokenClient.login(GeneratorConfig.ADMIN_AGENT_USERNAME, GeneratorConfig.ADMIN_AGENT_PASSWORD);
            return new UserSession(GeneratorConfig.ADMIN_AGENT_USERNAME, "AGENT_ADMIN", tokenClient);
        } catch (Exception e) {
            log.error("agent_admin login hatası: {}", e.getMessage());
            return null;
        }
    }

    private static void syncUser(ApiClient api, UserSession session) {
        try {
            JsonNode resp = api.post("/users/sync", null, session.getToken());
            if (resp.has("id")) session.setUserId(resp.get("id").asText());
        } catch (Exception e) {
            log.warn("Sync edilemedi ({}): {}", session.getUsername(), e.getMessage());
        }
    }
}
