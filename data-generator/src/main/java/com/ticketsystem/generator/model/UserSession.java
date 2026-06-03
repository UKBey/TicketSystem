package com.ticketsystem.generator.model;

import com.ticketsystem.generator.client.KeycloakTokenClient;

import java.io.IOException;

/**
 * Holds a user's generator session information.
 *
 * <p>Username, role and the token client are immutable; the backend user ID
 * returned by the {@code /users/sync} call is assigned later via the setter.
 * Token retrieval goes through {@link KeycloakTokenClient}, refreshing as needed.
 */
public class UserSession {

    private final String username;
    private final String role;
    private final KeycloakTokenClient tokenClient;
    private String userId;

    /**
     * @param username    Keycloak / backend username
     * @param role        role label for display/flow purposes (e.g. {@code AGENT}, {@code CUSTOMER})
     * @param tokenClient logged-in Keycloak token client bound to this user
     */
    public UserSession(String username, String role, KeycloakTokenClient tokenClient) {
        this.username    = username;
        this.role        = role;
        this.tokenClient = tokenClient;
    }

    /**
     * @return the current access token; refreshed automatically if it has expired
     * @throws IOException if token refresh/login fails
     */
    public String getToken() throws IOException {
        return tokenClient.getToken();
    }

    /** @return the Keycloak username. */
    public String getUsername() { return username; }
    /** @return the role label (e.g. {@code AGENT}, {@code CUSTOMER}, {@code ADMIN}). */
    public String getRole()     { return role; }
    /** @return the user ID assigned by the backend; {@code null} if not yet synced. */
    public String getUserId()   { return userId; }
    /** @param userId the user ID returned by the backend {@code /users/sync} response. */
    public void setUserId(String userId) { this.userId = userId; }

    @Override
    public String toString() {
        return username + " [" + role + "]";
    }
}
