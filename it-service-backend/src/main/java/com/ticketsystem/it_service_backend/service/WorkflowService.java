package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.service.SlaPolicyService;
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

    private final SlaPolicyService slaPolicyService;

    // process-id ve callback-base-url icin inline default'lar application.yml ile aynidir;
    // application.yml'in bozulmasi durumunda ikincil korumadir. Token bilinen bir secret oldugu
    // icin default yoktur — eksikse Spring boot'ta fail-fast yapar (guvenli davranis).
    @Value("${jbpm.kie-server.process-id:com.ticketsystem.workflow.ticket-lifecycle}")
    private String processId;

    @Value("${jbpm.kie-server.callback-base-url:http://host.docker.internal:8081/api/v1/internal/workflow/callback}")
    private String callbackBaseUrl;

    @Value("${jbpm.kie-server.callback-token}")
    private String callbackToken;

    private long getSlaDurationMs(String priority) {
        return slaPolicyService.getSlaDurationMs(priority);
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
     * Biletin anlık SLA bilgisini ve görsel durumunu (slaState) istemci tarafı için hesaplar.
     *
     * slaState değerleri:
     *   "active"    — SLA sayacı çalışıyor (NEW, IN_PROGRESS)
     *   "paused"    — SLA duraklatıldı, kalan süre var (WAITING_FOR_CUSTOMER, RESOLVED)
     *   "expired"   — SLA s��resi doldu (ihlal kaydı olsun ya da olmasın)
     *   "completed" — Bilet kapandı, SLA artık izlenmiyor (CLOSED)
     *
     * Karar önceliği:
     *   1. CLOSED         → her zaman "completed"  (ihlal kaydı DB'de korunur, badge etkilenmez)
     *   2. slaBreached    → "expired"
     *   3. Duraklı mod    → remaining > 0 ise "paused", değilse "expired"
     *   4. Aktif mod      → "active" ile geri sayım
     */
    public java.util.Map<String, Object> getSlaTimerInfo(com.ticketsystem.it_service_backend.entity.Ticket ticket) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        String status = ticket.getStatus() != null ? ticket.getStatus() : "";

        // CLOSED: süreç bitti — ihlal durumundan bağımsız olarak "completed"
        if ("CLOSED".equals(status)) {
            result.put("deadlineTimestamp", -1L);
            result.put("remainingMs", 0L);
            result.put("slaState", "completed");
            return result;
        }

        // Resmi ihlal kaydı varsa → "expired"
        if (Boolean.TRUE.equals(ticket.getSlaBreached())) {
            result.put("deadlineTimestamp", -1L);
            result.put("remainingMs", 0L);
            result.put("slaState", "expired");
            return result;
        }

        long elapsedMs = ticket.getSlaElapsedMs() != null ? ticket.getSlaElapsedMs() : 0L;

        boolean isPaused = ticket.getSlaPausedAt() != null
                || "RESOLVED".equals(status)
                || "WAITING_FOR_CUSTOMER".equals(status);

        if (isPaused) {
            // Derive the original SLA duration from the ticket's own deadline instead of the
            // current cached policy. This prevents badge flickering when the policy cache
            // expires and briefly returns a stale value different from what the ticket was
            // originally committed to.
            long originalDurationMs = (ticket.getSlaDeadline() != null && ticket.getCreatedAt() != null)
                    ? Duration.between(ticket.getCreatedAt(), ticket.getSlaDeadline()).toMillis()
                    : getSlaDurationMs(ticket.getPriority());

            long remaining = originalDurationMs - elapsedMs;
            result.put("deadlineTimestamp", -1L);
            result.put("remainingMs", Math.max(0L, remaining));
            result.put("slaState", remaining > 0 ? "paused" : "expired");
            return result;
        }

        // Aktif geri sayım — resume noktası ve birikmiş süreye göre hesaplanır.
        // slaDeadline DB'de varsa onu kullan (en güvenilir kaynak); yoksa dinamik hesapla.
        long durationMs = getSlaDurationMs(ticket.getPriority());
        long deadline;
        if (ticket.getSlaDeadline() != null) {
            deadline = ticket.getSlaDeadline().toInstant().toEpochMilli();
        } else {
            long resumedMs = ticket.getSlaResumedAt() != null
                    ? ticket.getSlaResumedAt().toInstant().toEpochMilli()
                    : (ticket.getCreatedAt() != null
                            ? ticket.getCreatedAt().toInstant().toEpochMilli()
                            : System.currentTimeMillis());
            long remaining = durationMs - elapsedMs;
            deadline = resumedMs + remaining;
        }
        long remainingMs = deadline - System.currentTimeMillis();

        if (remainingMs <= 0) {
            // Süre dolmuş ama slaBreached henüz DB'ye yazılmamış (async gecikme) → expired
            result.put("deadlineTimestamp", deadline);
            result.put("remainingMs", 0L);
            result.put("slaState", "expired");
            return result;
        }

        result.put("deadlineTimestamp", deadline);
        result.put("remainingMs", remainingMs);
        result.put("slaState", "active");
        return result;
    }

}