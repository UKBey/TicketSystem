package com.ticketsystem.llmservice.service;

import com.ticketsystem.llmservice.dto.SummarizeRequestDTO;
import com.ticketsystem.llmservice.dto.TicketDataDTO;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Ticket verisinden LLM prompt'u oluşturur.
 */
@Component
public class PromptBuilder {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * System prompt — LLM'e rolünü ve beklenen çıktı formatını tanımlar.
     */
    public String buildSystemPrompt(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return """
                    You are an IT support ticket analyst. Your task is to produce a concise, \
                    structured summary of a support ticket based on the provided data.
                    
                    The summary must include:
                    1. **Problem**: What issue was reported and by whom.
                    2. **Current Status**: Current ticket status and SLA situation.
                    3. **Actions Taken**: Key steps taken by agents (comments, worklogs, status changes).
                    4. **Resolution**: Resolution note if available, or current progress.
                    5. **Recommendation**: If the ticket is still open, suggest the next best action.
                    
                    Be concise. Use bullet points where appropriate. Do not repeat raw data verbatim.
                    """;
        }
        // Varsayılan: Türkçe
        return """
                Sen bir IT destek bileti analistsin. Görevin, sağlanan veriye dayanarak bir destek \
                biletinin kısa ve yapılandırılmış bir özetini üretmektir.
                
                Özet şu bölümleri içermelidir:
                1. **Sorun**: Hangi sorunun kim tarafından bildirildiği.
                2. **Güncel Durum**: Biletin mevcut durumu ve SLA durumu.
                3. **Yapılan İşlemler**: Ajanların attığı önemli adımlar (yorumlar, worklog'lar, durum değişiklikleri).
                4. **Çözüm**: Varsa çözüm notu, yoksa mevcut ilerleme.
                5. **Öneri**: Bilet hâlâ açıksa, yapılması gereken bir sonraki adımı öner.
                
                Kısa ve öz ol. Uygun yerlerde madde işareti kullan. Ham veriyi olduğu gibi tekrarlama.
                """;
    }

    /**
     * Ticket verisini okunabilir bir metin bloğuna dönüştürür.
     */
    public String buildUserPrompt(SummarizeRequestDTO req) {
        TicketDataDTO t = req.getTicket();
        StringBuilder sb = new StringBuilder();

        sb.append("=== BİLET VERİSİ ===\n\n");

        // Temel bilgiler
        sb.append("ID: ").append(t.getId()).append("\n");
        sb.append("Başlık: ").append(t.getTitle()).append("\n");
        sb.append("Açıklama: ").append(t.getDescription()).append("\n");
        sb.append("Durum: ").append(t.getStatus()).append("\n");
        sb.append("Öncelik: ").append(t.getPriority()).append("\n");
        sb.append("Ürün/Kategori: ").append(t.getProductName()).append("\n");
        sb.append("Müşteri: ").append(t.getCustomerName()).append("\n");

        if (t.getCreatedAt() != null) {
            sb.append("Oluşturulma: ").append(t.getCreatedAt().format(FMT)).append("\n");
        }
        if (t.getResolvedAt() != null) {
            sb.append("Çözüldü: ").append(t.getResolvedAt().format(FMT)).append("\n");
        }
        if (t.getClosedAt() != null) {
            sb.append("Kapatıldı: ").append(t.getClosedAt().format(FMT)).append("\n");
        }

        // SLA
        sb.append("SLA İhlali: ").append(Boolean.TRUE.equals(t.getSlaBreached()) ? "EVET" : "Hayır").append("\n");
        if (t.getSlaDeadline() != null) {
            sb.append("SLA Deadline: ").append(t.getSlaDeadline().format(FMT)).append("\n");
        }

        // Ajanlar
        if (t.getClaimers() != null && !t.getClaimers().isEmpty()) {
            sb.append("Atanan Ajanlar: ");
            t.getClaimers().forEach(c -> sb.append(c.getAgentName()).append(", "));
            sb.append("\n");
        }

        // Yorumlar
        List<TicketDataDTO.CommentInfo> comments = req.getComments();
        if (comments != null && !comments.isEmpty()) {
            sb.append("\n--- YORUMLAR (").append(comments.size()).append(" adet) ---\n");
            for (TicketDataDTO.CommentInfo c : comments) {
                sb.append("[").append(c.getType()).append("] ");
                if (c.getCreatedAt() != null) {
                    sb.append(c.getCreatedAt().format(FMT)).append(" ");
                }
                sb.append(c.getAuthorName()).append(": ").append(c.getMessage()).append("\n");
            }
        }

        // Worklog'lar
        List<TicketDataDTO.WorklogInfo> worklogs = req.getWorklogs();
        if (worklogs != null && !worklogs.isEmpty()) {
            int totalMinutes = worklogs.stream().mapToInt(w -> w.getMinutes() != null ? w.getMinutes() : 0).sum();
            sb.append("\n--- WORKLOG'LAR (Toplam: ").append(totalMinutes).append(" dk) ---\n");
            for (TicketDataDTO.WorklogInfo w : worklogs) {
                if (w.getCreatedAt() != null) {
                    sb.append(w.getCreatedAt().format(FMT)).append(" ");
                }
                sb.append(w.getMinutes()).append(" dk");
                if (w.getDescription() != null && !w.getDescription().isBlank()) {
                    sb.append(": ").append(w.getDescription());
                }
                sb.append("\n");
            }
        }

        // Çözüm notu
        TicketDataDTO.ResolutionNoteInfo note = req.getResolutionNote();
        if (note != null && note.getNote() != null) {
            sb.append("\n--- ÇÖZÜM NOTU ---\n");
            sb.append(note.getNote()).append("\n");
        }

        // Audit log (son 10 kayıt)
        if (t.getAuditLogs() != null && !t.getAuditLogs().isEmpty()) {
            sb.append("\n--- AKSİYON GEÇMİŞİ (son ").append(Math.min(t.getAuditLogs().size(), 10)).append(" kayıt) ---\n");
            t.getAuditLogs().stream().limit(10).forEach(a -> {
                if (a.getCreatedAt() != null) {
                    sb.append(a.getCreatedAt().format(FMT)).append(" ");
                }
                sb.append(a.getActionType());
                if (a.getPreviousState() != null && a.getNewState() != null) {
                    sb.append(" (").append(a.getPreviousState()).append(" → ").append(a.getNewState()).append(")");
                }
                if (a.getNote() != null && !a.getNote().isBlank()) {
                    sb.append(": ").append(a.getNote());
                }
                sb.append("\n");
            });
        }

        return sb.toString();
    }
}
