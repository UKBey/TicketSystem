package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Priority;
import com.ticketsystem.it_service_backend.entity.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository for {@link SlaPolicy} — provides the SLA target hours per priority.
 */
@Repository
public interface SlaPolicyJpaRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findByPriority(Priority priority);
}
