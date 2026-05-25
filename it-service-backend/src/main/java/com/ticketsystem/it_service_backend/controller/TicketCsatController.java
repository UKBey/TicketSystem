package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CsatDTO;
import com.ticketsystem.it_service_backend.entity.Csat;
import com.ticketsystem.it_service_backend.service.CsatService;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for customer satisfaction (CSAT) surveys submitted at ticket closure.
 *
 * <p>A customer can submit a survey only for their own {@code RESOLVED} ticket;
 * reading the results is restricted to the {@code AGENT_ADMIN} role. Business rules
 * are enforced in {@link CsatService}.
 */
@Tag(name = "Ticket CSAT", description = "Bilet kapanışında doldurulan müşteri memnuniyet anketleri (1-5 puan)")
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketCsatController {

    private final CsatService csatService;

    /**
     * Saves the customer's CSAT survey and moves the ticket into the {@code CLOSED} status.
     *
     * @param id ticket identifier
     * @param csatDTO rating between 1 and 5 and an optional comment
     * @return the created {@link Csat} record
     */
    @Operation(summary = "CSAT anketi gönder",
            description = """
                    Müşteri, çözülen bilet (`RESOLVED` statüsünde) için memnuniyet anketi doldurur.
                    Anket gönderildikten sonra bilet otomatik olarak `CLOSED` statüsüne geçer.
                    
                    **Kurallar:**
                    - Yalnızca biletin sahibi (CUSTOMER) anket doldurabilir
                    - Bilet `RESOLVED` statüsünde olmalıdır
                    - Her bilet için yalnızca bir kez anket gönderilebilir
                    - Puan (rating) 1-5 arasında zorunludur; yorum (comment) opsiyoneldir
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSAT anketi başarıyla kaydedildi ve bilet kapatıldı",
                    content = @Content(schema = @Schema(implementation = Csat.class))),
            @ApiResponse(responseCode = "400", description = "Geçersiz puan değeri veya bilet uygun statüde değil"),
            @ApiResponse(responseCode = "403", description = "Yalnızca bilet sahibi CSAT gönderebilir")
    })
    @PostMapping("/{id}/csat")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Csat> submitCsat(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @RequestBody CsatDTO csatDTO,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(csatService.submitCsat(id, csatDTO, userId, roles));
    }

    /**
     * Returns the CSAT survey result for the specified ticket.
     *
     * @param id ticket identifier
     * @return the matching {@link Csat} record; 404 if none exists
     */
    @Operation(summary = "CSAT detayını getir",
            description = "Belirtilen biletin müşteri memnuniyet anket sonucunu getirir.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSAT sonucu başarıyla döndü",
                    content = @Content(schema = @Schema(implementation = Csat.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER erişebilir"),
            @ApiResponse(responseCode = "404", description = "Bu bilet için CSAT anketi bulunamadı")
    })
    @GetMapping("/{id}/csat")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<Csat> getCsat(
            @Parameter(description = "Biletin ID'si", example = "42", required = true)
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        List<String> roles = JwtUtils.extractRoles(jwt);
        return ResponseEntity.ok(csatService.getCsatByTicketId(id, userId, roles));
    }

    /**
     * Returns all CSAT survey results in the system; intended for reporting and management.
     *
     * @return list of all {@link Csat} records
     */
    @Operation(summary = "Tüm CSAT anketlerini listele",
            description = "Sistemdeki tüm müşteri memnuniyet sonuçlarını getirir. Raporlama ve yönetim amaçlıdır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tüm CSAT sonuçları başarıyla listelendi"),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER erişebilir")
    })
    @GetMapping("/all-csats")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<List<Csat>> getAllCsats() {
        return ResponseEntity.ok(csatService.getAllCsats());
    }
}
