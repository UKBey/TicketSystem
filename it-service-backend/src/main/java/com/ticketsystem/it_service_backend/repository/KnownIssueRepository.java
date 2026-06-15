package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.KnownIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * JPA repository for {@link KnownIssue} — lists knowledge-base entries by product
 * (and optionally by topic) newest-first; active-only variants are also provided.
 * Plain {@code List} variants serve the full-list consumers (ticket creation modal,
 * mobile); {@code Page} variants back the paginated management screen.
 */
public interface KnownIssueRepository extends JpaRepository<KnownIssue, Long> {

    List<KnownIssue> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<KnownIssue> findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(Long productId);

    List<KnownIssue> findByProductIdAndTopicIdOrderByCreatedAtDesc(Long productId, Long topicId);

    List<KnownIssue> findByProductIdAndTopicIdAndIsActiveTrueOrderByCreatedAtDesc(Long productId, Long topicId);

    // ── Paginated variants (management screen) ────────────────────────────────
    Page<KnownIssue> findByProductId(Long productId, Pageable pageable);

    Page<KnownIssue> findByProductIdAndIsActiveTrue(Long productId, Pageable pageable);

    Page<KnownIssue> findByProductIdAndTopicId(Long productId, Long topicId, Pageable pageable);

    Page<KnownIssue> findByProductIdAndTopicIdAndIsActiveTrue(Long productId, Long topicId, Pageable pageable);
}
