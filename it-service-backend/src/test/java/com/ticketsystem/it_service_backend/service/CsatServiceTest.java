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

@ExtendWith(MockitoExtension.class)
class CsatServiceTest {

    @Mock
    private CsatRepository csatRepository;
    @Mock
    private TicketService ticketService;

    @InjectMocks
    private CsatService csatService;

    private Ticket resolvedTicket;

    @BeforeEach
    void setUp() {
        resolvedTicket = Ticket.builder().id(10L).customerId("customer-1").status("RESOLVED").build();
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
        verify(ticketService).updateTicketStatus(10L, "CLOSED", "customer-1", List.of("CUSTOMER"));
    }

    @Test
    void getCsatByTicketId_whenMissing_throwsNotFound() {
        when(ticketService.getTicketWithAuth(10L, "manager-1", List.of("MANAGER"))).thenReturn(resolvedTicket);
        when(csatRepository.findByTicketId(10L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> csatService.getCsatByTicketId(10L, "manager-1", List.of("MANAGER")));

        assertEquals(404, ex.getStatusCode().value());
    }
}
