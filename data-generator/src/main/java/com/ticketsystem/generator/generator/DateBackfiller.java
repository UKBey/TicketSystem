package com.ticketsystem.generator.generator;

import com.ticketsystem.generator.config.GeneratorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
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
 *
 * <p><b>Child records.</b> The ticket's comments, worklogs, CSAT survey and audit-log rows
 * are created via the API at generation time, so their {@code created_at} all collapse onto
 * "now". After the ticket dates are written, {@link #backfillChildTimestamps} redistributes
 * those child timestamps across the ticket's real timeline — comments and worklogs spread (in
 * insertion / conversation order) between creation and resolution, the CSAT survey at the
 * close moment, and audit events anchored to the action they represent (CREATED at creation,
 * CLAIM shortly after, the RESOLVED/WAITING status changes at their respective moments). The
 * result reads like a genuine ticket history rather than a burst of "just now" activity.
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
            log.info("No tickets to backfill.");
            return;
        }

        log.info("Backfilling dates and SLA fields ({} tickets, last {} days)...",
                ticketIds.size(), GeneratorConfig.DATE_SPREAD_DAYS);

        try (Connection conn = DriverManager.getConnection(
                GeneratorConfig.DB_URL,
                GeneratorConfig.DB_USER,
                GeneratorConfig.DB_PASSWORD)) {

            conn.setAutoCommit(false);
            backfillTickets(conn, ticketIds);
            conn.commit();
            log.info("Backfill complete.");

        } catch (SQLException e) {
            log.error("Could not connect to database: {}", e.getMessage());
            log.warn("Dates and SLA fields will remain as-is.");
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

        // Her bilet için hesaplanan zaman çizelgesi çıpaları; faz 2'de (child backfill) kullanılır.
        List<Anchors> anchorsList = new ArrayList<>();

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

                anchorsList.add(new Anchors(id, status, createdAt, resolvedAt, closedAt, pausedAt));

                if (count % 50 == 0) {
                    upd.executeBatch();
                    log.debug("{} tickets updated...", count);
                }
            }

            upd.executeBatch();
            log.info("Dates and SLA fields updated for {} ticket(s).", count);
        }

        // Faz 2: child kayıtların (yorum/worklog/CSAT/audit) zaman damgalarını çizelgeye yay.
        backfillChildTimestamps(conn, anchorsList);
    }

    // ---------------------------------------------------------------
    // Child kayıt zaman damgaları (yorum / worklog / CSAT / audit)
    // ---------------------------------------------------------------

    /**
     * Per-ticket timeline anchors captured during the main backfill, consumed by
     * {@link #backfillChildTimestamps}. Any of the optional moments may be {@code null}
     * depending on status (e.g. an active ticket has no {@code resolvedAt}/{@code closedAt}).
     *
     * @param id         ticket id
     * @param status     ticket status (drives which window the children fall into)
     * @param createdAt  ticket creation moment (window start for every child)
     * @param resolvedAt resolution moment, or {@code null}
     * @param closedAt   close moment, or {@code null}
     * @param pausedAt   SLA pause moment (last active moment for WAITING tickets), or {@code null}
     */
    private record Anchors(long id, String status, ZonedDateTime createdAt,
                           ZonedDateTime resolvedAt, ZonedDateTime closedAt,
                           ZonedDateTime pausedAt) {}

    /**
     * Redistributes every ticket's child-record timestamps across its real timeline so the
     * history reads chronologically: comments and worklogs are spread (in id / conversation
     * order) between creation and resolution, the CSAT survey sits at the close moment, and
     * audit events are anchored to the action they represent.
     */
    private void backfillChildTimestamps(Connection conn, List<Anchors> anchorsList) {
        int comments = 0, worklogs = 0, csat = 0, audits = 0;
        for (Anchors a : anchorsList) {
            ZonedDateTime convEnd = conversationEnd(a);   // yorum/worklog penceresinin sonu
            try {
                if (convEnd != null) {
                    comments += spreadChildRows(conn, "ticket_comments", a.id(),
                            a.createdAt().plus(Duration.ofMinutes(2)), convEnd, false);
                    worklogs += spreadChildRows(conn, "ticket_worklogs", a.id(),
                            a.createdAt().plus(Duration.ofMinutes(5)), convEnd, true);
                }
                csat   += backfillCsat(conn, a);
                audits += backfillAuditLogs(conn, a, convEnd);
            } catch (SQLException e) {
                log.warn("Failed to backfill child timestamps for ticket #{}: {}", a.id(), e.getMessage());
            }
        }
        log.info("Child timestamps spread across timeline: {} comment(s), {} worklog(s), {} CSAT, {} audit row(s).",
                comments, worklogs, csat, audits);
    }

    /**
     * End of a ticket's conversation/work window (the latest a comment or worklog may appear):
     * the resolution moment when present, otherwise the pause moment (WAITING), otherwise "now"
     * for still-active work, and {@code null} for NEW tickets (which have no children).
     */
    private ZonedDateTime conversationEnd(Anchors a) {
        return switch (a.status()) {
            case "RESOLVED", "CLOSED" -> a.resolvedAt();
            case "WAITING_FOR_CUSTOMER" -> a.pausedAt();
            case "IN_PROGRESS" -> now();
            default -> null; // NEW — yorum/worklog yok
        };
    }

    /**
     * Spreads the {@code created_at} (and, when {@code alsoUpdatedAt}, {@code updated_at}) of a
     * ticket's rows in the given child table evenly across {@code [start, end]}, preserving id
     * order so the conversation/work sequence stays intact.
     *
     * @return number of rows updated
     */
    private int spreadChildRows(Connection conn, String table, long ticketId,
                                ZonedDateTime start, ZonedDateTime end, boolean alsoUpdatedAt)
            throws SQLException {
        List<Long> ids = childIds(conn, table, ticketId);
        if (ids.isEmpty()) return 0;

        List<ZonedDateTime> times = spread(start, end, ids.size());
        String sql = alsoUpdatedAt
                ? "UPDATE " + table + " SET created_at = ?, updated_at = ? WHERE id = ?"
                : "UPDATE " + table + " SET created_at = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) {
                Timestamp ts = toTs(times.get(i));
                int col = 1;
                ps.setTimestamp(col++, ts);
                if (alsoUpdatedAt) ps.setTimestamp(col++, ts);
                ps.setLong(col, ids.get(i));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return ids.size();
    }

    /**
     * Places the CSAT survey at the ticket's close moment (the customer's rating is what closes
     * a RESOLVED ticket). No-op when the ticket never closed or has no survey row.
     *
     * @return 1 if a survey was updated, otherwise 0
     */
    private int backfillCsat(Connection conn, Anchors a) throws SQLException {
        ZonedDateTime when = a.closedAt() != null ? a.closedAt() : a.resolvedAt();
        if (when == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE csat_surveys SET created_at = ? WHERE ticket_id = ?")) {
            ps.setTimestamp(1, toTs(when));
            ps.setLong(2, a.id());
            return ps.executeUpdate();
        }
    }

    /**
     * Anchors each audit-log row to the moment of the action it records (CREATED at creation,
     * CLAIM/ASSIGN shortly after, the RESOLVED/CLOSED/WAITING status changes at their moments;
     * anything else proportionally in the window), then enforces strictly increasing timestamps
     * by id so the recorded order is never violated.
     *
     * @return number of audit rows updated
     */
    private int backfillAuditLogs(Connection conn, Anchors a, ZonedDateTime convEnd)
            throws SQLException {
        String selSql = "SELECT id, action_type, new_state FROM ticket_audit_logs "
                + "WHERE ticket_id = ? ORDER BY id";
        List<long[]> rows = new ArrayList<>();       // [id]
        List<String> actions = new ArrayList<>();
        List<String> newStates = new ArrayList<>();
        try (PreparedStatement sel = conn.prepareStatement(selSql)) {
            sel.setLong(1, a.id());
            try (ResultSet rs = sel.executeQuery()) {
                while (rs.next()) {
                    rows.add(new long[]{rs.getLong("id")});
                    actions.add(rs.getString("action_type"));
                    newStates.add(rs.getString("new_state"));
                }
            }
        }
        if (rows.isEmpty()) return 0;

        // Audit penceresinin sonu: kapanış/çözüm varsa o, yoksa konuşma sonu, yoksa şimdi.
        ZonedDateTime auditEnd = firstNonNull(a.closedAt(), a.resolvedAt(), convEnd, now());

        long prevMs = a.createdAt().toInstant().toEpochMilli() - 1000;
        long endMs  = auditEnd.toInstant().toEpochMilli();
        try (PreparedStatement upd = conn.prepareStatement(
                "UPDATE ticket_audit_logs SET created_at = ? WHERE id = ?")) {
            for (int i = 0; i < rows.size(); i++) {
                ZonedDateTime target = auditTarget(a, actions.get(i), newStates.get(i), auditEnd);
                long t = target.toInstant().toEpochMilli();
                if (t <= prevMs) t = prevMs + 1000;            // id sırası = zaman sırası
                if (t > endMs)   t = endMs;
                prevMs = t;
                upd.setTimestamp(1, toTs(ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneOffset.UTC)));
                upd.setLong(2, rows.get(i)[0]);
                upd.addBatch();
            }
            upd.executeBatch();
        }
        return rows.size();
    }

    /** Maps an audit action to the timeline moment it should carry. */
    private ZonedDateTime auditTarget(Anchors a, String action, String newState,
                                      ZonedDateTime auditEnd) {
        String act = action == null ? "" : action.toUpperCase();
        String ns  = newState == null ? "" : newState.toUpperCase();
        return switch (act) {
            case "CREATED" -> a.createdAt();
            // İlk müdahale: oluşturmadan 5–90 dk sonra.
            case "CLAIM", "ASSIGN" -> a.createdAt().plus(Duration.ofMillis(randLong(300_000L, 5_400_000L)));
            case "UNCLAIM" -> a.createdAt().plus(Duration.ofMillis(randLong(600_000L, 7_200_000L)));
            case "STATUS_CHANGE", "REOPEN" -> switch (ns) {
                case "RESOLVED" -> firstNonNull(a.resolvedAt(), auditEnd);
                case "CLOSED"   -> firstNonNull(a.closedAt(), a.resolvedAt(), auditEnd);
                case "WAITING_FOR_CUSTOMER" -> firstNonNull(a.pausedAt(), midpoint(a.createdAt(), auditEnd));
                case "IN_PROGRESS" -> a.createdAt().plus(Duration.ofMillis(randLong(300_000L, 5_400_000L)));
                default -> midpoint(a.createdAt(), auditEnd);
            };
            default -> midpoint(a.createdAt(), auditEnd);
        };
    }

    /** Ids of a ticket's rows in a child table, ordered by id (= insertion order). */
    private List<Long> childIds(Connection conn, String table, long ticketId) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM " + table + " WHERE ticket_id = ? ORDER BY id")) {
            ps.setLong(1, ticketId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    /**
     * {@code n} strictly increasing timestamps spread across {@code (start, end)} with light
     * jitter. Degenerate windows ({@code end <= start}) fall back to 1-minute steps from start.
     */
    private List<ZonedDateTime> spread(ZonedDateTime start, ZonedDateTime end, int n) {
        List<ZonedDateTime> out = new ArrayList<>(n);
        long startMs = start.toInstant().toEpochMilli();
        long endMs   = end.toInstant().toEpochMilli();
        if (endMs <= startMs) endMs = startMs + n * 60_000L; // güvenlik: dakikalık adımlar
        long step = (endMs - startMs) / (n + 1);
        long prev = startMs;
        for (int i = 1; i <= n; i++) {
            long base = startMs + step * i;
            long jitter = (long) ((RNG.nextDouble() - 0.5) * Math.max(1, step / 3));
            long t = base + jitter;
            if (t <= prev)  t = prev + 1000;
            if (t >= endMs) t = endMs - 1;
            prev = t;
            out.add(ZonedDateTime.ofInstant(Instant.ofEpochMilli(t), ZoneOffset.UTC));
        }
        return out;
    }

    private ZonedDateTime midpoint(ZonedDateTime a, ZonedDateTime b) {
        long mid = (a.toInstant().toEpochMilli() + b.toInstant().toEpochMilli()) / 2;
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(mid), ZoneOffset.UTC);
    }

    @SafeVarargs
    private final ZonedDateTime firstNonNull(ZonedDateTime... candidates) {
        for (ZonedDateTime c : candidates) if (c != null) return c;
        return now();
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
