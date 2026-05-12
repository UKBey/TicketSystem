package com.ticketsystem.llmservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * it-service-backend'in llm-service'e gönderdiği tam ticket verisi.
 * Tüm ilgili alt veriler (yorumlar, worklog'lar vb.) bu DTO içinde taşınır.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SummarizeRequestDTO {

    /** Özetlenecek ticket'ın ID'si */
    private Long ticketId;

    /** Ticket'ın tüm verisi */
    private TicketDataDTO ticket;

    /** Ticket'a ait yorumlar */
    private List<TicketDataDTO.CommentInfo> comments;

    /** Ticket'a ait worklog'lar */
    private List<TicketDataDTO.WorklogInfo> worklogs;

    /** Çözüm notu (varsa) */
    private TicketDataDTO.ResolutionNoteInfo resolutionNote;

    /** Özet dili: "tr" veya "en" (varsayılan: "tr") */
    private String language = "tr";
}
