package com.ticketsystem.generator.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.client.ApiClient;
import com.ticketsystem.generator.client.KeycloakAdminApi;
import com.ticketsystem.generator.client.KeycloakTokenClient;
import com.ticketsystem.generator.config.GeneratorConfig;
import com.ticketsystem.generator.model.SetupResult;
import com.ticketsystem.generator.model.UserSession;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sistemi setup.json'daki şablona göre hazırlar.
 *
 * <ul>
 *   <li>setup.json'daki agent ve customer kullanıcılarına yalnızca <em>login dener</em>;
 *       kullanıcı oluşturma adımı yoktur. Hesapların Keycloak'ta önceden hazır olması
 *       beklenir. Login başarısız olan kullanıcılar uyarıyla atlanır.</li>
 *   <li>Ürünleri / topic'leri / sıkça karşılaşılan sorunları idempotent şekilde oluşturur.</li>
 *   <li>Yetkilerini (authorizedProducts) login olabilen tüm agent ve customer'lara dağıtır.</li>
 * </ul>
 *
 * Re-run güvenli: ürün/topic/issue varsa atlanır.
 */
public class SetupGenerator {

    private static final Logger log = LoggerFactory.getLogger(SetupGenerator.class);

    private final ApiClient api;
    private final ObjectMapper mapper;
    private final OkHttpClient http;
    private final UserSession adminAgent;
    private final KeycloakAdminApi keycloakAdmin;

    /**
     * @param api        backend API çağrıları için istemci
     * @param mapper     setup.json'ı okumak için Jackson mapper
     * @param http       per-user {@link KeycloakTokenClient}'lar için paylaşılan OkHttp
     * @param adminAgent ürün/topic/yetki işlemlerini yürüten agent_admin oturumu
     */
    public SetupGenerator(ApiClient api, ObjectMapper mapper, OkHttpClient http, UserSession adminAgent) {
        this.api           = api;
        this.mapper        = mapper;
        this.http          = http;
        this.adminAgent    = adminAgent;
        this.keycloakAdmin = new KeycloakAdminApi(http, mapper);
    }

    /**
     * setup.json şablonuna göre sistemi hazırlar: kullanıcı oturumları, önceki
     * generator ürünlerinin temizliği, ürün/topic/known-issue oluşturma ve
     * yetki dağıtımı.
     *
     * @return TicketGenerator'a aktarılacak {@link SetupResult}
     * @throws IOException          API veya setup.json okuma hatası
     * @throws InterruptedException request temposu için yapılan {@code Thread.sleep} kesilirse
     * @throws IllegalStateException hiçbir agent ya da customer login olamadıysa
     */
    public SetupResult setup() throws IOException, InterruptedException {
        log.info("=== Sistem kurulumu başlıyor ===");

        JsonNode spec = loadSetupSpec();

        // 1. Kullanıcı oturumları — login dene, başarısız olanları atla
        List<UserSession> agents    = ensureUsers(spec.path("users").path("agents"),    "AGENT");
        List<UserSession> customers = ensureUsers(spec.path("users").path("customers"), "CUSTOMER");

        if (agents.isEmpty() || customers.isEmpty()) {
            throw new IllegalStateException("Setup başarısız: en az bir agent ve bir customer oturumu gerekli. " +
                    "Keycloak'ta setup.json'daki kullanıcıların önceden tanımlı ve şifresinin eşleştiğinden emin ol.");
        }

        // 2a. Generator'in onceden uretmis oldugu urunler varsa temizle. Backend
        //     deleteProduct cascade'i bagli bilet/yorum/worklog/csat'i da siliyor —
        //     her run sifirdan baslayabilir. Sadece setup.json'da tanimli isimler
        //     siliniyor; sistemdeki diger urunlere dokunulmuyor.
        cleanupOwnProducts(spec.path("products"));

        // 2b. Ürünler — idempotent (name eşleşmesiyle)
        Map<String, Long> productByName = ensureProducts(spec.path("products"));

        // 3. Topic'ler — idempotent (product + name eşleşmesiyle)
        Map<String, Long> topicByProductAndName = ensureTopics(spec.path("products"), productByName);

        // 4. Sıkça karşılaşılan sorunlar — idempotent (title eşleşmesiyle)
        ensureKnownIssues(spec.path("products"), productByName, topicByProductAndName);

        // 5. Ürün yetkilerini agent ve customer'lara dağıt
        assignProductsToUsers(agents,    productByName.values());
        assignProductsToUsers(customers, productByName.values());

        log.info("=== Sistem kurulumu tamamlandı ===");
        log.info("  Agent: {}, Customer: {}, Ürün: {}, Topic: {}",
                agents.size(), customers.size(), productByName.size(), topicByProductAndName.size());

        return new SetupResult(adminAgent, agents, customers, productByName, topicByProductAndName);
    }

    // -----------------------------------------------------------------
    // Setup.json yükleme
    // -----------------------------------------------------------------

    private JsonNode loadSetupSpec() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("setup.json")) {
            if (in == null) throw new IllegalStateException("setup.json classpath'te bulunamadı.");
            return mapper.readTree(in);
        }
    }

    // -----------------------------------------------------------------
    // Users — sadece login; oluşturma yok
    // -----------------------------------------------------------------

    private List<UserSession> ensureUsers(JsonNode userArray, String role) {
        List<UserSession> sessions = new ArrayList<>();
        if (userArray == null || !userArray.isArray()) return sessions;

        for (JsonNode u : userArray) {
            String username = u.path("username").asText();
            String password = u.path("password").asText();

            UserSession session = loginWithRecovery(username, password, role);
            if (session == null) continue;
            log.info("Oturum açıldı: {} ({})", username, role);
            syncUser(session);
            sessions.add(session);
        }
        return sessions;
    }

    /**
     * Login dener; "Account is not fully set up" hatası gelirse master admin REST ile
     * kullanıcının required-actions listesini temizleyip login'i tek sefer tekrar dener.
     * Diğer hatalarda (şifre yanlış, kullanıcı yok) sessizce atlanır — yalnızca WARN basar.
     */
    private UserSession loginWithRecovery(String username, String password, String role) {
        LoginAttempt first = tryLoginOnce(username, password, role);
        if (first.session != null) return first.session;

        if (first.notFullySetUp) {
            log.info("'{}' kullanıcısı required-action nedeniyle login olamadı, temizleniyor...", username);
            if (keycloakAdmin.clearRequiredActions(username)) {
                LoginAttempt second = tryLoginOnce(username, password, role);
                if (second.session != null) return second.session;
                log.warn("Required-action temizlendikten sonra hala login olunamadı: {} — Keycloak: {}",
                        username, second.errorMessage);
            }
            return null;
        }

        log.warn("Login başarısız → kullanıcı atlanıyor: {} ({}) — Keycloak hatası: {}",
                username, role, first.errorMessage);
        return null;
    }

    private LoginAttempt tryLoginOnce(String username, String password, String role) {
        try {
            KeycloakTokenClient tokenClient = new KeycloakTokenClient(http, mapper);
            tokenClient.login(username, password);
            return LoginAttempt.success(new UserSession(username, role, tokenClient));
        } catch (Exception e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            boolean notFullySetUp = msg.contains("Account is not fully set up");
            return LoginAttempt.failure(msg, notFullySetUp);
        }
    }

    private record LoginAttempt(UserSession session, String errorMessage, boolean notFullySetUp) {
        static LoginAttempt success(UserSession s) { return new LoginAttempt(s, null, false); }
        static LoginAttempt failure(String msg, boolean nfsu) { return new LoginAttempt(null, msg, nfsu); }
    }

    private void syncUser(UserSession session) {
        try {
            JsonNode resp = api.post("/users/sync", null, session.getToken());
            if (resp.has("id")) session.setUserId(resp.get("id").asText());
        } catch (Exception e) {
            log.warn("Sync edilemedi ({}): {}", session.getUsername(), e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Cleanup — generator'in onceki run'da uretmis oldugu urunleri sil
    // -----------------------------------------------------------------

    private void cleanupOwnProducts(JsonNode productArray) throws InterruptedException {
        Set<String> ownNames = new HashSet<>();
        for (JsonNode p : productArray) ownNames.add(p.path("name").asText());
        if (ownNames.isEmpty()) return;

        JsonNode existing;
        try {
            existing = api.get("/products", adminAgent.getToken());
        } catch (Exception e) {
            log.warn("Önceki ürünler listelenemedi, temizlik atlaniyor: {}", e.getMessage());
            return;
        }
        if (!existing.isArray()) return;

        int deleted = 0;
        for (JsonNode p : existing) {
            String name = p.path("name").asText();
            if (!ownNames.contains(name)) continue;
            Long id = p.path("id").asLong();
            try {
                api.delete("/products/" + id, null, adminAgent.getToken());
                log.info("Önceki ürün silindi: '{}' (ID: {}) — cascade ile bilet/yorum/worklog/csat da temizlendi", name, id);
                deleted++;
                Thread.sleep(GeneratorConfig.DELAY_MS);
            } catch (Exception e) {
                log.warn("Ürün silinemedi ({}, id={}): {}", name, id, e.getMessage());
            }
        }
        if (deleted > 0) log.info("Temizlik tamam: {} ürün silindi.", deleted);
    }

    // -----------------------------------------------------------------
    // Products — idempotent: name'e göre eşleştir, yoksa oluştur
    // -----------------------------------------------------------------

    private Map<String, Long> ensureProducts(JsonNode productArray) throws IOException, InterruptedException {
        Map<String, Long> result = new HashMap<>();

        Map<String, Long> existing = new HashMap<>();
        try {
            JsonNode resp = api.get("/products", adminAgent.getToken());
            if (resp.isArray()) {
                for (JsonNode p : resp) {
                    existing.put(p.path("name").asText(), p.path("id").asLong());
                }
            }
        } catch (Exception e) {
            log.warn("Ürün listesi alınamadı: {}", e.getMessage());
        }

        for (JsonNode product : productArray) {
            String name = product.path("name").asText();
            Long id = existing.get(name);
            if (id == null) {
                try {
                    JsonNode resp = api.post("/products",
                            Map.of("name", name, "isActive", true, "maxActiveTickets", 50),
                            adminAgent.getToken());
                    id = resp.path("id").asLong();
                    log.info("Ürün oluşturuldu: '{}' (ID: {})", name, id);
                    Thread.sleep(GeneratorConfig.DELAY_MS);
                } catch (Exception e) {
                    log.warn("Ürün oluşturulamadı ({}): {}", name, e.getMessage());
                    continue;
                }
            } else {
                log.info("Ürün zaten mevcut: '{}' (ID: {})", name, id);
            }
            result.put(name, id);
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Topics — idempotent: product+name eşleşmesine göre
    // -----------------------------------------------------------------

    private Map<String, Long> ensureTopics(JsonNode productArray, Map<String, Long> productByName)
            throws IOException, InterruptedException {
        Map<String, Long> result = new HashMap<>();

        for (JsonNode product : productArray) {
            String productName = product.path("name").asText();
            Long productId = productByName.get(productName);
            if (productId == null) continue;

            Map<String, Long> existingTopics = new HashMap<>();
            try {
                JsonNode resp = api.get("/products/" + productId + "/topics?includeInactive=true",
                        adminAgent.getToken());
                if (resp.isArray()) {
                    for (JsonNode t : resp) {
                        existingTopics.put(t.path("name").asText(), t.path("id").asLong());
                    }
                }
            } catch (Exception e) {
                log.warn("Topic listesi alınamadı ({}): {}", productName, e.getMessage());
            }

            for (JsonNode topic : product.path("topics")) {
                String topicName = topic.path("name").asText();
                Long topicId = existingTopics.get(topicName);
                if (topicId == null) {
                    try {
                        JsonNode resp = api.post("/products/" + productId + "/topics",
                                Map.of("name", topicName, "isActive", true),
                                adminAgent.getToken());
                        topicId = resp.path("id").asLong();
                        log.debug("Topic oluşturuldu: '{}' / '{}'", productName, topicName);
                        Thread.sleep(150);
                    } catch (Exception e) {
                        log.warn("Topic oluşturulamadı ({} / {}): {}", productName, topicName, e.getMessage());
                        continue;
                    }
                }
                result.put(SetupResult.topicKey(productName, topicName), topicId);
            }
            log.info("'{}' için topic kurulumu tamam ({} topic).", productName,
                    product.path("topics").size());
        }
        return result;
    }

    // -----------------------------------------------------------------
    // Known Issues — idempotent: topic içinde title eşleşmesine göre
    // -----------------------------------------------------------------

    private void ensureKnownIssues(JsonNode productArray,
                                    Map<String, Long> productByName,
                                    Map<String, Long> topicByProductAndName)
            throws IOException, InterruptedException {

        for (JsonNode product : productArray) {
            String productName = product.path("name").asText();
            Long productId = productByName.get(productName);
            if (productId == null) continue;

            List<String> existingTitles = new ArrayList<>();
            try {
                JsonNode resp = api.get("/products/" + productId + "/known-issues?includeInactive=true",
                        adminAgent.getToken());
                if (resp.isArray()) {
                    for (JsonNode item : resp) {
                        existingTitles.add(item.path("title").asText());
                    }
                }
            } catch (Exception e) {
                log.warn("Known issues listesi alınamadı ({}): {}", productName, e.getMessage());
            }

            int created = 0;
            for (JsonNode topic : product.path("topics")) {
                String topicName = topic.path("name").asText();
                Long topicId = topicByProductAndName.get(SetupResult.topicKey(productName, topicName));
                if (topicId == null) continue;

                for (JsonNode issue : topic.path("knownIssues")) {
                    String title = issue.path("title").asText();
                    if (existingTitles.contains(title)) continue;

                    try {
                        api.post("/products/" + productId + "/known-issues",
                                Map.of("title",   title,
                                       "content", issue.path("content").asText(),
                                       "topicId", topicId,
                                       "isActive", true),
                                adminAgent.getToken());
                        created++;
                        Thread.sleep(120);
                    } catch (Exception e) {
                        log.warn("Known issue oluşturulamadı ({} / {}): {}", productName, title, e.getMessage());
                    }
                }
            }
            if (created > 0) log.info("'{}' için {} known issue oluşturuldu.", productName, created);
        }
    }

    // -----------------------------------------------------------------
    // Product yetkileri
    // -----------------------------------------------------------------

    private void assignProductsToUsers(List<UserSession> users, Iterable<Long> productIds) throws IOException {
        for (UserSession user : users) {
            if (user.getUserId() == null) continue;
            for (Long productId : productIds) {
                try {
                    api.post("/users/" + user.getUserId() + "/products/" + productId,
                            Map.of(), adminAgent.getToken());
                } catch (ApiClient.ApiException e) {
                    if (e.getStatusCode() != 409) {
                        log.warn("Ürün atanamadı (user={}, product={}): {}",
                                user.getUsername(), productId, e.getMessage());
                    }
                }
            }
        }
    }
}
