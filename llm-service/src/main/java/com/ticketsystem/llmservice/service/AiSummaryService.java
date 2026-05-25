package com.ticketsystem.llmservice.service;

import com.ticketsystem.llmservice.dto.AiSummaryResponseDTO;
import com.ticketsystem.llmservice.dto.GroqChatResponse;
import com.ticketsystem.llmservice.dto.SummarizeRequestDTO;
import com.ticketsystem.llmservice.entity.TicketAiSummary;
import com.ticketsystem.llmservice.repository.TicketAiSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service that manages the ticket summarization business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    private final GroqService groqService;
    private final PromptBuilder promptBuilder;
    private final TicketAiSummaryRepository summaryRepository;

    /**
     * Calls the Groq API for the given ticket data, generates the summary and persists it.
     *
     * @param request Ticket data and language preference
     * @return Persisted summary
     */
    @Transactional
    public AiSummaryResponseDTO summarize(SummarizeRequestDTO request) {
        Long ticketId = request.getTicketId();
        log.info("Ticket özeti oluşturuluyor. TicketId: {}, Dil: {}", ticketId, request.getLanguage());

        // Prompt'ları oluştur
        String systemPrompt = promptBuilder.buildSystemPrompt(request.getLanguage());
        String userPrompt = promptBuilder.buildUserPrompt(request);

        // Groq API'yi çağır
        GroqChatResponse groqResponse = groqService.complete(systemPrompt, userPrompt);

        // Yanıttan özeti çıkar
        String summaryText = groqResponse.getChoices().get(0).getMessage().getContent();

        // Token kullanımını al
        Integer promptTokens = null;
        Integer completionTokens = null;
        if (groqResponse.getUsage() != null) {
            promptTokens = groqResponse.getUsage().getPromptTokens();
            completionTokens = groqResponse.getUsage().getCompletionTokens();
        }

        // Veritabanına kaydet
        TicketAiSummary entity = TicketAiSummary.builder()
                .ticketId(ticketId)
                .model(groqResponse.getModel())
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .summary(summaryText)
                .build();

        TicketAiSummary saved = summaryRepository.save(entity);
        log.info("Ticket özeti kaydedildi. SummaryId: {}, TicketId: {}", saved.getId(), ticketId);

        return AiSummaryResponseDTO.fromEntity(saved);
    }

    /**
     * Returns the most recent summary for a ticket.
     */
    @Transactional(readOnly = true)
    public AiSummaryResponseDTO getLatestSummary(Long ticketId) {
        return summaryRepository.findFirstByTicketIdOrderByCreatedAtDesc(ticketId)
                .map(AiSummaryResponseDTO::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ticket #" + ticketId + " için henüz özet oluşturulmamış"));
    }

    /**
     * Returns all summaries of a ticket sorted from newest to oldest.
     */
    @Transactional(readOnly = true)
    public List<AiSummaryResponseDTO> getAllSummaries(Long ticketId) {
        return summaryRepository.findByTicketIdOrderByCreatedAtDesc(ticketId)
                .stream()
                .map(AiSummaryResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
