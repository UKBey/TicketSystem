package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.ResolutionNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResolutionNoteRepository extends JpaRepository<ResolutionNote, Long> {

    Optional<ResolutionNote> findByTicketId(Long ticketId);

    boolean existsByTicketId(Long ticketId);

    List<ResolutionNote> findAllByAgentId(String agentId);
    void deleteByTicketId(Long ticketId);}
