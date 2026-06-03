package com.ticketsystem.generator.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.client.ApiClient;
import com.ticketsystem.generator.client.KeycloakAdminApi;
import com.ticketsystem.generator.client.KeycloakTokenClient;
import com.ticketsystem.generator.config.GeneratorConfig;
import com.ticketsystem.generator.model.SeedUser;
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
 * Prepares the system according to the template in setup.json.
 *
 * <ul>
 *   <li>Creates the agent and customer users defined in {@code users.json} via the Agent Admin
 *       API with a temporary password (forced change on first login), then completes that change
 *       by setting each user's final password and clearing the required action, and signs in.
 *       Users whose creation/login fails are skipped with a warning.</li>
 *   <li>Creates products / topics / known issues / canned responses idempotently.</li>
 *   <li>Assigns authorizedProducts to every agent and customer that was able to log in.</li>
 * </ul>
 *
 * Safe to re-run: existing users are detected (HTTP 409) and only re-provisioned; products /
 * topics / issues / canned responses are skipped if they already exist.
 */
public class SetupGenerator {

    private static final Logger log = LoggerFactory.getLogger(SetupGenerator.class);

    private final ApiClient api;
    private final ObjectMapper mapper;
    private final OkHttpClient http;
    private final UserSession adminAgent;
    private final KeycloakAdminApi keycloakAdmin;

    /**
     * @param api        client for backend API calls
     * @param mapper     Jackson mapper used to read setup.json
     * @param http       shared OkHttp used by per-user {@link KeycloakTokenClient}s
     * @param adminAgent the admin session that performs product/topic/authorization operations
     */
    public SetupGenerator(ApiClient api, ObjectMapper mapper, OkHttpClient http, UserSession adminAgent) {
        this.api           = api;
        this.mapper        = mapper;
        this.http          = http;
        this.adminAgent    = adminAgent;
        this.keycloakAdmin = new KeycloakAdminApi(http, mapper);
    }

    /**
     * Prepares the system according to the setup.json template: user sessions,
     * cleanup of products from prior generator runs, product/topic/known-issue
     * creation and authorization assignment.
     *
     * @return the {@link SetupResult} to be passed on to TicketGenerator
     * @throws IOException          API or setup.json read error
     * @throws InterruptedException if {@code Thread.sleep} (used for request pacing) is interrupted
     * @throws IllegalStateException if no agent or no customer was able to log in
     */
    public SetupResult setup() throws IOException, InterruptedException {
        log.info("=== Sistem kurulumu başlıyor ===");

        JsonNode spec = loadSetupSpec();

        // 1. Kullanıcılar — Agent Admin ile oluştur (geçici şifre → ilk girişte değişim zorunlu),
        //    ardından nihai şifreyi (users.json) belirleyip oturum aç. Tanımlar users.json'dadır.
        List<UserSession> agents    = createAndLoginUsers(GeneratorConfig.agents(),    "AGENT");
        // Lead agent'lar LEAD_AGENT rolüyle oluşturulur (Keycloak composite → AGENT'ı kapsar);
        // operasyonel olarak agent gibi davrandıkları için agent havuzuna eklenir (claim/atama).
        List<UserSession> leads     = createAndLoginUsers(GeneratorConfig.leads(),     "LEAD_AGENT");
        agents.addAll(leads);
        List<UserSession> customers = createAndLoginUsers(GeneratorConfig.customers(), "CUSTOMER");

        if (agents.isEmpty() || customers.isEmpty()) {
            throw new IllegalStateException("Setup başarısız: en az bir agent ve bir customer gerekli. " +
                    "data-generator/users.json içindeki agents/customers listelerini doldur (örnek: users.example.json).");
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

        // 4b. Hazır yanıtlar — global + ürün başına paylaşılan şablonlar (idempotent, title eşleşmesiyle)
        ensureCannedResponses(spec.path("cannedResponses"), productByName);

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

    /**
     * Creates each seed user via the Agent Admin API (temporary password → forced change on
     * first login), completes that change by setting the final password and clearing the
     * required action, then signs in. Users that cannot be created/logged in are skipped.
     *
     * @param seeds user definitions from {@code users.json}
     * @param role  realm role to assign ({@code AGENT} / {@code CUSTOMER})
     * @return the sessions of users that were created and logged in
     */
    private List<UserSession> createAndLoginUsers(List<SeedUser> seeds, String role) throws InterruptedException {
        List<UserSession> sessions = new ArrayList<>();
        for (SeedUser u : seeds) {
            ensureUserExists(u, role);
            UserSession session = loginWithRecovery(u.username(), u.password(), role);
            if (session == null) {
                log.warn("Kullanıcı oluşturuldu ancak oturum açılamadı, atlanıyor: {} ({})", u.username(), role);
                continue;
            }
            log.info("Kullanıcı hazır: {} ({})", u.username(), role);
            syncUser(session);
            sessions.add(session);
        }
        return sessions;
    }

    /**
     * Idempotently provisions one user: create via the Agent Admin API (409 = already exists),
     * then set the final permanent password and clear the forced-change required action so the
     * subsequent login with the {@code users.json} password succeeds.
     */
    private void ensureUserExists(SeedUser u, String realmRole) throws InterruptedException {
        boolean exists = true;
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("username",  u.username());
            body.put("email",     u.email());
            body.put("firstName", u.firstName());
            body.put("lastName",  u.lastName());
            body.put("password",  GeneratorConfig.TEMP_PASSWORD);
            body.put("temporaryPassword", true); // ilk girişte değişim zorunlu
            body.put("roles", List.of(realmRole));
            api.post("/users/admin/create", body, adminAgent.getToken());
            log.info("Kullanıcı oluşturuldu (Agent Admin, geçici şifre): {}", u.username());
        } catch (ApiClient.ApiException e) {
            if (e.getStatusCode() == 409) {
                log.info("Kullanıcı zaten mevcut, yeniden oluşturulmuyor: {}", u.username());
            } else {
                log.warn("Kullanıcı oluşturulamadı ({}): {}", u.username(), e.getMessage());
                exists = false;
            }
        } catch (IOException e) {
            log.warn("Kullanıcı oluşturulamadı ({}): {}", u.username(), e.getMessage());
            exists = false;
        }
        Thread.sleep(GeneratorConfig.DELAY_MS);
        if (!exists) return;

        // "İlk giriş şifre değişimi"ni tamamla: nihai (kalıcı) şifreyi belirle + zorunlu aksiyonu temizle.
        keycloakAdmin.resetPassword(u.username(), u.password());
        keycloakAdmin.clearRequiredActions(u.username());
    }

    /**
     * Attempts to log in; if an "Account is not fully set up" error occurs, clears the user's
     * required-actions list via the master admin REST API and retries the login exactly once.
     * Other errors (wrong password, unknown user) are silently skipped — only a WARN is logged.
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
    // Canned responses — paylaşılan (SHARED) şablonlar: global + ürün başına.
    // Idempotent: başlık eşleşmesine göre atlanır. Admin tüm paylaşılanları görür.
    // -----------------------------------------------------------------

    private void ensureCannedResponses(JsonNode spec, Map<String, Long> productByName)
            throws IOException, InterruptedException {
        if (spec == null || spec.isMissingNode()) return;

        Set<String> existingTitles = new HashSet<>();
        try {
            JsonNode resp = api.get("/canned-responses", adminAgent.getToken());
            if (resp.isArray()) {
                for (JsonNode c : resp) existingTitles.add(c.path("title").asText());
            }
        } catch (Exception e) {
            log.warn("Hazır yanıt listesi alınamadı: {}", e.getMessage());
        }

        int created = 0;

        // Global (ürünsüz) paylaşılan şablonlar — her ürün bağlamında görünür.
        for (JsonNode tpl : spec.path("global")) {
            created += createCannedIfMissing(tpl, null, null, existingTitles);
        }

        // Ürün başına şablonlar — "{product}" token'ı ürün adıyla değiştirilir.
        for (Map.Entry<String, Long> entry : productByName.entrySet()) {
            for (JsonNode tpl : spec.path("perProduct")) {
                created += createCannedIfMissing(tpl, entry.getValue(), entry.getKey(), existingTitles);
            }
        }

        log.info("Hazır yanıt kurulumu tamam: {} yeni paylaşılan şablon oluşturuldu (global + ürün başına).", created);
    }

    /**
     * Creates one canned response via the API unless a template with the same (resolved) title
     * already exists. Returns 1 when a record is created, 0 otherwise.
     */
    private int createCannedIfMissing(JsonNode tpl, Long productId, String productName,
                                      Set<String> existingTitles) throws InterruptedException {
        String title = substituteProduct(tpl.path("title").asText(""), productName);
        if (title.isBlank() || existingTitles.contains(title)) return 0;

        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("scope", "SHARED");
        body.put("visibility", tpl.path("visibility").asText("BOTH"));
        String shortcut = tpl.path("shortcut").asText("");
        if (!shortcut.isBlank()) body.put("shortcut", shortcut);
        if (productId != null) body.put("productId", productId);
        String tr = substituteProduct(tpl.path("contentTr").asText(""), productName);
        String en = substituteProduct(tpl.path("contentEn").asText(""), productName);
        if (!tr.isBlank()) body.put("contentTr", tr);
        if (!en.isBlank()) body.put("contentEn", en);

        try {
            api.post("/canned-responses", body, adminAgent.getToken());
            existingTitles.add(title);
            Thread.sleep(GeneratorConfig.DELAY_MS);
            return 1;
        } catch (Exception e) {
            log.warn("Hazır yanıt oluşturulamadı ({}): {}", title, e.getMessage());
            return 0;
        }
    }

    /** Replaces the {@code {product}} token in template text with the product name. */
    private String substituteProduct(String text, String productName) {
        if (text == null) return "";
        return productName == null ? text : text.replace("{product}", productName);
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
