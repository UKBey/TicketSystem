package com.ticketsystem.generator.model;

import com.ticketsystem.generator.client.KeycloakTokenClient;

import java.io.IOException;

/**
 * Bir kullanıcının oturum bilgilerini tutar.
 */
public class UserSession {

    private final String username;
    private final String role;
    private final KeycloakTokenClient tokenClient;
    private String userId;

    public UserSession(String username, String role, KeycloakTokenClient tokenClient) {
        this.username    = username;
        this.role        = role;
        this.tokenClient = tokenClient;
    }

    public String getToken() throws IOException {
        return tokenClient.getToken();
    }

    public String getUsername() { return username; }
    public String getRole()     { return role; }
    public String getUserId()   { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @Override
    public String toString() {
        return username + " [" + role + "]";
    }
}
