package com.ticketsystem.it_service_backend.entity;

import com.ticketsystem.it_service_backend.converter.LanguageConverter;
import com.ticketsystem.it_service_backend.converter.ThemeConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

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

    // Birincil/gösterim rolü (geriye uyumluluk + landing varsayılanı). Kümeden türetilir
    // (resolveHighestRole). Gerçek yetkilendirme JWT authority'leri + {@link #roles} üzerinden.
    @Column(nullable = true, length = 20)
    private String role; // null: rol ataması henüz yapılmamış

    /**
     * Kullanıcının sahip olduğu TÜM roller (additive çoklu rol). Keycloak realm rollerinden
     * her login'de senkronlanır. Etkin yetki = bu kümenin birleşimi.
     * Değerler: CUSTOMER, AGENT, LEAD_AGENT, ADMIN, MANAGER.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Builder.Default
    private Set<String> roles = new HashSet<>();

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * The user's preferred language (ISO 639-1). Persisted as its lower-case code
     * ({@code en}/{@code tr}) via {@link LanguageConverter}.
     */
    @Convert(converter = LanguageConverter.class)
    @Column(name = "preferred_language", length = 5, nullable = false)
    @Builder.Default
    private Language preferredLanguage = Language.DEFAULT;

    /**
     * The user's preferred theme. Persisted as its lower-case token ({@code light}/{@code dark})
     * via {@link ThemeConverter}. Email templates pick a light or dark palette from this value.
     */
    @Convert(converter = ThemeConverter.class)
    @Column(name = "preferred_theme", length = 10, nullable = false)
    @Builder.Default
    private Theme preferredTheme = Theme.DEFAULT;

    /**
     * The user's preferred date display format (a preset key the frontend understands).
     * Drives every date shown in the UI; persisted as the enum name (see {@link DateFormat}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_date_format", length = 20, nullable = false)
    @Builder.Default
    private DateFormat preferredDateFormat = DateFormat.DEFAULT;

    /**
     * Last-used selections in the PDF export modal (which sections, PDF language, PDF
     * theme), stored as an opaque JSON string defined by the frontend. Null until the
     * user generates their first PDF, after which defaults are derived client-side.
     */
    @Column(name = "pdf_export_preferences", length = 2000)
    private String pdfExportPreferences;

    /**
     * Agent/lead sidebar ticket-panel visibility selections (workspace, pool, history,
     * team, all-tickets), stored as an opaque JSON string defined by the frontend. Null
     * until the user toggles a panel, after which all panels default to visible client-side.
     */
    @Column(name = "panel_preferences", length = 500)
    private String panelPreferences;

    @Column(name = "onboarding_completed", nullable = false)
    @Builder.Default
    private Boolean onboardingCompleted = false;

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