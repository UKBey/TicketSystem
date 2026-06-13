package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.WorkflowCallbackDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.ticketsystem.it_service_backend.entity.TicketStatus;
import com.ticketsystem.it_service_backend.entity.Priority;

/**
 * Controller-level tests. The breach mutation (flag set + save + notify) now lives in
 * {@link TicketService#markSlaBreached}; here we verify the controller's protocol
 * handling (token check, ticket lookup → 404, event-type routing) and that it delegates
 * the breach to the service. The mutation/idempotency logic is covered in
 * {@code TicketServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class WorkflowCallbackControllerTest {

    @Mock
    private TicketService ticketService;

    private WorkflowCallbackController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new WorkflowCallbackController(ticketService);
        // expectedToken alanini reflection ile inject eder (normalde @Value ile dolardı).
        Field expectedTokenField = WorkflowCallbackController.class.getDeclaredField("expectedToken");
        expectedTokenField.setAccessible(true);
        expectedTokenField.set(controller, "test-secret-token");
    }

    // --- Token dogrulama testleri ---

    @Test
    void handleWorkflowCallback_withNullToken_returns401() {
        WorkflowCallbackDTO dto = buildDto(1L, "SLA_BREACHED");

        ResponseEntity<String> response = controller.handleWorkflowCallback(null, dto);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Unauthorized internal API call", response.getBody());
        verify(ticketService, never()).findById(any());
    }

    @Test
    void handleWorkflowCallback_withWrongToken_returns401() {
        WorkflowCallbackDTO dto = buildDto(1L, "SLA_BREACHED");

        ResponseEntity<String> response = controller.handleWorkflowCallback("wrong-token", dto);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Unauthorized internal API call", response.getBody());
    }

    // --- Bilet bulunamama testi ---

    @Test
    void handleWorkflowCallback_withValidTokenButMissingTicket_returns404() {
        WorkflowCallbackDTO dto = buildDto(999L, "SLA_BREACHED");
        when(ticketService.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.handleWorkflowCallback("test-secret-token", dto);

        assertEquals(404, response.getStatusCode().value());
        assertEquals("Ticket not found", response.getBody());
        verify(ticketService, never()).markSlaBreached(any());
    }

    // --- SLA_BREACHED olay testi ---

    @Test
    void handleWorkflowCallback_slaBreached_delegatesToServiceMark() {
        Ticket ticket = Ticket.builder().id(1L).title("Test").description("d").status(TicketStatus.IN_PROGRESS).priority(Priority.HIGH).customerId("c1").build();
        WorkflowCallbackDTO dto = buildDto(1L, "SLA_BREACHED");
        when(ticketService.findById(1L)).thenReturn(Optional.of(ticket));

        ResponseEntity<String> response = controller.handleWorkflowCallback("test-secret-token", dto);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Callback processed successfully", response.getBody());
        verify(ticketService).markSlaBreached(ticket);
    }

    // --- PROCESS_COMPLETED olay testi ---

    @Test
    void handleWorkflowCallback_processCompleted_returnsOkWithoutMark() {
        Ticket ticket = Ticket.builder().id(2L).title("Test2").description("d").status(TicketStatus.CLOSED).priority(Priority.LOW).customerId("c2").build();
        WorkflowCallbackDTO dto = buildDto(2L, "PROCESS_COMPLETED");
        when(ticketService.findById(2L)).thenReturn(Optional.of(ticket));

        ResponseEntity<String> response = controller.handleWorkflowCallback("test-secret-token", dto);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Callback processed successfully", response.getBody());
        // PROCESS_COMPLETED olayinda breach mark cagrilmamali.
        verify(ticketService, never()).markSlaBreached(any());
    }

    // --- Bilinmeyen olay tipi testi ---

    @Test
    void handleWorkflowCallback_unknownEventType_returns400() {
        Ticket ticket = Ticket.builder().id(3L).title("Test3").description("d").status(TicketStatus.NEW).priority(Priority.MEDIUM).customerId("c3").build();
        WorkflowCallbackDTO dto = buildDto(3L, "UNKNOWN_EVENT");
        when(ticketService.findById(3L)).thenReturn(Optional.of(ticket));

        ResponseEntity<String> response = controller.handleWorkflowCallback("test-secret-token", dto);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Unknown event type: UNKNOWN_EVENT", response.getBody());
        verify(ticketService, never()).markSlaBreached(any());
    }

    // --- Yardimci metot ---

    private WorkflowCallbackDTO buildDto(Long ticketId, String eventType) {
        WorkflowCallbackDTO dto = new WorkflowCallbackDTO();
        dto.setTicketId(ticketId);
        dto.setEventType(eventType);
        dto.setProcessInstanceId(100L);
        dto.setAdditionalData("test-data");
        return dto;
    }
}
