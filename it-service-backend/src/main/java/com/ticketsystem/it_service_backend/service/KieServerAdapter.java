package com.ticketsystem.it_service_backend.service;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.UserTaskServicesClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.log4j.Log4j2;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Düşük seviye jBPM REST adaptörü.
 * KieServicesClient üzerinden ProcessServicesClient, QueryServicesClient
 * ve UserTaskServicesClient ile iletişim kurar.
 * Tüm jBPM API detaylarını bu sınıfta soyutlar.
 *
 * Circuit Breaker ile korunur — KIE Server çöktüğünde
 * cascading failure önlenir.
 */
@Component
@Log4j2
public class KieServerAdapter {

    private final ProcessServicesClient processClient;
    private final QueryServicesClient queryClient;
    private final UserTaskServicesClient taskClient;
    private final CircuitBreaker circuitBreaker;

    @Value("${jbpm.kie-server.container-id}")
    private String containerId;

    public KieServerAdapter(KieServicesClient kieServicesClient, CircuitBreaker kieServerCircuitBreaker) {
        this.processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
        this.queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
        this.taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
        this.circuitBreaker = kieServerCircuitBreaker;
    }

    // ==================== SÜREÇ OPERASYONLARI ====================

    /**
     * Yeni bir BPMN süreç örneği başlatır.
     * Circuit Breaker ile korunur.
     *
     * @param processId  BPMN tanımlayıcısı (ör: com.ticketsystem.workflow.ticket-lifecycle)
     * @param variables  Sürece gönderilecek değişkenler
     * @return Process Instance ID
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
            log.error("⚡ KIE Server Circuit Breaker AÇIK! Süreç başlatılamıyor. processId={}", processId);
            throw new RuntimeException("KIE Server şu anda erişilemez (Circuit Breaker açık)", e);
        } catch (Exception e) {
            log.error("jBPM süreci başlatılamadı! processId={}, hata={}", processId, e.getMessage(), e);
            throw new RuntimeException("Workflow süreci başlatılamadı: " + e.getMessage(), e);
        }
    }

    /**
     * Çalışan bir süreçteki işlem değişkenini günceller.
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
            log.warn("⚡ Circuit Breaker açık — süreç değişkeni güncellenemedi: processInstanceId={}", processInstanceId);
        } catch (Exception e) {
            log.error("Süreç değişkeni güncellenemedi: processInstanceId={}, hata={}", processInstanceId, e.getMessage());
        }
    }

    /**
     * Bir süreç örneğinin mevcut durumunu sorgular.
     */
    public ProcessInstance getProcessInstance(Long processInstanceId) {
        Supplier<ProcessInstance> decoratedCall = CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                queryClient.findProcessInstanceById(processInstanceId));
        try {
            return decoratedCall.get();
        } catch (CallNotPermittedException e) {
            log.warn("⚡ Circuit Breaker açık — süreç durumu sorgulanamadı: processInstanceId={}", processInstanceId);
            return null;
        } catch (Exception e) {
            log.warn("Süreç örneği bulunamadı: processInstanceId={}, hata={}", processInstanceId, e.getMessage());
            return null;
        }
    }

    /**
     * Çalışan bir süreci iptal eder (abort).
     */
    public void abortProcess(Long processInstanceId) {
        log.info("jBPM süreci iptal ediliyor: processInstanceId={}", processInstanceId);

        Runnable decoratedCall = CircuitBreaker.decorateRunnable(circuitBreaker, () ->
                processClient.abortProcessInstance(containerId, processInstanceId));
        try {
            decoratedCall.run();
            log.info("jBPM süreci başarıyla iptal edildi. ProcessInstanceId={}", processInstanceId);
        } catch (CallNotPermittedException e) {
            log.warn("⚡ Circuit Breaker açık — süreç iptal edilemedi: processInstanceId={}", processInstanceId);
        } catch (Exception e) {
            log.error("jBPM süreci iptal edilemedi: processInstanceId={}, hata={}", processInstanceId, e.getMessage(), e);
        }
    }

    /**
     * Bir süreç işlem değişkenini okur.
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

    // ==================== SİNYAL OPERASYONLARI ====================

    /**
     * Çalışan bir süreç örneğine sinyal gönderir.
     * SLA duraklatma/devam ettirme ve bilet kapatma işlemleri için kullanılır.
     *
     * @param processInstanceId Hedef süreç örneği ID
     * @param signalName        Sinyal adı (ör: "pause_sla", "resume_sla", "ticket_closed")
     * @param data              Sinyale eklenecek veri (null olabilir)
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
            log.warn("⚡ Circuit Breaker açık — sinyal gönderilemedi: processInstanceId={}, signal={}",
                    processInstanceId, signalName);
        } catch (Exception e) {
            log.error("jBPM sinyal gönderilemedi: processInstanceId={}, signal={}, hata={}",
                    processInstanceId, signalName, e.getMessage(), e);
        }
    }

    // ==================== HUMAN TASK OPERASYONLARI ====================

    /**
     * Belirli bir süreç örneği için aktif Human Task'ları listeler.
     *
     * @param processInstanceId Süreç örneği ID
     * @return Aktif görev listesi
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
     * Bir Human Task'ı sahiplenip (claim), başlatıp (start), tamamlar (complete).
     *
     * @param taskId  Görev ID
     * @param userId  İşlemi yapan kullanıcı
     * @param output  Görev çıktı parametreleri
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
            log.error("⚡ Circuit Breaker açık — task tamamlanamıyor: taskId={}", taskId);
            throw new RuntimeException("KIE Server erişilemez (Circuit Breaker açık)", e);
        } catch (Exception e) {
            log.error("jBPM task tamamlanamadı: taskId={}, hata={}", taskId, e.getMessage(), e);
            throw new RuntimeException("Task tamamlanamadı: " + e.getMessage(), e);
        }
    }
}
