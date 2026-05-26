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
 *
 * <p><b>Language discipline.</b> Both the system prompt and the user-side data block
 * are localized to the requested {@code language}. The data labels match the target
 * language so the model does not mirror the source-data language and produce a
 * mixed-language summary. The system prompt opens and closes with an explicit
 * language rule to further pin the output language.
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
                    ==================================================================
                    ABSOLUTE LANGUAGE RULE — READ FIRST, OBEY ALWAYS
                    ==================================================================
                    Your ENTIRE response MUST be written in ENGLISH ONLY.

                    The ticket data below may contain Turkish text in the title,
                    description, comments, worklogs or known-issue records. DO NOT
                    mirror that language. Translate or paraphrase every piece of
                    Turkish content into English when you reference it.

                    Forbidden:
                    - Any Turkish word in your response, even inside quotes.
                    - Mixing English and Turkish in the same sentence.
                    - Leaving status codes in Turkish — translate them
                      (KAPALI -> CLOSED, İŞLEMDE -> IN PROGRESS,
                       MÜŞTERİ YANITI BEKLENİYOR -> WAITING FOR CUSTOMER,
                       YENİ -> NEW, ÇÖZÜLDÜ -> RESOLVED).

                    If a single Turkish word slips into your reply, the whole
                    response is invalid.
                    ==================================================================

                    You are an expert analyst reviewing IT support tickets. Your job
                    is to read the ticket data provided and clearly summarize
                    WHAT HAPPENED in the ticket conversation, WHETHER A SOLUTION WAS
                    REACHED, and if not, HOW ONE COULD BE REACHED. Your goal is to
                    let an agent picking up the ticket understand the situation
                    without reading the entire history.

                    CRITICAL RULES:
                    1. STRICT LANGUAGE: Write the entire response ONLY in English.
                       Do not use a single Turkish word. Translate any system
                       statuses to English (e.g. CLOSED, IN PROGRESS,
                       WAITING FOR CUSTOMER).
                    2. NO ASTERISKS: Never use the asterisk character (*) anywhere in
                       your response, not for bolding and not for bullets.
                    3. FORMAT: Use only a dash (-) for bullet points. Use ALL CAPS
                       for headings instead of bold text.
                    4. DATA ONLY: Use only the ticket data given to you. Never invent
                       or guess facts. If information is missing, write
                       "not specified".
                    5. SUMMARIZE: Do not copy raw data. Restate comments and actions
                       in your own words, briefly and clearly.
                    6. BE CONCISE: One line per bullet, two sentences maximum.

                    OUTPUT TEMPLATE (use these headings and this order exactly):

                    TICKET SUMMARY

                    PROBLEM
                    - The customer [name] reported the following issue:
                      [1-2 sentence summary].

                    WHAT HAPPENED
                    - [Describe the conversation chronologically with short bullets:
                      what the customer said, what the agent tried or did, what the
                      outcome was. Only the key steps.]
                    - [If there are no comments or actions yet, write:
                      "No actions or messages yet."]

                    SOLUTION STATUS
                    - Status: [SOLVED / NOT SOLVED - IN PROGRESS /
                               NOT SOLVED - WAITING FOR CUSTOMER]
                    - [If SOLVED: state exactly what fixed the problem.]
                    - [If NOT SOLVED: state what is missing and the recommended
                      NEXT STEP to reach a solution.]

                    KNOWN ISSUE MATCH
                    - [If the provided known issues list contains a record matching
                      this ticket's problem, write that record's title and convey
                      its suggested fix in 1-2 sentences.]
                    - [If several records match, pick the single most relevant one.]
                    - [If none match, or the list is empty, write:
                      "No matching known issue record found."]

                    ==================================================================
                    FINAL CHECK BEFORE YOU REPLY
                    ==================================================================
                    Re-read your draft. Every sentence must be in English. If you
                    see any Turkish word — even one — rewrite that sentence in
                    English before sending the response.
                    ==================================================================
                    """;
        }

        // Varsayılan: Türkçe
        return """
                ==================================================================
                MUTLAK DİL KURALI — İLK BUNU OKU, HER ZAMAN UY
                ==================================================================
                Yanıtının TAMAMI SADECE TÜRKÇE olmak ZORUNDA.

                Aşağıdaki bilet verisi başlıkta, açıklamada, yorumlarda, worklog
                kayıtlarında veya bilinen sorun kayıtlarında İngilizce metin
                içerebilir. Bu dili AYNALAMA. Atıfta bulunduğun her İngilizce
                içeriği Türkçeye çevir veya yeniden ifade et.

                Yasaklı:
                - Yanıtında tek bir İngilizce kelime (tırnak içinde dahil).
                - Aynı cümlede Türkçe ve İngilizce karıştırmak.
                - Sistem durum kodlarını İngilizce bırakmak — Türkçeye çevir
                  (CLOSED -> KAPALI, IN_PROGRESS -> İŞLEMDE,
                   WAITING_FOR_CUSTOMER -> MÜŞTERİ YANITI BEKLENİYOR,
                   NEW -> YENİ, RESOLVED -> ÇÖZÜLDÜ).

                Tek bir İngilizce kelime bile kaçarsa yanıtın tamamı geçersizdir.
                ==================================================================

                Sen, IT destek biletlerini inceleyen uzman bir analistsin. Görevin,
                sana verilen bilet verisini okuyup; biletin sohbetinde NE YAŞANDIĞINI,
                bir ÇÖZÜME ULAŞILIP ULAŞILMADIĞINI ve ulaşılmadıysa NASIL
                ULAŞILABİLECEĞİNİ net biçimde özetlemektir. Amacın, bileti yeni
                devralan bir temsilcinin tüm geçmişi tek tek okumadan durumu
                anlayabilmesidir.

                KRİTİK KURALLAR:
                1. KESİN DİL: Yanıtın tamamını SADECE Türkçe yaz. Tek bir İngilizce
                   kelime kullanma. Sistemden gelen İngilizce durumları da Türkçeye
                   çevir (örn. CLOSED -> KAPALI, IN_PROGRESS -> İŞLEMDE,
                   WAITING_FOR_CUSTOMER -> MÜŞTERİ YANITI BEKLENİYOR).
                2. YILDIZ YASAK: Yanıtının hiçbir yerinde yıldız karakteri (*)
                   kullanma; ne kalın yazı ne de madde imi için.
                3. BİÇİM: Madde imi olarak yalnızca tire (-) kullan. Başlıkları
                   vurgulamak için kalın yazı yerine TAMAMEN BÜYÜK HARF kullan.
                4. SADECE VERİYE DAYAN: Yalnızca sana verilen bilet verisini kullan.
                   Bilgi uydurma, tahmin yürütme. Bir bilgi yoksa "belirtilmemiş" yaz.
                5. ÖZETLE: Ham veriyi kopyalama. Yorumları ve işlemleri kendi
                   cümlelerinle, kısa ve anlaşılır biçimde yeniden anlat.
                6. KISA TUT: Her madde tek satır, en fazla iki cümle olsun.

                ÇIKTI ŞABLONU (Bu başlıkları ve bu sırayı aynen kullan):

                BİLET ÖZETİ

                PROBLEM
                - Müşteri [ad] şu sorunu bildirdi: [sorunun 1-2 cümlelik özeti].

                NELER YAŞANDI
                - [Sohbette olanları kronolojik sırayla, kısa maddelerle anlat:
                  müşteri ne dedi, temsilci ne denedi veya yaptı, sonuç ne oldu.
                  Yalnızca önemli adımları yaz.]
                - [Henüz yorum veya işlem yoksa:
                  "Henüz bir işlem veya yazışma yapılmamış." yaz.]

                ÇÖZÜM DURUMU
                - Durum: [ÇÖZÜLDÜ / ÇÖZÜLMEDİ - DEVAM EDİYOR /
                          ÇÖZÜLMEDİ - MÜŞTERİ BEKLENİYOR]
                - [ÇÖZÜLDÜ ise: sorunu tam olarak neyin çözdüğünü yaz.]
                - [ÇÖZÜLMEDİ ise: neyin eksik olduğunu ve çözüme ulaşmak için
                  önerilen SONRAKİ ADIMI yaz.]

                BİLİNEN SORUN EŞLEŞMESİ
                - [Sana verilen "bilinen sorunlar" listesinde bu biletteki problemle
                  aynı ya da benzer bir kayıt varsa: o kaydın başlığını yaz ve
                  önerdiği çözümü 1-2 cümleyle aktar.]
                - [Birden fazla kayıt uyuyorsa en alakalı olan tek kaydı seç.]
                - [Hiçbiri uymuyorsa veya liste boşsa: "Bu konuyla ilgili kayıtlı
                  bilinen sorun bulunamadı." yaz.]

                ==================================================================
                YANITI GÖNDERMEDEN ÖNCE SON KONTROL
                ==================================================================
                Taslağını tekrar oku. Her cümle Türkçe olmalı. Tek bir İngilizce
                kelime bile görürsen — göndermeden önce o cümleyi Türkçeye çevir.
                ==================================================================
                """;
    }

    /**
     * Converts the ticket data into a readable text block.
     *
     * <p>All field labels are localized to {@code req.getLanguage()} so the data
     * block reinforces — rather than fights with — the target output language.
     */
    public String buildUserPrompt(SummarizeRequestDTO req) {
        Labels L = "en".equalsIgnoreCase(req.getLanguage()) ? Labels.EN : Labels.TR;
        TicketDataDTO t = req.getTicket();
        StringBuilder sb = new StringBuilder();

        sb.append("=== ").append(L.ticketData).append(" ===\n\n");

        // Temel bilgiler
        sb.append("ID: ").append(t.getId()).append("\n");
        sb.append(L.title).append(": ").append(t.getTitle()).append("\n");
        sb.append(L.description).append(": ").append(t.getDescription()).append("\n");
        sb.append(L.status).append(": ").append(t.getStatus()).append("\n");
        sb.append(L.priority).append(": ").append(t.getPriority()).append("\n");
        sb.append(L.product).append(": ").append(t.getProductName()).append("\n");
        if (t.getTopicName() != null && !t.getTopicName().isBlank()) {
            sb.append(L.topic).append(": ").append(t.getTopicName()).append("\n");
        }
        sb.append(L.customer).append(": ").append(t.getCustomerName()).append("\n");

        if (t.getCreatedAt() != null) {
            sb.append(L.createdAt).append(": ").append(t.getCreatedAt().format(FMT)).append("\n");
        }
        if (t.getResolvedAt() != null) {
            sb.append(L.resolvedAt).append(": ").append(t.getResolvedAt().format(FMT)).append("\n");
        }
        if (t.getClosedAt() != null) {
            sb.append(L.closedAt).append(": ").append(t.getClosedAt().format(FMT)).append("\n");
        }

        // SLA
        sb.append(L.slaBreached).append(": ")
                .append(Boolean.TRUE.equals(t.getSlaBreached()) ? L.yes : L.no).append("\n");
        if (t.getSlaDeadline() != null) {
            sb.append(L.slaDeadline).append(": ").append(t.getSlaDeadline().format(FMT)).append("\n");
        }

        // Ajanlar
        if (t.getClaimers() != null && !t.getClaimers().isEmpty()) {
            sb.append(L.assignedAgents).append(": ");
            t.getClaimers().forEach(c -> sb.append(c.getAgentName()).append(", "));
            sb.append("\n");
        }

        // Yorumlar
        List<TicketDataDTO.CommentInfo> comments = req.getComments();
        if (comments != null && !comments.isEmpty()) {
            sb.append("\n--- ").append(L.comments).append(" (").append(comments.size())
                    .append(' ').append(L.itemsSuffix).append(") ---\n");
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
            sb.append("\n--- ").append(L.worklogs).append(" (")
                    .append(L.total).append(": ").append(totalMinutes).append(' ').append(L.minutesShort)
                    .append(") ---\n");
            for (TicketDataDTO.WorklogInfo w : worklogs) {
                if (w.getCreatedAt() != null) {
                    sb.append(w.getCreatedAt().format(FMT)).append(" ");
                }
                sb.append(w.getMinutes()).append(' ').append(L.minutesShort);
                if (w.getDescription() != null && !w.getDescription().isBlank()) {
                    sb.append(": ").append(w.getDescription());
                }
                sb.append("\n");
            }
        }

        // Çözüm notu
        TicketDataDTO.ResolutionNoteInfo note = req.getResolutionNote();
        if (note != null && note.getNote() != null) {
            sb.append("\n--- ").append(L.resolutionNote).append(" ---\n");
            sb.append(note.getNote()).append("\n");
        }

        // Audit log (son 10 kayıt)
        if (t.getAuditLogs() != null && !t.getAuditLogs().isEmpty()) {
            int shown = Math.min(t.getAuditLogs().size(), 10);
            sb.append("\n--- ").append(L.auditHistory).append(" (")
                    .append(L.lastNRecords).append(": ").append(shown).append(") ---\n");
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
        appendKnownIssues(sb, req.getKnownIssues(), L);

        // Son hatırlatma — LLM'lerin "recency bias"i nedeniyle prompt'un en sonundaki
        // talimat çok güçlü çalışır. Dil kuralını burada da pekiştiriyoruz.
        sb.append("\n").append(L.finalReminder).append("\n");

        return sb.toString();
    }

    /**
     * Appends the known issue records for the ticket's product/topic to the prompt.
     * If the list is empty it states so explicitly, to keep the LLM from inventing a match.
     */
    private void appendKnownIssues(StringBuilder sb, List<TicketDataDTO.KnownIssueInfo> knownIssues, Labels L) {
        sb.append("\n--- ").append(L.knownIssuesHeader).append(" ---\n");
        if (knownIssues == null || knownIssues.isEmpty()) {
            sb.append("(").append(L.noKnownIssues).append(")\n");
            return;
        }
        int idx = 1;
        for (TicketDataDTO.KnownIssueInfo ki : knownIssues) {
            if (idx > MAX_KNOWN_ISSUES) break;
            sb.append(idx++).append(") ").append(L.title).append(": ").append(ki.getTitle()).append("\n");
            if (ki.getContent() != null && !ki.getContent().isBlank()) {
                sb.append("   ").append(L.solutionDetail).append(": ").append(trim(ki.getContent())).append("\n");
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

    /**
     * Localized labels for the user-side data block. Matching the labels to the target
     * output language stops the LLM from drifting into the source-data language.
     */
    private static final class Labels {
        final String ticketData, title, description, status, priority, product, topic, customer;
        final String createdAt, resolvedAt, closedAt, slaBreached, slaDeadline, yes, no;
        final String assignedAgents, comments, itemsSuffix, worklogs, total, minutesShort;
        final String resolutionNote, auditHistory, lastNRecords;
        final String knownIssuesHeader, noKnownIssues, solutionDetail, finalReminder;

        private Labels(String ticketData, String title, String description, String status, String priority,
                       String product, String topic, String customer, String createdAt, String resolvedAt,
                       String closedAt, String slaBreached, String slaDeadline, String yes, String no,
                       String assignedAgents, String comments, String itemsSuffix, String worklogs,
                       String total, String minutesShort, String resolutionNote, String auditHistory,
                       String lastNRecords, String knownIssuesHeader, String noKnownIssues,
                       String solutionDetail, String finalReminder) {
            this.ticketData = ticketData; this.title = title; this.description = description;
            this.status = status; this.priority = priority; this.product = product; this.topic = topic;
            this.customer = customer; this.createdAt = createdAt; this.resolvedAt = resolvedAt;
            this.closedAt = closedAt; this.slaBreached = slaBreached; this.slaDeadline = slaDeadline;
            this.yes = yes; this.no = no; this.assignedAgents = assignedAgents; this.comments = comments;
            this.itemsSuffix = itemsSuffix; this.worklogs = worklogs; this.total = total;
            this.minutesShort = minutesShort; this.resolutionNote = resolutionNote;
            this.auditHistory = auditHistory; this.lastNRecords = lastNRecords;
            this.knownIssuesHeader = knownIssuesHeader; this.noKnownIssues = noKnownIssues;
            this.solutionDetail = solutionDetail; this.finalReminder = finalReminder;
        }

        static final Labels TR = new Labels(
                "BİLET VERİSİ", "Başlık", "Açıklama", "Durum", "Öncelik", "Ürün/Kategori", "Konu (Topic)",
                "Müşteri", "Oluşturulma", "Çözüldü", "Kapatıldı", "SLA İhlali", "SLA Deadline",
                "EVET", "Hayır", "Atanan Ajanlar", "YORUMLAR", "adet", "WORKLOG'LAR", "Toplam", "dk",
                "ÇÖZÜM NOTU", "AKSİYON GEÇMİŞİ", "son",
                "BU ÜRÜN/KONU İÇİN KAYITLI BİLİNEN SORUNLAR", "Kayıtlı bilinen sorun yok.",
                "Çözüm/Detay",
                "HATIRLATMA: Yanıtının tamamı SADECE TÜRKÇE olmak zorunda. Tek bir İngilizce " +
                        "kelime kullanma — sistem statüleri dahil her şeyi Türkçeye çevir."
        );

        static final Labels EN = new Labels(
                "TICKET DATA", "Title", "Description", "Status", "Priority", "Product/Category", "Topic",
                "Customer", "Created at", "Resolved at", "Closed at", "SLA breached", "SLA deadline",
                "YES", "No", "Assigned agents", "COMMENTS", "items", "WORKLOGS", "Total", "min",
                "RESOLUTION NOTE", "AUDIT HISTORY", "last",
                "KNOWN ISSUES ON RECORD FOR THIS PRODUCT/TOPIC", "No known issue on record.",
                "Solution/Detail",
                "REMINDER: Your entire response MUST be in English only. Do not use any " +
                        "Turkish word — translate every Turkish piece of data, including system statuses."
        );
    }
}
