package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.ResolutionNoteRequestDTO;
import com.ticketsystem.it_service_backend.dto.ResolutionNoteResponseDTO;
import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import com.ticketsystem.it_service_backend.service.ResolutionNoteService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketResolutionNoteControllerTest {

    @Mock
    private ResolutionNoteService resolutionNoteService;

    private TicketResolutionNoteController controller;

    @BeforeEach
    void setUp() {
        controller = new TicketResolutionNoteController(resolutionNoteService);
    }

    @Test
    void createResolutionNote_returnsCreated() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("fixed with restart").build();
        ResolutionNote saved = ResolutionNote.builder().id(1L).ticketId(10L).agentId("agent-1").note("fixed with restart").build();
        when(resolutionNoteService.createResolutionNote(10L, dto, "agent-1")).thenReturn(saved);

        ResponseEntity<ResolutionNoteResponseDTO> response = controller.createResolutionNote(10L, dto, jwtWithRoles("agent-1", List.of("AGENT")));

        assertEquals(201, response.getStatusCode().value());
        assertEquals(1L, response.getBody().getId());
    }

    @Test
    void updateResolutionNote_returnsOk() {
        ResolutionNoteRequestDTO dto = ResolutionNoteRequestDTO.builder().note("updated").build();
        ResolutionNote saved = ResolutionNote.builder().id(2L).ticketId(10L).agentId("agent-1").note("updated").build();
        when(resolutionNoteService.updateResolutionNote(10L, dto, "agent-1")).thenReturn(saved);

        ResponseEntity<ResolutionNoteResponseDTO> response = controller.updateResolutionNote(10L, dto, jwtWithRoles("agent-1", List.of("AGENT")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("updated", response.getBody().getNote());
    }

    @Test
    void getResolutionNote_returnsOk() {
        ResolutionNote note = ResolutionNote.builder().id(3L).ticketId(10L).agentId("agent-1").note("note").build();
        when(resolutionNoteService.getResolutionNoteByTicket(10L, "manager-1", List.of("MANAGER"))).thenReturn(note);

        ResponseEntity<ResolutionNoteResponseDTO> response = controller.getResolutionNote(10L, jwtWithRoles("manager-1", List.of("MANAGER")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3L, response.getBody().getId());
    }

    @Test
    void getAllResolutionNotes_returnsList() {
        when(resolutionNoteService.getAllResolutionNotes()).thenReturn(List.of(
                ResolutionNote.builder().id(1L).build(),
                ResolutionNote.builder().id(2L).build()
        ));

        ResponseEntity<List<ResolutionNoteResponseDTO>> response = controller.getAllResolutionNotes();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2, response.getBody().size());
    }

    private Jwt jwtWithRoles(String subject, List<String> roles) {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", roles));
        return jwt;
    }
}
