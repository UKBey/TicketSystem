package com.ticketsystem.generator.generator;

import com.ticketsystem.generator.config.GeneratorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * API üzerinden oluşturulan biletlerin tarihlerini ve SLA alanlarını
 * doğrudan PostgreSQL'e yazarak gerçekçi tarihsel veri üretir.
 *
 * Durum bazlı SLA backfill stratejisi:
 *   NEW               — SLA aktif, son (sla_duration * 0-80%) süresi içinde oluşturulmuş,
 *                        deadline gelecekte görünür.
 *   IN_PROGRESS       — SLA aktif, agent kısa süre önce claim almış;
 *                        sla_resumed_at son (remaining * 0-70%) içinde ayarlanır.
 *   WAITING_FOR_CUSTOMER — SLA duraklatılmış, bütçenin %20-75'i harcanmış.
 *   RESOLVED          — SLA duraklatılmış, bütçenin %30-95'i harcanmış.
 *   CLOSED            — SLA duraklatılmış (RESOLVED'dakiyle aynı), süreç tamamlanmış.
 */
public class DateBackfiller {

    private static final Logger log = LoggerFactory.getLogger(DateBackfiller.class);
    private static final Random RNG = new Random();

    /**
     * Verilen bilet ID'lerinin {@code created_at} ve SLA alanlarını ({@code sla_deadline},
     * {@code sla_elapsed_ms}, {@code sla_paused_at}, {@code sla_resumed_at},
     * {@code resolved_at}, {@code closed_at}) doğrudan PostgreSQL'e yazar.
     *
     * <p>Her bileti statüsüne göre farklı stratejiyle backfill eder; bağlantı
     * hatasında işlem atlanır ve uyarı log'lanır ({@link Exception} fırlatılmaz).
     *
     * @param ticketIds güncellenecek bilet ID'leri; boş liste no-op
     */
    public void backfill(List<Long> ticketIds) {
        if (ticketIds.isEmpty()) {
            log.info("Geriye çekilecek bilet yok.");
            return;
        }

        log.info("Tarihler ve SLA alanları güncelleniyor ({} bilet, son {} gün)...",
                ticketIds.size(), GeneratorConfig.DATE_SPREAD_DAYS);

        try (Connection conn = DriverManager.getConnection(
                GeneratorConfig.DB_URL,
                GeneratorConfig.DB_USER,
                GeneratorConfig.DB_PASSWORD)) {

            conn.setAutoCommit(false);
            backfillTickets(conn, ticketIds);
            conn.commit();
            log.info("Güncelleme tamamlandı.");

        } catch (SQLException e) {
            log.error("Veritabanı bağlantısı kurulamadı: {}", e.getMessage());
            log.warn("Tarihler ve SLA alanları güncel kalacak.");
        }
    }

    private void backfillTickets(Connection conn, List<Long> ticketIds) throws SQLException {
        String selectSql = "SELECT id, status, priority FROM tickets WHERE id = ANY(?)";
        String updateSql = """
                UPDATE tickets
                   SET created_at     = ?,
                       sla_deadline   = ?,
                       sla_elapsed_ms = ?,
                       sla_paused_at  = ?,
                       sla_resumed_at = ?,
                       resolved_at    = ?,
                       closed_at      = ?
                 WHERE id = ?
                """;

        List<Long> idList = new ArrayList<>(ticketIds);
        Array sqlArray = conn.createArrayOf("bigint",
                idList.stream().map(Object.class::cast).toArray());

        try (PreparedStatement sel = conn.prepareStatement(selectSql);
             PreparedStatement upd = conn.prepareStatement(updateSql)) {

            sel.setArray(1, sqlArray);
            ResultSet rs = sel.executeQuery();

            int count = 0;
            while (rs.next()) {
                long   id       = rs.getLong("id");
                String status   = rs.getString("status");
                String priority = rs.getString("priority");

                long durationMs = slaHoursForPriority(priority) * 3_600_000L;

                // --- Ortak: created_at ---
                ZonedDateTime createdAt;
                ZonedDateTime resolvedAt  = null;
                ZonedDateTime closedAt    = null;
                long          elapsedMs   = 0L;
                ZonedDateTime pausedAt    = null;
                ZonedDateTime resumedAt   = null;

                switch (status) {
                    case "NEW" -> {
                        // created_at: SLA'nın bitmemesi için son (duration * 0–80%) içinde
                        long maxAgeMs = (long) (durationMs * 0.8);
                        createdAt = now().minus(Duration.ofMillis(randLong(0, maxAgeMs)));
                        elapsedMs = 0L;
                        // pausedAt, resumedAt → null (aktif sayaç createdAt'ten başlar)
                    }
                    case "IN_PROGRESS" -> {
                        // Tarihsel oluşturma tarihi
                        createdAt = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS);
                        // Agent kısa süre önce claim aldı; bütçenin %5-35'ini harcadı
                        double elapsedRatio = 0.05 + RNG.nextDouble() * 0.30;
                        elapsedMs = (long) (durationMs * elapsedRatio);
                        long remainingMs = durationMs - elapsedMs;
                        // sla_resumed_at: deadline gelecekte olacak şekilde ayarla
                        long resumeOffsetMs = (long) (remainingMs * RNG.nextDouble() * 0.70);
                        resumedAt = now().minus(Duration.ofMillis(resumeOffsetMs));
                        // pausedAt → null (aktif)
                    }
                    case "WAITING_FOR_CUSTOMER" -> {
                        createdAt = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS);
                        // Agent bütçenin %20-75'ini harcadı, sonra müşteriden bilgi istedi
                        double elapsedRatio = 0.20 + RNG.nextDouble() * 0.55;
                        elapsedMs = (long) (durationMs * elapsedRatio);
                        pausedAt  = createdAt.plus(Duration.ofMillis(elapsedMs));
                    }
                    case "RESOLVED" -> {
                        createdAt  = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS);
                        resolvedAt = createdAt.plusHours(1 + RNG.nextInt(48));
                        double elapsedRatio = 0.30 + RNG.nextDouble() * 0.65;
                        elapsedMs = (long) (durationMs * elapsedRatio);
                        pausedAt  = resolvedAt; // SLA çözüm anında duraklatıldı
                    }
                    case "CLOSED" -> {
                        createdAt  = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS);
                        resolvedAt = createdAt.plusHours(1 + RNG.nextInt(48));
                        closedAt   = resolvedAt.plusHours(1 + RNG.nextInt(24));
                        double elapsedRatio = 0.30 + RNG.nextDouble() * 0.65;
                        elapsedMs = (long) (durationMs * elapsedRatio);
                        pausedAt  = resolvedAt; // RESOLVED'dan gelen duraklama korunur
                    }
                    default -> {
                        createdAt = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS);
                    }
                }

                ZonedDateTime slaDeadline = createdAt.plusHours(slaHoursForPriority(priority));

                upd.setTimestamp(1, toTs(createdAt));
                upd.setTimestamp(2, toTs(slaDeadline));
                upd.setLong     (3, elapsedMs);
                upd.setTimestamp(4, toTs(pausedAt));
                upd.setTimestamp(5, toTs(resumedAt));
                upd.setTimestamp(6, toTs(resolvedAt));
                upd.setTimestamp(7, toTs(closedAt));
                upd.setLong     (8, id);
                upd.addBatch();
                count++;

                if (count % 50 == 0) {
                    upd.executeBatch();
                    log.debug("{} bilet güncellendi...", count);
                }
            }

            upd.executeBatch();
            log.info("Toplam {} biletin tarihi ve SLA alanları güncellendi.", count);
        }
    }

    // ---------------------------------------------------------------
    // Yardımcı metodlar
    // ---------------------------------------------------------------

    private ZonedDateTime randomPastDate(int days) {
        long nowEpoch   = now().toEpochSecond();
        long daysInSecs = (long) days * 24 * 3600;
        long randomSecs = (long) (RNG.nextDouble() * daysInSecs);
        return ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(nowEpoch - randomSecs),
                ZoneOffset.UTC);
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    private long randLong(long min, long max) {
        return min + (long) (RNG.nextDouble() * (max - min));
    }

    /**
     * Bilet önceliğine göre SLA süresi (saat) — WorkflowService ile senkron.
     */
    private int slaHoursForPriority(String priority) {
        if (priority == null) return 12;
        return switch (priority.toUpperCase()) {
            case "LOW"      -> 24;
            case "MEDIUM"   -> 12;
            case "HIGH"     ->  4;
            case "CRITICAL" ->  1;
            default         -> 12;
        };
    }

    private Timestamp toTs(ZonedDateTime zdt) {
        return zdt != null ? Timestamp.from(zdt.toInstant()) : null;
    }
}
