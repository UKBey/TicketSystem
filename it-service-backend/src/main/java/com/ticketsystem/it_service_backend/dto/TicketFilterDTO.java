package com.ticketsystem.it_service_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Filter parameters shared by every ticket-listing endpoint.
 * Null values mean "no filter applied".
 */
@Data
@Builder
public class TicketFilterDTO {

    /** Text to search for in the title (case-insensitive, LIKE %search%). */
    private String search;

    /**
     * Ticket status — supports multi-select (NEW, IN_PROGRESS, etc.).
     * An empty/null list means no filter is applied.
     */
    private List<String> statuses;

    /**
     * Priority level — supports multi-select (CRITICAL, HIGH, MEDIUM, LOW).
     * An empty/null list means no filter is applied.
     */
    private List<String> priorities;

    /**
     * Product ID filter — supports multi-select.
     * An empty/null list means no filter is applied.
     */
    private List<Long> productIds;

    /**
     * Assigned agent IDs — supports multi-select.
     * An empty/null list means no filter is applied.
     */
    private List<String> agentIds;

    /**
     * Topic IDs — supports multi-select.
     * An empty/null list means no filter is applied.
     */
    private List<Long> topicIds;

    /**
     * SLA status — supports multi-select (BREACHED, ACTIVE, PAUSED).
     * An empty/null list means no filter is applied.
     */
    private List<String> slaStatuses;

    /** Creation date lower bound (inclusive). */
    private ZonedDateTime createdAtFrom;

    /** Creation date upper bound (inclusive). */
    private ZonedDateTime createdAtTo;

    /**
     * CSAT rating filter — supports multi-select. Each value is a star rating
     * ("1".."5") or the literal "NONE" (tickets without a CSAT response).
     * Honoured only for ADMIN/MANAGER viewers. An empty/null list = no filter.
     */
    private List<String> csatRatings;

    // -------------------------------------------------------------------------
    // Backwards compatibility — single-value setters
    // -------------------------------------------------------------------------

    /** Sets a single status value (kept for backwards compatibility). */
    public void setStatus(String status) {
        this.statuses = (status != null && !status.isBlank())
                ? java.util.List.of(status) : null;
    }

    /** Sets a single priority value (kept for backwards compatibility). */
    public void setPriority(String priority) {
        this.priorities = (priority != null && !priority.isBlank())
                ? java.util.List.of(priority) : null;
    }

    /** Sets a single productId value (kept for backwards compatibility). */
    public void setProductId(Long productId) {
        this.productIds = (productId != null) ? java.util.List.of(productId) : null;
    }

    /** Sets a single slaStatus value (kept for backwards compatibility). */
    public void setSlaStatus(String slaStatus) {
        this.slaStatuses = (slaStatus != null && !slaStatus.isBlank())
                ? java.util.List.of(slaStatus) : null;
    }

    /** Returns the active status list in a null-safe way. */
    public List<String> getStatuses() {
        return (statuses != null && !statuses.isEmpty()) ? statuses : null;
    }

    /** Returns the active priority list in a null-safe way. */
    public List<String> getPriorities() {
        return (priorities != null && !priorities.isEmpty()) ? priorities : null;
    }

    /** Returns the active productId list in a null-safe way. */
    public List<Long> getProductIds() {
        return (productIds != null && !productIds.isEmpty()) ? productIds : null;
    }

    /** Returns the active slaStatus list in a null-safe way. */
    public List<String> getSlaStatuses() {
        return (slaStatuses != null && !slaStatuses.isEmpty()) ? slaStatuses : null;
    }

    /** Returns the active agentId list in a null-safe way. */
    public List<String> getAgentIds() {
        return (agentIds != null && !agentIds.isEmpty()) ? agentIds : null;
    }

    /** Returns the active topicId list in a null-safe way. */
    public List<Long> getTopicIds() {
        return (topicIds != null && !topicIds.isEmpty()) ? topicIds : null;
    }

    /** Returns the active CSAT rating list in a null-safe way. */
    public List<String> getCsatRatings() {
        return (csatRatings != null && !csatRatings.isEmpty()) ? csatRatings : null;
    }
}
