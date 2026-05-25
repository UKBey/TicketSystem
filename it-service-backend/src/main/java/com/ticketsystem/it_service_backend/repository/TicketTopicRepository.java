package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link TicketTopic} — per-product topic listing; the
 * {@code isActive=true} variant is used by the new-ticket form (soft-delete aware).
 */
public interface TicketTopicRepository extends JpaRepository<TicketTopic, Long> {

    List<TicketTopic> findByProductIdOrderByNameAsc(Long productId);

    List<TicketTopic> findByProductIdAndIsActiveTrueOrderByNameAsc(Long productId);

    Optional<TicketTopic> findByProductIdAndNameIgnoreCase(Long productId, String name);

    boolean existsByProductIdAndId(Long productId, Long id);
}
