package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Canned response (quick reply) — a reusable text template an agent can insert into the
 * comment composer with one click or a {@code /shortcut}.
 *
 * <p>Two scopes exist:
 * <ul>
 *   <li>{@code PERSONAL} — owned by a single agent ({@link #ownerAgentId}).</li>
 *   <li>{@code SHARED}   — team-wide, managed by {@code AGENT_ADMIN}/{@code MANAGER}.</li>
 * </ul>
 * Either scope may optionally be tied to a {@link Product} ({@link #productId}) or be global
 * ({@code productId == null}); a product-scoped template only surfaces on that product's tickets.
 *
 * <p>{@link #visibility} aligns the template with the comment type it suits: {@code EXTERNAL}
 * (customer-facing reply), {@code INTERNAL} (internal note) or {@code BOTH}.
 *
 * <p>Content is bilingual: {@link #contentTr} / {@link #contentEn}; at least one must be present.
 * Placeholders ({@code {{musteri.ad}}} etc.) are stored raw and filled on the client.
 */
@Entity
@Table(name = "canned_responses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CannedResponse {

    /** {@code PERSONAL} or {@code SHARED}. */
    public static final String SCOPE_PERSONAL = "PERSONAL";
    public static final String SCOPE_SHARED = "SHARED";

    /** {@code EXTERNAL}, {@code INTERNAL} or {@code BOTH}. */
    public static final String VISIBILITY_EXTERNAL = "EXTERNAL";
    public static final String VISIBILITY_INTERNAL = "INTERNAL";
    public static final String VISIBILITY_BOTH = "BOTH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    /** Lower-cased keyword typed after {@code /} in the composer (without the slash); optional. */
    @Column(length = 50)
    private String shortcut;

    @Column(name = "content_tr", columnDefinition = "TEXT")
    private String contentTr;

    @Column(name = "content_en", columnDefinition = "TEXT")
    private String contentEn;

    @Column(nullable = false, length = 20)
    private String scope;

    /** Keycloak subject of the owning/creating agent. */
    @Column(name = "owner_agent_id", nullable = false, length = 50)
    private String ownerAgentId;

    /** Optional product binding (either scope); {@code null} means global (all products). */
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String visibility = VISIBILITY_BOTH;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
