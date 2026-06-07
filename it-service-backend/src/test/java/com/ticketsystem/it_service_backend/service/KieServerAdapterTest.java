package com.ticketsystem.it_service_backend.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.server.api.model.admin.TimerInstance;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.UserTaskServicesClient;
import org.kie.server.client.admin.ProcessAdminServicesClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KieServerAdapterTest {

    private KieServicesClient kieServicesClient;
    private ProcessServicesClient processClient;
    private QueryServicesClient queryClient;
    private UserTaskServicesClient taskClient;
    private ProcessAdminServicesClient adminClient;
    private KieServerAdapter adapter;

    @BeforeEach
    void setUp() {
        kieServicesClient = mock(KieServicesClient.class);
        processClient = mock(ProcessServicesClient.class);
        queryClient = mock(QueryServicesClient.class);
        taskClient = mock(UserTaskServicesClient.class);
        adminClient = mock(ProcessAdminServicesClient.class);

        when(kieServicesClient.getServicesClient(ProcessServicesClient.class)).thenReturn(processClient);
        when(kieServicesClient.getServicesClient(QueryServicesClient.class)).thenReturn(queryClient);
        when(kieServicesClient.getServicesClient(UserTaskServicesClient.class)).thenReturn(taskClient);
        when(kieServicesClient.getServicesClient(ProcessAdminServicesClient.class)).thenReturn(adminClient);

        adapter = new KieServerAdapter(kieServicesClient, CircuitBreaker.ofDefaults("kieServer"));
        ReflectionTestUtils.setField(adapter, "containerId", "ticket-container");
    }

    @Test
    void startProcessReturnsProcessId() {
        when(processClient.startProcess(eq("ticket-container"), eq("workflow"), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
            .thenReturn(88L);

        Long processInstanceId = adapter.startProcess("workflow", Map.of("k", "v"));

        assertEquals(88L, processInstanceId);
        verify(processClient).startProcess("ticket-container", "workflow", Map.of("k", "v"));
    }

    @Test
    void setProcessVariableDelegatesToProcessClient() {
        adapter.setProcessVariable(55L, "status", "IN_PROGRESS");

        verify(processClient).setProcessVariables("ticket-container", 55L, Map.of("status", "IN_PROGRESS"));
    }

    @Test
    void getProcessInstanceReturnsInstance() {
        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(queryClient.findProcessInstanceById(77L)).thenReturn(processInstance);

        assertSame(processInstance, adapter.getProcessInstance(77L));
    }

    @Test
    void getProcessVariableReturnsValue() {
        when(processClient.getProcessInstanceVariable("ticket-container", 66L, "status")).thenReturn("OPEN");

        assertEquals("OPEN", adapter.getProcessVariable(66L, "status"));
    }

    @Test
    void signalProcessInstanceDelegatesToClient() {
        adapter.signalProcessInstance(90L, "pause_sla", null);

        verify(processClient).signalProcessInstance("ticket-container", 90L, "pause_sla", null);
    }

    @Test
    void getActiveTasksReturnsTasksFromClient() {
        TaskSummary taskSummary = mock(TaskSummary.class);
        when(taskClient.findTasksByStatusByProcessInstanceId(91L, List.of("Ready", "Reserved", "InProgress"), 0, 100))
                .thenReturn(List.of(taskSummary));

        List<TaskSummary> tasks = adapter.getActiveTasks(91L);

        assertEquals(1, tasks.size());
        assertSame(taskSummary, tasks.get(0));
    }

    @Test
    void claimAndCompleteTaskCallsAllTaskOperations() {
        adapter.claimAndCompleteTask(101L, "agent-1", Map.of("result", "done"));

        verify(taskClient).claimTask("ticket-container", 101L, "agent-1");
        verify(taskClient).startTask("ticket-container", 101L, "agent-1");
        verify(taskClient).completeTask("ticket-container", 101L, "agent-1", Map.of("result", "done"));
    }

    @Test
    void getActiveTimerDeadlineReturnsFirstTimer() {
        TimerInstance timerInstance = mock(TimerInstance.class);
        when(timerInstance.getNextFireTime()).thenReturn(new Date(123456789L));
        when(adminClient.getTimerInstances("ticket-container", 111L)).thenReturn(List.of(timerInstance));

        assertEquals(123456789L, adapter.getActiveTimerDeadline(111L));
    }

    @Test
    void abortProcessDelegatesToClient() {
        adapter.abortProcess(222L);

        verify(processClient).abortProcessInstance("ticket-container", 222L);
    }

    @Test
    void startProcessWrapsClientExceptions() {
        when(processClient.startProcess(eq("ticket-container"), eq("workflow"), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenThrow(new RuntimeException("boom"));

        RuntimeException exception = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> adapter.startProcess("workflow", Map.of()));

        assertTrue(exception.getMessage().contains("Workflow süreci başlatılamadı"));
    }

    @Test
    void claimAndCompleteTaskWrapsClientFailures() {
        doThrow(new RuntimeException("task boom")).when(taskClient)
                .claimTask("ticket-container", 333L, "agent-1");

        RuntimeException exception = org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> adapter.claimAndCompleteTask(333L, "agent-1", Map.of()));

        assertTrue(exception.getMessage().contains("Task tamamlanamadı"));
    }

    @Test
    void getProcessVariableReturnsNullOnFailure() {
        when(processClient.getProcessInstanceVariable("ticket-container", 444L, "status"))
                .thenThrow(new RuntimeException("nope"));

        assertNull(adapter.getProcessVariable(444L, "status"));
    }

    // ---- isProcessFinished ----

    @Test
    void isProcessFinished_nullInstance_false() {
        when(queryClient.findProcessInstanceById(10L)).thenReturn(null);
        org.junit.jupiter.api.Assertions.assertFalse(adapter.isProcessFinished(10L));
    }

    @Test
    void isProcessFinished_nullState_false() {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getState()).thenReturn(null);
        when(queryClient.findProcessInstanceById(11L)).thenReturn(pi);
        org.junit.jupiter.api.Assertions.assertFalse(adapter.isProcessFinished(11L));
    }

    @Test
    void isProcessFinished_activeState_false() {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getState()).thenReturn(1); // ACTIVE
        when(queryClient.findProcessInstanceById(12L)).thenReturn(pi);
        org.junit.jupiter.api.Assertions.assertFalse(adapter.isProcessFinished(12L));
    }

    @Test
    void isProcessFinished_completedState_true() {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getState()).thenReturn(2); // COMPLETED
        when(queryClient.findProcessInstanceById(13L)).thenReturn(pi);
        assertTrue(adapter.isProcessFinished(13L));
    }

    @Test
    void isProcessFinished_abortedState_true() {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getState()).thenReturn(3); // ABORTED
        when(queryClient.findProcessInstanceById(14L)).thenReturn(pi);
        assertTrue(adapter.isProcessFinished(14L));
    }

    // ---- isProcessInstanceMissing ----

    @Test
    void isProcessInstanceMissing_nullId_false() {
        org.junit.jupiter.api.Assertions.assertFalse(adapter.isProcessInstanceMissing(null));
    }

    @Test
    void isProcessInstanceMissing_instanceFound_false() {
        when(queryClient.findProcessInstanceById(20L)).thenReturn(mock(ProcessInstance.class));
        org.junit.jupiter.api.Assertions.assertFalse(adapter.isProcessInstanceMissing(20L));
    }

    @Test
    void isProcessInstanceMissing_instanceNull_true() {
        when(queryClient.findProcessInstanceById(21L)).thenReturn(null);
        assertTrue(adapter.isProcessInstanceMissing(21L));
    }

    @Test
    void isProcessInstanceMissing_notFoundException_true() {
        when(queryClient.findProcessInstanceById(22L))
                .thenThrow(new RuntimeException("Could not find process instance with id 22"));
        assertTrue(adapter.isProcessInstanceMissing(22L));
    }

    @Test
    void isProcessInstanceMissing_otherException_false() {
        when(queryClient.findProcessInstanceById(23L))
                .thenThrow(new RuntimeException("connection refused"));
        org.junit.jupiter.api.Assertions.assertFalse(adapter.isProcessInstanceMissing(23L));
    }
}