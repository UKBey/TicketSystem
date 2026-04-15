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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    void getResolutionNoteByTicket_agentNotAssignee_throwsForbidden() {
        Ticket otherTicket = Ticket.builder().id(30L).assigneeId("agent-2").status("IN_PROGRESS").build();
        when(ticketService.getTicketById(30L)).thenReturn(otherTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> resolutionNoteService.getResolutionNoteByTicket(30L, "agent-1", List.of("AGENT")));

        assertEquals(403, ex.getStatusCode().value());
    }
}
