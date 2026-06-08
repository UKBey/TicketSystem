package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.WorkflowCallbackDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Internal REST controller that receives process events from the jBPM KIE Server.
 *
 * <p>Secured with the fixed {@code X-Internal-Token} header instead of JWT; handles
 * SLA breach and process completion events and triggers notifications via
 * {@link NotificationService} when needed.
 */
@Log4j2
@Tag(name = "Workflow Callback", description = "jBPM KIE Server'dan gelen dahili SLA ihlali ve süreç olayları (Internal API)")
@RestController
@RequestMapping("/api/v1/internal/workflow")
@RequiredArgsConstructor
public class WorkflowCallbackController {

    private final TicketService ticketService;

    @Value("${jbpm.kie-server.callback-token}")
    private String expectedToken;

    /**
     * Handles a jBPM process event notification ({@code SLA_BREACHED} / {@code PROCESS_COMPLETED}).
     *
     * <p>The token is verified with a constant-time comparison; a mismatch returns {@code 401}.
     * The handler is idempotent if the SLA breach callback for the same ticket is delivered repeatedly.
     *
     * @param headerToken inter-service authentication token
     * @param callback event type, ticket identifier and process information
     * @return plain-text outcome; {@code 400}/{@code 401}/{@code 404} on the corresponding error cases
     */
    @Operation(summary = "jBPM süreç olayı bildirimi",
            description = """
                    jBPM KIE Server, SLA zaman aşımı veya süreç tamamlanması gibi olayları bu endpoint üzerinden backend'e bildirir.
                    
                    **Güvenlik:** Bu endpoint JWT yerine `X-Internal-Token` başlığıyla korunur.
                    Token, `application.yml` içindeki `jbpm.kie-server.callback-token` değeriyle eşleşmelidir.
                    
                    **Desteklenen olay tipleri:**
                    - `SLA_BREACHED`: SLA süresi dolmuş; bilet `slaBreached=true` olarak işaretlenir
                    - `PROCESS_COMPLETED`: jBPM süreci başarıyla sonlandı (bilgi amaçlı loglama)
                    
                    **İlerideki genişleme noktaları:** E-posta bildirimi, otomatik eskalasyon, Slack webhook
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback başarıyla işlendi"),
            @ApiResponse(responseCode = "400", description = "Bilinmeyen olay tipi gönderildi"),
            @ApiResponse(responseCode = "401", description = "X-Internal-Token başlığı eksik veya hatalı"),
            @ApiResponse(responseCode = "404", description = "Belirtilen bilet bulunamadı")
    })
    @PostMapping("/callback")
    public ResponseEntity<String> handleWorkflowCallback(
            @Parameter(description = "Servisler arası kimlik doğrulama token'ı", required = true,
                    example = "my-secret-callback-token-2024")
            @RequestHeader(value = "X-Internal-Token", required = false) String headerToken,
            @Valid @RequestBody WorkflowCallbackDTO callback) {

        // Callback sadece servisler arasi paylasilan token ile kabul edilir.
        // Constant-time karşılaştırma: String.equals early-return yaptığı için
        // timing attack ile token karakter-karakter tahmin edilebilirdi.
        if (!constantTimeEquals(headerToken, expectedToken)) {
            log.warn("Yetkisiz Callback İsteği! Token eşleşmedi veya gönderilmedi.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized internal API call");
        }

        log.info("jBPM Callback Alındı: TicketId={}, EventType={}, ProcessInstanceId={}, EkData={}",
                callback.getTicketId(), callback.getEventType(), callback.getProcessInstanceId(), callback.getAdditionalData());

        // Olayin bagli oldugu bilet kaydi bulunur. Bulunamazsa protokol geregi 404 doneriz;
        // bu yuzden servisin throw eden getTicketById'si yerine Optional donen findById kullanilir.
        Ticket ticket = ticketService.findById(callback.getTicketId()).orElse(null);

        if (ticket == null) {
            log.error("Callback hatası: Bilet bulunamadı! TicketId={}", callback.getTicketId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ticket not found");
        }

        // Olay tipine gore is mantigi servis katmanina delege edilir; controller yalnizca
        // protokol (token, event tipi, HTTP statu) ile ilgilenir.
        switch (callback.getEventType()) {
            case "SLA_BREACHED":
                // SLA bayragini set+kaydet+bildir; idempotent — tekrar callback'te no-op.
                ticketService.markSlaBreached(ticket);
                break;
            case "PROCESS_COMPLETED":
                log.info("TicketId={} süreci KIE Server'da başarıyla sonlandı.", ticket.getId());
                break;
            default:
                log.warn("Bilinmeyen Callback Olayı: {}", callback.getEventType());
                return ResponseEntity.badRequest().body("Unknown event type: " + callback.getEventType());
        }

        return ResponseEntity.ok("Callback processed successfully");
    }

    /**
     * Constant-time token comparison. {@code MessageDigest.isEqual} runs in constant
     * time regardless of the byte lengths, so the token cannot leak through response timing.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }
}
