package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.service.TicketDtoAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller-level test. The full-ticket bundle assembly now lives in
 * {@link TicketDtoAssembler#buildFullTicketData}; here we only verify the controller
 * delegates and returns the assembled payload. The assembly itself (name resolution,
 * known-issue filtering, fallbacks) is covered in {@code TicketDtoAssemblerTest}.
 */
@ExtendWith(MockitoExtension.class)
class InternalTicketControllerTest {

    @Mock private TicketDtoAssembler ticketDtoAssembler;

    @InjectMocks private InternalTicketController controller;

    @Test
    void getFullTicketData_delegatesToAssembler() {
        Long ticketId = 42L;
        Map<String, Object> payload = Map.of(
                "ticket", "t",
                "comments", List.of(),
                "worklogs", List.of(),
                "knownIssues", List.of());
        when(ticketDtoAssembler.buildFullTicketData(ticketId)).thenReturn(payload);

        ResponseEntity<Map<String, Object>> response = controller.getFullTicketData(ticketId);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(payload);
        verify(ticketDtoAssembler).buildFullTicketData(ticketId);
    }
}
