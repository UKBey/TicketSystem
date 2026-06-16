package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.CsatDTO;
import com.ticketsystem.it_service_backend.entity.Csat;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.CsatRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ticketsystem.it_service_backend.entity.TicketStatus;

@ExtendWith(MockitoExtension.class)
class CsatServiceTest {

    @Mock
    private CsatRepository csatRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private TicketCommandService ticketCommandService;
    @Mock
    private TicketAuditHelper auditHelper;

    @InjectMocks
    private CsatService csatService;

    private Ticket resolvedTicket;

    @BeforeEach
    void setUp() {
        resolvedTicket = Ticket.builder().id(10L).customerId("customer-1").status(TicketStatus.RESOLVED).build();
    }

    @Test
    void submitCsat_invalidRating_throwsBadRequest() {
        CsatDTO dto = CsatDTO.builder().rating(6).comment("bad").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> csatService.submitCsat(10L, dto, "customer-1", List.of("CUSTOMER")));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void submitCsat_nonOwner_throwsForbidden() {
        CsatDTO dto = CsatDTO.builder().rating(5).comment("great").build();
        when(ticketService.getTicketById(10L)).thenReturn(resolvedTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> csatService.submitCsat(10L, dto, "other", List.of("CUSTOMER")));

        assertEquals(403, ex.getStatusCode().value());
        verify(csatRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void submitCsat_resolvedTicket_savesAndClosesTicket() {
        CsatDTO dto = CsatDTO.builder().rating(4).comment("ok").build();
        when(ticketService.getTicketById(10L)).thenReturn(resolvedTicket);
        when(csatRepository.existsByTicketId(10L)).thenReturn(false);
        when(csatRepository.save(org.mockito.ArgumentMatchers.any(Csat.class))).thenAnswer(invocation -> {
            Csat c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        Csat saved = csatService.submitCsat(10L, dto, "customer-1", List.of("CUSTOMER"));

        assertEquals(1L, saved.getId());
        verify(ticketCommandService).closeTicket(10L, "CSAT_SUBMITTED", null, "customer-1", List.of("CUSTOMER"));
    }

    @Test
    void getCsatByTicketId_whenMissing_throwsNotFound() {
        // Manager removed from operational access; ADMIN should be used for ticket auth
        when(ticketService.getTicketWithAuth(10L, "admin-1", List.of("ADMIN"))).thenReturn(resolvedTicket);
        when(csatRepository.findByTicketId(10L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> csatService.getCsatByTicketId(10L, "admin-1", List.of("ADMIN")));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getCsatByTicketId_whenFound_returnsCsat() {
        Csat csat = Csat.builder().id(1L).ticketId(10L).rating(4).build();
        when(ticketService.getTicketWithAuth(10L, "customer-1", List.of("CUSTOMER"))).thenReturn(resolvedTicket);
        when(csatRepository.findByTicketId(10L)).thenReturn(Optional.of(csat));

        Csat result = csatService.getCsatByTicketId(10L, "customer-1", List.of("CUSTOMER"));

        assertEquals(1L, result.getId());
        assertEquals(4, result.getRating());
    }

    @Test
    void submitCsat_nullRating_throwsBadRequest() {
        CsatDTO dto = CsatDTO.builder().rating(null).comment("ok").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> csatService.submitCsat(10L, dto, "customer-1", List.of("CUSTOMER")));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void submitCsat_zeroRating_throwsBadRequest() {
        CsatDTO dto = CsatDTO.builder().rating(0).comment("bad").build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> csatService.submitCsat(10L, dto, "customer-1", List.of("CUSTOMER")));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void submitCsat_inProgressTicket_throwsBadRequest() {
        CsatDTO dto = CsatDTO.builder().rating(4).comment("ok").build();
        Ticket inProgressTicket = Ticket.builder().id(10L).customerId("customer-1").status(TicketStatus.IN_PROGRESS).build();
        when(ticketService.getTicketById(10L)).thenReturn(inProgressTicket);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> csatService.submitCsat(10L, dto, "customer-1", List.of("CUSTOMER")));

        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void submitCsat_closedTicket_savesWithoutClosingAgain() {
        CsatDTO dto = CsatDTO.builder().rating(5).comment("great").build();
        Ticket closedTicket = Ticket.builder().id(10L).customerId("customer-1").status(TicketStatus.CLOSED).build();
        when(ticketService.getTicketById(10L)).thenReturn(closedTicket);
        when(csatRepository.existsByTicketId(10L)).thenReturn(false);
        when(csatRepository.save(org.mockito.ArgumentMatchers.any(Csat.class))).thenAnswer(invocation -> {
            Csat c = invocation.getArgument(0);
            c.setId(2L);
            return c;
        });

        Csat saved = csatService.submitCsat(10L, dto, "customer-1", List.of("CUSTOMER"));

        assertEquals(2L, saved.getId());
    }

    @Test
    void submitCsat_duplicateCsat_onClosedTicket_throwsBadRequest() {
        CsatDTO dto = CsatDTO.builder().rating(3).comment("ok").build();
        Ticket closedTicket = Ticket.builder().id(10L).customerId("customer-1").status(TicketStatus.CLOSED).build();
        when(ticketService.getTicketById(10L)).thenReturn(closedTicket);
        when(csatRepository.existsByTicketId(10L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> csatService.submitCsat(10L, dto, "customer-1", List.of("CUSTOMER")));

        assertEquals(400, ex.getStatusCode().value());
        verify(csatRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(ticketCommandService, never()).closeTicket(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /**
     * Recovery path: a prior submit saved the survey but the auto-close failed
     * (workflow hiccup), leaving the ticket RESOLVED. Re-submitting must complete
     * the close idempotently — not reject with "already exists" — and must not
     * write a second survey.
     */
    @Test
    void submitCsat_existingCsat_resolvedTicket_completesCloseAndKeepsSurvey() {
        CsatDTO dto = CsatDTO.builder().rating(3).comment("retry").build();
        Csat existing = Csat.builder().id(7L).ticketId(10L).rating(4).comment("first").build();
        when(ticketService.getTicketById(10L)).thenReturn(resolvedTicket);
        when(csatRepository.existsByTicketId(10L)).thenReturn(true);
        when(csatRepository.findByTicketId(10L)).thenReturn(Optional.of(existing));

        Csat result = csatService.submitCsat(10L, dto, "customer-1", List.of("CUSTOMER"));

        assertEquals(7L, result.getId());
        assertEquals(4, result.getRating()); // original survey preserved, not overwritten
        verify(ticketCommandService).closeTicket(10L, "CSAT_SUBMITTED", null, "customer-1", List.of("CUSTOMER"));
        verify(csatRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
