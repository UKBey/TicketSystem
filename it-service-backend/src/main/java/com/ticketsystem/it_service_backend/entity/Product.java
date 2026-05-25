package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * IT-servisi tarafından desteklenen ürün (örn. "ERP", "CRM").
 *
 * <p>{@link User} ile {@code user_products} köprüsü üzerinden many-to-many yetkilendirme
 * ilişkisi vardır; {@link Ticket}, {@link TicketTopic}, {@link KnownIssue} ve
 * {@link AgentProductLimit} bu ürünü referans alır. Soft-delete pattern'i için
 * {@code isActive} bayrağı kullanılır — kayıt silinmez, deaktive edilir.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Otomatik artan ID
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /** Bu ürün için bir agent'ın aynı anda sahiplenebileceği maks. aktif bilet sayısı (varsayılan limit). */
    @Column(name = "max_active_tickets")
    private Integer maxActiveTickets;
}