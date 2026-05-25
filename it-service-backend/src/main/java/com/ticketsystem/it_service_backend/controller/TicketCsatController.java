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
 * Bilet kapanışında müşteri memnuniyet (CSAT) anketleri için REST kontrolcüsü.
 *
 * <p>Müşteri yalnızca kendi {@code RESOLVED} biletine anket gönderir;
 * sonuçları okuma yetkisi {@code AGENT_ADMIN} rolüne aittir. İş kuralları
 * {@link CsatService}'te uygulanır.
 */
@Tag(name = "Ticket CSAT", description = "Bilet kapanışında doldurulan müşteri memnuniyet anketleri (1-5 puan)")
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketCsatController {

    private final CsatService csatService;

    /**
     * Müşterinin CSAT anketini kaydeder ve bileti {@code CLOSED} statüsüne geçirir.
     *
     * @param id biletin kimliği
     * @param csatDTO 1-5 arası puan ve opsiyonel yorum
     * @return oluşturulan {@link Csat} kaydı
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
     * Belirtilen biletin CSAT anket sonucunu döner.
     *
     * @param id biletin kimliği
     * @return ilgili {@link Csat} kaydı; yoksa 404
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
     * Sistemdeki tüm CSAT anket sonuçlarını döner; raporlama/yönetim amaçlıdır.
     *
     * @return tüm {@link Csat} kayıtlarının listesi
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
