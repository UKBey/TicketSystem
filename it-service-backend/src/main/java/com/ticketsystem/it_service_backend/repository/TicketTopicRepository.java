package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * {@link TicketTopic} için JPA repository — ürün bazında topic listeleme;
 * {@code isActive=true} varyantı yeni bilet form'unda kullanılır (soft-delete aware).
 */
public interface TicketTopicRepository extends JpaRepository<TicketTopic, Long> {

    List<TicketTopic> findByProductIdOrderByNameAsc(Long productId);

    List<TicketTopic> findByProductIdAndIsActiveTrueOrderByNameAsc(Long productId);

    Optional<TicketTopic> findByProductIdAndNameIgnoreCase(Long productId, String name);

    boolean existsByProductIdAndId(Long productId, Long id);
}
