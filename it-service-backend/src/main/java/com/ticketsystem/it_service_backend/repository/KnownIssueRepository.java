package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.KnownIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnownIssueRepository extends JpaRepository<KnownIssue, Long> {

    List<KnownIssue> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<KnownIssue> findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(Long productId);

    List<KnownIssue> findByProductIdAndTopicIdOrderByCreatedAtDesc(Long productId, Long topicId);

    List<KnownIssue> findByProductIdAndTopicIdAndIsActiveTrueOrderByCreatedAtDesc(Long productId, Long topicId);
}
