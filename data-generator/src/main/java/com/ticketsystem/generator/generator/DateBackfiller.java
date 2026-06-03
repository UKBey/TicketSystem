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
 * Generates realistic historical data by writing the dates and SLA fields of
 * tickets created via the API directly into PostgreSQL.
 *
 * Status-based SLA backfill strategy:
 *   NEW               — SLA active, created within the last (sla_duration * 0-80%),
 *                        deadline appears in the future.
 *   IN_PROGRESS       — SLA active, the agent claimed recently;
 *                        sla_resumed_at is set within the last (remaining * 0-70%).
 *   WAITING_FOR_CUSTOMER — SLA paused, 20-75% of the budget spent.
 *   RESOLVED          — SLA paused, 30-95% of the budget spent.
 *   CLOSED            — SLA paused (same as RESOLVED), workflow completed.
 */
public class DateBackfiller {

    private static final Logger log = LoggerFactory.getLogger(DateBackfiller.class);
    private static final Random RNG = new Random();

    /** Fraction of tickets that intentionally breach their SLA (realistic demo mix). */
    private static final double BREACH_RATE = 0.30;

    /**
     * Writes the {@code created_at} and SLA fields ({@code sla_deadline},
     * {@code sla_elapsed_ms}, {@code sla_paused_at}, {@code sla_resumed_at},
     * {@code resolved_at}, {@code closed_at}) of the given ticket IDs directly
     * into PostgreSQL.
     *
     * <p>Each ticket is backfilled with a different strategy depending on its status;
     * on a connection error the operation is skipped and a warning is logged (no
     * {@link Exception} is thrown).
     *
     * @param ticketIds ticket IDs to update; an empty list is a no-op
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
                       closed_at      = ?,
                       sla_breached   = ?
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

                ZonedDateTime createdAt;
                ZonedDateTime slaDeadline;
                ZonedDateTime resolvedAt  = null;
                ZonedDateTime closedAt    = null;
                long          elapsedMs   = 0L;
                ZonedDateTime pausedAt    = null;
                ZonedDateTime resumedAt   = null;
                // Gerçekçi karışım: biletlerin ~%30'u SLA ihlal eder. Tarihler ve sla_breached
                // birbiriyle TUTARLI kurulur (canlı SLA hesabı ve dashboard ile uyumlu olsun).
                // WAITING_FOR_CUSTOMER hariç: saat duraklı olduğu için ihlal edemez.
                boolean breached = RNG.nextDouble() < BREACH_RATE;

                switch (status) {
                    case "NEW" -> {
                        // Aktif, hiç duraklatılmadı; canlı SLA stored sla_deadline'ı kullanır.
                        if (breached) {
                            // (1.05–1.8)*duration önce oluşturuldu → deadline geçmişte → expired
                            createdAt = now().minus(Duration.ofMillis(
                                    randLong((long) (durationMs * 1.05), (long) (durationMs * 1.8))));
                        } else {
                            // son (0–%80 duration) içinde → deadline gelecekte → sağlıklı
                            createdAt = now().minus(Duration.ofMillis(randLong(0, (long) (durationMs * 0.8))));
                        }
                        slaDeadline = createdAt.plus(Duration.ofMillis(durationMs));
                    }
                    case "IN_PROGRESS" -> {
                        // Bütçenin %5-35'i aktif harcandı; kalanı resume noktasından sayılır.
                        elapsedMs = (long) (durationMs * (0.05 + RNG.nextDouble() * 0.30));
                        long remainingMs = durationMs - elapsedMs;
                        // Deadline'ı doğrudan konumlandır — canlı SLA aktif biletlerde stored
                        // sla_deadline'ı kullandığı için bu, expired/active'i belirler.
                        if (breached) {
                            slaDeadline = now().minus(Duration.ofMillis(
                                    randLong((long) (durationMs * 0.05), (long) (durationMs * 0.5))));
                        } else {
                            slaDeadline = now().plus(Duration.ofMillis(
                                    randLong((long) (remainingMs * 0.1), (long) (remainingMs * 0.9))));
                        }
                        resumedAt = slaDeadline.minus(Duration.ofMillis(remainingMs)); // deadline = resume + kalan
                        // created, resume'dan önce: aktif çalışma + 1-24h duraklama boşluğu kadar.
                        createdAt = resumedAt.minus(Duration.ofMillis(elapsedMs + randLong(3_600_000L, 86_400_000L)));
                    }
                    case "WAITING_FOR_CUSTOMER" -> {
                        // Saat duraklı: bekleyen bilet ihlal edemez (kalan > 0). Daima sağlıklı.
                        breached  = false;
                        elapsedMs = (long) (durationMs * (0.20 + RNG.nextDouble() * 0.55)); // %20-75
                        pausedAt  = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS);        // son 7 gün içinde duraklatıldı
                        createdAt = pausedAt.minus(Duration.ofMillis(elapsedMs));            // aktif süre kadar önce açıldı
                        slaDeadline = pausedAt.plus(Duration.ofMillis(durationMs - elapsedMs));
                    }
                    case "RESOLVED" -> {
                        // breached → bütçe aşıldı (resolved > deadline); değilse bütçe içinde.
                        elapsedMs = breached
                                ? (long) (durationMs * (1.05 + RNG.nextDouble() * 0.55))
                                : (long) (durationMs * (0.15 + RNG.nextDouble() * 0.75));
                        resolvedAt  = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS); // son 7 gün içinde çözüldü
                        createdAt   = resolvedAt.minus(Duration.ofMillis(elapsedMs));   // aktif süre kadar önce açıldı
                        pausedAt    = resolvedAt;                                        // SLA çözümde duraklatıldı
                        slaDeadline = createdAt.plus(Duration.ofMillis(durationMs));     // breached ⟺ resolvedAt > deadline
                    }
                    case "CLOSED" -> {
                        elapsedMs = breached
                                ? (long) (durationMs * (1.05 + RNG.nextDouble() * 0.55))
                                : (long) (durationMs * (0.15 + RNG.nextDouble() * 0.75));
                        closedAt    = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS); // son 7 gün içinde kapandı
                        resolvedAt  = closedAt.minus(Duration.ofMillis(randLong(3_600_000L, 86_400_000L))); // 1-24h önce çözüldü
                        createdAt   = resolvedAt.minus(Duration.ofMillis(elapsedMs));
                        pausedAt    = resolvedAt;
                        slaDeadline = createdAt.plus(Duration.ofMillis(durationMs));
                    }
                    default -> {
                        breached    = false;
                        createdAt   = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS);
                        slaDeadline = createdAt.plus(Duration.ofMillis(durationMs));
                    }
                }

                upd.setTimestamp(1, toTs(createdAt));
                upd.setTimestamp(2, toTs(slaDeadline));
                upd.setLong     (3, elapsedMs);
                upd.setTimestamp(4, toTs(pausedAt));
                upd.setTimestamp(5, toTs(resumedAt));
                upd.setTimestamp(6, toTs(resolvedAt));
                upd.setTimestamp(7, toTs(closedAt));
                upd.setBoolean  (8, breached);
                upd.setLong     (9, id);
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
     * SLA duration (hours) by ticket priority. MUST mirror the backend SLA policy
     * ({@code app.sla.policies} in application.yml / {@code SlaPolicyService}) —
     * otherwise the backfilled {@code sla_deadline} / {@code sla_breached} values
     * disagree with the live SLA computation and the dashboard.
     */
    private int slaHoursForPriority(String priority) {
        if (priority == null) return 24;
        return switch (priority.toUpperCase()) {
            case "LOW"      -> 72;
            case "MEDIUM"   -> 24;
            case "HIGH"     ->  4;
            case "CRITICAL" ->  1;
            default         -> 24;
        };
    }

    private Timestamp toTs(ZonedDateTime zdt) {
        return zdt != null ? Timestamp.from(zdt.toInstant()) : null;
    }
}
