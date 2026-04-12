package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

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
}
