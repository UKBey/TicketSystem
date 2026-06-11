package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ticket topic/category under a {@link Product} (e.g. "Installation", "License").
 *
 * <p>{@link Ticket} references a topic, and at creation time the topic names are also
 * snapshotted as {@link Ticket#getTopicNameSnapshotTr()} / {@link Ticket#getTopicNameSnapshotEn()}.
 * Topics with {@code isActive=false} are hidden when opening new tickets, but existing
 * tickets keep their reference.
 *
 * <p>The name is bilingual ({@code nameTr} / {@code nameEn}); at least one variant must
 * be present (DB CHECK) and each variant is unique within its product.
 */
@Entity
@Table(name = "ticket_topics", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ticket_topics_product_name_tr", columnNames = {"product_id", "name_tr"}),
        @UniqueConstraint(name = "uq_ticket_topics_product_name_en", columnNames = {"product_id", "name_en"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "name_tr")
    private String nameTr;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
