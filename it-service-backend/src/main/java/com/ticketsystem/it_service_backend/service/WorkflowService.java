package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * İş mantığı facade katmanı.
 * <p>
 * Ticket yaşam döngüsüyle ilgili workflow operasyonlarını yönetir.
 * KieServerAdapter'ı kullanarak jBPM ile haberleşir, ancak
 * TicketService'e jBPM detaylarını sızdırmaz.
 * </p>
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class WorkflowService {

    private final KieServerAdapter kieServerAdapter;

    @Value("${jbpm.kie-server.process-id}")
    private String processId;

    @Value("${jbpm.kie-server.callback-base-url}")
    private String callbackBaseUrl;

    @Value("${jbpm.kie-server.callback-token}")
    private String callbackToken;

    private long getSlaDurationMs(String priority) {
        if (priority == null) return 10 * 60 * 1000L;
        return switch (priority.toUpperCase()) {
            case "LOW" -> 20 * 60 * 1000L;
            case "MEDIUM" -> 10 * 60 * 1000L;
            case "HIGH" -> 5 * 60 * 1000L;
            case "CRITICAL" -> 1 * 60 * 1000L;
            default -> 10 * 60 * 1000L;
        };
    }


    /**
     * Yeni bir ticket için workflow sürecini başlatır.
     * Ticket oluşturulduktan sonra çağrılır ve processInstanceId'yi döner.
     *
     * @param ticket Veritabanına kaydedilmiş ticket
     * @return jBPM ProcessInstance ID
     */
    public Long startTicketWorkflow(Ticket ticket) {
        log.info("Ticket için workflow başlatılıyor. TicketId={}, Priority={}, CustomerId={}",
                ticket.getId(), ticket.getPriority(), ticket.getCustomerId());

        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("ticketId", String.valueOf(ticket.getId()));
        processVariables.put("priority", ticket.getPriority());
        processVariables.put("customerId", ticket.getCustomerId());
        processVariables.put("status", ticket.getStatus());

        // SLA süresini ISO 8601 Duration formatında gönder (jBPM Timer için)
        processVariables.put("slaDuration", msToIsoDuration(getSlaDurationMs(ticket.getPriority())));

        // Fix 3: Callback URL'i süreç değişkeni olarak gönder (BPMN'de hardcoded olmaz)
        String fullCallbackUrl = callbackBaseUrl + "?token=" + callbackToken;
        processVariables.put("callbackUrl", fullCallbackUrl);

        // Eğer atanmış ajan varsa onu da gönder
        if (ticket.getAssigneeId() != null) {
            processVariables.put("assigneeId", ticket.getAssigneeId());
        }

        Long processInstanceId = kieServerAdapter.startProcess(processId, processVariables);

        log.info("Workflow başarıyla başlatıldı. TicketId={}, ProcessInstanceId={}",
                ticket.getId(), processInstanceId);
        return processInstanceId;
    }

    /**
     * Ticket statüsü değiştiğinde jBPM sürecindeki status değişkenini günceller.
     */
    public void syncTicketStatus(Ticket ticket) {
        if (ticket.getProcessInstanceId() == null) {
            log.warn("Ticket'ın processInstanceId'si yok, status sync atlanıyor. TicketId={}", ticket.getId());
            return;
        }

        log.info("Ticket statüsü jBPM'e senkronize ediliyor. TicketId={}, Status={}, ProcessInstanceId={}",
                ticket.getId(), ticket.getStatus(), ticket.getProcessInstanceId());

        kieServerAdapter.setProcessVariable(ticket.getProcessInstanceId(), "status", ticket.getStatus());
    }

    /**
     * Ticket atanma bilgisini jBPM sürecine yansıtır.
     */
    public void syncTicketAssignment(Ticket ticket) {
        if (ticket.getProcessInstanceId() == null) {
            log.warn("Ticket'ın processInstanceId'si yok, assignment sync atlanıyor. TicketId={}", ticket.getId());
            return;
        }

        log.info("Ticket ataması jBPM'e senkronize ediliyor. TicketId={}, AssigneeId={}, ProcessInstanceId={}",
                ticket.getId(), ticket.getAssigneeId(), ticket.getProcessInstanceId());

        kieServerAdapter.setProcessVariable(ticket.getProcessInstanceId(), "assigneeId", ticket.getAssigneeId());
        kieServerAdapter.setProcessVariable(ticket.getProcessInstanceId(), "status", ticket.getStatus());
    }

    /**
     * SLA kronometresini duraklatır.
     * WAITING_FOR_CUSTOMER veya RESOLVED durumuna geçildiğinde çağrılır.
     * <p>
     * Mantık: Ticket.createdAt'ten veya son resume anından bu yana geçen süreyi
     * slaElapsedMs'ye ekler ve slaPausedAt'i ayarlar.
     * jBPM'ye "pause_sla" sinyali göndererek SLA Timer'ı olan daldan çıkarır.
     * </p>
     */
        public void pauseSla(Ticket ticket) {
        // Toplam geçen süreyi hesapla
        if (ticket.getSlaPausedAt() != null) {
            log.debug("SLA zaten duraklatýlmý durumda gibi görünüyor. TicketId={}", ticket.getId());
            return;
        }

        ZonedDateTime slaStartPoint = ticket.getSlaResumedAt() != null ? ticket.getSlaResumedAt() : ticket.getCreatedAt();
        if (slaStartPoint == null) {
            slaStartPoint = ZonedDateTime.now();
        }
        
        long previousElapsed = ticket.getSlaElapsedMs() != null ? ticket.getSlaElapsedMs() : 0L;
        long currentSegmentElapsed = Duration.between(slaStartPoint, ZonedDateTime.now()).toMillis();
        long totalElapsed = previousElapsed + currentSegmentElapsed;

        ticket.setSlaElapsedMs(totalElapsed);
        ticket.setSlaPausedAt(ZonedDateTime.now());

        if (ticket.getProcessInstanceId() == null) {
            log.debug("processInstanceId yok, sadece veritabaný tarafýnda SLA duraklatýldý. TicketId={}", ticket.getId());
            return;
        }

        log.info("SLA duraklatýlýyor. TicketId={}, ToplamGeçenSüre={}ms, KalanSLA={}ms",
                ticket.getId(), totalElapsed, getSlaDurationMs(ticket.getPriority()) - totalElapsed);

        // jBPM'ye sinyal gönder — süreç "SLA aktif" dalından "Bekleme" dalına geçer
        try {
            kieServerAdapter.signalProcessInstance(ticket.getProcessInstanceId(), "pause_sla", null);
        } catch (Exception e) {
            log.error("pause_sla sinyali gönderilemedi. TicketId={}, Hata={}", ticket.getId(), e.getMessage());
        }
    }

    /**
     * SLA kronometresini kaldığı yerden devam ettirir.
     * WAITING_FOR_CUSTOMER → IN_PROGRESS veya RESOLVED → IN_PROGRESS geçişinde çağrılır.
     * <p>
     * Mantık: Toplam geçen süreyi DEFAULT_SLA_DURATION'dan çıkararak kalan süreyi
     * ISO 8601 Duration formatına çevirir ve jBPM'ye "resume_sla" sinyali ile gönderir.
     * jBPM yeni bir Timer ile bu kalan süreyi saymaya başlar.
     * </p>
     */
            public void resumeSla(Ticket ticket) {
        ticket.setSlaPausedAt(null);
        ticket.setSlaResumedAt(java.time.ZonedDateTime.now());

        if (ticket.getProcessInstanceId() == null) {
            log.debug("processInstanceId yok, sadece veritabaný tarafýnda SLA resume edildi. TicketId={}", ticket.getId());
            return;
        }

        long currentSlaDurationMs = getSlaDurationMs(ticket.getPriority());
        long remainingMs = Math.max(0, currentSlaDurationMs - (ticket.getSlaElapsedMs() != null ? ticket.getSlaElapsedMs() : 0));
        String remainingDuration = msToIsoDuration(remainingMs);

        log.info("SLA devam ettiriliyor. TicketId={}, KalanSüre={} ({}ms)",
                ticket.getId(), remainingDuration, remainingMs);

        // Önce jBPM'deki slaDuration değişkenini güncelle
        kieServerAdapter.setProcessVariable(ticket.getProcessInstanceId(), "slaDuration", remainingDuration);

        // Sonra resume sinyalini gönder — süreç "Bekleme" dalından tekrar "SLA aktif" dalına geçer
        try {
            kieServerAdapter.signalProcessInstance(ticket.getProcessInstanceId(), "resume_sla", remainingDuration);
        } catch (Exception e) {
            log.error("resume_sla sinyali gönderilemedi. TicketId={}, Hata={}", ticket.getId(), e.getMessage());
        }
    }

    /**
     * Ticket kapatıldığında veya silindiğinde ilgili süreci tamamen sonlandırır.
     * CLOSED durumuna geçişte çağrılır.
     */
    public void closeTicketWorkflow(Ticket ticket) {
        if (ticket.getProcessInstanceId() == null) {
            log.debug("processInstanceId yok, close ticket atlanıyor. TicketId={}", ticket.getId());
            return;
        }

        log.info("Ticket workflow'u kapatılıyor (ticket_closed sinyali). TicketId={}, ProcessInstanceId={}",
                ticket.getId(), ticket.getProcessInstanceId());

        try {
            kieServerAdapter.signalProcessInstance(ticket.getProcessInstanceId(), "ticket_closed", null);
        } catch (Exception e) {
            log.error("ticket_closed sinyali gönderilemedi. TicketId={}, Hata={}", ticket.getId(), e.getMessage());
            // Fallback: Süreci doğrudan abort et
            try {
                kieServerAdapter.abortProcess(ticket.getProcessInstanceId());
            } catch (Exception ex) {
                log.error("Abort fallback'i de başarısız oldu. TicketId={}, Hata={}",
                        ticket.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Ticket silindiğinde veya iptal edildiğinde ilgili süreci sonlandırır.
     */
    public void abortTicketWorkflow(Ticket ticket) {
        if (ticket.getProcessInstanceId() == null) {
            log.debug("Ticket'ın processInstanceId'si yok, abort atlanıyor. TicketId={}", ticket.getId());
            return;
        }

        log.info("Ticket workflow'u iptal ediliyor. TicketId={}, ProcessInstanceId={}",
                ticket.getId(), ticket.getProcessInstanceId());

        kieServerAdapter.abortProcess(ticket.getProcessInstanceId());
    }

    /**
     * Milisaniyeyi ISO 8601 Duration formatına çevirir.
     * Örn: 90000ms → "PT1M30S"
     */
    private String msToIsoDuration(long ms) {
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes > 0 && seconds > 0) {
            return String.format("PT%dM%dS", minutes, seconds);
        } else if (minutes > 0) {
            return String.format("PT%dM", minutes);
        } else {
            return String.format("PT%dS", Math.max(seconds, 1));
        }
    }


    /**
     * Gets SLA Timer information by asking KIE Server.
     */
    public java.util.Map<String, Long> getSlaTimerInfo(com.ticketsystem.it_service_backend.entity.Ticket ticket) {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        
        if (Boolean.TRUE.equals(ticket.getSlaBreached())) {
            result.put("deadlineTimestamp", -1L);
            result.put("remainingMs", 0L);
            return result;
        }

        long elapsedMs = ticket.getSlaElapsedMs() != null ? ticket.getSlaElapsedMs() : 0;
        long durationMs = getSlaDurationMs(ticket.getPriority());

        if (ticket.getSlaPausedAt() != null || "CLOSED".equals(ticket.getStatus()) || "RESOLVED".equals(ticket.getStatus()) || "WAITING_FOR_CUSTOMER".equals(ticket.getStatus())) {
            long remaining = durationMs - elapsedMs;
            result.put("deadlineTimestamp", -1L);
            result.put("remainingMs", Math.max(0, remaining));
            return result;
        }

        // Active ticket - dynamically calculate mathematically with resumedAt!
        long resumedMs = ticket.getSlaResumedAt() != null ? ticket.getSlaResumedAt().toInstant().toEpochMilli() : 
                         (ticket.getCreatedAt() != null ? ticket.getCreatedAt().toInstant().toEpochMilli() : System.currentTimeMillis());
        
        long remaining = durationMs - elapsedMs;
        long deadline = resumedMs + remaining;

        result.put("deadlineTimestamp", deadline);
        result.put("remainingMs", Math.max(0, deadline - System.currentTimeMillis()));
        return result;
    }

}