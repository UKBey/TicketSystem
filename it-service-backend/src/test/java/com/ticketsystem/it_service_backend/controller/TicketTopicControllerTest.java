package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.TicketTopicDTO;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.service.TicketTopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketTopicControllerTest {

    @Mock private TicketTopicService topicService;
    private TicketTopicController controller;

    @BeforeEach
    void setUp() {
        controller = new TicketTopicController(topicService);
    }

    @Test
    void listByProduct_defaultActiveOnly() {
        TicketTopic t = TicketTopic.builder().id(1L).productId(10L).name("Şifre").isActive(true).build();
        when(topicService.listByProduct(10L, true)).thenReturn(List.of(t));

        ResponseEntity<List<TicketTopicDTO>> res = controller.listByProduct(10L, false);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).hasSize(1);
        assertThat(res.getBody().get(0).getName()).isEqualTo("Şifre");
    }

    @Test
    void listByProduct_includeInactive_passesFalseFlagToService() {
        when(topicService.listByProduct(10L, false)).thenReturn(List.of());

        ResponseEntity<List<TicketTopicDTO>> res = controller.listByProduct(10L, true);

        assertThat(res.getBody()).isEmpty();
    }

    @Test
    void create_returnsDtoFromService() {
        TicketTopicDTO body = TicketTopicDTO.builder().name("Yeni").isActive(true).build();
        TicketTopic created = TicketTopic.builder().id(42L).productId(10L).name("Yeni").isActive(true).build();
        when(topicService.create(10L, "Yeni", true)).thenReturn(created);

        ResponseEntity<TicketTopicDTO> res = controller.create(10L, body);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().getId()).isEqualTo(42L);
        assertThat(res.getBody().getProductId()).isEqualTo(10L);
    }

    @Test
    void update_passesArgsAndReturnsDto() {
        TicketTopicDTO body = TicketTopicDTO.builder().name("Düzenli").isActive(false).build();
        TicketTopic updated = TicketTopic.builder().id(5L).productId(10L).name("Düzenli").isActive(false).build();
        when(topicService.update(5L, "Düzenli", false)).thenReturn(updated);

        ResponseEntity<TicketTopicDTO> res = controller.update(5L, body);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().getIsActive()).isFalse();
    }

    @Test
    void delete_returnsNoContent() {
        ResponseEntity<Void> res = controller.delete(5L);

        assertThat(res.getStatusCode().value()).isEqualTo(204);
        verify(topicService).delete(5L);
    }
}
