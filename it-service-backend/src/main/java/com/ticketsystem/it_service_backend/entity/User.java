package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    private String id; // Keycloak UUID gelecek

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String role; // CUSTOMER, AGENT, AGENT_ADMIN, MANAGER

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Kullanıcının tercih ettiği dil kodu (ISO 639-1).
     * Desteklenen değerler: "en" (varsayılan), "tr"
     */
    @Column(name = "preferred_language", length = 5, nullable = false)
    @Builder.Default
    private String preferredLanguage = "en";

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_products",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    @Builder.Default
    private List<Product> authorizedProducts = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}