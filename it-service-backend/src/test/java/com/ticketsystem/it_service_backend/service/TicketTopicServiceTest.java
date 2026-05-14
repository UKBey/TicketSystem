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
        existing = TicketTopic.builder().id(7L).productId(10L).name("Diğer").isActive(true).build();
    }

    // ---- listByProduct --------------------------------------------------------

    @Test
    void listByProduct_activeOnly_returnsActiveOrdered() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndIsActiveTrueOrderByNameAsc(10L))
                .thenReturn(List.of(existing));

        List<TicketTopic> result = service.listByProduct(10L, true);

        assertThat(result).containsExactly(existing);
        verify(topicRepository, never()).findByProductIdOrderByNameAsc(any());
    }

    @Test
    void listByProduct_includeInactive_returnsAllOrdered() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdOrderByNameAsc(10L)).thenReturn(List.of(existing));

        List<TicketTopic> result = service.listByProduct(10L, false);

        assertThat(result).containsExactly(existing);
        verify(topicRepository, never()).findByProductIdAndIsActiveTrueOrderByNameAsc(any());
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
    void create_trimsNameAndDefaultsActiveTrue_whenNullActive() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndNameIgnoreCase(10L, "Şifre")).thenReturn(Optional.empty());
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic created = service.create(10L, "  Şifre  ", null);

        ArgumentCaptor<TicketTopic> captor = ArgumentCaptor.forClass(TicketTopic.class);
        verify(topicRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Şifre");
        assertThat(captor.getValue().getIsActive()).isTrue();
        assertThat(created.getProductId()).isEqualTo(10L);
    }

    @Test
    void create_respectsExplicitActiveFalse() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndNameIgnoreCase(10L, "Pasif")).thenReturn(Optional.empty());
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic created = service.create(10L, "Pasif", false);

        assertThat(created.getIsActive()).isFalse();
    }

    @Test
    void create_unknownProduct_throwsNotFound() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(99L, "x", true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_emptyName_throwsBadRequest() {
        when(productRepository.existsById(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(10L, "   ", true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_nullName_throwsBadRequest() {
        when(productRepository.existsById(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(10L, null, true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_duplicateName_throwsConflict() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(topicRepository.findByProductIdAndNameIgnoreCase(10L, "Diğer"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(10L, "Diğer", true))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
        verify(topicRepository, never()).save(any());
    }

    // ---- update ---------------------------------------------------------------

    @Test
    void update_changesNameAndActiveFlag() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByProductIdAndNameIgnoreCase(10L, "Yeni"))
                .thenReturn(Optional.empty());
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic updated = service.update(7L, "  Yeni  ", false);

        assertThat(updated.getName()).isEqualTo("Yeni");
        assertThat(updated.getIsActive()).isFalse();
    }

    @Test
    void update_sameNameCaseInsensitive_skipsDuplicateCheck() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        service.update(7L, "diğer", null);

        verify(topicRepository, never()).findByProductIdAndNameIgnoreCase(any(), any());
    }

    @Test
    void update_duplicateNameOnOtherTopic_throwsConflict() {
        TicketTopic other = TicketTopic.builder().id(8L).productId(10L).name("Diger").build();
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByProductIdAndNameIgnoreCase(10L, "Yeni"))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(7L, "Yeni", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void update_duplicateButSameId_doesNotThrow() {
        // Edge case: lookup returns self — should not throw.
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.findByProductIdAndNameIgnoreCase(10L, "Yeni"))
                .thenReturn(Optional.of(existing));
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic updated = service.update(7L, "Yeni", null);

        assertThat(updated.getName()).isEqualTo("Yeni");
    }

    @Test
    void update_emptyName_throwsBadRequest() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(7L, "   ", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_onlyActive_keepsName() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic updated = service.update(7L, null, false);

        assertThat(updated.getName()).isEqualTo("Diğer");
        assertThat(updated.getIsActive()).isFalse();
    }

    @Test
    void update_nullNameAndNullActive_isNoOp() {
        when(topicRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(topicRepository.save(any(TicketTopic.class))).thenAnswer(i -> i.getArgument(0));

        TicketTopic updated = service.update(7L, null, null);

        assertThat(updated.getName()).isEqualTo("Diğer");
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
