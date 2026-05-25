package com.ticketsystem.llmservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * it-service-backend'den gelen ticket verisi.
 * Sadece LLM özetlemesi için gerekli alanlar alınır.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketDataDTO {

    private Long id;
    private String title;
    private String description;
    private String status;
    private String priority;
    private String productName;
    private String topicName;
    private String customerName;
    private Boolean slaBreached;
    private ZonedDateTime slaDeadline;
    private ZonedDateTime createdAt;
    private ZonedDateTime resolvedAt;
    private ZonedDateTime closedAt;
    private Boolean hasCsat;
    private List<ClaimerInfo> claimers;
    private List<CommentInfo> comments;
    private List<WorklogInfo> worklogs;
    private ResolutionNoteInfo resolutionNote;
    private List<AuditLogInfo> auditLogs;
    private Map<String, Object> slaInfo;

    /**
     * Ticket'ı üstlenen ajan kimliği.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClaimerInfo {
        private String agentId;
        private String agentName;
    }

    /**
     * Ticket üzerindeki tek bir yorum (müşteri / ajan / dahili).
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CommentInfo {
        private String authorName;
        private String message;
        private String type;
        private ZonedDateTime createdAt;
    }

    /**
     * Ajanın bilet üzerinde geçirdiği süreyi temsil eden worklog kaydı.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorklogInfo {
        private String agentId;
        private Integer minutes;
        private String description;
        private ZonedDateTime createdAt;
    }

    /**
     * Bilet kapatılırken bırakılan çözüm notu.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResolutionNoteInfo {
        private String note;
        private ZonedDateTime createdAt;
    }

    /**
     * Bilet üzerinde yapılan tek bir aksiyonun denetim kaydı.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AuditLogInfo {
        private String actionType;
        private String note;
        private String previousState;
        private String newState;
        private ZonedDateTime createdAt;
    }

    /**
     * Biletin ürün/konusuna bağlı "sıkça karşılaşılan sorun" kaydı.
     * LLM, biletteki sorunu bu kayıtlarla karşılaştırıp eşleşme varsa belirtir.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KnownIssueInfo {
        private Long id;
        private Long topicId;
        private String title;
        private String content;
    }
}
