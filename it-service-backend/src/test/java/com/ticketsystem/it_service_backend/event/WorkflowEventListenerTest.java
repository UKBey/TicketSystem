package com.ticketsystem.it_service_backend.event;

import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.ticketsystem.it_service_backend.entity.TicketStatus;
import com.ticketsystem.it_service_backend.entity.Priority;

@ExtendWith(MockitoExtension.class)
class WorkflowEventListenerTest {

    @Mock
    private WorkflowService workflowService;

    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private WorkflowEventListener listener;

    @Test
    void onTicketCreatedStartsWorkflowAndPersistsProcessInstanceId() {
        Ticket ticket = Ticket.builder()
                .id(42L)
                .title("Printer broken")
                .description("Printer does not start")
                .status(TicketStatus.NEW)
                .priority(Priority.HIGH)
                .customerId("customer-1")
                .build();

        when(workflowService.startTicketWorkflow(ticket)).thenReturn(99L);

        listener.onTicketCreated(new TicketCreatedEvent(ticket));

        assertEquals(99L, ticket.getProcessInstanceId());
        verify(ticketRepository).save(ticket);
    }

    @Test
    void onTicketCreatedSwallowsWorkflowFailures() {
        Ticket ticket = Ticket.builder()
                .id(43L)
                .title("VPN issue")
                .description("Cannot connect")
                .status(TicketStatus.NEW)
                .priority(Priority.MEDIUM)
                .customerId("customer-2")
                .build();

        when(workflowService.startTicketWorkflow(ticket)).thenThrow(new RuntimeException("boom"));

        listener.onTicketCreated(new TicketCreatedEvent(ticket));

        assertNull(ticket.getProcessInstanceId());
        verifyNoInteractions(ticketRepository);
    }
}