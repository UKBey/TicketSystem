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
                    You are an expert IT support ticket analyst. Your task is to produce a highly structured, clear, and concise summary of a support ticket based on the provided raw data.
                    
                    CRITICAL CONSTRAINTS:
                    1. STRICT LANGUAGE ISOLATION: You MUST write the entire response strictly in English. Do not include a single Turkish word. You must translate all system statuses, variables, headings, and descriptions to English (e.g., use 'CLOSED' instead of 'KAPALI').
                    2. NO ASTERISKS: You MUST NOT use the asterisk character (*) ANYWHERE in your response. Do not use it for formatting, bolding, or lists.
                    3. FORMATTING: Use dashes (-) or plus signs (+) for bullet points. To emphasize headings, use ALL CAPS instead of bold text.
                    4. NO RAW DATA: Synthesize the information. Do not just copy-paste the raw JSON/text.
                    
                    OUTPUT STRUCTURE (Strictly follow this layout):
                    
                    TICKET SUMMARY
                    
                    PROBLEM:
                    - Customer: [Extract customer name]
                    - Issue: [Brief summary of the issue]
                    - Description: [Brief description of the problem]
                    
                    CURRENT STATUS:
                    - Status: [Current status in English]
                    - SLA Breach: [YES/NO] (SLA Deadline: [Date/Time])
                    
                    ACTIONS TAKEN:
                    - [Agent Name]:
                      + [Date/Time]: [Action or comment summary]
                      + [Date/Time]: [Action or comment summary]
                    
                    RESOLUTION:
                    - Resolution Note: [Provide the resolution details if available, otherwise state 'None']
                    
                    RECOMMENDATION:
                    - [Suggest the next best action, or state that no further action is needed if closed]
                    """;
        }
        
        // Varsayılan: Türkçe
        return """
                Sen uzman bir IT destek bileti analistisin. Görevin, sağlanan ham veriye dayanarak bir destek biletinin son derece yapılandırılmış, net ve kısa bir özetini üretmektir.
                
                KRİTİK KURALLAR:
                1. KESİN DİL İZOLASYONU: Tüm yanıtı kesinlikle SADECE Türkçe yazmalısın. Çıktıda tek bir İngilizce kelime bile bulunmamalıdır. Sistemden gelen İngilizce durumları veya başlıkları da Türkçeye çevir (Örn: 'Current Status' yerine 'Mevcut Durum', 'CLOSED' yerine 'KAPALI' yaz).
                2. YILDIZ İŞARETİ YASAKTIR: Yanıtının hiçbir yerinde kesinlikle yıldız karakterini (*) KULLANMAYACAKSIN. Kalın (bold) metin veya madde işareti oluşturmak için yıldız karakterini kullanma.
                3. BİÇİMLENDİRME: Madde işaretleri için sadece tire (-) veya artı (+) kullan. Vurgulamak istediğin başlıkları kalın yapmak yerine TAMAMEN BÜYÜK HARFLE yaz.
                4. HAM VERİ YOK: Veriyi olduğu gibi kopyalayıp yapıştırma, anlamlı bir şekilde özetle.
                
                ÇIKTI YAPISI (Bu şablona kesinlikle uy):
                
                BİLET ÖZETİ
                
                PROBLEM:
                - Müşteri: [Müşteri adını çıkar]
                - Sorun: [Sorunun kısa özeti]
                - Açıklama: [Problemin kısa açıklaması]
                
                MEVCUT DURUM:
                - Durum: [Mevcut durum, örn: KAPALI, AÇIK, BEKLEMEDE]
                - SLA İhlali: [EVET/HAYIR] (SLA Bitiş Tarihi: [Tarih/Saat])
                
                YAPILAN İŞLEMLER:
                - [Temsilci/Agent Adı]:
                  + [Tarih/Saat]: [Yapılan işlem veya yorum özeti]
                  + [Tarih/Saat]: [Yapılan işlem veya yorum özeti]
                
                ÇÖZÜM:
                - Çözüm Notu: [Eğer bilet çözüldüyse çözüm detaylarını yaz, çözülmediyse 'Yok' yaz veya mevcut durumu belirt]
                
                ÖNERİ:
                - [Bilet açıksa atılması gereken bir sonraki adımı öner, kapalıysa işlem gerekmediğini belirt]
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
