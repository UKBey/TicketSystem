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
 * Reads ticket generation not from setup.json but from <code>tickets/ticket-*.json</code>
 * files on the classpath. Each file declaratively describes the full lifecycle of one ticket:
 *
 * <pre>
 * {
 *   "title": "...",                 // required
 *   "description": "...",           // required
 *   "priority": "LOW|MEDIUM|HIGH|CRITICAL",
 *   "productName": "VPN ve Ağ",
 *   "topicName": "VPN Bağlantısı",
 *   "status": "NEW|IN_PROGRESS|WAITING_FOR_CUSTOMER|RESOLVED|CLOSED",
 *   "worklogs":  [{ "minutes": 30, "description": "..." }],
 *   "comments":  [{ "author": "agent|customer", "type": "INTERNAL|EXTERNAL", "message": "..." }],
 *   "resolutionNote": "...",        // for RESOLVED/CLOSED
 *   "csat":     { "rating": 5, "comment": "..." }  // for CLOSED
 * }
 * </pre>
 *
 * Customer/agent assignments are taken round-robin from the user list in setup.json.
 *
 * <p>Comments are sent in three phases (see {@link #generate()}): all tickets are first
 * created/claimed and their comments collected, then every ticket's comments are flushed in
 * global "waves" (one comment per ticket per wave, skipping any author already used this wave
 * so the per-user 5s cooldown is never hit), then status transitions + CSAT run. Batching the
 * comments globally lets the per-user cooldowns overlap across all tickets instead of
 * serialising them ticket-by-ticket.
 */
public class TicketGenerator {

    private static final Logger log = LoggerFactory.getLogger(TicketGenerator.class);
    private static final int    TICKET_FILE_LIMIT = 200; // 50 ticket için fazlasıyla yeterli

    private final ApiClient api;
    private final ObjectMapper mapper;
    private final SetupResult setup;

    /**
     * @param api    backend API client
     * @param mapper Jackson mapper used to read ticket-*.json templates
     * @param setup  SetupGenerator output (users + product/topic ID maps)
     */
    public TicketGenerator(ApiClient api, ObjectMapper mapper, SetupResult setup) {
        this.api     = api;
        this.mapper  = mapper;
        this.setup   = setup;
    }

    /**
     * Generates tickets from the {@code tickets/ticket-NNN.json} templates on the classpath
     * and plays out each template's lifecycle based on its target status (claim,
     * worklog, comment, status update, CSAT).
     *
     * <p>Customer/agent assignments are taken round-robin from the setup. Templates are
     * processed in the order CLOSED → RESOLVED → WAITING → IN_PROGRESS → NEW
     * (so the per-agent active-claim limit is not exhausted).
     *
     * <p>Runs in three phases so the per-user comment cooldown overlaps across all tickets
     * instead of being paid ticket-by-ticket:
     * <ol>
     *   <li><b>Setup</b> — create every ticket, claim it (agent keeps the claim, so we never
     *       transition to RESOLVED yet — that would drop the claim and 403 later comments) and
     *       add worklogs; comments are only collected, not sent.</li>
     *   <li><b>Comment waves</b> — {@link #flushAllComments} drains every ticket's comments in
     *       global rounds: each wave sends one comment per ticket, then waits one cooldown.</li>
     *   <li><b>Finish</b> — apply the target status transition + CSAT for each ticket.</li>
     * </ol>
     *
     * @return IDs of successfully created tickets (handed to DateBackfiller)
     * @throws IOException          template read or API error
     * @throws InterruptedException if {@code Thread.sleep} (used for request pacing) is interrupted
     */
    public List<Long> generate() throws IOException, InterruptedException {
        log.info("=== Bilet üretimi başlıyor (JSON şablon tabanlı) ===");

        List<JsonNode> specs = loadTicketSpecs();
        log.info("Okunan ticket şablonu: {}", specs.size());

        // Önce CLOSED ve RESOLVED'ler oluşturulur ki claim limiti dolmasın
        specs.sort(Comparator.comparingInt(this::statusPriority));

        List<Long> ticketIds = new ArrayList<>();
        List<PendingTicket> pending = new ArrayList<>();
        int customerIdx = 0;
        int agentIdx    = 0;

        // Faz 1: tüm biletleri oluştur + claim + worklog, yorumları topla (henüz gönderme).
        for (int i = 0; i < specs.size(); i++) {
            JsonNode spec = specs.get(i);
            UserSession customer = setup.customers().get(customerIdx++ % setup.customers().size());
            UserSession agent    = setup.agents().get(agentIdx++ % setup.agents().size());

            try {
                PendingTicket pt = setupTicket(spec, customer, agent);
                if (pt != null) {
                    ticketIds.add(pt.ticketId);
                    pending.add(pt);
                }
            } catch (Exception e) {
                log.warn("Şablon işlenirken hata (#{}, başlık: {}): {}",
                        i, spec.path("title").asText(), e.getMessage());
            }
            sleep();
        }

        // Faz 2: tüm biletlerin yorumlarını global dalgalar hâlinde gönder.
        flushAllComments(pending);

        // Faz 3: status geçişleri + CSAT.
        for (PendingTicket pt : pending) {
            try {
                finishTicket(pt);
            } catch (Exception e) {
                log.warn("Bilet sonlandırılamadı #{}: {}", pt.ticketId, e.getMessage());
            }
        }

        log.info("=== Bilet üretimi tamamlandı. Üretilen bilet: {} ===", ticketIds.size());
        return ticketIds;
    }

    // -----------------------------------------------------------------
    // Yaşam döngüsü orchestrasyonu
    // -----------------------------------------------------------------

    /**
     * Faz 1: bileti oluşturur, agent claim'ini alır ve worklog'ları ekler. Yorumlar yalnızca
     * {@link PendingTicket} içinde toplanır; gönderim faz 2'de ({@link #flushAllComments}) yapılır.
     * Claim bilerek bırakılmaz (RESOLVED'e geçiş claim'i siler → sonraki yorumlar 403 yer), bu
     * yüzden status geçişi faz 3'e ({@link #finishTicket}) ertelenir.
     */
    private PendingTicket setupTicket(JsonNode spec, UserSession customer, UserSession agent)
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

        PendingTicket pt = new PendingTicket(ticketId, spec, customer, agent, status);

        if ("NEW".equals(status)) {
            return pt; // Yorum/worklog/state değişikliği yok
        }

        // 2. Agent claim alır → bilet IN_PROGRESS'e geçer (faz 3'e kadar bu claim korunur)
        if (!claimTicket(ticketId, agent)) {
            pt.skipLifecycle = true; // claim yoksa yorum atılamaz, status ilerletilemez
            return pt;
        }
        sleep();

        // 3. Worklog kayıtları
        addWorklogs(ticketId, agent, spec.path("worklogs"));

        // 4. Yorumlar toplanır (gönderim faz 2'de)
        collectComments(pt, spec.path("comments"));
        return pt;
    }

    /**
     * Faz 3: bileti hedef statüsüne taşır ve gerekirse CSAT gönderir. Yorumlar faz 2'de
     * gönderildiği için claim hâlâ agent'ta; RESOLVED'e geçiş artık güvenle claim'i silebilir.
     */
    private void finishTicket(PendingTicket pt) throws InterruptedException {
        if (pt.skipLifecycle || "NEW".equals(pt.status)) return;

        switch (pt.status) {
            case "IN_PROGRESS" -> { /* zaten IN_PROGRESS */ }
            case "WAITING_FOR_CUSTOMER" -> {
                // WAITING'e geçişte reasonCode zorunlu değil; göndersek de backend yok sayıyor.
                updateStatus(pt.ticketId, "WAITING_FOR_CUSTOMER", pt.agent, null, null);
                sleep();
            }
            case "RESOLVED" -> {
                // Backend RESOLVED'e geçişte reasonCode zorunlu kılıyor; resolutionNote artık
                // ayrı bir endpoint değil, status update body'sinde 'note' alanı olarak gider.
                String reasonCode = pt.spec.path("reasonCode").asText("SOLUTION_PROVIDED");
                String note       = noteOrNull(pt.spec.path("resolutionNote"));
                updateStatus(pt.ticketId, "RESOLVED", pt.agent, reasonCode, note);
                sleep();
            }
            case "CLOSED" -> {
                String reasonCode = pt.spec.path("reasonCode").asText("SOLUTION_PROVIDED");
                String note       = noteOrNull(pt.spec.path("resolutionNote"));
                updateStatus(pt.ticketId, "RESOLVED", pt.agent, reasonCode, note);
                sleep();
                // CSAT'ı customer gönderir; CsatService RESOLVED → CLOSED'a otomatik geçirir.
                submitCsat(pt.ticketId, pt.customer, pt.spec.path("csat"));
                sleep();
            }
            default -> log.warn("Bilinmeyen status: {}", pt.status);
        }
    }

    private String noteOrNull(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        String txt = node.asText("");
        return txt.isBlank() ? null : txt;
    }

    /**
     * Processing order by status — closed tickets first, so the agent limit does not
     * fill up and claims remain available for tickets that will be opened later.
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

    private void collectComments(PendingTicket pt, JsonNode comments) {
        if (!comments.isArray()) return;
        for (JsonNode c : comments) {
            String author = c.path("author").asText("agent");
            UserSession sender = "customer".equalsIgnoreCase(author) ? pt.customer : pt.agent;
            String type    = c.path("type").asText("EXTERNAL").toUpperCase();
            String message = c.path("message").asText();
            pt.comments.add(new CommentTask(pt.ticketId, type, message, sender));
        }
    }

    /**
     * Updates the ticket status. The backend requires reasonCode on the transition to
     * RESOLVED, and there is a separate endpoint for CLOSED (since CLOSED is auto-applied
     * by the CSAT flow, we never send CLOSED directly here).
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
    // Yorum gönderimi — global dalgalar (faz 2)
    // -----------------------------------------------------------------

    /**
     * Tüm biletlerin yorumlarını "dalga dalga" gönderir: her dalgada her biletten sıradaki bir
     * yorum atılır, sonra bir cooldown ({@link GeneratorConfig#COMMENT_DELAY_MS}) beklenir. Aynı
     * kullanıcı (agent/customer aynı kullanıcı havuzundan round-robin paylaşıldığı için) bir
     * dalga içinde ikinci kez yorum yapacaksa o bilet bir sonraki dalgaya ertelenir — böylece
     * backend'in kullanıcı-bazlı 5 sn cooldown'ına takılmadan (429), bilet içi sıra da bozulmadan
     * tüm biletlerin cooldown'ları üst üste biner. Toplam süre ≈ (en çok yorumu olan kullanıcı) ×
     * cooldown — biletler arası seri beklemeye gerek kalmaz.
     */
    private void flushAllComments(List<PendingTicket> pending) throws InterruptedException {
        int total = pending.stream().mapToInt(p -> p.comments.size()).sum();
        if (total == 0) return;

        log.info("Yorumlar gönderiliyor: {} adet, {} bilet (global dalga round-robin)",
                total, pending.size());

        int sent = 0;
        int wave = 0;
        while (true) {
            Set<String> usedThisWave = new HashSet<>();
            boolean anySent = false;
            for (PendingTicket pt : pending) {
                CommentTask next = pt.comments.peek();
                if (next == null) continue;
                // Bu kullanıcı bu dalgada zaten yorum yaptıysa cooldown'a takılmamak için ertele.
                if (!usedThisWave.add(next.user().getUsername())) continue;
                sendComment(pt.comments.poll());
                sent++;
                anySent = true;
            }
            if (!anySent) break;
            wave++;
            boolean moreLeft = pending.stream().anyMatch(p -> !p.comments.isEmpty());
            if (moreLeft) Thread.sleep(GeneratorConfig.COMMENT_DELAY_MS);
        }
        log.info("Tüm yorumlar gönderildi: {} ({} dalga)", sent, wave);
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

    /**
     * Faz 1'de toplanan, faz 2/3'te işlenen bir biletin durumu. {@code comments} biletteki
     * yorumları şablon sırasında tutar (FIFO); faz 2 bunları tüketir. {@code skipLifecycle},
     * claim alınamayan biletlerde yorum/geçişlerin atlanması için işaretlenir.
     */
    private static final class PendingTicket {
        final Long ticketId;
        final JsonNode spec;
        final UserSession customer;
        final UserSession agent;
        final String status;
        final Deque<CommentTask> comments = new ArrayDeque<>();
        boolean skipLifecycle = false;

        PendingTicket(Long ticketId, JsonNode spec, UserSession customer, UserSession agent, String status) {
            this.ticketId = ticketId;
            this.spec     = spec;
            this.customer = customer;
            this.agent    = agent;
            this.status   = status;
        }
    }
}
