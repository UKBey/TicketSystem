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
 * Bilet event'lerini jBPM workflow servisine bagleyen koprü.
 *
 * <p>Listener {@code AFTER_COMMIT} fazinda calisir; boylece workflow yalnizca
 * bilet basariyla veritabanina yazildiktan sonra baslatilir ve rollback edilen
 * islemler icin gereksiz KIE cagrisi yapilmaz. KIE Server hatasi olusursa bilet
 * yine de kullanim icin hazirdir — workflow olmadan da bilet yasayabilir.
 */
@Component
@RequiredArgsConstructor
@Log4j2
public class WorkflowEventListener {

    private final WorkflowService workflowService;
    private final TicketRepository ticketRepository;


    /**
     * {@link TicketCreatedEvent} sonrasi KIE Server'da yeni bir
     * {@code ticket-lifecycle} process instance baslatir ve donen
     * {@code processInstanceId}'yi ayri transaction'da bilete yazar.
     *
     * <p>{@code REQUIRES_NEW} kullanildi cunku original transaction commit
     * edildigi icin event tetiklendi; ayni transaction'a katilmak mumkun degil.
     * Workflow basarisiz olursa hata loglanir ama exception yutulur — bilet
     * yasamini surdurur.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTicketCreated(TicketCreatedEvent event) {
        Ticket ticket = event.ticket();
        log.info("TicketCreatedEvent alındı — workflow başlatılıyor. TicketId={}", ticket.getId());

        try {
            Long processInstanceId = workflowService.startTicketWorkflow(ticket);

            // Workflow'dan donen instance kimligi ayri transaction ile bilete yazilir.
            ticket.setProcessInstanceId(processInstanceId);
            ticketRepository.save(ticket);
            log.info("Workflow bağlantısı kaydedildi. TicketId={}, ProcessInstanceId={}",
                    ticket.getId(), processInstanceId);

        } catch (Exception e) {
            log.error("Workflow başlatılamadı, ancak bilet zaten oluşturuldu. TicketId={}, Hata={}",
                    ticket.getId(), e.getMessage());
        }
    }
}
