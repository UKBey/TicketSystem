package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.WorklogRequestDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.repository.WorklogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorklogServiceTest {

    @Mock
    private WorklogRepository worklogRepository;
    @Mock
    private TicketService ticketService;

    @InjectMocks
    private WorklogService worklogService;

    private Ticket assignedTicket;

    @BeforeEach
    void setUp() {
        assignedTicket = Ticket.builder().id(20L).assigneeId("agent-1").status("IN_PROGRESS").build();
    }

    @Test
    void addWorklog_nonPositiveMinutes_throwsBadRequest() {
        WorklogRequestDTO dto = WorklogRequestDTO.builder().minutes(0).description("x").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.addWorklog(20L, dto, "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void addWorklog_assigneeCanAdd_savesRecord() {
        WorklogRequestDTO dto = WorklogRequestDTO.builder().minutes(30).description("investigation").build();
        when(ticketService.getTicketById(20L)).thenReturn(assignedTicket);
        when(worklogRepository.save(any(TicketWorklog.class))).thenAnswer(invocation -> {
            TicketWorklog w = invocation.getArgument(0);
            w.setId(1L);
            return w;
        });

        TicketWorklog saved = worklogService.addWorklog(20L, dto, "agent-1");

        assertEquals(1L, saved.getId());
        assertEquals(30, saved.getMinutes());
    }

    @Test
    void getWorklogsByTicket_agentNotAssignee_throwsForbidden() {
        Ticket otherAssigned = Ticket.builder().id(20L).assigneeId("agent-2").status("IN_PROGRESS").build();
        when(ticketService.getTicketById(20L)).thenReturn(otherAssigned);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.getWorklogsByTicket(20L, "agent-1", List.of("AGENT")));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void deleteWorklog_agentOwner_deletes() {
        TicketWorklog worklog = TicketWorklog.builder().id(99L).ticketId(20L).agentId("agent-1").minutes(15).build();
        when(worklogRepository.findById(99L)).thenReturn(Optional.of(worklog));

        worklogService.deleteWorklog(99L, "agent-1", List.of("AGENT"));

        verify(worklogRepository).deleteById(99L);
    }
}
