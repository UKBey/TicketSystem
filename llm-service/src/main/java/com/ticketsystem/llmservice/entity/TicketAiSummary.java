package com.ticketsystem.llmservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

/**
 * Summary record produced by the LLM for a ticket.
 * Every summarize request creates a new record; previous summaries are not deleted.
 */
@Entity
@Table(name = "ticket_ai_summaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketAiSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID of the related ticket (tickets.id in it-service-backend) */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** Name of the model that produced the summary (e.g. llama3-8b-8192) */
    @Column(nullable = false, length = 100)
    private String model;

    /** Prompt token count reported by Groq */
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    /** Completion token count reported by Groq */
    @Column(name = "completion_tokens")
    private Integer completionTokens;

    /** Summary text produced by the LLM */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
