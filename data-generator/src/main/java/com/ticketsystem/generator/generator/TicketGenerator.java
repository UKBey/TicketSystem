package com.ticketsystem.generator.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.generator.client.ApiClient;
import com.ticketsystem.generator.config.GeneratorConfig;
import com.ticketsystem.generator.model.UserSession;
import com.ticketsystem.generator.util.FakeData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Bilet yaşam döngüsünü simüle eder.
 *
 * Yorum rate limit optimizasyonu:
 *   Rate limit kullanıcı başına uygulandığından, yorumlar kullanıcı bazında
 *   gruplandırılır. Her kullanıcı kendi sırasında yorum atar; farklı
 *   kullanıcılar paralel olarak ilerler (round-robin).
 *   Bu sayede N kullanıcı varsa bekleme süresi N kat azalır.
 */
public class TicketGenerator {

    private static final Logger log = LoggerFactory.getLogger(TicketGenerator.class);

    private final ApiClient api;
    private final ObjectMapper mapper;
    private final List<UserSession> customers;
    private final List<UserSession> agents;
    private final List<Long> productIds;

    // Kullanıcı başına yorum kuyruğu: userId → [(ticketId, type, session)]
    private final Map<String, Queue<CommentTask>> commentQueues = new LinkedHashMap<>();

    public TicketGenerator(ApiClient api, ObjectMapper mapper,
                           List<UserSession> customers,
                           List<UserSession> agents,
                           List<Long> productIds) {
        this.api        = api;
        this.mapper     = mapper;
        this.customers  = customers;
        this.agents     = agents;
        this.productIds = productIds;

        // Her kullanıcı için boş kuyruk oluştur
        for (UserSession u : customers) commentQueues.put(u.getUsername(), new LinkedList<>());
        for (UserSession u : agents)    commentQueues.put(u.getUsername(), new LinkedList<>());
    }

    public List<Long> generate() throws IOException, InterruptedException {
        int total       = GeneratorConfig.TICKET_COUNT;
        int cntNew      = (int) Math.round(total * GeneratorConfig.PCT_NEW              / 100.0);
        int cntProgress = (int) Math.round(total * GeneratorConfig.PCT_IN_PROGRESS      / 100.0);
        int cntWaiting  = (int) Math.round(total * GeneratorConfig.PCT_WAITING          / 100.0);
        int cntResolved = (int) Math.round(total * GeneratorConfig.PCT_RESOLVED         / 100.0);
        int cntClosed   = total - cntNew - cntProgress - cntWaiting - cntResolved;

        log.info("=== Bilet üretimi başlıyor ===");
        log.info("Toplam: {} | NEW: {} | IN_PROGRESS: {} | WAITING: {} | RESOLVED: {} | CLOSED: {}",
                total, cntNew, cntProgress, cntWaiting, cntResolved, cntClosed);

        List<Long> allIds = new ArrayList<>();

        // ---------------------------------------------------------------
        // Aşama 1: Tüm biletleri oluştur + claim + worklog + çözüm notu + statü
        //          (yorumlar kuyruğa alınır)
        // ---------------------------------------------------------------
        allIds.addAll(createNewTickets(cntNew));
        allIds.addAll(createInProgressTickets(cntProgress));
        allIds.addAll(createWaitingTickets(cntWaiting));
        allIds.addAll(createResolvedTickets(cntResolved));
        allIds.addAll(createClosedTickets(cntClosed));

        // ---------------------------------------------------------------
        // Aşama 2: Yorumları kullanıcı bazında round-robin ile gönder
        // ---------------------------------------------------------------
        flushCommentQueues();

        log.info("=== Bilet üretimi tamamlandı. Toplam: {} bilet ===", allIds.size());
        return allIds;
    }

    // ---------------------------------------------------------------
    // Bilet oluşturma metodları (yorum yok, sadece kuyruğa ekle)
    // ---------------------------------------------------------------

    private List<Long> createNewTickets(int count) throws IOException, InterruptedException {
        log.info("NEW biletler oluşturuluyor: {}", count);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Long id = createTicket(FakeData.pick(customers));
            if (id != null) ids.add(id);
            sleep();
        }
        return ids;
    }

    private List<Long> createInProgressTickets(int count) throws IOException, InterruptedException {
        log.info("IN_PROGRESS biletler oluşturuluyor: {}", count);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UserSession customer = FakeData.pick(customers);
            UserSession agent    = FakeData.pick(agents);

            Long ticketId = createTicket(customer);
            if (ticketId == null) continue;
            ids.add(ticketId);
            sleep();

            claimTicket(ticketId, agent);
            sleep();

            // Worklog: agent üzerinde aktif çalışma kaydı
            int worklogCount = 1 + FakeData.nextInt(2);
            addWorklogsForTicket(ticketId, agent, worklogCount);

            // Yorumları kuyruğa ekle (hemen gönderme)
            enqueueComment(ticketId, agent,    "INTERNAL");
            enqueueComment(ticketId, customer, "EXTERNAL");
        }
        return ids;
    }

    private List<Long> createWaitingTickets(int count) throws IOException, InterruptedException {
        log.info("WAITING_FOR_CUSTOMER biletler oluşturuluyor: {}", count);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UserSession customer = FakeData.pick(customers);
            UserSession agent    = FakeData.pick(agents);

            Long ticketId = createTicket(customer);
            if (ticketId == null) continue;
            ids.add(ticketId);
            sleep();

            claimTicket(ticketId, agent);
            sleep();

            // Agent inceleme yaptı, worklog ekledi
            addWorklogsForTicket(ticketId, agent, 1 + FakeData.nextInt(2));

            // Agent müşteriden bilgi bekliyor — içeride not bırakıyor
            enqueueComment(ticketId, agent, "INTERNAL");

            // Müşteriden yanıt/aksiyon bekleniyor
            updateStatus(ticketId, "WAITING_FOR_CUSTOMER", agent);
            sleep();
        }
        return ids;
    }

    private List<Long> createResolvedTickets(int count) throws IOException, InterruptedException {
        log.info("RESOLVED biletler oluşturuluyor: {}", count);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UserSession customer = FakeData.pick(customers);
            UserSession agent    = FakeData.pick(agents);

            Long ticketId = createTicket(customer);
            if (ticketId == null) continue;
            ids.add(ticketId);
            sleep();

            claimTicket(ticketId, agent);
            sleep();

            // Worklog: araştırma ve çözüm çalışması
            int worklogCount = 1 + FakeData.nextInt(3);
            addWorklogsForTicket(ticketId, agent, worklogCount);

            enqueueComment(ticketId, agent, "EXTERNAL");

            createResolutionNote(ticketId, agent);
            sleep();

            updateStatus(ticketId, "RESOLVED", agent);
            sleep();
        }
        return ids;
    }

    private List<Long> createClosedTickets(int count) throws IOException, InterruptedException {
        log.info("CLOSED biletler oluşturuluyor: {}", count);
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UserSession customer = FakeData.pick(customers);
            UserSession agent    = FakeData.pick(agents);

            Long ticketId = createTicket(customer);
            if (ticketId == null) continue;
            ids.add(ticketId);
            sleep();

            claimTicket(ticketId, agent);
            sleep();

            // Worklog: CLOSED öncesinde eklenebilir (CLOSED sonrası yasak)
            int worklogCount = 1 + FakeData.nextInt(3);
            addWorklogsForTicket(ticketId, agent, worklogCount);

            enqueueComment(ticketId, agent,    "EXTERNAL");
            enqueueComment(ticketId, customer, "EXTERNAL");

            createResolutionNote(ticketId, agent);
            sleep();

            updateStatus(ticketId, "RESOLVED", agent);
            sleep();

            submitCsat(ticketId, customer);
            sleep();

            // Müşteri CSAT sonrası bileti kapatır
            updateStatus(ticketId, "CLOSED", customer);
            sleep();
        }
        return ids;
    }

    // ---------------------------------------------------------------
    // Yorum kuyruğu — round-robin ile gönder
    // ---------------------------------------------------------------

    private void enqueueComment(Long ticketId, UserSession user, String type) {
        commentQueues
            .computeIfAbsent(user.getUsername(), k -> new LinkedList<>())
            .add(new CommentTask(ticketId, type, user));
    }

    /**
     * Tüm kullanıcıların yorum kuyruklarını round-robin ile boşaltır.
     *
     * Her turda her kullanıcıdan bir yorum gönderilir.
     * Tur sonunda 5.5 saniye beklenir (rate limit: kullanıcı başına 5 sn).
     * Böylece N kullanıcı varsa her turda N yorum gönderilir, toplam süre
     * (toplam_yorum / N) * 5.5 saniyeye düşer.
     */
    private void flushCommentQueues() throws IOException, InterruptedException {
        int totalComments = commentQueues.values().stream()
                .mapToInt(Queue::size).sum();

        if (totalComments == 0) return;

        log.info("Yorumlar gönderiliyor: {} yorum, {} kullanıcı (round-robin)",
                totalComments, commentQueues.size());

        int sent = 0;
        while (true) {
            boolean anyLeft = false;

            for (Queue<CommentTask> queue : commentQueues.values()) {
                CommentTask task = queue.poll();
                if (task == null) continue;
                anyLeft = true;

                sendComment(task.ticketId, task.user, task.type);
                sent++;
            }

            if (!anyLeft) break;

            // Bir tur tamamlandı — rate limit için bekle
            log.debug("Yorum turu tamamlandı ({} gönderildi), {}ms bekleniyor...",
                    sent, GeneratorConfig.COMMENT_DELAY_MS);
            Thread.sleep(GeneratorConfig.COMMENT_DELAY_MS);
        }

        log.info("Tüm yorumlar gönderildi: {} yorum.", sent);
    }

    private void sendComment(Long ticketId, UserSession user, String type)
            throws IOException, InterruptedException {
        Map<String, String> body = Map.of(
            "message", FakeData.randomComment(),
            "type",    type
        );
        for (int attempt = 1; attempt <= GeneratorConfig.RATE_LIMIT_RETRY_COUNT; attempt++) {
            try {
                api.post("/tickets/" + ticketId + "/comments", body, user.getToken());
                log.debug("Yorum eklendi: #{} ({} / {})", ticketId, user.getUsername(), type);
                return;
            } catch (ApiClient.ApiException e) {
                if (e.getStatusCode() == 429 && attempt < GeneratorConfig.RATE_LIMIT_RETRY_COUNT) {
                    log.debug("Rate limit, {}ms bekleniyor (deneme {}/{})",
                        GeneratorConfig.RATE_LIMIT_BACKOFF_MS, attempt, GeneratorConfig.RATE_LIMIT_RETRY_COUNT);
                    Thread.sleep(GeneratorConfig.RATE_LIMIT_BACKOFF_MS);
                } else {
                    log.warn("Yorum eklenemedi #{}: {}", ticketId, e.getMessage());
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Worklog metodları
    // ---------------------------------------------------------------

    private void addWorklogsForTicket(Long ticketId, UserSession agent, int count)
            throws IOException, InterruptedException {
        for (int i = 0; i < count; i++) {
            addWorklog(ticketId, agent);
            sleep();
        }
    }

    private void addWorklog(Long ticketId, UserSession agent) throws IOException {
        Map<String, Object> body = new HashMap<>();
        body.put("minutes",     FakeData.randomWorklogMinutes());
        body.put("description", FakeData.randomWorklogDescription());
        try {
            api.post("/tickets/" + ticketId + "/worklogs", body, agent.getToken());
            log.debug("Worklog eklendi: #{} → {} dk ({})", ticketId,
                    body.get("minutes"), agent.getUsername());
        } catch (Exception e) {
            log.warn("Worklog eklenemedi #{}: {}", ticketId, e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Atomik işlemler
    // ---------------------------------------------------------------

    private Long createTicket(UserSession customer) throws IOException {
        Long productId = FakeData.pick(productIds);
        Map<String, Object> body = Map.of(
            "title",       FakeData.randomTitle(),
            "description", FakeData.randomDescription(),
            "priority",    FakeData.randomPriority(),
            "productId",   productId
        );
        try {
            JsonNode resp = api.post("/tickets", body, customer.getToken());
            long id = resp.get("id").asLong();
            log.debug("Bilet oluşturuldu: #{} ({})", id, customer.getUsername());
            return id;
        } catch (Exception e) {
            log.warn("Bilet oluşturulamadı ({}): {}", customer.getUsername(), e.getMessage());
            return null;
        }
    }

    private void claimTicket(Long ticketId, UserSession agent) throws IOException {
        try {
            api.put("/tickets/" + ticketId + "/claim", Map.of(), agent.getToken());
            log.debug("Claim alındı: #{} → {}", ticketId, agent.getUsername());
        } catch (Exception e) {
            log.warn("Claim alınamadı #{}: {}", ticketId, e.getMessage());
        }
    }

    private void createResolutionNote(Long ticketId, UserSession agent) throws IOException {
        Map<String, String> body = Map.of("note", FakeData.randomResolutionNote());
        try {
            api.post("/tickets/" + ticketId + "/resolution-note", body, agent.getToken());
            log.debug("Çözüm notu eklendi: #{}", ticketId);
        } catch (Exception e) {
            log.warn("Çözüm notu eklenemedi #{}: {}", ticketId, e.getMessage());
        }
    }

    private void updateStatus(Long ticketId, String status, UserSession user) throws IOException {
        Map<String, String> body = Map.of("status", status);
        try {
            api.put("/tickets/" + ticketId + "/status", body, user.getToken());
            log.debug("Statü güncellendi: #{} → {}", ticketId, status);
        } catch (Exception e) {
            log.warn("Statü güncellenemedi #{}: {}", ticketId, e.getMessage());
        }
    }

    private void submitCsat(Long ticketId, UserSession customer) throws IOException {
        Map<String, Object> body = Map.of(
            "rating",  FakeData.randomCsatRating(),
            "comment", FakeData.randomCsatComment()
        );
        try {
            api.post("/tickets/" + ticketId + "/csat", body, customer.getToken());
            log.debug("CSAT gönderildi: #{}", ticketId);
        } catch (Exception e) {
            log.warn("CSAT gönderilemedi #{}: {}", ticketId, e.getMessage());
        }
    }

    private void sleep() throws InterruptedException {
        Thread.sleep(GeneratorConfig.DELAY_MS);
    }

    // ---------------------------------------------------------------
    // Yardımcı sınıf
    // ---------------------------------------------------------------

    private record CommentTask(Long ticketId, String type, UserSession user) {}
}
