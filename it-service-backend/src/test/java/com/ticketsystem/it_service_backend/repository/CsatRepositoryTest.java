package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Csat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(RepositoryTestConfig.class)
@Transactional
class CsatRepositoryTest {

    @Autowired
    private CsatRepository csatRepository;

    @BeforeEach
    void setUp() {
        csatRepository.deleteAll();
        csatRepository.save(Csat.builder()
                .ticketId(100L)
                .rating(5)
                .comment("Excellent")
                .build());
    }

    @Test
    void existsByTicketId_returnsTrueForExistingTicket() {
        assertTrue(csatRepository.existsByTicketId(100L));
        assertFalse(csatRepository.existsByTicketId(999L));
    }

    @Test
    void findByTicketId_returnsCsatRecord() {
        Optional<Csat> result = csatRepository.findByTicketId(100L);

        assertTrue(result.isPresent());
        assertEquals(5, result.get().getRating());
        assertEquals("Excellent", result.get().getComment());
    }

    @Test
    void deleteByTicketId_removesMatchingRecord() {
        csatRepository.deleteByTicketId(100L);

        assertFalse(csatRepository.existsByTicketId(100L));
    }
}
