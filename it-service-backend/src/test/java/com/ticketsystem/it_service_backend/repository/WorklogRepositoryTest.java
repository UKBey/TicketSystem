package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketWorklog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(RepositoryTestConfig.class)
@Transactional
class WorklogRepositoryTest {

    @Autowired
    private WorklogRepository worklogRepository;

    @BeforeEach
    void setUp() {
        worklogRepository.deleteAll();
        worklogRepository.save(TicketWorklog.builder().ticketId(20L).agentId("agent-1").minutes(30).description("investigate").build());
        worklogRepository.save(TicketWorklog.builder().ticketId(20L).agentId("agent-1").minutes(15).description("fix").build());
        worklogRepository.save(TicketWorklog.builder().ticketId(21L).agentId("agent-2").minutes(40).description("analysis").build());
    }

    @Test
    void findByTicketId_returnsTicketWorklogs() {
        List<TicketWorklog> worklogs = worklogRepository.findByTicketId(20L);

        assertEquals(2, worklogs.size());
    }

    @Test
    void findByAgentId_returnsAgentWorklogs() {
        List<TicketWorklog> worklogs = worklogRepository.findByAgentId("agent-1");

        assertEquals(2, worklogs.size());
    }

    @Test
    void deleteByTicketId_removesMatchingRows() {
        worklogRepository.deleteByTicketId(21L);

        assertEquals(0, worklogRepository.findByTicketId(21L).size());
    }
}
