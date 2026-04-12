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

    /** Varsayılan SLA süresi — test için 2 dakika (üretimde PT4H/PT8H/PT24H olur) */
    private static final long DEFAULT_SLA_DURATION_MS = 2 * 60 * 1000; // 2 dakika

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
        processVariables.put("slaDuration", "PT2M");

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
        if (ticket.getProcessInstanceId() == null) {
            log.debug("processInstanceId yok, SLA pause atlanıyor. TicketId={}", ticket.getId());
            return;
        }

        // Toplam geçen süreyi hesapla
        ZonedDateTime slaStartPoint = ticket.getSlaPausedAt() != null
                ? ticket.getSlaPausedAt() // En son resume'dan beri
                : ticket.getCreatedAt();  // İlk kez duraklatılıyor

        // Eğer zaten duraklatılmış bir bilet tekrar duraklatılmaya çalışılırsa
        if (ticket.getSlaPausedAt() != null && ticket.getSlaElapsedMs() > 0) {
            log.debug("SLA zaten duraklatılmış durumda gibi görünüyor. TicketId={}", ticket.getId());
        }

        long currentSegmentElapsed = Duration.between(slaStartPoint, ZonedDateTime.now()).toMillis();
        long totalElapsed = ticket.getSlaElapsedMs() + currentSegmentElapsed;

        ticket.setSlaElapsedMs(totalElapsed);
        ticket.setSlaPausedAt(ZonedDateTime.now());

        log.info("SLA duraklatılıyor. TicketId={}, ToplamGeçenSüre={}ms, KalanSLA={}ms",
                ticket.getId(), totalElapsed, DEFAULT_SLA_DURATION_MS - totalElapsed);

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
        if (ticket.getProcessInstanceId() == null) {
            log.debug("processInstanceId yok, SLA resume atlanıyor. TicketId={}", ticket.getId());
            return;
        }

        long remainingMs = DEFAULT_SLA_DURATION_MS - ticket.getSlaElapsedMs();
        if (remainingMs <= 0) {
            log.warn("SLA süresi zaten dolmuş! TicketId={}, ElapsedMs={}", ticket.getId(), ticket.getSlaElapsedMs());
            remainingMs = 1000; // Minimum 1 saniye
        }

        // ISO 8601 Duration formatına çevir (Örn: PT1M30S)
        String remainingDuration = msToIsoDuration(remainingMs);

        // slaPausedAt'i temizle (artık aktif)
        ticket.setSlaPausedAt(null);

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
}
