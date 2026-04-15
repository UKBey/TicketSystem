package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZonedDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private KieServerAdapter kieServerAdapter;

    @InjectMocks
    private WorkflowService workflowService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(workflowService, "processId", "ticket-workflow");
        ReflectionTestUtils.setField(workflowService, "callbackBaseUrl", "https://example.com/callback");
        ReflectionTestUtils.setField(workflowService, "callbackToken", "token-123");
    }

    @Test
    void startTicketWorkflowAddsAssigneeAndCallbackData() {
        Ticket ticket = Ticket.builder()
                .id(11L)
                .priority("HIGH")
                .status("NEW")
                .customerId("customer-1")
                .assigneeId("agent-1")
                .build();

        when(kieServerAdapter.startProcess(eq("ticket-workflow"), any())).thenReturn(77L);

        Long processInstanceId = workflowService.startTicketWorkflow(ticket);

        assertEquals(77L, processInstanceId);
        verify(kieServerAdapter).startProcess(eq("ticket-workflow"), org.mockito.ArgumentMatchers.argThat(variables ->
                "11".equals(variables.get("ticketId"))
                        && "HIGH".equals(variables.get("priority"))
                        && "customer-1".equals(variables.get("customerId"))
                        && "NEW".equals(variables.get("status"))
                        && "PT5M".equals(variables.get("slaDuration"))
                        && "https://example.com/callback?token=token-123".equals(variables.get("callbackUrl"))
                        && "agent-1".equals(variables.get("assigneeId"))
        ));
    }

    @Test
    void startTicketWorkflowOmitsAssigneeWhenMissing() {
        Ticket ticket = Ticket.builder()
                .id(12L)
                .priority("LOW")
                .status("NEW")
                .customerId("customer-2")
                .build();

        when(kieServerAdapter.startProcess(eq("ticket-workflow"), any())).thenReturn(78L);

        Long processInstanceId = workflowService.startTicketWorkflow(ticket);

        assertEquals(78L, processInstanceId);
        verify(kieServerAdapter).startProcess(eq("ticket-workflow"), org.mockito.ArgumentMatchers.argThat(variables ->
                !variables.containsKey("assigneeId")
                        && "PT20M".equals(variables.get("slaDuration"))
        ));
    }

    @Test
    void syncTicketStatusSkipsWhenProcessInstanceMissing() {
        Ticket ticket = Ticket.builder().id(13L).status("IN_PROGRESS").build();

        workflowService.syncTicketStatus(ticket);

        verify(kieServerAdapter, never()).setProcessVariable(any(), any(), any());
    }

    @Test
    void syncTicketStatusUpdatesWorkflowVariable() {
        Ticket ticket = Ticket.builder()
                .id(14L)
                .status("IN_PROGRESS")
                .processInstanceId(500L)
                .build();

        workflowService.syncTicketStatus(ticket);

        verify(kieServerAdapter).setProcessVariable(500L, "status", "IN_PROGRESS");
    }

    @Test
    void syncTicketAssignmentUpdatesBothAssignmentAndStatus() {
        Ticket ticket = Ticket.builder()
                .id(15L)
                .assigneeId("agent-2")
                .status("IN_PROGRESS")
                .processInstanceId(600L)
                .build();

        workflowService.syncTicketAssignment(ticket);

        verify(kieServerAdapter).setProcessVariable(600L, "assigneeId", "agent-2");
        verify(kieServerAdapter).setProcessVariable(600L, "status", "IN_PROGRESS");
    }

    @Test
    void pauseSlaWithoutProcessInstanceUpdatesTicketOnly() {
        Ticket ticket = Ticket.builder()
                .id(16L)
                .priority("MEDIUM")
                .createdAt(ZonedDateTime.now().minusMinutes(2))
                .slaElapsedMs(0L)
                .build();

        workflowService.pauseSla(ticket);

        assertNotNull(ticket.getSlaPausedAt());
        assertFalse(ticket.getSlaElapsedMs() < 0);
        verify(kieServerAdapter, never()).signalProcessInstance(any(), any(), any());
    }

    @Test
    void pauseSlaSignalsWorkflowWhenProcessExists() {
        Ticket ticket = Ticket.builder()
                .id(17L)
                .priority("MEDIUM")
                .createdAt(ZonedDateTime.now().minusMinutes(1))
                .processInstanceId(700L)
                .build();

        workflowService.pauseSla(ticket);

        assertNotNull(ticket.getSlaPausedAt());
        verify(kieServerAdapter).signalProcessInstance(700L, "pause_sla", null);
    }

    @Test
    void resumeSlaWithoutProcessInstanceUpdatesTicketOnly() {
        Ticket ticket = Ticket.builder()
                .id(18L)
                .priority("HIGH")
                .slaElapsedMs(1000L)
                .slaPausedAt(ZonedDateTime.now().minusMinutes(1))
                .build();

        workflowService.resumeSla(ticket);

        assertNull(ticket.getSlaPausedAt());
        assertNotNull(ticket.getSlaResumedAt());
        verify(kieServerAdapter, never()).setProcessVariable(any(), any(), any());
    }

    @Test
    void resumeSlaSignalsWorkflowWithRemainingDuration() {
        Ticket ticket = Ticket.builder()
                .id(19L)
                .priority("LOW")
                .slaElapsedMs(60_000L)
                .processInstanceId(800L)
                .build();

        workflowService.resumeSla(ticket);

        verify(kieServerAdapter).setProcessVariable(800L, "slaDuration", "PT19M");
        verify(kieServerAdapter).signalProcessInstance(800L, "resume_sla", "PT19M");
    }

    @Test
    void closeTicketWorkflowFallsBackToAbortOnSignalFailure() {
        Ticket ticket = Ticket.builder()
                .id(20L)
                .processInstanceId(900L)
                .build();

        doThrow(new RuntimeException("signal failed")).when(kieServerAdapter)
                .signalProcessInstance(900L, "ticket_closed", null);

        workflowService.closeTicketWorkflow(ticket);

        verify(kieServerAdapter).abortProcess(900L);
    }

    @Test
    void abortTicketWorkflowSkipsWhenProcessMissing() {
        Ticket ticket = Ticket.builder().id(21L).build();

        workflowService.abortTicketWorkflow(ticket);

        verify(kieServerAdapter, never()).abortProcess(any());
    }

    @Test
    void abortTicketWorkflowCallsAdapterWhenProcessExists() {
        Ticket ticket = Ticket.builder().id(22L).processInstanceId(1000L).build();

        workflowService.abortTicketWorkflow(ticket);

        verify(kieServerAdapter).abortProcess(1000L);
    }

    @Test
    void getSlaTimerInfoReturnsPausedRemainingTime() {
        Ticket ticket = Ticket.builder()
                .priority("CRITICAL")
                .slaElapsedMs(15_000L)
                .slaPausedAt(ZonedDateTime.now())
                .build();

        Map<String, Long> result = workflowService.getSlaTimerInfo(ticket);

        assertEquals(-1L, result.get("deadlineTimestamp"));
        assertEquals(45_000L, result.get("remainingMs"));
    }

    @Test
    void getSlaTimerInfoReturnsBreachedMarker() {
        Ticket ticket = Ticket.builder().slaBreached(true).build();

        Map<String, Long> result = workflowService.getSlaTimerInfo(ticket);

        assertEquals(-1L, result.get("deadlineTimestamp"));
        assertEquals(0L, result.get("remainingMs"));
    }
}