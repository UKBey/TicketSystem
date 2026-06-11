package com.ticketsystem.llmservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * The full ticket payload that it-service-backend sends to llm-service.
 * All related child data (comments, worklogs, etc.) is carried inside this DTO.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummarizeRequestDTO {

    /** ID of the ticket to summarize */
    private Long ticketId;

    /** Full data of the ticket */
    private TicketDataDTO ticket;

    /** Comments attached to the ticket */
    private List<TicketDataDTO.CommentInfo> comments;

    /** Worklogs attached to the ticket */
    private List<TicketDataDTO.WorklogInfo> worklogs;

    /** Resolution note (if any) */
    private TicketDataDTO.ResolutionNoteInfo resolutionNote;

    /** Frequently encountered issues for the ticket's product/topic (knowledge base records) */
    private List<TicketDataDTO.KnownIssueInfo> knownIssues;

    /** Summary language: "tr" or "en" (default: "tr") */
    private String language = "tr";
}
