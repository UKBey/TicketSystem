package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketClaimRepository extends JpaRepository<TicketClaim, Long> {

    List<TicketClaim> findByTicketId(Long ticketId);

    List<TicketClaim> findByAgentId(String agentId);

    Optional<TicketClaim> findByTicketIdAndAgentId(Long ticketId, String agentId);

    boolean existsByTicketIdAndAgentId(Long ticketId, String agentId);

    void deleteByTicketIdAndAgentId(Long ticketId, String agentId);

    void deleteByTicketId(Long ticketId);

    long countByTicketId(Long ticketId);

    // Ajanin sahiplendig biletlerin ID listesini dondurur.
    @Query("SELECT tc.ticket.id FROM TicketClaim tc WHERE tc.agentId = :agentId")
    List<Long> findTicketIdsByAgentId(@Param("agentId") String agentId);

    // Birden fazla ajan icin sahiplenilen bilet ID'lerini toplu ceker.
    @Query("SELECT tc.agentId, tc.ticket.id FROM TicketClaim tc WHERE tc.agentId IN :agentIds")
    List<Object[]> findAgentIdAndTicketIdByAgentIdIn(@Param("agentIds") List<String> agentIds);
}
