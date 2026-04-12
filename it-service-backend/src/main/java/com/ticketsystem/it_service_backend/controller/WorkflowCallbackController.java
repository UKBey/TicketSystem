package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.WorkflowCallbackDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Workflow Callback", description = "jBPM'den gelecek olan iç bildirimler (Internal API)")
@RestController
@RequestMapping("/api/internal/workflow")
@RequiredArgsConstructor
public class WorkflowCallbackController {

    private final TicketRepository ticketRepository;

    @Value("${jbpm.kie-server.callback-token}")
    private String expectedToken;

    @Operation(summary = "jBPM Süreç Olayı Bildirimi", description = "jBPM süreci SLA zaman aşımı veya görev tamamlanmasını backend'e buradan bildirir.")
    @PostMapping("/callback")
    public ResponseEntity<String> handleWorkflowCallback(
            @RequestHeader(value = "X-Internal-Token", required = false) String headerToken,
            @RequestParam(value = "token", required = false) String queryToken,
            @Valid @RequestBody WorkflowCallbackDTO callback) {

        // 1. Güvenlik Kontrolü (Header VEYA Query Parameter üzerinden)
        String token = headerToken != null ? headerToken : queryToken;
        if (token == null || !token.equals(expectedToken)) {
            log.warn("Yetkisiz Callback İsteği! Token eşleşmedi veya gönderilmedi.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized internal API call");
        }

        log.info("jBPM Callback Alındı: TicketId={}, EventType={}, ProcessInstanceId={}, EkData={}",
                callback.getTicketId(), callback.getEventType(), callback.getProcessInstanceId(), callback.getAdditionalData());

        // 2. İlgili Ticket'ı Bul
        Ticket ticket = ticketRepository.findById(callback.getTicketId())
                .orElse(null);

        if (ticket == null) {
            log.error("Callback hatası: Bilet bulunamadı! TicketId={}", callback.getTicketId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Ticket not found");
        }

        // 3. Olay Türüne Göre İşlem Yap (İleride genişletilebilir)
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
        
        // Burada ileride ek işlemler tetiklenebilir: 
        // - E-posta bildirimi gönderme (NotificationService)
        // - Priority'i yükseltme, vb.
    }
}
