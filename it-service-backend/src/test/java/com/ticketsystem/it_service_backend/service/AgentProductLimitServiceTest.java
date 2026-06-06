package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AgentProductLimitResponseDTO;
import com.ticketsystem.it_service_backend.entity.AgentProductLimit;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentProductLimitServiceTest {

    @Mock
    private AgentProductLimitRepository agentProductLimitRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AgentProductLimitService agentProductLimitService;

    private User agent;
    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(10L).name("CRM").maxActiveTickets(4).build();
        agent = User.builder().id("agent-1").role("AGENT").build();
    }

    @Test
    void getAgentLimits_returnsMappedDtos() {
        AgentProductLimit limit = AgentProductLimit.builder()
                .id(1L)
                .agentId("agent-1")
                .product(product)
                .useCustomLimit(true)
                .maxActiveTickets(2)
                .build();
        when(agentProductLimitRepository.findByAgentId("agent-1")).thenReturn(List.of(limit));

        List<AgentProductLimitResponseDTO> result = agentProductLimitService.getAgentLimits("agent-1");

        assertEquals(1, result.size());
        assertEquals("CRM", result.get(0).getProductName());
        assertEquals(2, result.get(0).getEffectiveLimit());
    }

    @Test
    void setAgentLimit_whenCustomLimitEnabled_savesOverride() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L)).thenReturn(Optional.empty());
        when(agentProductLimitRepository.save(any(AgentProductLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentProductLimitResponseDTO result = agentProductLimitService.setAgentLimit("agent-1", 10L, true, 3);

        assertEquals(true, result.isUseCustomLimit());
        assertEquals(3, result.getEffectiveLimit());
    }

    @Test
    void setAgentLimit_whenCustomLimitDisabled_deletesExisting() {
        AgentProductLimit existing = AgentProductLimit.builder()
                .id(1L)
                .agentId("agent-1")
                .product(product)
                .useCustomLimit(true)
                .maxActiveTickets(2)
                .build();

        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L)).thenReturn(Optional.of(existing));

        AgentProductLimitResponseDTO result = agentProductLimitService.setAgentLimit("agent-1", 10L, false, null);

        assertEquals(false, result.isUseCustomLimit());
        assertEquals(4, result.getEffectiveLimit());
        verify(agentProductLimitRepository).delete(existing);
    }

    @Test
    void setAgentLimit_whenCustomLimitEnabledWithoutValue_savesUnlimitedOverride() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(agent));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L)).thenReturn(Optional.empty());
        when(agentProductLimitRepository.save(any(AgentProductLimit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentProductLimitResponseDTO result = agentProductLimitService.setAgentLimit("agent-1", 10L, true, null);

        assertEquals(true, result.isUseCustomLimit());
        assertNull(result.getMaxActiveTickets());
        assertNull(result.getEffectiveLimit());
        verify(agentProductLimitRepository).save(any(AgentProductLimit.class));
    }

    @Test
    void deleteAgentLimit_deletesExistingOverride() {
        AgentProductLimit existing = AgentProductLimit.builder()
                .id(1L)
                .agentId("agent-1")
                .product(product)
                .useCustomLimit(true)
                .maxActiveTickets(2)
                .build();

        when(agentProductLimitRepository.findByAgentIdAndProductId("agent-1", 10L)).thenReturn(Optional.of(existing));

        agentProductLimitService.deleteAgentLimit("agent-1", 10L);

        verify(agentProductLimitRepository).delete(existing);
    }
}