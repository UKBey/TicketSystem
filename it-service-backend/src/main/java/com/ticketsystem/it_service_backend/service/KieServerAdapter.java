package com.ticketsystem.it_service_backend.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.UserTaskServicesClient;
import org.kie.server.api.model.admin.TimerInstance;
import org.kie.server.client.admin.ProcessAdminServicesClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Adapter that centralizes jBPM client call details in one place.
 * Higher-level services perform process, task and signal operations through this class.
 */
@Component
@Log4j2
public class KieServerAdapter {

    private final ProcessServicesClient processClient;
    private final QueryServicesClient queryClient;
    private final UserTaskServicesClient taskClient;
    private final ProcessAdminServicesClient adminClient;
    private final CircuitBreaker circuitBreaker;

    @Value("${jbpm.kie-server.container-id}")
    private String containerId;

    public KieServerAdapter(KieServicesClient kieServicesClient, CircuitBreaker kieServerCircuitBreaker) {
        this.processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
        this.queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
        this.taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
        this.adminClient = kieServicesClient.getServicesClient(ProcessAdminServicesClient.class);
        this.circuitBreaker = kieServerCircuitBreaker;
    }

    // Surec ornegi yasam dongusu islemleri.

    /**
     * Starts a new process instance for the given process definition. When the
     * circuit breaker is open, the call is rejected and {@link RuntimeException}
     * is thrown.
     *
     * @param processId process definition ID to start
     * @param variables initial process variables
     * @return the created process instance ID
     * @throws RuntimeException if the KIE Server is unreachable or the breaker is open
     */
    public Long startProcess(String processId, Map<String, Object> variables) {
        log.info("jBPM süreci başlatılıyor: processId={}, containerId={}, variables={}", processId, containerId, variables);

        Supplier<Long> decoratedCall = CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                processClient.startProcess(containerId, processId, variables));

        try {
            Long processInstanceId = decoratedCall.get();
            log.info("jBPM süreci başarıyla başlatıldı. ProcessInstanceId={}", processInstanceId);
            return processInstanceId;
        } catch (CallNotPermittedException e) {
            log.error("KIE Server Circuit Breaker AÇIK! Süreç başlatılamıyor. processId={}", processId);
            throw new RuntimeException("KIE Server şu anda erişilemez (Circuit Breaker açık)", e);
        } catch (Exception e) {
            log.error("jBPM süreci başlatılamadı! processId={}, hata={}", processId, e.getMessage(), e);
            throw new RuntimeException("Workflow süreci başlatılamadı: " + e.getMessage(), e);
        }
    }

    /**
     * Updates one variable on a running process. Errors are logged but not
     * propagated to the caller (best-effort).
     *
     * @param processInstanceId target process instance ID
     * @param variableName variable name to update
     * @param value new value
     */
    public void setProcessVariable(Long processInstanceId, String variableName, Object value) {
        log.debug("Süreç değişkeni güncelleniyor: processInstanceId={}, variable={}={}", processInstanceId, variableName, value);

        Runnable decoratedCall = CircuitBreaker.decorateRunnable(circuitBreaker, () -> {
            Map<String, Object> variables = new HashMap<>();
            variables.put(variableName, value);
            processClient.setProcessVariables(containerId, processInstanceId, variables);
        });

        try {
            decoratedCall.run();
        } catch (CallNotPermittedException e) {
            log.warn("KIE Server Circuit Breaker açık — süreç değişkeni güncellenemedi: processInstanceId={}", processInstanceId);
        } catch (Exception e) {
            log.error("Süreç değişkeni güncellenemedi: processInstanceId={}, hata={}", processInstanceId, e.getMessage());
        }
    }

    /**
     * Returns the current state of a process instance. Returns {@code null}
     * when not found or when the breaker is open.
     *
     * @param processInstanceId target process instance ID
     * @return process instance or {@code null}
     */
    public ProcessInstance getProcessInstance(Long processInstanceId) {
        Supplier<ProcessInstance> decoratedCall = CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                queryClient.findProcessInstanceById(processInstanceId));
        try {
            return decoratedCall.get();
        } catch (CallNotPermittedException e) {
            log.warn("KIE Server Circuit Breaker açık — süreç durumu sorgulanamadı: processInstanceId={}", processInstanceId);
            return null;
        } catch (Exception e) {
            log.warn("Süreç örneği bulunamadı: processInstanceId={}, hata={}", processInstanceId, e.getMessage());
            return null;
        }
    }

    /**
     * Returns whether the process instance has reached a terminal state (COMPLETED
     * or ABORTED).
     *
     * <p>Used to confirm a transition to a terminal BPMN state: the CLOSED branch
     * ends with a terminate event, so once the process finishes its variables are no
     * longer readable and a value-based check on the {@code status} variable would
     * give a false negative. Returns {@code false} when the instance is still active
     * or cannot be queried (breaker open / not found), keeping callers conservative.
     *
     * @param processInstanceId target process instance ID
     * @return {@code true} only when the instance is confirmed COMPLETED or ABORTED
     */
    public boolean isProcessFinished(Long processInstanceId) {
        ProcessInstance pi = getProcessInstance(processInstanceId);
        if (pi == null || pi.getState() == null) {
            return false;
        }
        // jBPM process states (org.kie.api.runtime.process.ProcessInstance):
        // 1 = ACTIVE, 2 = COMPLETED, 3 = ABORTED.
        int state = pi.getState();
        return state == 2 || state == 3;
    }

    /**
     * Aborts a running process. Errors are not propagated (best-effort).
     *
     * @param processInstanceId process instance ID to abort
     */
    public void abortProcess(Long processInstanceId) {
        log.info("jBPM süreci iptal ediliyor: processInstanceId={}", processInstanceId);

        Runnable decoratedCall = CircuitBreaker.decorateRunnable(circuitBreaker, () ->
                processClient.abortProcessInstance(containerId, processInstanceId));
        try {
            decoratedCall.run();
            log.info("jBPM süreci başarıyla iptal edildi. ProcessInstanceId={}", processInstanceId);
        } catch (CallNotPermittedException e) {
            log.warn("KIE Server Circuit Breaker açık — süreç iptal edilemedi: processInstanceId={}", processInstanceId);
        } catch (Exception e) {
            log.error("jBPM süreci iptal edilemedi: processInstanceId={}, hata={}", processInstanceId, e.getMessage(), e);
        }
    }

    /**
     * Reads a single process variable. Returns {@code null} on failure.
     *
     * @param processInstanceId target process instance ID
     * @param variableName variable name to read
     * @return variable value or {@code null}
     */
    public Object getProcessVariable(Long processInstanceId, String variableName) {
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                    processClient.getProcessInstanceVariable(containerId, processInstanceId, variableName)).get();
        } catch (Exception e) {
            log.warn("Süreç değişkeni okunamadı: processInstanceId={}, variable={}", processInstanceId, variableName);
            return null;
        }
    }

    // Surece olay sinyali gonderme islemleri.

    /**
     * Sends a named signal to a process instance. Errors are not propagated;
     * they are only logged (best-effort).
     *
     * @param processInstanceId target process instance ID
     * @param signalName signal name (declared in the BPMN)
     * @param data signal payload (optional)
     */
    public void signalProcessInstance(Long processInstanceId, String signalName, Object data) {
        log.info("jBPM sürece sinyal gönderiliyor: processInstanceId={}, signal={}, data={}",
                processInstanceId, signalName, data);

        Runnable decoratedCall = CircuitBreaker.decorateRunnable(circuitBreaker, () ->
                processClient.signalProcessInstance(containerId, processInstanceId, signalName, data));

        try {
            decoratedCall.run();
            log.info("jBPM sinyal başarıyla gönderildi: processInstanceId={}, signal={}", processInstanceId, signalName);
        } catch (CallNotPermittedException e) {
            log.warn("KIE Server Circuit Breaker açık — sinyal gönderilemedi: processInstanceId={}, signal={}",
                    processInstanceId, signalName);
        } catch (Exception e) {
            log.error("jBPM sinyal gönderilemedi: processInstanceId={}, signal={}, hata={}",
                    processInstanceId, signalName, e.getMessage(), e);
        }
    }

    // Human task sorgu ve tamamlama islemleri.

    /**
     * Returns the active tasks (Ready/Reserved/InProgress) on a process instance.
     * Returns an empty list on failure.
     *
     * @param processInstanceId target process instance ID
     * @return up to 100 active tasks; empty list on failure
     */
    public List<TaskSummary> getActiveTasks(Long processInstanceId) {
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                    taskClient.findTasksByStatusByProcessInstanceId(
                            processInstanceId,
                            Arrays.asList("Ready", "Reserved", "InProgress"),
                            0, 100)).get();
        } catch (Exception e) {
            log.warn("Aktif görevler sorgulanamadı: processInstanceId={}, hata={}", processInstanceId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Completes a task end-to-end via the claim/start/complete chain.
     *
     * @param taskId target human task ID
     * @param userId user completing the task
     * @param output output map passed to the complete step
     * @throws RuntimeException if the KIE Server is unreachable or the breaker is open
     */
    public void claimAndCompleteTask(Long taskId, String userId, Map<String, Object> output) {
        log.info("jBPM task tamamlanıyor: taskId={}, user={}", taskId, userId);

        Runnable decoratedCall = CircuitBreaker.decorateRunnable(circuitBreaker, () -> {
            taskClient.claimTask(containerId, taskId, userId);
            taskClient.startTask(containerId, taskId, userId);
            taskClient.completeTask(containerId, taskId, userId, output);
        });

        try {
            decoratedCall.run();
            log.info("jBPM task başarıyla tamamlandı: taskId={}", taskId);
        } catch (CallNotPermittedException e) {
            log.error("KIE Server Circuit Breaker açık — task tamamlanamıyor: taskId={}", taskId);
            throw new RuntimeException("KIE Server erişilemez (Circuit Breaker açık)", e);
        } catch (Exception e) {
            log.error("jBPM task tamamlanamadı: taskId={}, hata={}", taskId, e.getMessage(), e);
            throw new RuntimeException("Task tamamlanamadı: " + e.getMessage(), e);
        }
    }


    /**
     * Returns the next fire time of the active timer in epoch milliseconds, when
     * one exists. With multiple timers the first record is used (a single SLA
     * timer is expected on this flow).
     *
     * @param processInstanceId target process instance ID
     * @return next-fire-time epoch ms; {@code null} when no active timer or on failure
     */
    public Long getActiveTimerDeadline(Long processInstanceId) {
        Supplier<Long> decoratedCall = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            List<TimerInstance> timers = adminClient.getTimerInstances(containerId, processInstanceId);
            if (timers != null && !timers.isEmpty()) {
                // Bu akisda tek SLA timer beklendigi icin ilk kayit yeterlidir.
                return timers.get(0).getNextFireTime().getTime();
            }
            return null;
        });
        try {
            return decoratedCall.get();
        } catch (Exception e) {
            log.warn("KIE Server Circuit Breaker açık — active timer sorgulanamadı: processInstanceId={}, hata={}", processInstanceId, e.getMessage());
            return null;
        }
    }

}