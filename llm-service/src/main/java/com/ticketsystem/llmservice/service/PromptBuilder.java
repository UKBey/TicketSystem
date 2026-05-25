package com.ticketsystem.llmservice.service;

import com.ticketsystem.llmservice.dto.SummarizeRequestDTO;
import com.ticketsystem.llmservice.dto.TicketDataDTO;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds the LLM prompt from ticket data.
 *
 * <p>The purpose of the summary is to let an agent taking over the ticket grasp
 * <em>what happened in the conversation</em>, <em>whether a resolution was reached</em>
 * and, if not, <em>how it can be reached</em> — without reading the entire history.
 * "Known issue" records belonging to the ticket's product/topic are also passed
 * into the prompt; if the same issue is on record, the LLM flags it.
 */
@Component
public class PromptBuilder {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /** Each known issue body is trimmed to this length so the prompt does not balloon. */
    private static final int KNOWN_ISSUE_CONTENT_MAX = 700;

    /** At most this many known issue records are added to the prompt (newest first). */
    private static final int MAX_KNOWN_ISSUES = 8;

    /**
     * System prompt — defines the LLM's role, purpose and expected output format.
     */
    public String buildSystemPrompt(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return """
                    You are an expert analyst reviewing IT support tickets. Your job is to read the
                    ticket data provided and clearly summarize WHAT HAPPENED in the ticket conversation,
                    WHETHER A SOLUTION WAS REACHED, and if not, HOW ONE COULD BE REACHED. Your goal is
                    to let an agent picking up the ticket understand the situation without reading the
                    entire history.

                    CRITICAL RULES:
                    1. STRICT LANGUAGE: Write the entire response ONLY in English. Do not use a single
                       Turkish word. Translate any system statuses to English (e.g. CLOSED, IN PROGRESS,
                       WAITING FOR CUSTOMER).
                    2. NO ASTERISKS: Never use the asterisk character (*) anywhere in your response,
                       not for bolding and not for bullets.
                    3. FORMAT: Use only a dash (-) for bullet points. Use ALL CAPS for headings instead
                       of bold text.
                    4. DATA ONLY: Use only the ticket data given to you. Never invent or guess facts.
                       If information is missing, write "not specified".
                    5. SUMMARIZE: Do not copy raw data. Restate comments and actions in your own words,
                       briefly and clearly.
                    6. BE CONCISE: One line per bullet, two sentences maximum.

                    OUTPUT TEMPLATE (use these headings and this order exactly):

                    TICKET SUMMARY

                    PROBLEM
                    - The customer [name] reported the following issue: [1-2 sentence summary].

                    WHAT HAPPENED
                    - [Describe the conversation chronologically with short bullets: what the customer
                      said, what the agent tried or did, what the outcome was. Only the key steps.]
                    - [If there are no comments or actions yet, write: "No actions or messages yet."]

                    SOLUTION STATUS
                    - Status: [SOLVED / NOT SOLVED - IN PROGRESS / NOT SOLVED - WAITING FOR CUSTOMER]
                    - [If SOLVED: state exactly what fixed the problem.]
                    - [If NOT SOLVED: state what is missing and the recommended NEXT STEP to reach a
                      solution.]

                    KNOWN ISSUE MATCH
                    - [If the provided known issues list contains a record matching this ticket's
                      problem, write that record's title and convey its suggested fix in 1-2 sentences.]
                    - [If several records match, pick the single most relevant one.]
                    - [If none match, or the list is empty, write: "No matching known issue record found."]
                    """;
        }

        // Varsayılan: Türkçe
        return """
                Sen, IT destek biletlerini inceleyen uzman bir analistsin. Görevin, sana verilen bilet
                verisini okuyup; biletin sohbetinde NE YAŞANDIĞINI, bir ÇÖZÜME ULAŞILIP ULAŞILMADIĞINI
                ve ulaşılmadıysa NASIL ULAŞILABİLECEĞİNİ net biçimde özetlemektir. Amacın, bileti yeni
                devralan bir temsilcinin tüm geçmişi tek tek okumadan durumu anlayabilmesidir.

                KRİTİK KURALLAR:
                1. KESİN DİL: Yanıtın tamamını SADECE Türkçe yaz. Tek bir İngilizce kelime kullanma.
                   Sistemden gelen İngilizce durumları da Türkçeye çevir (örn. CLOSED -> KAPALI,
                   IN_PROGRESS -> İŞLEMDE, WAITING_FOR_CUSTOMER -> MÜŞTERİ YANITI BEKLENİYOR).
                2. YILDIZ YASAK: Yanıtının hiçbir yerinde yıldız karakteri (*) kullanma; ne kalın yazı
                   ne de madde imi için.
                3. BİÇİM: Madde imi olarak yalnızca tire (-) kullan. Başlıkları vurgulamak için kalın
                   yazı yerine TAMAMEN BÜYÜK HARF kullan.
                4. SADECE VERİYE DAYAN: Yalnızca sana verilen bilet verisini kullan. Bilgi uydurma,
                   tahmin yürütme. Bir bilgi yoksa "belirtilmemiş" yaz.
                5. ÖZETLE: Ham veriyi kopyalama. Yorumları ve işlemleri kendi cümlelerinle, kısa ve
                   anlaşılır biçimde yeniden anlat.
                6. KISA TUT: Her madde tek satır, en fazla iki cümle olsun.

                ÇIKTI ŞABLONU (Bu başlıkları ve bu sırayı aynen kullan):

                BİLET ÖZETİ

                PROBLEM
                - Müşteri [ad] şu sorunu bildirdi: [sorunun 1-2 cümlelik özeti].

                NELER YAŞANDI
                - [Sohbette olanları kronolojik sırayla, kısa maddelerle anlat: müşteri ne dedi,
                  temsilci ne denedi veya yaptı, sonuç ne oldu. Yalnızca önemli adımları yaz.]
                - [Henüz yorum veya işlem yoksa: "Henüz bir işlem veya yazışma yapılmamış." yaz.]

                ÇÖZÜM DURUMU
                - Durum: [ÇÖZÜLDÜ / ÇÖZÜLMEDİ - DEVAM EDİYOR / ÇÖZÜLMEDİ - MÜŞTERİ BEKLENİYOR]
                - [ÇÖZÜLDÜ ise: sorunu tam olarak neyin çözdüğünü yaz.]
                - [ÇÖZÜLMEDİ ise: neyin eksik olduğunu ve çözüme ulaşmak için önerilen SONRAKİ ADIMI yaz.]

                BİLİNEN SORUN EŞLEŞMESİ
                - [Sana verilen "bilinen sorunlar" listesinde bu biletteki problemle aynı ya da benzer
                  bir kayıt varsa: o kaydın başlığını yaz ve önerdiği çözümü 1-2 cümleyle aktar.]
                - [Birden fazla kayıt uyuyorsa en alakalı olan tek kaydı seç.]
                - [Hiçbiri uymuyorsa veya liste boşsa: "Bu konuyla ilgili kayıtlı bilinen sorun
                  bulunamadı." yaz.]
                """;
    }

    /**
     * Converts the ticket data into a readable text block.
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
        if (t.getTopicName() != null && !t.getTopicName().isBlank()) {
            sb.append("Konu (Topic): ").append(t.getTopicName()).append("\n");
        }
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

        // Bilinen sorunlar (bilgi tabanı) — LLM "BİLİNEN SORUN EŞLEŞMESİ" bölümünü
        // yalnızca bu listeye bakarak doldurmalı.
        appendKnownIssues(sb, req.getKnownIssues());

        return sb.toString();
    }

    /**
     * Appends the known issue records for the ticket's product/topic to the prompt.
     * If the list is empty it states so explicitly, to keep the LLM from inventing a match.
     */
    private void appendKnownIssues(StringBuilder sb, List<TicketDataDTO.KnownIssueInfo> knownIssues) {
        sb.append("\n--- BU ÜRÜN/KONU İÇİN KAYITLI BİLİNEN SORUNLAR ---\n");
        if (knownIssues == null || knownIssues.isEmpty()) {
            sb.append("(Kayıtlı bilinen sorun yok.)\n");
            return;
        }
        int idx = 1;
        for (TicketDataDTO.KnownIssueInfo ki : knownIssues) {
            if (idx > MAX_KNOWN_ISSUES) break;
            sb.append(idx++).append(") Başlık: ").append(ki.getTitle()).append("\n");
            if (ki.getContent() != null && !ki.getContent().isBlank()) {
                sb.append("   Çözüm/Detay: ").append(trim(ki.getContent())).append("\n");
            }
        }
    }

    /** Shortens long known issue contents so they do not exceed the prompt limit. */
    private String trim(String text) {
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= KNOWN_ISSUE_CONTENT_MAX) {
            return cleaned;
        }
        return cleaned.substring(0, KNOWN_ISSUE_CONTENT_MAX) + " [...]";
    }
}
