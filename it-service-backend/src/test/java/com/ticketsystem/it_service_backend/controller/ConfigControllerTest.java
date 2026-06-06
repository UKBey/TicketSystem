package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.service.CommentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {

    @Mock private CommentService commentService;

    @Test
    void commentConfig_returnsCooldownAndMaxLengthFromService() {
        when(commentService.getCooldownSeconds()).thenReturn(3L);
        when(commentService.getMaxMessageLength()).thenReturn(500);

        ResponseEntity<Map<String, Object>> res = new ConfigController(commentService).commentConfig();

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsEntry("cooldownSeconds", 3L);
        assertThat(res.getBody()).containsEntry("maxLength", 500);
    }
}
