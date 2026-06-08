package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.WorklogRequestDTO;
import com.ticketsystem.it_service_backend.dto.WorklogResponseDTO;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.service.WorklogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller-level tests. Agent display-name resolution and DTO mapping now live in
 * {@link WorklogService}; here we verify the controller delegates to the service's
 * DTO methods. The resolution logic itself is covered in {@code WorklogServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class TicketWorklogControllerTest {

    @Mock
    private WorklogService worklogService;

    private TicketWorklogController controller;

    @BeforeEach
    void setUp() {
        controller = new TicketWorklogController(worklogService);
    }

    @Test
    void addWorklog_returnsCreated() {
        WorklogRequestDTO dto = WorklogRequestDTO.builder().minutes(30).description("debugging").build();
        TicketWorklog saved = TicketWorklog.builder().id(1L).ticketId(10L).agentId("agent-1").minutes(30).description("debugging").build();
        when(worklogService.addWorklog(10L, dto, "agent-1")).thenReturn(saved);
        when(worklogService.toDto(saved)).thenReturn(WorklogResponseDTO.fromEntity(saved, null));

        ResponseEntity<WorklogResponseDTO> response = controller.addWorklog(10L, dto, jwtWithRoles("agent-1", List.of("AGENT")));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(1L, response.getBody().getId());
    }

    @Test
    void getWorklogsByTicket_returnsList() {
        TicketWorklog w = TicketWorklog.builder().id(1L).ticketId(10L).agentId("agent-1").minutes(20).build();
        when(worklogService.getWorklogDtosByTicket(10L, "agent-1", List.of("AGENT")))
                .thenReturn(List.of(WorklogResponseDTO.fromEntity(w, null)));

        ResponseEntity<List<WorklogResponseDTO>> response = controller.getWorklogsByTicket(10L, jwtWithRoles("agent-1", List.of("AGENT")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void getWorklogsByTicket_resolvesAgentName() {
        TicketWorklog w = TicketWorklog.builder().id(1L).ticketId(10L).agentId("agent-1").minutes(20).build();
        when(worklogService.getWorklogDtosByTicket(10L, "agent-1", List.of("AGENT")))
                .thenReturn(List.of(WorklogResponseDTO.fromEntity(w, "Ahmet Yılmaz")));

        ResponseEntity<List<WorklogResponseDTO>> response = controller.getWorklogsByTicket(10L, jwtWithRoles("agent-1", List.of("AGENT")));

        assertEquals("Ahmet Yılmaz", response.getBody().get(0).getAgentName());
    }

    @Test
    void getAllWorklogs_returnsList() {
        when(worklogService.getAllWorklogDtos()).thenReturn(List.of(
                WorklogResponseDTO.fromEntity(TicketWorklog.builder().id(1L).build(), null),
                WorklogResponseDTO.fromEntity(TicketWorklog.builder().id(2L).build(), null)));

        ResponseEntity<List<WorklogResponseDTO>> response = controller.getAllWorklogs();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void updateWorklog_returnsUpdated() {
        WorklogRequestDTO dto = WorklogRequestDTO.builder().minutes(45).description("done").build();
        TicketWorklog updated = TicketWorklog.builder().id(5L).ticketId(10L).agentId("agent-1").minutes(45).description("done").build();
        when(worklogService.updateWorklog(10L, 5L, dto, "agent-1")).thenReturn(updated);
        when(worklogService.toDto(updated)).thenReturn(WorklogResponseDTO.fromEntity(updated, null));

        ResponseEntity<WorklogResponseDTO> response = controller.updateWorklog(10L, 5L, dto, jwtWithRoles("agent-1", List.of("AGENT")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(45, response.getBody().getMinutes());
    }

    @Test
    void deleteWorklog_returnsNoContent() {
        ResponseEntity<Void> response = controller.deleteWorklog(10L, 5L, jwtWithRoles("agent-1", List.of("AGENT")));

        assertEquals(204, response.getStatusCode().value());
        verify(worklogService).deleteWorklog(5L, "agent-1", List.of("AGENT"));
    }

    private Jwt jwtWithRoles(String subject, List<String> roles) {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", roles));
        return jwt;
    }
}
