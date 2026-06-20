package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.exception.InvalidPasswordException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Enforces the Keycloak realm password policy on the application side.
 *
 * <p><strong>Why this exists:</strong> Keycloak's Admin REST API
 * ({@code PUT .../users/{id}/reset-password}) does <em>not</em> validate the new
 * password against the realm password policy — that check only runs in
 * user-facing flows (registration, account console, Keycloak's own reset
 * screen). The self-service change-password flow in this backend sets the
 * password through the Admin API, so a weak password would be accepted
 * silently. This validator re-enforces the policy before the Admin call,
 * mirroring the realm config in {@code keycloak-init/realm-export.json}:
 * {@code length(8) and lowerCase(1) and upperCase(1) and digits(1) and notUsername}.
 *
 * <p>Keep these limits in sync with the realm export if the policy changes.
 */
@Log4j2
@Component
public class PasswordPolicyValidator {

    @Value("${app.password-policy.min-length:8}")
    private int minLength;

    @Value("${app.password-policy.min-lowercase:1}")
    private int minLowercase;

    @Value("${app.password-policy.min-uppercase:1}")
    private int minUppercase;

    @Value("${app.password-policy.min-digits:1}")
    private int minDigits;

    @Value("${app.password-policy.not-username:true}")
    private boolean notUsername;

    /**
     * Validates a candidate password against the realm policy.
     *
     * @param password the new password
     * @param username the owner's username for the {@code notUsername} rule;
     *                 may be {@code null} (rule is skipped when unavailable)
     * @throws InvalidPasswordException if the password violates any rule
     */
    public void validate(String password, String username) {
        if (password == null || password.isBlank()) {
            throw new InvalidPasswordException("Password must not be blank");
        }
        if (password.length() < minLength) {
            throw new InvalidPasswordException("Password must be at least " + minLength + " characters");
        }

        int lower = 0;
        int upper = 0;
        int digits = 0;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLowerCase(c)) {
                lower++;
            } else if (Character.isUpperCase(c)) {
                upper++;
            } else if (Character.isDigit(c)) {
                digits++;
            }
        }

        if (lower < minLowercase) {
            throw new InvalidPasswordException("Password must contain at least " + minLowercase + " lowercase letter(s)");
        }
        if (upper < minUppercase) {
            throw new InvalidPasswordException("Password must contain at least " + minUppercase + " uppercase letter(s)");
        }
        if (digits < minDigits) {
            throw new InvalidPasswordException("Password must contain at least " + minDigits + " digit(s)");
        }
        if (notUsername && username != null && password.equalsIgnoreCase(username)) {
            throw new InvalidPasswordException("Password must not equal the username");
        }
    }
}
