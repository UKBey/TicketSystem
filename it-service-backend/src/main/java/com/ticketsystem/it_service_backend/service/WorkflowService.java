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
 * Centralizes ticket-side workflow steps under a single service.
 * TicketService is decoupled from jBPM details through this layer.
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


    /**
     * Starts the jBPM process instance for a newly created ticket and returns the
     * instance ID. The SLA duration is sent in ISO-8601 format and the env-driven
     * callback URL is placed alongside the other process variables.
     *
     * @param ticket the new ticket
     * @return the started process instance ID
     * @throws RuntimeException if the KIE Server is unreachable or returns an error
     */
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
     * Advances the BPMN state machine to the ticket's current status. Used by
     * side-effect transitions (e.g. unclaim IN_PROGRESS→NEW) that change the
     * status outside of {@link TicketService#updateTicketStatus}. Silently
     * skipped when {@code processInstanceId} is missing. KIE Server failures are
     * only logged.
     *
     * <p>This drives the transition via {@link #requestStatusTransition} rather
     * than {@code setProcessVariable} — writing the {@code status} variable alone
     * does NOT move the process token to the matching state node, so the BPMN
     * would stay on the previous state and silently drop later transitions.
     *
     * @param ticket ticket carrying the (already updated) current status
     */
    public void syncTicketStatus(Ticket ticket) {
        if (ticket.getProcessInstanceId() == null) {
            log.warn("Ticket'ın processInstanceId'si yok, status sync atlanıyor. TicketId={}", ticket.getId());
            return;
        }

        log.info("Ticket statüsü jBPM state machine'e ilerletiliyor. TicketId={}, Status={}, ProcessInstanceId={}",
                ticket.getId(), ticket.getStatus(), ticket.getProcessInstanceId());

        // setProcessVariable status'u ZORLAMAZ — dogru state node'a gecis icin transition
        // sinyali sart. BPMN artik authoritative state machine oldugu icin status degisimini
        // transition ile surduruyoruz (yan-etki gecisleri dahil).
        requestStatusTransition(ticket, ticket.getStatus());
    }

    /**
     * Propagates the claiming agent to the workflow side and advances the BPMN
     * state machine to the ticket's (possibly auto-promoted) status. In the
     * multi-agent model the ID of the latest claim holder is sent.
     *
     * <p>The {@code assigneeId} is a plain process variable, but the status is
     * driven via {@link #requestStatusTransition}: a claim auto-promotes
     * NEW→IN_PROGRESS, and only the {@code transition_IN_PROGRESS} signal moves
     * the process token to {@code State_IN_PROGRESS}. Without it the process
     * stays on {@code State_NEW} and silently drops the next transition (e.g.
     * WAITING), which would make the user-facing status update return HTTP 400.
     *
     * @param ticket ticket carrying the (already updated) current status
     * @param agentId ID of the assigned/claiming agent
     */
    public void syncTicketAssignment(Ticket ticket, String agentId) {
        if (ticket.getProcessInstanceId() == null) {
            log.warn("Ticket'ın processInstanceId'si yok, assignment sync atlanıyor. TicketId={}", ticket.getId());
            return;
        }

        log.info("Ticket ataması jBPM'e senkronize ediliyor. TicketId={}, AgentId={}, ProcessInstanceId={}",
                ticket.getId(), agentId, ticket.getProcessInstanceId());

        kieServerAdapter.setProcessVariable(ticket.getProcessInstanceId(), "assigneeId", agentId);
        // status'u setProcessVariable ile ZORLAMAK yerine transition sinyali gonder: claim
        // NEW→IN_PROGRESS gibi yan-etki gecislerinde BPMN state node'u da ilerlesin, aksi
        // halde surec State_NEW'de takilir ve sonraki transition (orn. WAITING) dusurulur.
        requestStatusTransition(ticket, ticket.getStatus());
    }

    /**
     * Pauses the SLA counter and adds the elapsed segment to the cumulative field.
     * No-op when already paused. When {@code processInstanceId} is missing, only
     * the DB side is paused (the workflow signal is skipped).
     *
     * @param ticket ticket whose SLA is being paused
     */
        public void pauseSla(Ticket ticket) {
        // Son baslangic noktasindan itibaren gecen sureyi toplama ekler.
        if (ticket.getSlaPausedAt() != null) {
            log.debug("SLA zaten duraklatılmış durumda gibi görünüyor. TicketId={}", ticket.getId());
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
     * Resumes the SLA counter from the remaining duration. The remainder is
     * recomputed against the current priority and the jBPM timer is rescheduled.
     *
     * @param ticket ticket whose SLA is being resumed
     */
            public void resumeSla(Ticket ticket) {
        ticket.setSlaPausedAt(null);
        ticket.setSlaResumedAt(java.time.ZonedDateTime.now());

        // slaDeadline'i kalan AKTIF butce kadar simdiden ileri projekte et. Boylece
        // IN_PROGRESS badge'i ve SlaNotificationScheduler breach tespiti, duraklatmada
        // (WAITING/RESOLVED) gecen sureyi YANLISLIKLA dusmez — yalniz aktif gecen sure
        // sayilir. Eskiden bunu sadece priority-change yolu yapiyordu; normal resume
        // slaDeadline'i guncellemiyordu, bu yuzden badge duraklatmada gecen zamani
        // kaybediyordu.
        long resumeRemainingMs = Math.max(0L,
                getSlaDurationMs(ticket.getPriority())
                        - (ticket.getSlaElapsedMs() != null ? ticket.getSlaElapsedMs() : 0L));
        ticket.setSlaDeadline(ticket.getSlaResumedAt().plus(Duration.ofMillis(resumeRemainingMs)));

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
     * Terminates the process by sending the {@code ticket_closed} signal on ticket
     * closure. If the signal fails, {@link KieServerAdapter#abortProcess} is used
     * as a fallback. Silently skipped when {@code processInstanceId} is missing.
     *
     * @param ticket the closing ticket
     */
    /**
     * Requests a state transition by sending the corresponding {@code transition_<TARGET>}
     * signal to the BPMN process. The BPMN itself defines which transitions are valid by
     * which signals each state node listens to — invalid targets are silently dropped by
     * the process. This makes the BPMN the authoritative state machine for ticket status
     * changes.
     *
     * <p>The mapping is:
     * <ul>
     *   <li>{@code NEW} → {@code transition_NEW}</li>
     *   <li>{@code IN_PROGRESS} → {@code transition_IN_PROGRESS}</li>
     *   <li>{@code WAITING_FOR_CUSTOMER} → {@code transition_WAITING_FOR_CUSTOMER}</li>
     *   <li>{@code RESOLVED} → {@code transition_RESOLVED}</li>
     *   <li>{@code CLOSED} → {@code transition_CLOSED}</li>
     * </ul>
     *
     * <p>Silently skipped when {@code processInstanceId} is missing. Errors are logged but
     * not rethrown — the DB persistence in {@link TicketService} stays in charge of user-
     * visible failures.
     *
     * @param ticket the ticket whose state is changing
     * @param targetStatus the desired new status
     */
    public void requestStatusTransition(Ticket ticket, String targetStatus) {
        if (ticket.getProcessInstanceId() == null) {
            log.debug("processInstanceId yok, transition sinyali atlanıyor. TicketId={}, Target={}",
                    ticket.getId(), targetStatus);
            return;
        }
        if (targetStatus == null || targetStatus.isBlank()) return;

        String signal = "transition_" + targetStatus;
        log.info("State transition sinyali gönderiliyor. TicketId={}, ProcessInstanceId={}, Signal={}",
                ticket.getId(), ticket.getProcessInstanceId(), signal);
        try {
            kieServerAdapter.signalProcessInstance(ticket.getProcessInstanceId(), signal, null);
        } catch (Exception e) {
            log.error("State transition sinyali gönderilemedi. TicketId={}, Signal={}, Hata={}",
                    ticket.getId(), signal, e.getMessage());
        }
    }

    /**
     * Verifies that the BPMN process actually accepted the most recent transition request
     * by reading back its {@code status} process variable. Provides the "feedback" half of
     * the otherwise fire-and-forget {@link #requestStatusTransition} call.
     *
     * <p>jBPM signals are silently dropped when no state node is listening for them, so a
     * mismatch here means the BPMN state machine REJECTED the transition (typically because
     * the source state didn't allow that target). Mismatches are logged as warnings so the
     * out-of-sync can be detected in observability — they don't throw because the Java
     * {@link TicketService#VALID_TRANSITIONS} pre-flight catch should make this impossible
     * in practice; surfacing the mismatch tells us the two state machines drifted.
     *
     * @param ticket the ticket whose transition was just signalled
     * @param expectedStatus the target status the BPMN was supposed to enter
     * @return {@code true} if the BPMN variable matches {@code expectedStatus}, {@code false}
     *         on mismatch / read failure / missing process instance
     */
    /**
     * Returns {@code true} only when the ticket's BPMN process instance no longer
     * exists on the KIE Server (stale {@code processInstanceId} — e.g. jBPM history
     * pruned/reset). Lets callers treat the missing state machine like "no workflow"
     * and accept the DB-side transition instead of blocking the ticket forever.
     *
     * @param ticket the ticket whose process instance is checked
     * @return {@code true} if the instance is confirmed gone; {@code false} otherwise
     */
    public boolean isProcessInstanceMissing(Ticket ticket) {
        if (ticket.getProcessInstanceId() == null) return false;
        return kieServerAdapter.isProcessInstanceMissing(ticket.getProcessInstanceId());
    }

    public boolean verifyTransitionApplied(Ticket ticket, String expectedStatus) {
        if (ticket.getProcessInstanceId() == null || expectedStatus == null) return false;

        Object raw = kieServerAdapter.getProcessVariable(ticket.getProcessInstanceId(), "status");
        String actual = raw == null ? null : raw.toString();

        if (expectedStatus.equals(actual)) {
            log.debug("BPMN state confirmed. TicketId={}, Status={}", ticket.getId(), actual);
            return true;
        }

        // CLOSED is the only terminal target: the BPMN sets status=CLOSED and then fires a
        // terminate end event, so the instance COMPLETES and its `status` variable is no
        // longer readable (null). That is the success outcome, not a dropped signal —
        // confirm it via the process instance state instead of the now-gone variable,
        // otherwise CSAT-driven (and manual) closes would be falsely rejected with 400.
        if ("CLOSED".equals(expectedStatus) && actual == null
                && kieServerAdapter.isProcessFinished(ticket.getProcessInstanceId())) {
            log.debug("BPMN terminal transition (CLOSED) confirmed via process completion. TicketId={}",
                    ticket.getId());
            return true;
        }

        log.warn("BPMN state mismatch — signal dropped veya state machine reddetti. " +
                        "TicketId={}, ProcessInstanceId={}, Expected={}, Actual={}",
                ticket.getId(), ticket.getProcessInstanceId(), expectedStatus, actual);
        return false;
    }

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
     * Aborts the process for a deleted or cancelled ticket.
     * Silently skipped when {@code processInstanceId} is missing.
     *
     * @param ticket the ticket about to be deleted or cancelled
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
     * Converts a millisecond value to an ISO-8601 duration string.
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
     * Computes the ticket's live SLA information and visual state ({@code slaState}) for the client.
     *
     * <p>slaState values:
     * <ul>
     *   <li>{@code "active"} — SLA counter is running (NEW, IN_PROGRESS)</li>
     *   <li>{@code "paused"} — SLA paused with remaining time (WAITING_FOR_CUSTOMER, RESOLVED)</li>
     *   <li>{@code "expired"} — SLA time has elapsed (whether or not a breach was recorded)</li>
     *   <li>{@code "completed"} — Ticket closed, SLA no longer tracked (CLOSED)</li>
     * </ul>
     *
     * <p>Decision priority:
     * <ol>
     *   <li>CLOSED → always "completed" (breach record is preserved in the DB)</li>
     *   <li>{@code slaBreached} → "expired"</li>
     *   <li>Paused mode → "paused" when remaining &gt; 0, otherwise "expired"</li>
     *   <li>Active mode → "active" with a live countdown</li>
     * </ol>
     *
     * @param ticket ticket for which SLA information is being computed
     * @return map carrying the {@code slaState}, {@code remainingMs} and
     *         {@code deadlineTimestamp} keys
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
            // Paused kalan = (onceligin SLA butcesi) - (birikmis AKTIF sure).
            // slaElapsedMs yalniz aktif gecen sureyi tutar; duraklatma onu artirmaz,
            // bu yuzden bu deger duraklatma boyunca sabit (frozen) kalir. slaDeadline'dan
            // TURETMIYORUZ: resume slaDeadline'i ileri ittigi icin (slaDeadline - createdAt)
            // artik orijinal sureyi vermez. getSlaDurationMs in-memory config'ten okur
            // (cache yok), deterministiktir — flicker riski yoktur.
            long originalDurationMs = getSlaDurationMs(ticket.getPriority());
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
            long resumedMs;
            if (ticket.getSlaResumedAt() != null) {
                resumedMs = ticket.getSlaResumedAt().toInstant().toEpochMilli();
            } else if (ticket.getCreatedAt() != null) {
                resumedMs = ticket.getCreatedAt().toInstant().toEpochMilli();
            } else {
                resumedMs = System.currentTimeMillis();
            }
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