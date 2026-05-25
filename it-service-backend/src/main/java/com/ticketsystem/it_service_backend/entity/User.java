package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * System user — the application-side mirror of identities defined in Keycloak.
 *
 * <p>The ID arrives as a Keycloak UUID (not generated). The many-to-many authorization
 * relationship with {@link Product} is established through the {@code user_products}
 * bridge table — a customer can only open tickets for products they are authorized on,
 * and an agent can only claim tickets on products they are authorized on. Language and
 * theme preferences (V21+) live here; role information is also cached in the DB
 * alongside Keycloak.
 */
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

    @Column(nullable = true, length = 20)
    private String role; // CUSTOMER, AGENT, AGENT_ADMIN, MANAGER — null: rol ataması henüz yapılmamış

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * The user's preferred language code (ISO 639-1).
     * Supported values: "en" (default), "tr".
     */
    @Column(name = "preferred_language", length = 5, nullable = false)
    @Builder.Default
    private String preferredLanguage = "en";

    /**
     * The user's preferred theme. Supported values: "light" (default), "dark".
     * Email templates pick a light or dark color palette based on this value.
     */
    @Column(name = "preferred_theme", length = 10, nullable = false)
    @Builder.Default
    private String preferredTheme = "light";

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