package com.ticketsystem.llmservice.dto;

import com.ticketsystem.llmservice.entity.TicketAiSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Response model returning the result of LLM summarization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSummaryResponseDTO {

    private Long id;
    private Long ticketId;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private String summary;
    private ZonedDateTime createdAt;

    public static AiSummaryResponseDTO fromEntity(TicketAiSummary entity) {
        return AiSummaryResponseDTO.builder()
                .id(entity.getId())
                .ticketId(entity.getTicketId())
                .model(entity.getModel())
                .promptTokens(entity.getPromptTokens())
                .completionTokens(entity.getCompletionTokens())
                .summary(entity.getSummary())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
