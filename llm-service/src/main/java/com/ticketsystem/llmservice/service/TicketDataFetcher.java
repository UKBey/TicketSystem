package com.ticketsystem.llmservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketsystem.llmservice.dto.SummarizeRequestDTO;
import com.ticketsystem.llmservice.dto.TicketDataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that fetches ticket data from it-service-backend.
 * Operates without JWT through the internal endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketDataFetcher {

    @Qualifier("ticketServiceWebClient")
    private final WebClient ticketServiceWebClient;

    /**
     * Fetches the full ticket data from it-service-backend for the given ticketId
     * and converts it into a SummarizeRequestDTO.
     */
    public SummarizeRequestDTO fetchTicketData(Long ticketId, String language) {
        log.info("Ticket verisi çekiliyor. TicketId: {}, Dil: {}", ticketId, language);
        long start = System.currentTimeMillis();

        JsonNode root = ticketServiceWebClient.get()
                .uri("/api/v1/internal/tickets/{id}/full", ticketId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, resp ->
                        resp.bodyToMono(String.class).map(body -> {
                            log.warn("Ticket fetch 4xx. TicketId: {}, Status: {}, Body: {}",
                                    ticketId, resp.statusCode(), body);
                            return new RuntimeException("Ticket bulunamadı veya erişim reddedildi: " + body);
                        }))
                .onStatus(HttpStatusCode::is5xxServerError, resp ->
                        resp.bodyToMono(String.class).map(body -> {
                            log.error("Ticket fetch 5xx. TicketId: {}, Status: {}, Body: {}",
                                    ticketId, resp.statusCode(), body);
                            return new RuntimeException("it-service-backend hatası: " + body);
                        }))
                .bodyToMono(JsonNode.class)
                .block();

        if (root == null) {
            log.error("it-service-backend boş yanıt döndü. TicketId: {}", ticketId);
            throw new RuntimeException("it-service-backend boş yanıt döndü. TicketId: " + ticketId);
        }

        SummarizeRequestDTO req = new SummarizeRequestDTO();
        req.setTicketId(ticketId);
        req.setLanguage(language != null ? language : "tr");

        // Ticket ana verisi — ürün/konu adları çift dilli gelir; özetin üretileceği
        // dile uyan varyant seçilir (boşsa diğerine düşülür).
        TicketDataDTO ticket = parseTicket(root.get("ticket"), req.getLanguage());
        req.setTicket(ticket);

        // Yorumlar
        req.setComments(parseComments(root.get("comments")));

        // Worklog'lar
        req.setWorklogs(parseWorklogs(root.get("worklogs")));

        // Çözüm notu
        req.setResolutionNote(parseResolutionNote(root.get("resolutionNote")));

        // Bilinen sorunlar (bilgi tabanı kayıtları)
        req.setKnownIssues(parseKnownIssues(root.get("knownIssues")));

        long elapsedMs = System.currentTimeMillis() - start;
        log.info("Ticket verisi başarıyla çekildi. TicketId: {}, Yorum: {}, Worklog: {}, Bilinen sorun: {}, Süre: {}ms",
                ticketId,
                req.getComments() != null ? req.getComments().size() : 0,
                req.getWorklogs() != null ? req.getWorklogs().size() : 0,
                req.getKnownIssues() != null ? req.getKnownIssues().size() : 0,
                elapsedMs);

        return req;
    }

    private TicketDataDTO parseTicket(JsonNode node, String language) {
        if (node == null) return new TicketDataDTO();
        TicketDataDTO t = new TicketDataDTO();
        t.setId(longVal(node, "id"));
        t.setTitle(strVal(node, "title"));
        t.setDescription(strVal(node, "description"));
        t.setStatus(strVal(node, "status"));
        t.setPriority(strVal(node, "priority"));
        t.setProductName(pickLocalized(strVal(node, "productNameTr"), strVal(node, "productNameEn"), language));
        t.setTopicName(pickLocalized(strVal(node, "topicNameTr"), strVal(node, "topicNameEn"), language));
        t.setCustomerName(strVal(node, "customerName"));
        t.setSlaBreached(boolVal(node, "slaBreached"));
        t.setSlaDeadline(dateVal(node, "slaDeadline"));
        t.setCreatedAt(dateVal(node, "createdAt"));
        t.setResolvedAt(dateVal(node, "resolvedAt"));
        t.setClosedAt(dateVal(node, "closedAt"));
        t.setHasCsat(boolVal(node, "hasCsat"));

        // Claimers
        JsonNode claimersNode = node.get("claimers");
        if (claimersNode != null && claimersNode.isArray()) {
            List<TicketDataDTO.ClaimerInfo> claimers = new ArrayList<>();
            for (JsonNode c : claimersNode) {
                TicketDataDTO.ClaimerInfo ci = new TicketDataDTO.ClaimerInfo();
                ci.setAgentId(strVal(c, "agentId"));
                ci.setAgentName(strVal(c, "agentName"));
                claimers.add(ci);
            }
            t.setClaimers(claimers);
        }

        // Audit logs
        JsonNode auditNode = node.get("auditLogs");
        if (auditNode != null && auditNode.isArray()) {
            List<TicketDataDTO.AuditLogInfo> logs = new ArrayList<>();
            for (JsonNode a : auditNode) {
                TicketDataDTO.AuditLogInfo al = new TicketDataDTO.AuditLogInfo();
                al.setActionType(strVal(a, "actionType"));
                al.setNote(strVal(a, "note"));
                al.setPreviousState(strVal(a, "previousState"));
                al.setNewState(strVal(a, "newState"));
                al.setCreatedAt(dateVal(a, "createdAt"));
                logs.add(al);
            }
            t.setAuditLogs(logs);
        }

        return t;
    }

    private List<TicketDataDTO.CommentInfo> parseComments(JsonNode node) {
        List<TicketDataDTO.CommentInfo> list = new ArrayList<>();
        if (node == null || !node.isArray()) return list;
        for (JsonNode c : node) {
            TicketDataDTO.CommentInfo ci = new TicketDataDTO.CommentInfo();
            ci.setAuthorName(strVal(c, "authorName"));
            ci.setMessage(strVal(c, "message"));
            ci.setType(strVal(c, "type"));
            ci.setCreatedAt(dateVal(c, "createdAt"));
            list.add(ci);
        }
        return list;
    }

    private List<TicketDataDTO.WorklogInfo> parseWorklogs(JsonNode node) {
        List<TicketDataDTO.WorklogInfo> list = new ArrayList<>();
        if (node == null || !node.isArray()) return list;
        for (JsonNode w : node) {
            TicketDataDTO.WorklogInfo wi = new TicketDataDTO.WorklogInfo();
            wi.setAgentId(strVal(w, "agentId"));
            wi.setMinutes(intVal(w, "minutes"));
            wi.setDescription(strVal(w, "description"));
            wi.setCreatedAt(dateVal(w, "createdAt"));
            list.add(wi);
        }
        return list;
    }

    private List<TicketDataDTO.KnownIssueInfo> parseKnownIssues(JsonNode node) {
        List<TicketDataDTO.KnownIssueInfo> list = new ArrayList<>();
        if (node == null || !node.isArray()) return list;
        for (JsonNode k : node) {
            TicketDataDTO.KnownIssueInfo ki = new TicketDataDTO.KnownIssueInfo();
            ki.setId(longVal(k, "id"));
            ki.setTopicId(longVal(k, "topicId"));
            ki.setTitleTr(strVal(k, "titleTr"));
            ki.setTitleEn(strVal(k, "titleEn"));
            ki.setContentTr(strVal(k, "contentTr"));
            ki.setContentEn(strVal(k, "contentEn"));
            list.add(ki);
        }
        return list;
    }

    private TicketDataDTO.ResolutionNoteInfo parseResolutionNote(JsonNode node) {
        if (node == null || node.isEmpty()) return null;
        String note = strVal(node, "note");
        if (note == null || note.isBlank()) return null;
        TicketDataDTO.ResolutionNoteInfo rn = new TicketDataDTO.ResolutionNoteInfo();
        rn.setNote(note);
        rn.setCreatedAt(dateVal(node, "createdAt"));
        return rn;
    }

    // ---- Yardımcı metodlar ----

    /** Hedef özet diline uyan ad varyantını seçer; o dil boşsa diğer dile düşer. */
    private String pickLocalized(String tr, String en, String language) {
        String primary = "en".equalsIgnoreCase(language) ? en : tr;
        String fallback = "en".equalsIgnoreCase(language) ? tr : en;
        return (primary != null && !primary.isBlank()) ? primary : fallback;
    }

    private String strVal(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText() : null;
    }

    private Long longVal(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asLong() : null;
    }

    private Integer intVal(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asInt() : null;
    }

    private Boolean boolVal(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asBoolean() : null;
    }

    private ZonedDateTime dateVal(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull()) return null;
        try {
            return ZonedDateTime.parse(f.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
