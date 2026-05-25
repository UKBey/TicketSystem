package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link SlaPolicy} için JPA repository — öncelik bazında SLA hedef saatlerini sağlar.
 */
@Repository
public interface SlaPolicyJpaRepository extends JpaRepository<SlaPolicy, Long> {

    Optional<SlaPolicy> findByPriority(String priority);
}
