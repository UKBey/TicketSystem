package com.ticketsystem.llmservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Ticket data received from it-service-backend.
 * Only the fields needed for LLM summarization are included.
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
     * Identity of the agent who claimed the ticket.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClaimerInfo {
        private String agentId;
        private String agentName;
    }

    /**
     * A single comment on the ticket (customer / agent / internal).
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
     * Worklog record representing the time the agent spent on the ticket.
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
     * Resolution note left when the ticket is closed.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResolutionNoteInfo {
        private String note;
        private ZonedDateTime createdAt;
    }

    /**
     * Audit record of a single action performed on the ticket.
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
     * "Frequently encountered issue" record tied to the ticket's product/topic.
     * The LLM compares the ticket's problem to these records and reports any match.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KnownIssueInfo {
        private Long id;
        private Long topicId;
        // Bilingual (tr/en); at least one is present. The prompt reads whichever exists,
        // preferring Turkish (the backend's default locale) and falling back to the other.
        private String titleTr;
        private String titleEn;
        private String contentTr;
        private String contentEn;

        /** Title in the preferred language (tr first), falling back to the other variant. */
        public String getTitle() {
            return titleTr != null && !titleTr.isBlank() ? titleTr : titleEn;
        }

        /** Content in the preferred language (tr first), falling back to the other variant. */
        public String getContent() {
            return contentTr != null && !contentTr.isBlank() ? contentTr : contentEn;
        }
    }
}
