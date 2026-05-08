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
 * Ticket tarafindaki workflow adimlarini tek servis uzerinde toplar.
 * TicketService bu katmanla jBPM ayrintilarindan ayristirilir.
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
        if (priority == null) return 12 * 60 * 60 * 1000L;
        return switch (priority.toUpperCase()) {
            case "LOW"      -> 24 * 60 * 60 * 1000L;  // 24 saat
            case "MEDIUM"   -> 12 * 60 * 60 * 1000L;  // 12 saat
            case "HIGH"     ->  4 * 60 * 60 * 1000L;  //  4 saat
            case "CRITICAL" ->  1 * 60 * 60 * 1000L;  //  1 saat
            default         -> 12 * 60 * 60 * 1000L;
        };
    }


    // Yeni olusturulan bilet icin surec baslatir ve instance kimligini dondurur.
    public Long startTicketWorkflow(Ticket ticket) {
        log.info("Ticket için workflow başlatılıyor. TicketId={}, Priority={}, CustomerId={}",
                ticket.getId(), ticket.getPriority(), ticket.getCustomerId());

        Map<String, Object> processVariables = new HashMap<>();
        processVariables.put("ticketId", String.valueOf(ticket.getId()));
        processVariables.put("priority", ticket.getPriority());
        processVariables.put("customerId", ticket.getCustomerId());
        processVariables.put("status", ticket.getStatus());

        // SLA suresi, jBPM timer'inin bekledigi ISO-8601 formatinda gonderilir.
        processVariables.put("slaDuration", msToIsoDuration(getSlaDurationMs(ticket.getPriority())));

        // Callback adresi ortam bazli oldugu icin surece degisken olarak verilir.
        String fullCallbackUrl = callbackBaseUrl + "?token=" + callbackToken;
        processVariables.put("callbackUrl", fullCallbackUrl);

        // Yeni biletler her zaman NEW statüsünde oluşur; claim bilgisi yoktur.

        Long processInstanceId = kieServerAdapter.startProcess(processId, processVariables);

        log.info("Workflow başarıyla başlatıldı. TicketId={}, ProcessInstanceId={}",
                ticket.getId(), processInstanceId);
        return processInstanceId;
    }

    /**
     * Bilet durumunu workflow degiskeni ile senkron tutar.
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
     * Claim alan ajanın bilgisini workflow tarafına aktarır.
     * Çok-agentli yapıda en son claim'i alan ajanın ID'si iletilir.
     */
    public void syncTicketAssignment(Ticket ticket, String agentId) {
        if (ticket.getProcessInstanceId() == null) {
            log.warn("Ticket'ın processInstanceId'si yok, assignment sync atlanıyor. TicketId={}", ticket.getId());
            return;
        }

        log.info("Ticket ataması jBPM'e senkronize ediliyor. TicketId={}, AgentId={}, ProcessInstanceId={}",
                ticket.getId(), agentId, ticket.getProcessInstanceId());

        kieServerAdapter.setProcessVariable(ticket.getProcessInstanceId(), "assigneeId", agentId);
        kieServerAdapter.setProcessVariable(ticket.getProcessInstanceId(), "status", ticket.getStatus());
    }

    /**
     * SLA sayaç ilerleyisini durdurur ve o ana kadar gecen sureyi birikimli alana yazar.
     */
        public void pauseSla(Ticket ticket) {
        // Son baslangic noktasindan itibaren gecen sureyi toplama ekler.
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

        // Workflow'a pause sinyali gondererek aktif SLA akisini bekleme koluna alir.
        try {
            kieServerAdapter.signalProcessInstance(ticket.getProcessInstanceId(), "pause_sla", null);
        } catch (Exception e) {
            log.error("pause_sla sinyali gönderilemedi. TicketId={}, Hata={}", ticket.getId(), e.getMessage());
        }
    }

    /**
     * SLA sayacini kalan sure uzerinden kaldigi yerden devam ettirir.
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

        // Kalan sure workflow degiskenine yazilir.
        kieServerAdapter.setProcessVariable(ticket.getProcessInstanceId(), "slaDuration", remainingDuration);

        // Sonrasinda resume sinyali ile aktif SLA akisi yeniden baslatilir.
        try {
            kieServerAdapter.signalProcessInstance(ticket.getProcessInstanceId(), "resume_sla", remainingDuration);
        } catch (Exception e) {
            log.error("resume_sla sinyali gönderilemedi. TicketId={}, Hata={}", ticket.getId(), e.getMessage());
        }
    }

    /**
     * Bilet kapanisinda sureci sinyal gondererek sonlandirir.
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
            // Sinyal calismazsa sureci dogrudan abort ederek acik instance birakmaz.
            try {
                kieServerAdapter.abortProcess(ticket.getProcessInstanceId());
            } catch (Exception ex) {
                log.error("Abort fallback'i de başarısız oldu. TicketId={}, Hata={}",
                        ticket.getId(), ex.getMessage());
            }
        }
    }

    /**
     * Silinen veya iptal edilen biletin surecini abort ederek kapatir.
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
     * Milisaniye degerini ISO-8601 duration metnine cevirir.
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
     * Biletin anlik SLA bilgisini istemci tarafi icin hesaplar.
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

        // Aktif durumda kalan sure, resume noktasi ve birikmis sureye gore hesaplanir.
        long resumedMs = ticket.getSlaResumedAt() != null ? ticket.getSlaResumedAt().toInstant().toEpochMilli() : 
                         (ticket.getCreatedAt() != null ? ticket.getCreatedAt().toInstant().toEpochMilli() : System.currentTimeMillis());
        
        long remaining = durationMs - elapsedMs;
        long deadline = resumedMs + remaining;

        result.put("deadlineTimestamp", deadline);
        result.put("remainingMs", Math.max(0, deadline - System.currentTimeMillis()));
        return result;
    }

}