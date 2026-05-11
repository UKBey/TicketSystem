package com.ticketsystem.it_service_backend.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TicketFilterDTOTest {

    @Nested
    @DisplayName("setStatus() uyumluluk setter'ı")
    class SetStatus {

        @Test
        @DisplayName("Geçerli değer → tek elemanlı statuses listesi")
        void validValue_setsSingleElementList() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setStatus("IN_PROGRESS");

            assertThat(dto.getStatuses()).containsExactly("IN_PROGRESS");
        }

        @Test
        @DisplayName("null değer → statuses null kalır")
        void nullValue_setsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setStatus(null);

            assertThat(dto.getStatuses()).isNull();
        }

        @Test
        @DisplayName("Boş string → statuses null kalır")
        void blankValue_setsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setStatus("   ");

            assertThat(dto.getStatuses()).isNull();
        }
    }

    @Nested
    @DisplayName("setPriority() uyumluluk setter'ı")
    class SetPriority {

        @Test
        @DisplayName("Geçerli değer → tek elemanlı priorities listesi")
        void validValue_setsSingleElementList() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setPriority("HIGH");

            assertThat(dto.getPriorities()).containsExactly("HIGH");
        }

        @Test
        @DisplayName("null değer → priorities null kalır")
        void nullValue_setsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setPriority(null);

            assertThat(dto.getPriorities()).isNull();
        }

        @Test
        @DisplayName("Boş string → priorities null kalır")
        void blankValue_setsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setPriority("");

            assertThat(dto.getPriorities()).isNull();
        }
    }

    @Nested
    @DisplayName("setProductId() uyumluluk setter'ı")
    class SetProductId {

        @Test
        @DisplayName("Geçerli ID → tek elemanlı productIds listesi")
        void validValue_setsSingleElementList() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setProductId(42L);

            assertThat(dto.getProductIds()).containsExactly(42L);
        }

        @Test
        @DisplayName("null → productIds null kalır")
        void nullValue_setsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setProductId(null);

            assertThat(dto.getProductIds()).isNull();
        }
    }

    @Nested
    @DisplayName("setSlaStatus() uyumluluk setter'ı")
    class SetSlaStatus {

        @Test
        @DisplayName("Geçerli değer → tek elemanlı slaStatuses listesi")
        void validValue_setsSingleElementList() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setSlaStatus("BREACHED");

            assertThat(dto.getSlaStatuses()).containsExactly("BREACHED");
        }

        @Test
        @DisplayName("null değer → slaStatuses null kalır")
        void nullValue_setsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setSlaStatus(null);

            assertThat(dto.getSlaStatuses()).isNull();
        }

        @Test
        @DisplayName("Boş string → slaStatuses null kalır")
        void blankValue_setsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();
            dto.setSlaStatus("  ");

            assertThat(dto.getSlaStatuses()).isNull();
        }
    }

    @Nested
    @DisplayName("getStatuses() null-safe getter")
    class GetStatuses {

        @Test
        @DisplayName("Dolu liste → listeyi döner")
        void withValues_returnsList() {
            TicketFilterDTO dto = TicketFilterDTO.builder()
                    .statuses(List.of("NEW", "IN_PROGRESS")).build();

            assertThat(dto.getStatuses()).containsExactly("NEW", "IN_PROGRESS");
        }

        @Test
        @DisplayName("null liste → null döner")
        void nullList_returnsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();

            assertThat(dto.getStatuses()).isNull();
        }

        @Test
        @DisplayName("Boş liste → null döner")
        void emptyList_returnsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().statuses(List.of()).build();

            assertThat(dto.getStatuses()).isNull();
        }
    }

    @Nested
    @DisplayName("getPriorities() null-safe getter")
    class GetPriorities {

        @Test
        @DisplayName("Dolu liste → listeyi döner")
        void withValues_returnsList() {
            TicketFilterDTO dto = TicketFilterDTO.builder()
                    .priorities(List.of("CRITICAL", "HIGH")).build();

            assertThat(dto.getPriorities()).containsExactly("CRITICAL", "HIGH");
        }

        @Test
        @DisplayName("null liste → null döner")
        void nullList_returnsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();

            assertThat(dto.getPriorities()).isNull();
        }
    }

    @Nested
    @DisplayName("getProductIds() null-safe getter")
    class GetProductIds {

        @Test
        @DisplayName("Dolu liste → listeyi döner")
        void withValues_returnsList() {
            TicketFilterDTO dto = TicketFilterDTO.builder()
                    .productIds(List.of(1L, 2L)).build();

            assertThat(dto.getProductIds()).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("null liste → null döner")
        void nullList_returnsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();

            assertThat(dto.getProductIds()).isNull();
        }
    }

    @Nested
    @DisplayName("getSlaStatuses() null-safe getter")
    class GetSlaStatuses {

        @Test
        @DisplayName("Dolu liste → listeyi döner")
        void withValues_returnsList() {
            TicketFilterDTO dto = TicketFilterDTO.builder()
                    .slaStatuses(List.of("BREACHED", "ACTIVE")).build();

            assertThat(dto.getSlaStatuses()).containsExactly("BREACHED", "ACTIVE");
        }

        @Test
        @DisplayName("null liste → null döner")
        void nullList_returnsNull() {
            TicketFilterDTO dto = TicketFilterDTO.builder().build();

            assertThat(dto.getSlaStatuses()).isNull();
        }
    }
}
