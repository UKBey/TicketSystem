package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(RepositoryTestConfig.class)
@Transactional
class ResolutionNoteRepositoryTest {

    @Autowired
    private ResolutionNoteRepository resolutionNoteRepository;

    @BeforeEach
    void setUp() {
        resolutionNoteRepository.deleteAll();
        resolutionNoteRepository.save(ResolutionNote.builder().ticketId(10L).agentId("agent-1").note("fixed issue").build());
        resolutionNoteRepository.save(ResolutionNote.builder().ticketId(11L).agentId("agent-1").note("another fix").build());
        resolutionNoteRepository.save(ResolutionNote.builder().ticketId(12L).agentId("agent-2").note("agent2 fix").build());
    }

    @Test
    void existsAndFindByTicketId_workAsExpected() {
        assertTrue(resolutionNoteRepository.existsByTicketId(10L));
        assertEquals("fixed issue", resolutionNoteRepository.findByTicketId(10L).orElseThrow().getNote());
    }

    @Test
    void findAllByAgentId_returnsOnlyAgentNotes() {
        List<ResolutionNote> notes = resolutionNoteRepository.findAllByAgentId("agent-1");

        assertEquals(2, notes.size());
    }

    @Test
    void deleteByTicketId_removesMatchingNote() {
        resolutionNoteRepository.deleteByTicketId(12L);

        assertEquals(false, resolutionNoteRepository.existsByTicketId(12L));
    }
}
