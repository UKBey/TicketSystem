package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AgentProductLimitRequestDTO;
import com.ticketsystem.it_service_backend.dto.AgentProductLimitResponseDTO;
import com.ticketsystem.it_service_backend.service.AgentProductLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentProductLimitControllerTest {

    @Mock
    private AgentProductLimitService agentProductLimitService;

    private AgentProductLimitController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentProductLimitController(agentProductLimitService);
    }

    @Test
    void getAgentLimits_returnsDtos() {
        AgentProductLimitResponseDTO dto = AgentProductLimitResponseDTO.builder()
                .agentId("agent-1")
                .productId(10L)
                .productName("CRM")
                .useCustomLimit(true)
                .maxActiveTickets(3)
                .effectiveLimit(3)
                .build();
        when(agentProductLimitService.getAgentLimits("agent-1")).thenReturn(List.of(dto));

        ResponseEntity<List<AgentProductLimitResponseDTO>> response = controller.getAgentLimits("agent-1");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void setAgentLimit_returnsDto() {
        AgentProductLimitRequestDTO request = AgentProductLimitRequestDTO.builder()
                .useCustomLimit(true)
                .maxActiveTickets(3)
                .build();
        AgentProductLimitResponseDTO dto = AgentProductLimitResponseDTO.builder()
                .agentId("agent-1")
                .productId(10L)
                .productName("CRM")
                .useCustomLimit(true)
                .maxActiveTickets(3)
                .effectiveLimit(3)
                .build();
        when(agentProductLimitService.setAgentLimit("agent-1", 10L, true, 3)).thenReturn(dto);

        ResponseEntity<AgentProductLimitResponseDTO> response = controller.setAgentLimit("agent-1", 10L, request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().getEffectiveLimit());
    }

    @Test
    void deleteAgentLimit_returnsNoContent() {
        ResponseEntity<Void> response = controller.deleteAgentLimit("agent-1", 10L);

        assertEquals(204, response.getStatusCode().value());
        verify(agentProductLimitService).deleteAgentLimit("agent-1", 10L);
    }
}