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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowServiceTest {

    @Mock
    private KieServerAdapter kieServerAdapter;

    @Mock
    private SlaPolicyService slaPolicyService;

    @InjectMocks
    private WorkflowService workflowService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(workflowService, "processId", "ticket-workflow");
        ReflectionTestUtils.setField(workflowService, "callbackBaseUrl", "https://example.com/callback");
        ReflectionTestUtils.setField(workflowService, "callbackToken", "token-123");

        lenient().when(slaPolicyService.getSlaDurationMs("CRITICAL")).thenReturn(3_600_000L);
        lenient().when(slaPolicyService.getSlaDurationMs("HIGH")).thenReturn(14_400_000L);
        lenient().when(slaPolicyService.getSlaDurationMs("MEDIUM")).thenReturn(43_200_000L);
        lenient().when(slaPolicyService.getSlaDurationMs("LOW")).thenReturn(86_400_000L);
        lenient().when(slaPolicyService.getSlaDurationMs("UNSUPPORTED")).thenReturn(43_200_000L);
    }

    @Test
    void startTicketWorkflow_addsCallbackDataWithoutAssigneeId() {
        Ticket ticket = Ticket.builder()
                .id(11L)
                .priority("HIGH")
                .status("NEW")
                .customerId("customer-1")
                .build();

        when(kieServerAdapter.startProcess(eq("ticket-workflow"), any())).thenReturn(77L);

        Long processInstanceId = workflowService.startTicketWorkflow(ticket);

        assertEquals(77L, processInstanceId);
        verify(kieServerAdapter).startProcess(eq("ticket-workflow"), org.mockito.ArgumentMatchers.argThat(variables ->
                "11".equals(variables.get("ticketId"))
                        && "HIGH".equals(variables.get("priority"))
                        && "customer-1".equals(variables.get("customerId"))
                        && "NEW".equals(variables.get("status"))
                        && "PT240M".equals(variables.get("slaDuration"))
                        && "https://example.com/callback?token=token-123".equals(variables.get("callbackUrl"))
                        && !variables.containsKey("assigneeId")
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
                        && "PT1440M".equals(variables.get("slaDuration"))
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
                .status("IN_PROGRESS")
                .processInstanceId(600L)
                .build();

        workflowService.syncTicketAssignment(ticket, "agent-2");

        verify(kieServerAdapter).setProcessVariable(600L, "assigneeId", "agent-2");
        verify(kieServerAdapter).setProcessVariable(600L, "status", "IN_PROGRESS");
    }

    @Test
    void syncTicketAssignmentSkipsWhenProcessInstanceMissing() {
        Ticket ticket = Ticket.builder().id(15L).status("IN_PROGRESS").build();

        workflowService.syncTicketAssignment(ticket, "agent-2");

        verify(kieServerAdapter, never()).setProcessVariable(any(), any(), any());
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
    void pauseSlaWhenAlreadyPaused_returnsEarly() {
        ZonedDateTime pausedAt = ZonedDateTime.now().minusSeconds(30);
        Ticket ticket = Ticket.builder()
                .id(171L)
                .priority("MEDIUM")
                .createdAt(ZonedDateTime.now().minusMinutes(3))
                .slaElapsedMs(120_000L)
                .slaPausedAt(pausedAt)
                .processInstanceId(700L)
                .build();

        workflowService.pauseSla(ticket);

        assertEquals(pausedAt, ticket.getSlaPausedAt());
        verify(kieServerAdapter, never()).signalProcessInstance(any(), any(), any());
    }

    @Test
    void pauseSlaWhenSignalFails_doesNotThrow() {
        Ticket ticket = Ticket.builder()
                .id(172L)
                .priority("MEDIUM")
                .createdAt(ZonedDateTime.now().minusMinutes(1))
                .processInstanceId(701L)
                .build();
        doThrow(new RuntimeException("signal failed"))
                .when(kieServerAdapter).signalProcessInstance(701L, "pause_sla", null);

        workflowService.pauseSla(ticket);

        assertNotNull(ticket.getSlaPausedAt());
        verify(kieServerAdapter).signalProcessInstance(701L, "pause_sla", null);
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

        verify(kieServerAdapter).setProcessVariable(800L, "slaDuration", "PT1439M");
        verify(kieServerAdapter).signalProcessInstance(800L, "resume_sla", "PT1439M");
    }

    @Test
    void resumeSlaWhenRemainingHasMinutesAndSeconds_usesMixedIsoFormat() {
        Ticket ticket = Ticket.builder()
                .id(191L)
                .priority("LOW")
                .slaElapsedMs(119_000L)
                .processInstanceId(801L)
                .build();

        workflowService.resumeSla(ticket);

        // LOW=86_400_000ms, elapsed=119_000ms → remaining=86_281_000ms = 1438m1s
        verify(kieServerAdapter).setProcessVariable(801L, "slaDuration", "PT1438M1S");
        verify(kieServerAdapter).signalProcessInstance(801L, "resume_sla", "PT1438M1S");
    }

    @Test
    void resumeSlaWhenRemainingSecondsOnly_usesSecondsIsoFormat() {
        Ticket ticket = Ticket.builder()
                .id(192L)
                .priority("CRITICAL")
                .slaElapsedMs(3_599_000L)
                .processInstanceId(802L)
                .build();

        workflowService.resumeSla(ticket);

        // CRITICAL=3_600_000ms, elapsed=3_599_000ms → remaining=1_000ms = 1s
        verify(kieServerAdapter).setProcessVariable(802L, "slaDuration", "PT1S");
        verify(kieServerAdapter).signalProcessInstance(802L, "resume_sla", "PT1S");
    }

    @Test
    void resumeSlaWhenSignalFails_doesNotThrow() {
        Ticket ticket = Ticket.builder()
                .id(193L)
                .priority("LOW")
                .slaElapsedMs(60_000L)
                .processInstanceId(803L)
                .build();
        doThrow(new RuntimeException("signal failed"))
                .when(kieServerAdapter).signalProcessInstance(803L, "resume_sla", "PT1439M");

        workflowService.resumeSla(ticket);

        verify(kieServerAdapter).setProcessVariable(803L, "slaDuration", "PT1439M");
        verify(kieServerAdapter).signalProcessInstance(803L, "resume_sla", "PT1439M");
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
    void closeTicketWorkflowSkipsWhenProcessMissing() {
        Ticket ticket = Ticket.builder().id(201L).build();

        workflowService.closeTicketWorkflow(ticket);

        verify(kieServerAdapter, never()).signalProcessInstance(any(), any(), any());
        verify(kieServerAdapter, never()).abortProcess(any());
    }

    @Test
    void closeTicketWorkflowWhenSignalAndAbortFail_doesNotThrow() {
        Ticket ticket = Ticket.builder().id(202L).processInstanceId(901L).build();
        doThrow(new RuntimeException("signal failed"))
                .when(kieServerAdapter).signalProcessInstance(901L, "ticket_closed", null);
        doThrow(new RuntimeException("abort failed"))
                .when(kieServerAdapter).abortProcess(901L);

        workflowService.closeTicketWorkflow(ticket);

        verify(kieServerAdapter).signalProcessInstance(901L, "ticket_closed", null);
        verify(kieServerAdapter).abortProcess(901L);
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

        Map<String, Object> result = workflowService.getSlaTimerInfo(ticket);

        assertEquals(-1L, result.get("deadlineTimestamp"));
        // CRITICAL=3_600_000ms, elapsed=15_000ms → remaining=3_585_000ms
        assertEquals(3_585_000L, result.get("remainingMs"));
        assertEquals("paused", result.get("slaState"));
    }

    @Test
    void getSlaTimerInfoReturnsBreachedMarker() {
        Ticket ticket = Ticket.builder().slaBreached(true).build();

        Map<String, Object> result = workflowService.getSlaTimerInfo(ticket);

        assertEquals(-1L, result.get("deadlineTimestamp"));
        assertEquals(0L, result.get("remainingMs"));
        assertEquals("expired", result.get("slaState"));
    }

    @Test
    void startTicketWorkflowWithUnknownPriority_usesDefaultSlaDuration() {
        Ticket ticket = Ticket.builder()
                .id(23L)
                .priority("UNSUPPORTED")
                .status("NEW")
                .customerId("customer-9")
                .build();

        when(kieServerAdapter.startProcess(eq("ticket-workflow"), any())).thenReturn(81L);

        workflowService.startTicketWorkflow(ticket);

        verify(kieServerAdapter).startProcess(eq("ticket-workflow"), org.mockito.ArgumentMatchers.argThat(variables ->
                "PT720M".equals(variables.get("slaDuration"))
        ));
    }

    @Test
    void getSlaTimerInfoReturnsCompletedForClosedStatus() {
        Ticket ticket = Ticket.builder()
                .priority("HIGH")
                .status("CLOSED")
                .slaElapsedMs(120_000L)
                .build();

        Map<String, Object> result = workflowService.getSlaTimerInfo(ticket);

        assertEquals(-1L, result.get("deadlineTimestamp"));
        assertEquals(0L, result.get("remainingMs"));
        assertEquals("completed", result.get("slaState"));
    }

    @Test
    void getSlaTimerInfoClosedWithBreachedReturnsCompleted() {
        // CLOSED + slaBreached: ihlal DB'de kayıtlı ama badge "completed" gösterir
        Ticket ticket = Ticket.builder()
                .priority("HIGH")
                .status("CLOSED")
                .slaBreached(true)
                .slaElapsedMs(14_400_001L)
                .build();

        Map<String, Object> result = workflowService.getSlaTimerInfo(ticket);

        assertEquals("completed", result.get("slaState"));
    }

    @Test
    void getSlaTimerInfoReturnsExpiredWhenElapsedExceedsDuration() {
        // slaBreached henüz set edilmemiş ama süre dolmuş → expired
        Ticket ticket = Ticket.builder()
                .priority("CRITICAL")
                .status("RESOLVED")
                .slaBreached(false)
                .slaElapsedMs(3_600_001L) // CRITICAL=3_600_000ms, 1ms aşım
                .build();

        Map<String, Object> result = workflowService.getSlaTimerInfo(ticket);

        assertEquals(-1L, result.get("deadlineTimestamp"));
        assertEquals(0L, result.get("remainingMs"));
        assertEquals("expired", result.get("slaState"));
    }

    @Test
    void getSlaTimerInfoActiveStatusComputesPositiveDeadline() {
        Ticket ticket = Ticket.builder()
                .priority("MEDIUM")
                .status("IN_PROGRESS")
                .createdAt(ZonedDateTime.now().minusMinutes(1))
                .slaElapsedMs(30_000L)
                .build();

        Map<String, Object> result = workflowService.getSlaTimerInfo(ticket);

        assertNotNull(result.get("deadlineTimestamp"));
        assertNotNull(result.get("remainingMs"));
        assertTrue((Long) result.get("deadlineTimestamp") > 0);
        assertTrue((Long) result.get("remainingMs") >= 0);
        assertEquals("active", result.get("slaState"));
    }
}