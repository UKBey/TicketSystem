package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.ResolutionNoteRequestDTO;
import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.ResolutionNoteRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolutionNoteServiceTest {

    @Mock
    private ResolutionNoteRepository resolutionNoteRepository;
    @Mock
    private TicketService ticketService;

    @InjectMocks
    private ResolutionNoteService resolutionNoteService;

    private Ticket assignedTicket;

    @BeforeEach
    void setUp() {
        assignedTicket = Ticket.builder().id(30L).assigneeId("agent-1").status("IN_PROGRESS").build();
    }

    @Test
    void createResolutionNote_blankNote_throwsBadRequest() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("   ").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.createResolutionNote(30L, dto, "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void createResolutionNote_alreadyExists_throwsConflict() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("fixed by patch").build();
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);
        when(resolutionNoteRepository.existsByTicketId(30L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.createResolutionNote(30L, dto, "agent-1"));

        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void createResolutionNote_whenAssigneeAndOpen_createsNote() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("fixed by restart").build();
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);
        when(resolutionNoteRepository.existsByTicketId(30L)).thenReturn(false);
        when(resolutionNoteRepository.save(any(ResolutionNote.class))).thenAnswer(invocation -> {
            ResolutionNote note = invocation.getArgument(0);
            note.setId(9L);
            return note;
        });

        ResolutionNote created = resolutionNoteService.createResolutionNote(30L, dto, "agent-1");

        assertNotNull(created.getId());
        assertEquals("fixed by restart", created.getNote());
        assertEquals("agent-1", created.getAgentId());
    }

    @Test
    void createResolutionNote_whenAgentNotAssignee_throwsForbidden() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("fixed by patch").build();
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.createResolutionNote(30L, dto, "agent-2"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void createResolutionNote_whenTicketClosed_throwsBadRequest() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("fixed by patch").build();
        Ticket closed = Ticket.builder().id(30L).assigneeId("agent-1").status("CLOSED").build();
        when(ticketService.getTicketById(30L)).thenReturn(closed);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.createResolutionNote(30L, dto, "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateResolutionNote_ownerCanUpdate() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("updated note").build();
        ResolutionNote existing = ResolutionNote.builder().id(1L).ticketId(30L).agentId("agent-1").note("old").build();
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);
        when(resolutionNoteRepository.findByTicketId(30L)).thenReturn(Optional.of(existing));
        when(resolutionNoteRepository.save(any(ResolutionNote.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResolutionNote updated = resolutionNoteService.updateResolutionNote(30L, dto, "agent-1");

        assertEquals("updated note", updated.getNote());
    }

    @Test
    void updateResolutionNote_blankNote_throwsBadRequest() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note(" ").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.updateResolutionNote(30L, dto, "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateResolutionNote_whenAgentNotAssignee_throwsForbidden() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("updated").build();
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.updateResolutionNote(30L, dto, "agent-2"));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void updateResolutionNote_whenTicketClosed_throwsBadRequest() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("updated").build();
        Ticket closed = Ticket.builder().id(30L).assigneeId("agent-1").status("CLOSED").build();
        when(ticketService.getTicketById(30L)).thenReturn(closed);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.updateResolutionNote(30L, dto, "agent-1"));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void updateResolutionNote_whenMissing_throwsNotFound() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("updated").build();
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);
        when(resolutionNoteRepository.findByTicketId(30L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.updateResolutionNote(30L, dto, "agent-1"));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getResolutionNoteByTicket_agentNotAssignee_throwsForbidden() {
        Ticket otherTicket = Ticket.builder().id(30L).assigneeId("agent-2").status("IN_PROGRESS").build();
        when(ticketService.getTicketById(30L)).thenReturn(otherTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.getResolutionNoteByTicket(30L, "agent-1", List.of("AGENT")));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void getResolutionNoteByTicket_managerCanView() {
        ResolutionNote note = ResolutionNote.builder().id(2L).ticketId(30L).note("fixed").build();
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);
        when(resolutionNoteRepository.findByTicketId(30L)).thenReturn(Optional.of(note));

        // Manager role removed from operational access; AGENT_ADMIN should view resolution notes.
        ResolutionNote result = resolutionNoteService.getResolutionNoteByTicket(30L, "admin-1", List.of("AGENT_ADMIN"));

        assertEquals(2L, result.getId());
    }

    @Test
    void getResolutionNoteByTicket_assignedAgentCanView() {
        ResolutionNote note = ResolutionNote.builder().id(3L).ticketId(30L).note("fixed").build();
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);
        when(resolutionNoteRepository.findByTicketId(30L)).thenReturn(Optional.of(note));

        ResolutionNote result = resolutionNoteService.getResolutionNoteByTicket(30L, "agent-1", List.of("AGENT"));

        assertEquals(3L, result.getId());
    }

    @Test
    void getResolutionNoteByTicket_whenNoRole_throwsForbidden() {
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.getResolutionNoteByTicket(30L, "user-1", List.of("CUSTOMER")));

        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void getResolutionNoteByTicket_whenNotFound_throwsNotFound() {
        when(ticketService.getTicketById(30L)).thenReturn(assignedTicket);
        when(resolutionNoteRepository.findByTicketId(30L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> resolutionNoteService.getResolutionNoteByTicket(30L, "admin-1", List.of("AGENT_ADMIN")));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getAllResolutionNotes_returnsAll() {
        when(resolutionNoteRepository.findAll()).thenReturn(List.of(
                ResolutionNote.builder().id(10L).build(),
                ResolutionNote.builder().id(11L).build()
        ));

        List<ResolutionNote> result = resolutionNoteService.getAllResolutionNotes();

        assertEquals(2, result.size());
        verify(resolutionNoteRepository).findAll();
    }
}
