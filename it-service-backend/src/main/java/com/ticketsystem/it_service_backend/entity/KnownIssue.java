package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Known issue — a knowledge-base entry tied to a product (and optionally a topic).
 * Users can find a resolution here before opening a ticket.
 */
@Entity
@Table(name = "known_issues")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnownIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "title_tr", length = 255)
    private String titleTr;

    @Column(name = "title_en", length = 255)
    private String titleEn;

    @Column(name = "content_tr", columnDefinition = "TEXT")
    private String contentTr;

    @Column(name = "content_en", columnDefinition = "TEXT")
    private String contentEn;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
