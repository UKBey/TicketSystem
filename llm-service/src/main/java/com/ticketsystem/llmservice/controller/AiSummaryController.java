package com.ticketsystem.llmservice.controller;

import com.ticketsystem.llmservice.dto.AiSummaryResponseDTO;
import com.ticketsystem.llmservice.dto.SummarizeRequestDTO;
import com.ticketsystem.llmservice.service.AiSummaryService;
import com.ticketsystem.llmservice.service.TicketDataFetcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that provides LLM-based summary generation and read
 * endpoints for tickets. All paths sit under {@code /api/v1/ai/summaries} and
 * are protected by the rate-limit interceptor.
 */
@Slf4j
@Tag(name = "AI Özet", description = "Ticket verisi için LLM tabanlı özetleme işlemleri")
@RestController
@RequestMapping("/api/v1/ai/summaries")
@RequiredArgsConstructor
public class AiSummaryController {

    private final AiSummaryService aiSummaryService;
    private final TicketDataFetcher ticketDataFetcher;

    /**
     * Creates a summary from raw ticket data.
     *
     * @param request ticket data to summarize and language preference
     * @return the generated and persisted summary
     */
    @Operation(
            summary = "Ticket özeti oluştur (ham veri ile)",
            description = "Gönderilen ticket verisi Groq LLM'e iletilir, üretilen özet veritabanına kaydedilir ve döner."
    )
    @PostMapping
    public ResponseEntity<AiSummaryResponseDTO> summarize(
            @RequestBody @Valid SummarizeRequestDTO request) {
        log.info("Özetleme isteği alındı. TicketId: {}", request.getTicketId());
        AiSummaryResponseDTO response = aiSummaryService.summarize(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a summary automatically given a ticket ID; fetches the data from it-service-backend.
     *
     * @param ticketId ID of the ticket to summarize
     * @param language summary language ({@code tr} or {@code en})
     * @return the generated and persisted summary
     */
    @Operation(
            summary = "Ticket özetini otomatik oluştur",
            description = "Ticket ID'si verilir; llm-service it-service-backend'den veriyi çeker, Groq'a gönderir ve özeti kaydeder."
    )
    @PostMapping("/tickets/{ticketId}/generate")
    public ResponseEntity<AiSummaryResponseDTO> generateForTicket(
            @Parameter(description = "Özetlenecek ticket ID'si") @PathVariable Long ticketId,
            @Parameter(description = "Özet dili: tr veya en") @RequestParam(defaultValue = "tr") String language) {
        log.info("Otomatik özetleme isteği. TicketId: {}, Dil: {}", ticketId, language);
        SummarizeRequestDTO request = ticketDataFetcher.fetchTicketData(ticketId, language);
        AiSummaryResponseDTO response = aiSummaryService.summarize(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the most recently generated summary for the given ticket.
     *
     * @param ticketId ID of the queried ticket
     * @return the latest summary
     */
    @Operation(
            summary = "Ticket'ın en son özetini getir",
            description = "Belirtilen ticket için en son oluşturulan özeti döner."
    )
    @GetMapping("/tickets/{ticketId}/latest")
    public ResponseEntity<AiSummaryResponseDTO> getLatest(
            @Parameter(description = "Ticket ID") @PathVariable Long ticketId) {
        return ResponseEntity.ok(aiSummaryService.getLatestSummary(ticketId));
    }

    /**
     * Lists every summary generated for the given ticket, sorted from newest to oldest.
     *
     * @param ticketId ID of the queried ticket
     * @return list of summaries (may be empty)
     */
    @Operation(
            summary = "Ticket'ın tüm özetlerini listele",
            description = "Belirtilen ticket için oluşturulmuş tüm özetleri en yeniden eskiye sıralı döner."
    )
    @GetMapping("/tickets/{ticketId}")
    public ResponseEntity<List<AiSummaryResponseDTO>> getAll(
            @Parameter(description = "Ticket ID") @PathVariable Long ticketId) {
        return ResponseEntity.ok(aiSummaryService.getAllSummaries(ticketId));
    }
}
