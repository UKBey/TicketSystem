package com.ticketsystem.generator.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.client.ApiClient;
import com.ticketsystem.generator.config.GeneratorConfig;
import com.ticketsystem.generator.model.SetupResult;
import com.ticketsystem.generator.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Bilet üretimini setup.json'dan değil, classpath'teki <code>tickets/ticket-*.json</code>
 * dosyalarından okur. Her dosya bir biletin tüm yaşam döngüsünü deklaratif olarak tanımlar:
 *
 * <pre>
 * {
 *   "title": "...",                 // zorunlu
 *   "description": "...",           // zorunlu
 *   "priority": "LOW|MEDIUM|HIGH|CRITICAL",
 *   "productName": "VPN ve Ağ",
 *   "topicName": "VPN Bağlantısı",
 *   "status": "NEW|IN_PROGRESS|WAITING_FOR_CUSTOMER|RESOLVED|CLOSED",
 *   "worklogs":  [{ "minutes": 30, "description": "..." }],
 *   "comments":  [{ "author": "agent|customer", "type": "INTERNAL|EXTERNAL", "message": "..." }],
 *   "resolutionNote": "...",        // RESOLVED/CLOSED için
 *   "csat":     { "rating": 5, "comment": "..." }  // CLOSED için
 * }
 * </pre>
 *
 * Customer/agent atamaları setup.json'daki kullanıcı listesinden round-robin alınır.
 * Yorumlar rate-limit dostu olması için aşama sonunda round-robin kuyrukla gönderilir.
 */
public class TicketGenerator {

    private static final Logger log = LoggerFactory.getLogger(TicketGenerator.class);
    private static final int    TICKET_FILE_LIMIT = 200; // 50 ticket için fazlasıyla yeterli

    private final ApiClient api;
    private final ObjectMapper mapper;
    private final SetupResult setup;

    private final Map<String, Queue<CommentTask>> commentQueues = new LinkedHashMap<>();

    public TicketGenerator(ApiClient api, ObjectMapper mapper, SetupResult setup) {
        this.api     = api;
        this.mapper  = mapper;
        this.setup   = setup;

        // Kullanıcı bazlı yorum kuyrukları — round-robin için
        for (UserSession u : setup.customers()) commentQueues.put(u.getUsername(), new LinkedList<>());
        for (UserSession u : setup.agents())    commentQueues.put(u.getUsername(), new LinkedList<>());
    }

    public List<Long> generate() throws IOException, InterruptedException {
        log.info("=== Bilet üretimi başlıyor (JSON şablon tabanlı) ===");

        List<JsonNode> specs = loadTicketSpecs();
        log.info("Okunan ticket şablonu: {}", specs.size());

        // Önce CLOSED ve RESOLVED'ler oluşturulur ki claim limiti dolmasın
        specs.sort(Comparator.comparingInt(this::statusPriority));

        List<Long> ticketIds = new ArrayList<>();
        int customerIdx = 0;
        int agentIdx    = 0;

        for (int i = 0; i < specs.size(); i++) {
            JsonNode spec = specs.get(i);
            UserSession customer = setup.customers().get(customerIdx++ % setup.customers().size());
            UserSession agent    = setup.agents().get(agentIdx++ % setup.agents().size());

            try {
                Long id = runLifecycle(spec, customer, agent);
                if (id != null) ticketIds.add(id);
            } catch (Exception e) {
                log.warn("Şablon işlenirken hata (#{}, başlık: {}): {}",
                        i, spec.path("title").asText(), e.getMessage());
            }
            sleep();
        }

        log.info("=== Bilet üretimi tamamlandı. Üretilen bilet: {} ===", ticketIds.size());
        return ticketIds;
    }

    // -----------------------------------------------------------------
    // Yaşam döngüsü orchestrasyonu
    // -----------------------------------------------------------------

    private Long runLifecycle(JsonNode spec, UserSession customer, UserSession agent)
            throws IOException, InterruptedException {

        String status      = spec.path("status").asText("NEW").toUpperCase();
        String productName = spec.path("productName").asText();
        String topicName   = spec.path("topicName").asText("");

        Long productId = setup.productByName().get(productName);
        if (productId == null) {
            log.warn("Şablonda geçen ürün bulunamadı: '{}'. Şablon atlanıyor.", productName);
            return null;
        }
        Long topicId = setup.topicByProductAndName().get(SetupResult.topicKey(productName, topicName));

        // 1. Bilet oluştur (customer)
        Long ticketId = createTicket(spec, customer, productId, topicId);
        if (ticketId == null) return null;
        sleep();

        if ("NEW".equals(status)) {
            return ticketId; // Yorum/worklog/state değişikliği yok
        }

        // 2. Agent claim alır → bilet IN_PROGRESS'e geçer
        if (!claimTicket(ticketId, agent)) return ticketId;
        sleep();

        // 3. Worklog kayıtları
        addWorklogs(ticketId, agent, spec.path("worklogs"));

        // 4. Yorumlar kuyruğa eklenir ve HEMEN gönderilir.
        //    Backend RESOLVED'e geçişte agent claim'ini siliyor (TicketService.java:801);
        //    yorumları status update'ten sonraya bıraksak claim sahibi olmayan agent
        //    yorum atmaya çalışıp 403 yer. Cooldown (COMMENT_DELAY_MS) için kullanıcı
        //    bazlı round-robin queue içinde flush yapılıyor.
        enqueueComments(ticketId, customer, agent, spec.path("comments"));
        flushCommentQueues();

        // 5. Status hedefe göre ilerlet
        switch (status) {
            case "IN_PROGRESS" -> { /* zaten IN_PROGRESS */ }
            case "WAITING_FOR_CUSTOMER" -> {
                // WAITING'e geçişte reasonCode zorunlu değil; göndersek de backend yok sayıyor.
                updateStatus(ticketId, "WAITING_FOR_CUSTOMER", agent, null, null);
                sleep();
            }
            case "RESOLVED" -> {
                // Backend RESOLVED'e geçişte reasonCode zorunlu kılıyor; resolutionNote artık
                // ayrı bir endpoint değil, status update body'sinde 'note' alanı olarak gider.
                String reasonCode = spec.path("reasonCode").asText("SOLUTION_PROVIDED");
                String note       = noteOrNull(spec.path("resolutionNote"));
                updateStatus(ticketId, "RESOLVED", agent, reasonCode, note);
                sleep();
            }
            case "CLOSED" -> {
                String reasonCode = spec.path("reasonCode").asText("SOLUTION_PROVIDED");
                String note       = noteOrNull(spec.path("resolutionNote"));
                updateStatus(ticketId, "RESOLVED", agent, reasonCode, note);
                sleep();
                // CSAT'ı customer gönderir; CsatService RESOLVED → CLOSED'a otomatik geçirir.
                submitCsat(ticketId, customer, spec.path("csat"));
                sleep();
            }
            default -> log.warn("Bilinmeyen status: {}", status);
        }
        return ticketId;
    }

    private String noteOrNull(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        String txt = node.asText("");
        return txt.isBlank() ? null : txt;
    }

    /**
     * Status'ler için işlem sırası — kapanan biletler önce, böylece agent limiti
     * dolmaz ve sonradan açılacak biletler için claim alınabilir.
     */
    private int statusPriority(JsonNode spec) {
        return switch (spec.path("status").asText("NEW").toUpperCase()) {
            case "CLOSED"   -> 0;
            case "RESOLVED" -> 1;
            case "WAITING_FOR_CUSTOMER" -> 2;
            case "IN_PROGRESS" -> 3;
            case "NEW"      -> 4;
            default          -> 5;
        };
    }

    // -----------------------------------------------------------------
    // Şablon yükleme
    // -----------------------------------------------------------------

    private List<JsonNode> loadTicketSpecs() throws IOException {
        List<JsonNode> specs = new ArrayList<>();
        ClassLoader cl = getClass().getClassLoader();
        // Sıralı 001..200 sırasıyla taranır; bulunmayan numaralar atlanır.
        for (int i = 1; i <= TICKET_FILE_LIMIT; i++) {
            String name = String.format("tickets/ticket-%03d.json", i);
            try (InputStream in = cl.getResourceAsStream(name)) {
                if (in == null) continue;
                specs.add(mapper.readTree(in));
            }
        }
        return specs;
    }

    // -----------------------------------------------------------------
    // Atomik API çağrıları
    // -----------------------------------------------------------------

    private Long createTicket(JsonNode spec, UserSession customer, Long productId, Long topicId)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title",       spec.path("title").asText());
        body.put("description", spec.path("description").asText());
        body.put("priority",    spec.path("priority").asText("MEDIUM"));
        body.put("productId",   productId);
        if (topicId != null) body.put("topicId", topicId);
        try {
            JsonNode resp = api.post("/tickets", body, customer.getToken());
            return resp.path("id").asLong();
        } catch (Exception e) {
            log.warn("Bilet oluşturulamadı (başlık: {}): {}", spec.path("title").asText(), e.getMessage());
            return null;
        }
    }

    private boolean claimTicket(Long ticketId, UserSession agent) {
        try {
            api.put("/tickets/" + ticketId + "/claim", Map.of(), agent.getToken());
            return true;
        } catch (Exception e) {
            log.warn("Claim alınamadı #{}: {}", ticketId, e.getMessage());
            return false;
        }
    }

    private void addWorklogs(Long ticketId, UserSession agent, JsonNode worklogs) throws InterruptedException {
        if (!worklogs.isArray()) return;
        for (JsonNode w : worklogs) {
            Map<String, Object> body = Map.of(
                    "minutes",     w.path("minutes").asInt(30),
                    "description", w.path("description").asText("İnceleme ve müdahale."));
            try {
                api.post("/tickets/" + ticketId + "/worklogs", body, agent.getToken());
            } catch (Exception e) {
                log.warn("Worklog eklenemedi #{}: {}", ticketId, e.getMessage());
            }
            sleep();
        }
    }

    private void enqueueComments(Long ticketId, UserSession customer, UserSession agent, JsonNode comments) {
        if (!comments.isArray()) return;
        for (JsonNode c : comments) {
            String author = c.path("author").asText("agent");
            UserSession sender = "customer".equalsIgnoreCase(author) ? customer : agent;
            String type    = c.path("type").asText("EXTERNAL").toUpperCase();
            String message = c.path("message").asText();
            commentQueues.computeIfAbsent(sender.getUsername(), k -> new LinkedList<>())
                    .add(new CommentTask(ticketId, type, message, sender));
        }
    }

    /**
     * Bilet statüsünü günceller. Backend RESOLVED'e geçişte reasonCode zorunluyor,
     * CLOSED için ise ayrı endpoint var (CSAT akışında auto-CLOSE olduğu için burada
     * doğrudan CLOSED göndermiyoruz).
     */
    private boolean updateStatus(Long ticketId, String status, UserSession user,
                                  String reasonCode, String note) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        if (reasonCode != null) body.put("reasonCode", reasonCode);
        if (note != null)       body.put("note",       note);
        try {
            api.put("/tickets/" + ticketId + "/status", body, user.getToken());
            return true;
        } catch (Exception e) {
            log.warn("Statü güncellenemedi #{} ({}): {}", ticketId, status, e.getMessage());
            return false;
        }
    }

    private void submitCsat(Long ticketId, UserSession customer, JsonNode csat) {
        int rating = csat.path("rating").asInt(5);
        String comment = csat.path("comment").asText("Sorun çözüldü, teşekkürler.");
        try {
            api.post("/tickets/" + ticketId + "/csat",
                    Map.of("rating", rating, "comment", comment), customer.getToken());
        } catch (Exception e) {
            log.warn("CSAT gönderilemedi #{}: {}", ticketId, e.getMessage());
        }
    }

    // -----------------------------------------------------------------
    // Yorum kuyruğu — round-robin
    // -----------------------------------------------------------------

    private void flushCommentQueues() throws InterruptedException {
        int total = commentQueues.values().stream().mapToInt(Queue::size).sum();
        if (total == 0) return;

        log.info("Yorumlar gönderiliyor: {} adet, {} kullanıcı (round-robin)",
                total, commentQueues.size());

        int sent = 0;
        while (true) {
            boolean anyLeft = false;
            for (Queue<CommentTask> queue : commentQueues.values()) {
                CommentTask task = queue.poll();
                if (task == null) continue;
                anyLeft = true;
                sendComment(task);
                sent++;
            }
            if (!anyLeft) break;
            Thread.sleep(GeneratorConfig.COMMENT_DELAY_MS);
        }
        log.info("Tüm yorumlar gönderildi: {}", sent);
    }

    private void sendComment(CommentTask task) throws InterruptedException {
        Map<String, String> body = Map.of("message", task.message, "type", task.type);
        for (int attempt = 1; attempt <= GeneratorConfig.RATE_LIMIT_RETRY_COUNT; attempt++) {
            try {
                api.post("/tickets/" + task.ticketId + "/comments", body, task.user.getToken());
                return;
            } catch (ApiClient.ApiException e) {
                if (e.getStatusCode() == 429 && attempt < GeneratorConfig.RATE_LIMIT_RETRY_COUNT) {
                    Thread.sleep(GeneratorConfig.RATE_LIMIT_BACKOFF_MS);
                } else {
                    log.warn("Yorum eklenemedi #{}: {}", task.ticketId, e.getMessage());
                    return;
                }
            } catch (Exception e) {
                log.warn("Yorum eklenemedi #{}: {}", task.ticketId, e.getMessage());
                return;
            }
        }
    }

    private void sleep() throws InterruptedException {
        Thread.sleep(GeneratorConfig.DELAY_MS);
    }

    private record CommentTask(Long ticketId, String type, String message, UserSession user) {}
}
