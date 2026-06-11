package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for {@link TicketTopic} — per-product topic listing; the
 * {@code isActive=true} variant is used by the new-ticket form (soft-delete aware).
 * Listing is id-ordered (stable); display-language sorting happens on the client.
 */
public interface TicketTopicRepository extends JpaRepository<TicketTopic, Long> {

    List<TicketTopic> findByProductIdOrderByIdAsc(Long productId);

    List<TicketTopic> findByProductIdAndIsActiveTrueOrderByIdAsc(Long productId);

    Optional<TicketTopic> findByProductIdAndNameTrIgnoreCase(Long productId, String nameTr);

    Optional<TicketTopic> findByProductIdAndNameEnIgnoreCase(Long productId, String nameEn);

    boolean existsByProductIdAndId(Long productId, Long id);
}
