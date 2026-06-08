package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.service.TicketDtoAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Ticket data endpoints for service-to-service (internal) communication.
 *
 * <p>These endpoints are only reachable with the {@code X-Internal-Token} header
 * (configured in SecurityConfig; no JWT is required). The primary consumer is the
 * LLM service, which fetches all the context for a ticket in a single call. The
 * actual data gathering and DTO assembly is delegated to {@link TicketDtoAssembler};
 * this controller only maps the HTTP request.
 */
@Log4j2
@Tag(name = "Internal", description = "Servisler arası iletişim endpoint'leri (JWT gerektirmez, internal token gerektirir)")
@RestController
@RequestMapping("/api/v1/internal/tickets")
@RequiredArgsConstructor
public class InternalTicketController {

    private final TicketDtoAssembler ticketDtoAssembler;

    /**
     * Returns all data for a ticket in a single call, intended for the LLM service.
     *
     * <p>Comments, worklogs, audit log, list of claiming agents, SLA information and
     * matching known-issue records are bundled into the same response.
     *
     * @param ticketId identifier of the ticket whose data is requested
     * @return map containing the {@code ticket}, {@code comments}, {@code worklogs} and {@code knownIssues} keys
     */
    @Operation(summary = "Ticket'ın tüm verisini getir (internal)")
    @GetMapping("/{ticketId}/full")
    public ResponseEntity<Map<String, Object>> getFullTicketData(@PathVariable Long ticketId) {
        log.info("Internal full ticket data isteği. TicketId: {}", ticketId);
        return ResponseEntity.ok(ticketDtoAssembler.buildFullTicketData(ticketId));
    }
}
