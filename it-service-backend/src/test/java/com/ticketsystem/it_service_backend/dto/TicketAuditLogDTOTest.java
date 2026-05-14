package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.TicketAuditLog;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

class TicketAuditLogDTOTest {

    @Test
    void fromEntity_singleArg_mapsFields() {
        ZonedDateTime now = ZonedDateTime.now();
        TicketAuditLog entity = TicketAuditLog.builder()
                .id(7L).actorId("agent-1").actionType("CLAIM")
                .reasonCode("SOLUTION_PROVIDED").note("done")
                .previousState("NEW").newState("IN_PROGRESS").createdAt(now)
                .build();

        TicketAuditLogDTO dto = TicketAuditLogDTO.fromEntity(entity);

        assertThat(dto.getId()).isEqualTo(7L);
        assertThat(dto.getActorId()).isEqualTo("agent-1");
        assertThat(dto.getActorName()).isNull();
        assertThat(dto.getActionType()).isEqualTo("CLAIM");
        assertThat(dto.getReasonCode()).isEqualTo("SOLUTION_PROVIDED");
        assertThat(dto.getNote()).isEqualTo("done");
        assertThat(dto.getPreviousState()).isEqualTo("NEW");
        assertThat(dto.getNewState()).isEqualTo("IN_PROGRESS");
        assertThat(dto.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void fromEntity_singleArg_nullReturnsNull() {
        assertNull(TicketAuditLogDTO.fromEntity(null));
    }

    @Test
    void fromEntity_withActorName_setsActorName() {
        TicketAuditLog entity = TicketAuditLog.builder()
                .id(8L).actorId("agent-1").actionType("UNCLAIM")
                .reasonCode("WORKLOAD").note(null)
                .previousState("IN_PROGRESS").newState("NEW")
                .createdAt(ZonedDateTime.now()).build();

        TicketAuditLogDTO dto = TicketAuditLogDTO.fromEntity(entity, "Bob Agent");

        assertThat(dto.getActorName()).isEqualTo("Bob Agent");
        assertThat(dto.getReasonCode()).isEqualTo("WORKLOAD");
        assertThat(dto.getNote()).isNull();
    }

    @Test
    void fromEntity_withActorName_nullEntityReturnsNull() {
        assertNull(TicketAuditLogDTO.fromEntity(null, "anyone"));
    }
}
