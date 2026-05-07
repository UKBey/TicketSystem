package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.WorklogRequestDTO;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import com.ticketsystem.it_service_backend.repository.TicketClaimRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorklogServiceTest {

    @Mock
    private WorklogRepository worklogRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private TicketClaimRepository ticketClaimRepository;

    @InjectMocks
    private WorklogService worklogService;

    private Ticket assignedTicket;

    @BeforeEach
    void setUp() {
        assignedTicket = Ticket.builder().id(20L).status("IN_PROGRESS").build();
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
        when(ticketClaimRepository.existsByTicketIdAndAgentId(20L, "agent-1")).thenReturn(true);
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
    void addWorklog_whenNotAssignee_throwsForbidden() {
        WorklogRequestDTO dto = WorklogRequestDTO.builder().minutes(30).description("investigation").build();
        when(ticketService.getTicketById(20L)).thenReturn(assignedTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.addWorklog(20L, dto, "agent-2"));

        assertEquals(403, ex.getStatusCode().value());
        verify(worklogRepository, never()).save(any(TicketWorklog.class));
    }

    @Test
    void addWorklog_whenTicketClosed_throwsBadRequest() {
        Ticket closedTicket = Ticket.builder().id(20L).status("CLOSED").build();
        WorklogRequestDTO dto = WorklogRequestDTO.builder().minutes(30).description("investigation").build();
        when(ticketService.getTicketById(20L)).thenReturn(closedTicket);
        when(ticketClaimRepository.existsByTicketIdAndAgentId(20L, "agent-1")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.addWorklog(20L, dto, "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void getWorklogsByTicket_agentNotAssignee_throwsForbidden() {
        Ticket otherAssigned = Ticket.builder().id(20L).status("IN_PROGRESS").build();
        when(ticketService.getTicketById(20L)).thenReturn(otherAssigned);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.getWorklogsByTicket(20L, "agent-1", List.of("AGENT")));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void getWorklogsByTicket_managerReturnsRepositoryResult() {
        TicketWorklog worklog = TicketWorklog.builder().id(10L).ticketId(20L).agentId("agent-1").minutes(15).build();
        when(ticketService.getTicketById(20L)).thenReturn(assignedTicket);
        when(worklogRepository.findByTicketId(20L)).thenReturn(List.of(worklog));

        // Manager no longer has operational access; AGENT_ADMIN can view worklogs
        List<TicketWorklog> result = worklogService.getWorklogsByTicket(20L, "admin-1", List.of("AGENT_ADMIN"));

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    void getWorklogsByTicket_withoutManagerOrAgent_throwsForbidden() {
        when(ticketService.getTicketById(20L)).thenReturn(assignedTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.getWorklogsByTicket(20L, "customer-1", List.of("CUSTOMER")));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void deleteWorklog_agentOwner_deletes() {
        TicketWorklog worklog = TicketWorklog.builder().id(99L).ticketId(20L).agentId("agent-1").minutes(15).build();
        when(worklogRepository.findById(99L)).thenReturn(Optional.of(worklog));

        worklogService.deleteWorklog(99L, "agent-1", List.of("AGENT"));

        verify(worklogRepository).deleteById(99L);
    }

    @Test
    void deleteWorklog_managerDeletes() {
        TicketWorklog worklog = TicketWorklog.builder().id(100L).ticketId(20L).agentId("agent-2").minutes(15).build();
        when(worklogRepository.findById(100L)).thenReturn(Optional.of(worklog));

        // Manager no longer authorized to delete worklogs; AGENT_ADMIN should be used
        worklogService.deleteWorklog(100L, "admin-1", List.of("AGENT_ADMIN"));

        verify(worklogRepository).deleteById(100L);
    }

    @Test
    void updateWorklog_nonPositiveMinutes_throwsBadRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.updateWorklog(20L, 101L,
                        WorklogRequestDTO.builder().minutes(0).description("x").build(), "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateWorklog_whenTicketMismatched_throwsBadRequest() {
        TicketWorklog worklog = TicketWorklog.builder().id(102L).ticketId(21L).agentId("agent-1").minutes(15).build();
        when(worklogRepository.findById(102L)).thenReturn(Optional.of(worklog));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.updateWorklog(20L, 102L,
                        WorklogRequestDTO.builder().minutes(10).description("x").build(), "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateWorklog_whenOwnerMismatch_throwsForbidden() {
        TicketWorklog worklog = TicketWorklog.builder().id(103L).ticketId(20L).agentId("agent-2").minutes(15).build();
        when(worklogRepository.findById(103L)).thenReturn(Optional.of(worklog));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.updateWorklog(20L, 103L,
                        WorklogRequestDTO.builder().minutes(10).description("x").build(), "agent-1"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void updateWorklog_whenTicketClosed_throwsBadRequest() {
        Ticket closedTicket = Ticket.builder().id(20L).status("CLOSED").build();
        TicketWorklog worklog = TicketWorklog.builder().id(104L).ticketId(20L).agentId("agent-1").minutes(15).build();
        when(worklogRepository.findById(104L)).thenReturn(Optional.of(worklog));
        when(ticketService.getTicketById(20L)).thenReturn(closedTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> worklogService.updateWorklog(20L, 104L,
                        WorklogRequestDTO.builder().minutes(10).description("x").build(), "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateWorklog_partialUpdate_keepsMissingFields() {
        TicketWorklog worklog = TicketWorklog.builder().id(105L).ticketId(20L).agentId("agent-1").minutes(15).description("old").build();
        when(worklogRepository.findById(105L)).thenReturn(Optional.of(worklog));
        when(ticketService.getTicketById(20L)).thenReturn(assignedTicket);
        when(worklogRepository.save(any(TicketWorklog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketWorklog updated = worklogService.updateWorklog(20L, 105L,
                WorklogRequestDTO.builder().description("new").build(), "agent-1");

        assertEquals(15, updated.getMinutes());
        assertEquals("new", updated.getDescription());
        verify(worklogRepository).save(worklog);
    }
}
