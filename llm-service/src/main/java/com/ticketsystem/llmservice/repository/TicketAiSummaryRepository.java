package com.ticketsystem.llmservice.repository;

import com.ticketsystem.llmservice.entity.TicketAiSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link TicketAiSummary} records.
 *
 * <p>Provides access to a ticket's summary history — summaries are never
 * deleted, every generation creates a new row.
 */
@Repository
public interface TicketAiSummaryRepository extends JpaRepository<TicketAiSummary, Long> {

    /** Lists all summaries of a ticket sorted from newest to oldest */
    List<TicketAiSummary> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    /** Returns the most recent summary of a ticket */
    Optional<TicketAiSummary> findFirstByTicketIdOrderByCreatedAtDesc(Long ticketId);
}
