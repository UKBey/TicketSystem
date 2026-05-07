package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "max_active_tickets")
    private Integer maxActiveTickets;
}