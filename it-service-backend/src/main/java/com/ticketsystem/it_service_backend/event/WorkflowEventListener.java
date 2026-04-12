package com.ticketsystem.it_service_backend.event;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bilet oluşturma transaction'ı başarıyla commit'lendikten SONRA
 * jBPM workflow sürecini başlatan olay dinleyicisi.
 *
 * Bu sayede:
 * - DB transaction süresi kısa kalır (HTTP çağrısı dışarıda)
 * - DB connection pool tüketimi önlenir
 * - Workflow hatası bilet oluşturmayı etkilemez
 * - processInstanceId ayrı bir transaction'da güncellenir
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class WorkflowEventListener {

    private final WorkflowService workflowService;
    private final TicketRepository ticketRepository;

    /**
     * Transaction COMMIT'lendikten sonra tetiklenir.
     * jBPM'de workflow başlatır ve processInstanceId'yi ayrı bir
     * transaction'da ticket'a kaydeder.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketCreated(TicketCreatedEvent event) {
        Ticket ticket = event.ticket();
        log.info("TicketCreatedEvent alındı — workflow başlatılıyor. TicketId={}", ticket.getId());

        try {
            Long processInstanceId = workflowService.startTicketWorkflow(ticket);

            // processInstanceId'yi ayrı bir transaction'da güncelle
            ticket.setProcessInstanceId(processInstanceId);
            ticketRepository.save(ticket);
            log.info("Workflow bağlantısı kaydedildi. TicketId={}, ProcessInstanceId={}",
                    ticket.getId(), processInstanceId);

        } catch (Exception e) {
            log.error("Workflow başlatılamadı, ancak bilet zaten oluşturuldu. TicketId={}, Hata={}",
                    ticket.getId(), e.getMessage());
            // Bilet oluşturma zaten commit'lenmiş — workflow sonradan yeniden denenebilir
        }
    }
}
