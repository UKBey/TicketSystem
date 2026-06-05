package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.User;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserDTOTest {

    private User.UserBuilder baseUser() {
        return User.builder()
                .id("kc-1").email("a@b.com").fullName("Ali Yılmaz")
                .role("AGENT").isActive(true)
                .preferredLanguage("tr").preferredTheme("dark");
    }

    @Test
    void fromEntity_mapsScalarFields() {
        UserDTO dto = UserDTO.fromEntity(baseUser().roles(Set.of("AGENT")).build());

        assertThat(dto.getId()).isEqualTo("kc-1");
        assertThat(dto.getEmail()).isEqualTo("a@b.com");
        assertThat(dto.getFullName()).isEqualTo("Ali Yılmaz");
        assertThat(dto.getRole()).isEqualTo("AGENT");
        assertThat(dto.getIsActive()).isTrue();
        assertThat(dto.getPreferredLanguage()).isEqualTo("tr");
        assertThat(dto.getPreferredTheme()).isEqualTo("dark");
        // default authorizedProducts is an empty (non-null) list → maps to empty list
        assertThat(dto.getAuthorizedProducts()).isEmpty();
    }

    @Test
    void fromEntity_ordersRolesByDisplayPriority() {
        Set<String> roles = new LinkedHashSet<>(Set.of("AGENT", "ADMIN", "MANAGER"));
        UserDTO dto = UserDTO.fromEntity(baseUser().roles(roles).build());

        // ADMIN > MANAGER > LEAD_AGENT > AGENT > CUSTOMER
        assertThat(dto.getRoles()).containsExactly("ADMIN", "MANAGER", "AGENT");
    }

    @Test
    void fromEntity_leadAgentSuppressesPlainAgent() {
        Set<String> roles = new LinkedHashSet<>(Set.of("AGENT", "LEAD_AGENT"));
        UserDTO dto = UserDTO.fromEntity(baseUser().roles(roles).build());

        assertThat(dto.getRoles()).containsExactly("LEAD_AGENT");
        assertThat(dto.getRoles()).doesNotContain("AGENT");
    }

    @Test
    void fromEntity_unknownRolesAppendedAlphabeticallyAfterKnown() {
        Set<String> roles = new LinkedHashSet<>(Set.of("CUSTOMER", "ZETA", "ALPHA"));
        UserDTO dto = UserDTO.fromEntity(baseUser().roles(roles).build());

        assertThat(dto.getRoles()).containsExactly("CUSTOMER", "ALPHA", "ZETA");
    }

    @Test
    void fromEntity_emptyRoles_yieldEmptyList() {
        UserDTO dto = UserDTO.fromEntity(baseUser().roles(Set.of()).build());

        assertThat(dto.getRoles()).isEmpty();
    }

    @Test
    void fromEntity_nullAuthorizedProducts_yieldsNull() {
        UserDTO dto = UserDTO.fromEntity(baseUser().roles(Set.of("AGENT")).authorizedProducts(null).build());

        assertThat(dto.getAuthorizedProducts()).isNull();
    }
}
