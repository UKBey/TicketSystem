package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.WorkflowCallbackDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
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

import java.time.ZonedDateTime;

@Log4j2
@Tag(name = "Workflow Callback", description = "jBPM KIE Server'dan gelen dahili SLA ihlali ve süreç olayları (Internal API)")
@RestController
@RequestMapping("/api/internal/workflow")
@RequiredArgsConstructor
public class WorkflowCallbackController {

    private final TicketRepository ticketRepository;

    @Value("${jbpm.kie-server.callback-token}")
    private String expectedToken;

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
        if (headerToken == null || !headerToken.equals(expectedToken)) {
            log.warn("Yetkisiz Callback İsteği! Token eşleşmedi veya gönderilmedi.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized internal API call");
        }

        log.info("jBPM Callback Alındı: TicketId={}, EventType={}, ProcessInstanceId={}, EkData={}",
                callback.getTicketId(), callback.getEventType(), callback.getProcessInstanceId(), callback.getAdditionalData());

        // Olayin bagli oldugu bilet kaydi bulunur.
        Ticket ticket = ticketRepository.findById(callback.getTicketId())
                .orElse(null);

        if (ticket == null) {
            log.error("Callback hatası: Bilet bulunamadı! TicketId={}", callback.getTicketId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ticket not found");
        }

        // Olay tipine gore alan guncelleme veya takip adimi calistirilir.
        switch (callback.getEventType()) {
            case "SLA_BREACHED":
                handleSlaBreach(ticket);
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

    private void handleSlaBreach(Ticket ticket) {
        log.warn("SLA AŞIMI GERÇEKLEŞTİ! TicketId={}", ticket.getId());
        ticket.setSlaBreached(true);
        ticketRepository.save(ticket);
        
        // Ileride bildirim, otomatik eskalasyon gibi ek aksiyonlar buradan zincirlenebilir.
    }
}
