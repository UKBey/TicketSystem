package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.DashboardMetricsDTO;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.SLAPolicyRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock
    TicketRepository ticketRepository;

    @Mock
    CsatRepository csatRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    WorklogRepository worklogRepository;

    @Mock
    SLAPolicyRepository slaPolicyRepository;

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    MetricsService metricsService;

    @Test
    void getDashboardSummary_returnsDefaultsWhenNoData() {
        when(ticketRepository.findByStatus("NEW")).thenReturn(Collections.emptyList());
        when(ticketRepository.findByStatus("IN_PROGRESS")).thenReturn(Collections.emptyList());
        when(ticketRepository.findByStatus("WAITING_FOR_CUSTOMER")).thenReturn(Collections.emptyList());
        when(ticketRepository.findByStatus("RESOLVED")).thenReturn(Collections.emptyList());
        when(csatRepository.findAverageRating()).thenReturn(0.0);
        when(csatRepository.count()).thenReturn(0L);

        DashboardMetricsDTO dto = metricsService.getDashboardSummary();

        assertThat(dto).isNotNull();
        assertThat(dto.getTotalOpenTickets()).isZero();
        assertThat(dto.getSlaBreachedCount()).isZero();
        assertThat(dto.getAvgResponseTimeHours()).isEqualTo(0.0);
        assertThat(dto.getCsatAverage()).isEqualTo(0.0);
        assertThat(dto.getPriorityDistribution()).isNotNull();
    }
}
