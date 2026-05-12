package com.ticketsystem.llmservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.ZonedDateTime;

/**
 * Bir ticket için LLM tarafından üretilen özet kaydı.
 * Her summarize isteği yeni bir kayıt oluşturur; geçmiş özetler silinmez.
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

    /** İlgili ticket'ın ID'si (it-service-backend'deki tickets.id) */
    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    /** Özeti üreten model adı (ör: llama3-8b-8192) */
    @Column(nullable = false, length = 100)
    private String model;

    /** Groq'un raporladığı prompt token sayısı */
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    /** Groq'un raporladığı completion token sayısı */
    @Column(name = "completion_tokens")
    private Integer completionTokens;

    /** LLM'in ürettiği özet metni */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
