package com.ticketsystem.generator.model;

/**
 * A seed user defined in {@code users.json}. The generator creates the account via the
 * Agent Admin API and then signs in with {@link #password()}.
 *
 * @param username  Keycloak username (unique)
 * @param email     email address (unique)
 * @param firstName given name
 * @param lastName  family name
 * @param password  final password the user ends up with after the forced first-login change
 */
public record SeedUser(String username, String email, String firstName, String lastName, String password) {
}
