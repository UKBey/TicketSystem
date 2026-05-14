package com.ticketsystem.generator.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.client.ApiClient;
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
import java.util.List;
import java.util.Map;

/**
 * Sistemi setup.json'daki şablona göre hazırlar.
 *
 * <ul>
 *   <li>Setup.json'daki agent ve customer kullanıcılarını oluşturur (varsa dokunmaz).</li>
 *   <li>Ürünleri / topic'leri / sıkça karşılaşılan sorunları idempotent şekilde oluşturur.</li>
 *   <li>Yetkilerini (authorizedProducts) tüm agent ve customer'lara dağıtır.</li>
 * </ul>
 *
 * Re-run güvenli: ürün/topic/issue ve user varlıkları varsa atlanır, eklemez.
 */
public class SetupGenerator {

    private static final Logger log = LoggerFactory.getLogger(SetupGenerator.class);

    private final ApiClient api;
    private final ObjectMapper mapper;
    private final OkHttpClient http;
    private final UserSession adminAgent;

    public SetupGenerator(ApiClient api, ObjectMapper mapper, OkHttpClient http, UserSession adminAgent) {
        this.api        = api;
        this.mapper     = mapper;
        this.http       = http;
        this.adminAgent = adminAgent;
    }

    public SetupResult setup() throws IOException, InterruptedException {
        log.info("=== Sistem kurulumu başlıyor ===");

        JsonNode spec = loadSetupSpec();

        // 1. Kullanıcılar — yoksa oluştur, varsa atla; tüm hesaplara giriş yap
        List<UserSession> agents    = ensureUsers(spec.path("users").path("agents"),    "AGENT");
        List<UserSession> customers = ensureUsers(spec.path("users").path("customers"), "CUSTOMER");

        if (agents.isEmpty() || customers.isEmpty()) {
            throw new IllegalStateException("Setup başarısız: en az bir agent ve bir customer oturumu gerekli.");
        }

        // 2. Ürünleri oluştur (idempotent — name eşleşmesine göre)
        Map<String, Long> productByName = ensureProducts(spec.path("products"));

        // 3. Topic'leri oluştur (idempotent — product+name eşleşmesine göre)
        Map<String, Long> topicByProductAndName = ensureTopics(spec.path("products"), productByName);

        // 4. Sıkça karşılaşılan sorunları oluştur (idempotent — title eşleşmesine göre)
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
    // Users — idempotent: önce login dene, başarısızsa admin endpoint ile oluştur
    // -----------------------------------------------------------------

    private List<UserSession> ensureUsers(JsonNode userArray, String role) throws IOException, InterruptedException {
        List<UserSession> sessions = new ArrayList<>();
        if (userArray == null || !userArray.isArray()) return sessions;

        for (JsonNode u : userArray) {
            String username  = u.path("username").asText();
            String email     = u.path("email").asText();
            String firstName = u.path("firstName").asText();
            String lastName  = u.path("lastName").asText();
            String password  = u.path("password").asText();

            UserSession existing = tryLogin(username, password, role);
            if (existing != null) {
                log.info("Kullanıcı zaten mevcut, oturum açıldı: {} ({})", username, role);
                syncUser(existing);
                sessions.add(existing);
                continue;
            }

            // Kullanıcı yok veya şifresi eşleşmiyor — admin ile oluştur (kalıcı şifre)
            try {
                createUserViaAdmin(username, email, firstName, lastName, password, role);
                Thread.sleep(GeneratorConfig.DELAY_MS);
            } catch (Exception e) {
                log.warn("Kullanıcı oluşturulamadı ({}): {}", username, e.getMessage());
                continue;
            }

            UserSession created = tryLogin(username, password, role);
            if (created == null) {
                log.warn("Kullanıcı oluşturuldu ama oturum açılamadı: {}", username);
                continue;
            }
            syncUser(created);
            sessions.add(created);
            log.info("Yeni kullanıcı oluşturuldu ve giriş yapıldı: {} ({})", username, role);
        }
        return sessions;
    }

    private UserSession tryLogin(String username, String password, String role) {
        try {
            KeycloakTokenClient tokenClient = new KeycloakTokenClient(http, mapper);
            tokenClient.login(username, password);
            return new UserSession(username, role, tokenClient);
        } catch (Exception e) {
            log.debug("Login başarısız ({}): {}", username, e.getMessage());
            return null;
        }
    }

    private void createUserViaAdmin(String username, String email, String firstName, String lastName,
                                     String password, String role) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("username",          username);
        body.put("email",             email);
        body.put("firstName",         firstName);
        body.put("lastName",          lastName);
        body.put("password",          password);
        body.put("roles",             List.of(role));
        body.put("temporaryPassword", false);
        api.post("/users/admin/create", body, adminAgent.getToken());
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
    // Products — idempotent: name'e göre eşleştir, yoksa oluştur
    // -----------------------------------------------------------------

    private Map<String, Long> ensureProducts(JsonNode productArray) throws IOException, InterruptedException {
        Map<String, Long> result = new HashMap<>();

        // Mevcut ürünleri çek
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

            // Mevcut topic'leri çek
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

            // Mevcut known-issues'leri tek seferde çek (tüm topic'ler dahil)
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
                    // 409 = zaten atanmış, normal
                    if (e.getStatusCode() != 409) {
                        log.warn("Ürün atanamadı (user={}, product={}): {}",
                                user.getUsername(), productId, e.getMessage());
                    }
                }
            }
        }
    }
}
