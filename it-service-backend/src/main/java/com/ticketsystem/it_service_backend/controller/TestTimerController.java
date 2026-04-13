package com.ticketsystem.it_service_backend.controller;
import org.springframework.web.bind.annotation.*;
import org.kie.server.api.model.admin.TimerInstance;
import com.ticketsystem.it_service_backend.service.KieServerAdapter;
import java.util.List;
@RestController
@RequestMapping("/api/test-timer")
public class TestTimerController {
    private final org.kie.server.client.KieServicesClient kieServicesClient;
    public TestTimerController(org.kie.server.client.KieServicesClient kieServicesClient) {
        this.kieServicesClient = kieServicesClient;
    }
    @GetMapping("/{pId}")
    public List<TimerInstance> getTimers(@PathVariable Long pId) {
        return kieServicesClient.getServicesClient(org.kie.server.client.admin.ProcessAdminServicesClient.class)
            .getTimerInstances("ticket-workflow", pId);
    }
}
