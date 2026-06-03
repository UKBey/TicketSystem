package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CsatDTO;
import com.ticketsystem.it_service_backend.entity.Csat;
import com.ticketsystem.it_service_backend.service.CsatService;
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
class TicketCsatControllerTest {

    @Mock
    private CsatService csatService;

    private TicketCsatController controller;

    @BeforeEach
    void setUp() {
        controller = new TicketCsatController(csatService);
    }

    @Test
    void submitCsat_returnsSavedEntity() {
        CsatDTO dto = CsatDTO.builder().rating(5).comment("great").build();
        Csat saved = Csat.builder().id(1L).ticketId(10L).rating(5).comment("great").build();
        when(csatService.submitCsat(10L, dto, "customer-1", List.of("CUSTOMER"))).thenReturn(saved);

        ResponseEntity<Csat> response = controller.submitCsat(10L, dto, jwtWithRoles("customer-1", List.of("CUSTOMER")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1L, response.getBody().getId());
    }

    @Test
    void getCsat_returnsEntity() {
        Csat csat = Csat.builder().id(2L).ticketId(11L).rating(4).build();

        // Manager role no longer has operational access; ADMIN should be used
        when(csatService.getCsatByTicketId(11L, "admin-1", List.of("ADMIN"))).thenReturn(csat);

        ResponseEntity<Csat> response = controller.getCsat(11L, jwtWithRoles("admin-1", List.of("ADMIN")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(2L, response.getBody().getId());
    }

    @Test
    void getAllCsats_returnsList() {
        when(csatService.getAllCsats()).thenReturn(List.of(Csat.builder().id(1L).build(), Csat.builder().id(2L).build()));

        ResponseEntity<List<Csat>> response = controller.getAllCsats();

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
