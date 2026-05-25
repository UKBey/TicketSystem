package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.KnownIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * {@link KnownIssue} için JPA repository — ürün ve opsiyonel topic bazında bilgi tabanı
 * kayıtlarını en yeni → en eski sırada listeler; aktif-yalnız varyantları da sağlanır.
 */
public interface KnownIssueRepository extends JpaRepository<KnownIssue, Long> {

    List<KnownIssue> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<KnownIssue> findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(Long productId);

    List<KnownIssue> findByProductIdAndTopicIdOrderByCreatedAtDesc(Long productId, Long topicId);

    List<KnownIssue> findByProductIdAndTopicIdAndIsActiveTrueOrderByCreatedAtDesc(Long productId, Long topicId);
}
