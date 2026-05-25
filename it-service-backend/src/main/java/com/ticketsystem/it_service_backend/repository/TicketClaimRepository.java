package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link TicketClaim} — queries claim records on the ticket ↔ agent
 * many-to-many bridge, and provides active-workload counts (excluding CLOSED) plus
 * bulk ID lookups.
 */
public interface TicketClaimRepository extends JpaRepository<TicketClaim, Long> {

    List<TicketClaim> findByTicketId(Long ticketId);

    List<TicketClaim> findByAgentId(String agentId);

    Optional<TicketClaim> findByTicketIdAndAgentId(Long ticketId, String agentId);

    boolean existsByTicketIdAndAgentId(Long ticketId, String agentId);

    void deleteByTicketIdAndAgentId(Long ticketId, String agentId);

    void deleteByTicketId(Long ticketId);

    long countByTicketId(Long ticketId);

        /**
         * Counts an agent's active (non-CLOSED) tickets under a given product.
         * Used in {@code AgentProductLimit} checks and pre-claim limit validation.
         */
        @Query("""
                        SELECT COUNT(tc)
                        FROM TicketClaim tc
                        JOIN tc.ticket t
                        WHERE tc.agentId = :agentId
                            AND t.productId = :productId
                            AND t.status <> 'CLOSED'
                        """)
        long countActiveTicketsByAgentAndProduct(@Param("agentId") String agentId,
                                                                                         @Param("productId") Long productId);

    /** Returns the ID list of all tickets claimed by an agent (used as an IN-list in ticket paging queries). */
    @Query("SELECT tc.ticket.id FROM TicketClaim tc WHERE tc.agentId = :agentId")
    List<Long> findTicketIdsByAgentId(@Param("agentId") String agentId);

    /**
     * Returns the claimed ticket IDs for multiple agents in a single query.
     * Returns: each row is {@code [agent_id, ticket_id]}; used to avoid N+1 queries.
     */
    @Query("SELECT tc.agentId, tc.ticket.id FROM TicketClaim tc WHERE tc.agentId IN :agentIds")
    List<Object[]> findAgentIdAndTicketIdByAgentIdIn(@Param("agentIds") List<String> agentIds);
}
