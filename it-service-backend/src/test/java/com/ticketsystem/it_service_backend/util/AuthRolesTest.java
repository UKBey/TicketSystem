package com.ticketsystem.it_service_backend.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the centralized RBAC role semantics in {@link AuthRoles}.
 */
class AuthRolesTest {

    @Test
    void isAdmin_trueOnlyForAdmin() {
        assertTrue(AuthRoles.isAdmin(List.of("ADMIN")));
        assertTrue(AuthRoles.isAdmin(List.of("AGENT", "ADMIN")));
        assertFalse(AuthRoles.isAdmin(List.of("MANAGER")));
        assertFalse(AuthRoles.isAdmin(List.of("LEAD_AGENT")));
        assertFalse(AuthRoles.isAdmin(List.of("AGENT")));
    }

    @Test
    void isManager_trueOnlyForManager() {
        assertTrue(AuthRoles.isManager(List.of("MANAGER")));
        assertTrue(AuthRoles.isManager(Set.of("LEAD_AGENT", "MANAGER")));
        assertFalse(AuthRoles.isManager(List.of("ADMIN")));
        assertFalse(AuthRoles.isManager(List.of("AGENT")));
    }

    @Test
    void isLeadAgent_trueOnlyForLead() {
        assertTrue(AuthRoles.isLeadAgent(List.of("LEAD_AGENT")));
        assertTrue(AuthRoles.isLeadAgent(List.of("AGENT", "LEAD_AGENT")));
        assertFalse(AuthRoles.isLeadAgent(List.of("AGENT")));
        assertFalse(AuthRoles.isLeadAgent(List.of("ADMIN")));
    }

    @Test
    void isGlobal_adminOrManagerBypassesScope() {
        assertTrue(AuthRoles.isGlobal(List.of("ADMIN")));
        assertTrue(AuthRoles.isGlobal(List.of("MANAGER")));
        assertTrue(AuthRoles.isGlobal(List.of("AGENT", "ADMIN")));
        assertFalse(AuthRoles.isGlobal(List.of("AGENT")));
        assertFalse(AuthRoles.isGlobal(List.of("LEAD_AGENT")));
        assertFalse(AuthRoles.isGlobal(List.of("CUSTOMER")));
    }

    @Test
    void isAgentLevel_agentOrLeadAreOperationalStaff() {
        assertTrue(AuthRoles.isAgentLevel(List.of("AGENT")));
        assertTrue(AuthRoles.isAgentLevel(List.of("LEAD_AGENT")));
        assertTrue(AuthRoles.isAgentLevel(List.of("AGENT", "LEAD_AGENT")));
        assertFalse(AuthRoles.isAgentLevel(List.of("ADMIN")));
        assertFalse(AuthRoles.isAgentLevel(List.of("MANAGER")));
        assertFalse(AuthRoles.isAgentLevel(List.of("CUSTOMER")));
    }

    @Test
    void canActWithoutClaim_leadOrAdminOnly() {
        assertTrue(AuthRoles.canActWithoutClaim(List.of("LEAD_AGENT")));
        assertTrue(AuthRoles.canActWithoutClaim(List.of("ADMIN")));
        assertFalse(AuthRoles.canActWithoutClaim(List.of("AGENT")));
        assertFalse(AuthRoles.canActWithoutClaim(List.of("MANAGER")));
        assertFalse(AuthRoles.canActWithoutClaim(List.of("CUSTOMER")));
    }

    @Test
    void nullAndEmptyRolesAreSafe() {
        assertFalse(AuthRoles.isAdmin(null));
        assertFalse(AuthRoles.isManager(null));
        assertFalse(AuthRoles.isLeadAgent(null));
        assertFalse(AuthRoles.isGlobal(null));
        assertFalse(AuthRoles.isAgentLevel(null));
        assertFalse(AuthRoles.canActWithoutClaim(null));
        assertFalse(AuthRoles.isGlobal(List.of()));
        assertFalse(AuthRoles.isAgentLevel(Set.of()));
    }

    @Test
    void customerIsNeitherStaffNorGlobal() {
        List<String> customer = List.of("CUSTOMER");
        assertFalse(AuthRoles.isAgentLevel(customer));
        assertFalse(AuthRoles.isGlobal(customer));
        assertFalse(AuthRoles.isAdmin(customer));
        assertFalse(AuthRoles.canActWithoutClaim(customer));
    }
}
