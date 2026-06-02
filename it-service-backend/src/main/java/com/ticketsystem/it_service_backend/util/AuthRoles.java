package com.ticketsystem.it_service_backend.util;

import java.util.Collection;

/**
 * Centralized semantic role checks for the RBAC model.
 *
 * <p>Roles (additive multi-role; {@code LEAD_AGENT} is a Keycloak composite that includes
 * {@code AGENT}, so a lead always carries the {@code AGENT} authority too):
 * <ul>
 *   <li>{@code CUSTOMER} — end-user (own tickets, product-scoped).</li>
 *   <li>{@code AGENT} — fulfiller; product-scoped; must hold a claim to mutate.</li>
 *   <li>{@code LEAD_AGENT} — operational lead; product-scoped; may act without a claim, assign tickets.</li>
 *   <li>{@code ADMIN} — system configuration; <b>global</b> (bypasses product scope).</li>
 *   <li>{@code MANAGER} — oversight/reporting; <b>global</b> read-only.</li>
 * </ul>
 *
 * <p>Authorization is primarily enforced by {@code @PreAuthorize} on controllers; this helper
 * is for the service-layer scoping/claim decisions where the role list (from the JWT) is needed.
 */
public final class AuthRoles {

    private AuthRoles() {
    }

    public static final String CUSTOMER   = "CUSTOMER";
    public static final String AGENT      = "AGENT";
    public static final String LEAD_AGENT = "LEAD_AGENT";
    public static final String ADMIN      = "ADMIN";
    public static final String MANAGER    = "MANAGER";

    private static boolean has(Collection<String> roles, String role) {
        return roles != null && roles.contains(role);
    }

    /** ADMIN — system configuration (global). */
    public static boolean isAdmin(Collection<String> roles) {
        return has(roles, ADMIN);
    }

    /** MANAGER — oversight/reporting (global, read-only). */
    public static boolean isManager(Collection<String> roles) {
        return has(roles, MANAGER);
    }

    /** LEAD_AGENT — operational team lead (product-scoped). */
    public static boolean isLeadAgent(Collection<String> roles) {
        return has(roles, LEAD_AGENT);
    }

    /**
     * Global visibility / scope bypass: ADMIN or MANAGER see and (for ADMIN) act across
     * ALL products, ignoring the product-scoping that applies to AGENT/LEAD_AGENT.
     */
    public static boolean isGlobal(Collection<String> roles) {
        return has(roles, ADMIN) || has(roles, MANAGER);
    }

    /**
     * Operational staff that work tickets under product scope: AGENT or LEAD_AGENT
     * (LEAD_AGENT carries AGENT via the composite role).
     */
    public static boolean isAgentLevel(Collection<String> roles) {
        return has(roles, AGENT) || has(roles, LEAD_AGENT);
    }

    /**
     * May mutate a ticket WITHOUT holding a claim: LEAD_AGENT (within its products) and
     * ADMIN (global). A plain AGENT must hold the claim.
     */
    public static boolean canActWithoutClaim(Collection<String> roles) {
        return has(roles, LEAD_AGENT) || has(roles, ADMIN);
    }
}
