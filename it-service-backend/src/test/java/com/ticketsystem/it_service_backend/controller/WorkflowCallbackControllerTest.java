package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.WorkflowCallbackDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
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

@ExtendWith(MockitoExtension.class)
class WorkflowCallbackControllerTest {

    @Mock
    private TicketRepository ticketRepository;

    private WorkflowCallbackController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new WorkflowCallbackController(ticketRepository);
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
        when(ticketRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.handleWorkflowCallback("test-secret-token", dto);

        assertEquals(404, response.getStatusCode().value());
        assertEquals("Ticket not found", response.getBody());
    }

    // --- SLA_BREACHED olay testi ---

    @Test
    void handleWorkflowCallback_slaBreached_setsBreachedFlagAndSaves() {
        Ticket ticket = Ticket.builder().id(1L).title("Test").description("d").status("IN_PROGRESS").priority("HIGH").customerId("c1").build();
        WorkflowCallbackDTO dto = buildDto(1L, "SLA_BREACHED");
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenReturn(ticket);

        ResponseEntity<String> response = controller.handleWorkflowCallback("test-secret-token", dto);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Callback processed successfully", response.getBody());
        assertTrue(ticket.getSlaBreached());
        verify(ticketRepository).save(ticket);
    }

    // --- PROCESS_COMPLETED olay testi ---

    @Test
    void handleWorkflowCallback_processCompleted_returnsOkWithoutSave() {
        Ticket ticket = Ticket.builder().id(2L).title("Test2").description("d").status("CLOSED").priority("LOW").customerId("c2").build();
        WorkflowCallbackDTO dto = buildDto(2L, "PROCESS_COMPLETED");
        when(ticketRepository.findById(2L)).thenReturn(Optional.of(ticket));

        ResponseEntity<String> response = controller.handleWorkflowCallback("test-secret-token", dto);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("Callback processed successfully", response.getBody());
        // PROCESS_COMPLETED olayinda save cagrilmamali.
        verify(ticketRepository, never()).save(any());
    }

    // --- Bilinmeyen olay tipi testi ---

    @Test
    void handleWorkflowCallback_unknownEventType_returns400() {
        Ticket ticket = Ticket.builder().id(3L).title("Test3").description("d").status("NEW").priority("MEDIUM").customerId("c3").build();
        WorkflowCallbackDTO dto = buildDto(3L, "UNKNOWN_EVENT");
        when(ticketRepository.findById(3L)).thenReturn(Optional.of(ticket));

        ResponseEntity<String> response = controller.handleWorkflowCallback("test-secret-token", dto);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Unknown event type: UNKNOWN_EVENT", response.getBody());
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
