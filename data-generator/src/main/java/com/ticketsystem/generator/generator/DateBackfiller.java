package com.ticketsystem.generator.generator;

import com.ticketsystem.generator.config.GeneratorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * API üzerinden oluşturulan biletlerin tarihlerini
 * doğrudan PostgreSQL'e yazarak geriye çeker.
 *
 * Biletler son DATE_SPREAD_DAYS gün içinde rastgele dağıtılır.
 * Durum geçişleri mantıksal sırayı korur:
 *   created_at < resolved_at < closed_at
 */
public class DateBackfiller {

    private static final Logger log = LoggerFactory.getLogger(DateBackfiller.class);
    private static final Random RNG = new Random();

    public void backfill(List<Long> ticketIds) {
        if (ticketIds.isEmpty()) {
            log.info("Geriye çekilecek bilet yok.");
            return;
        }

        log.info("Tarihler geriye çekiliyor ({} bilet, son {} gün)...",
                ticketIds.size(), GeneratorConfig.DATE_SPREAD_DAYS);

        try (Connection conn = DriverManager.getConnection(
                GeneratorConfig.DB_URL,
                GeneratorConfig.DB_USER,
                GeneratorConfig.DB_PASSWORD)) {

            conn.setAutoCommit(false);
            backfillTickets(conn, ticketIds);
            conn.commit();
            log.info("Tarih geriye çekme tamamlandı.");

        } catch (SQLException e) {
            log.error("Veritabanı bağlantısı kurulamadı: {}", e.getMessage());
            log.warn("Tarihler güncel kalacak — biletler bugünün tarihi ile görünecek.");
        }
    }

    private void backfillTickets(Connection conn, List<Long> ticketIds) throws SQLException {
        // Biletlerin mevcut durumlarını çek
        String selectSql = "SELECT id, status, priority FROM tickets WHERE id = ANY(?)";
        String updateSql = "UPDATE tickets SET created_at = ?, sla_deadline = ?, " +
                           "resolved_at = ?, closed_at = ? WHERE id = ?";

        List<Long> idList = new ArrayList<>(ticketIds);
        Array sqlArray = conn.createArrayOf("bigint",
                idList.stream().map(Object.class::cast).toArray());

        try (PreparedStatement sel = conn.prepareStatement(selectSql);
             PreparedStatement upd = conn.prepareStatement(updateSql)) {

            sel.setArray(1, sqlArray);
            ResultSet rs = sel.executeQuery();

            int count = 0;
            while (rs.next()) {
                long id       = rs.getLong("id");
                String status   = rs.getString("status");
                String priority = rs.getString("priority");

                ZonedDateTime createdAt   = randomPastDate(GeneratorConfig.DATE_SPREAD_DAYS);
                ZonedDateTime slaDeadline = createdAt.plusHours(slaHoursForPriority(priority));
                ZonedDateTime resolvedAt = null;
                ZonedDateTime closedAt   = null;

                if ("RESOLVED".equals(status) || "CLOSED".equals(status)) {
                    // Çözüm: oluşturma + 1-48 saat sonra
                    resolvedAt = createdAt.plusHours(1 + RNG.nextInt(48));
                }
                if ("CLOSED".equals(status) && resolvedAt != null) {
                    // Kapanış: çözüm + 1-24 saat sonra
                    closedAt = resolvedAt.plusHours(1 + RNG.nextInt(24));
                }

                upd.setTimestamp(1, toTimestamp(createdAt));
                upd.setTimestamp(2, toTimestamp(slaDeadline));
                upd.setTimestamp(3, resolvedAt != null ? toTimestamp(resolvedAt) : null);
                upd.setTimestamp(4, closedAt   != null ? toTimestamp(closedAt)   : null);
                upd.setLong(5, id);
                upd.addBatch();
                count++;

                if (count % 50 == 0) {
                    upd.executeBatch();
                    log.debug("{} bilet güncellendi...", count);
                }
            }

            upd.executeBatch();
            log.info("Toplam {} biletin tarihi güncellendi.", count);
        }
    }

    /**
     * Son {@code days} gün içinde rastgele bir zaman döner.
     */
    private ZonedDateTime randomPastDate(int days) {
        long nowEpoch    = ZonedDateTime.now(ZoneOffset.UTC).toEpochSecond();
        long daysInSecs  = (long) days * 24 * 3600;
        long randomSecs  = (long) (RNG.nextDouble() * daysInSecs);
        return ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(nowEpoch - randomSecs),
                ZoneOffset.UTC);
    }

    /**
     * Bilet önceliğine göre SLA süresi (saat) — WorkflowService ile aynı değerler.
     * LOW=48, MEDIUM=12, HIGH=4, CRITICAL=1
     */
    private int slaHoursForPriority(String priority) {
        if (priority == null) return 12;
        return switch (priority.toUpperCase()) {
            case "LOW"      -> 48;
            case "MEDIUM"   -> 12;
            case "HIGH"     ->  4;
            case "CRITICAL" ->  1;
            default         -> 12;
        };
    }

    private Timestamp toTimestamp(ZonedDateTime zdt) {
        return zdt != null ? Timestamp.from(zdt.toInstant()) : null;
    }
}
