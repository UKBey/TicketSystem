package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Bir {@link Product} altındaki bilet konusu/kategorisi (örn. "Kurulum", "Lisans").
 *
 * <p>{@link Ticket} bir topic'e referans verir ve oluşturma anında topic adı
 * {@link Ticket#getTopicNameSnapshot()} olarak da snapshot'lanır. {@code isActive=false}
 * topic'leri yeni biletlerde gösterilmez ama mevcut biletlerin referansı korunur.
 * {@code (product_id, name)} unique — aynı ürün altında aynı isimli topic olmaz.
 */
@Entity
@Table(name = "ticket_topics",
        uniqueConstraints = @UniqueConstraint(name = "uq_ticket_topics_product_name", columnNames = {"product_id", "name"}))
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

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
