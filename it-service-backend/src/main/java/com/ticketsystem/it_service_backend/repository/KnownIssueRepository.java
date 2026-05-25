package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.KnownIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for {@link KnownIssue} — lists knowledge-base entries by product
 * (and optionally by topic) newest-first; active-only variants are also provided.
 */
public interface KnownIssueRepository extends JpaRepository<KnownIssue, Long> {

    List<KnownIssue> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<KnownIssue> findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(Long productId);

    List<KnownIssue> findByProductIdAndTopicIdOrderByCreatedAtDesc(Long productId, Long topicId);

    List<KnownIssue> findByProductIdAndTopicIdAndIsActiveTrueOrderByCreatedAtDesc(Long productId, Long topicId);
}
