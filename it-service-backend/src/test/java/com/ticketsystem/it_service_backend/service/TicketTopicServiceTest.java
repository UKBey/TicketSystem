package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketTopicServiceTest {

    @Mock private TicketTopicRepository topicRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private TicketTopicService service;

    private TicketTopic existing;

    @BeforeEach
    void setUp() {
        existing = TicketTopic.builder()
                .id(7L).productId(10L).nameTr("Diğer").nameEn("Other").isActive(true).build();
    }

    // ---- listByProduct --------------------------------------------------------

    @Test
    void listByProduct_activeOnly_returnsActiveOrdered() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndIsActiveTrueOrderByIdAsc(10L))
                .thenReturn(List.of(existing));

        List<TicketTopic> result = service.listByProduct(10L, true);

        assertThat(result).containsExactly(existing);
        verify(topicRepository, never()).findByProductIdOrderByIdAsc(any());
    }

    @Test
    void listByProduct_includeInactive_returnsAllOrdered() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdOrderByIdAsc(10L)).thenReturn(List.of(existing));

        List<TicketTopic> result = service.listByProduct(10L, false);

        assertThat(result).containsExactly(existing);
        verify(topicRepository, never()).findByProductIdAndIsActiveTrueOrderByIdAsc(any());
    }

    @Test
    void listByProduct_unknownProduct_throwsNotFound() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.listByProduct(99L, true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- getById --------------------------------------------------------------

    @Test
    void getById_existing_returnsTopic() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        assertThat(service.getById(7L)).isSameAs(existing);
    }

    @Test
    void getById_missing_throwsNotFound() {
        when(topicRepository.findById(8L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(8L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- create ---------------------------------------------------------------

    @Test
    void create_trimsNamesAndDefaultsActiveTrue_whenNullActive() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndNameTrIgnoreCase(10L, "Şifre")).thenReturn(Optional.empty());
        when(topicRepository.findByProductIdAndNameEnIgnoreCase(10L, "Password")).thenReturn(Optional.empty());
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic created = service.create(10L, "  Şifre  ", "  Password  ", null);

        ArgumentCaptor<TicketTopic> captor = ArgumentCaptor.forClass(TicketTopic.class);
        verify(topicRepository).save(captor.capture());
        assertThat(captor.getValue().getNameTr()).isEqualTo("Şifre");
        assertThat(captor.getValue().getNameEn()).isEqualTo("Password");
        assertThat(captor.getValue().getIsActive()).isTrue();
        assertThat(created.getProductId()).isEqualTo(10L);
    }

    @Test
    void create_singleLanguage_storesNullForMissingVariant() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndNameTrIgnoreCase(10L, "Kurulum")).thenReturn(Optional.empty());
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic created = service.create(10L, "Kurulum", "  ", true);

        assertThat(created.getNameTr()).isEqualTo("Kurulum");
        assertThat(created.getNameEn()).isNull();
        verify(topicRepository, never()).findByProductIdAndNameEnIgnoreCase(any(), any());
    }

    @Test
    void create_respectsExplicitActiveFalse() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndNameTrIgnoreCase(10L, "Pasif")).thenReturn(Optional.empty());
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic created = service.create(10L, "Pasif", null, false);

        assertThat(created.getIsActive()).isFalse();
    }

    @Test
    void create_unknownProduct_throwsNotFound() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(99L, "x", null, true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_bothNamesBlank_throwsBadRequest() {
        when(productRepository.existsById(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(10L, "   ", "", true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_bothNamesNull_throwsBadRequest() {
        when(productRepository.existsById(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(10L, null, null, true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_duplicateTrName_throwsConflict() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndNameTrIgnoreCase(10L, "Diğer"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(10L, "Diğer", null, true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
        verify(topicRepository, never()).save(any());
    }

    @Test
    void create_duplicateEnName_throwsConflict() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndNameEnIgnoreCase(10L, "Other"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(10L, null, "Other", true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
        verify(topicRepository, never()).save(any());
    }

    // ---- update ---------------------------------------------------------------

    @Test
    void update_changesNamesAndActiveFlag() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByProductIdAndNameTrIgnoreCase(10L, "Yeni"))
                .thenReturn(Optional.empty());
        when(topicRepository.findByProductIdAndNameEnIgnoreCase(10L, "New"))
                .thenReturn(Optional.empty());
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic updated = service.update(7L, "  Yeni  ", "  New  ", false);

        assertThat(updated.getNameTr()).isEqualTo("Yeni");
        assertThat(updated.getNameEn()).isEqualTo("New");
        assertThat(updated.getIsActive()).isFalse();
    }

    @Test
    void update_blankVariant_clearsThatLanguage() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByProductIdAndNameTrIgnoreCase(10L, "Yeni"))
                .thenReturn(Optional.empty());
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic updated = service.update(7L, "Yeni", "", null);

        assertThat(updated.getNameTr()).isEqualTo("Yeni");
        assertThat(updated.getNameEn()).isNull();
    }

    @Test
    void update_duplicateNameOnOtherTopic_throwsConflict() {
        TicketTopic other = TicketTopic.builder().id(8L).productId(10L).nameTr("Diger").build();
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByProductIdAndNameTrIgnoreCase(10L, "Yeni"))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(7L, "Yeni", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void update_duplicateButSameId_doesNotThrow() {
        // Edge case: lookup returns self — should not throw.
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByProductIdAndNameTrIgnoreCase(10L, "Yeni"))
                .thenReturn(Optional.of(existing));
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic updated = service.update(7L, "Yeni", null, null);

        assertThat(updated.getNameTr()).isEqualTo("Yeni");
    }

    @Test
    void update_bothNamesBlank_throwsBadRequest() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(7L, "   ", "", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_onlyActive_keepsNames() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic updated = service.update(7L, null, null, false);

        assertThat(updated.getNameTr()).isEqualTo("Diğer");
        assertThat(updated.getNameEn()).isEqualTo("Other");
        assertThat(updated.getIsActive()).isFalse();
        verify(topicRepository, never()).findByProductIdAndNameTrIgnoreCase(any(), any());
        verify(topicRepository, never()).findByProductIdAndNameEnIgnoreCase(any(), any());
    }

    @Test
    void update_nullNamesAndNullActive_isNoOp() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic updated = service.update(7L, null, null, null);

        assertThat(updated.getNameTr()).isEqualTo("Diğer");
        assertThat(updated.getIsActive()).isTrue();
    }

    // ---- delete ---------------------------------------------------------------

    @Test
    void delete_existing_invokesRepository() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));

        service.delete(7L);

        verify(topicRepository).delete(existing);
    }

    @Test
    void delete_missing_throwsNotFound() {
        when(topicRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }
}
