package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.AttachmentDTO;
import com.ticketsystem.it_service_backend.entity.Attachment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.service.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

    @Mock
    private AttachmentService attachmentService;

    private AttachmentController attachmentController;

    @BeforeEach
    void setUp() {
        attachmentController = new AttachmentController(attachmentService);
    }

    @Test
    void uploadAttachment_returnsDto() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "a.log", "text/plain", "ERROR x".getBytes());
        Attachment saved = Attachment.builder().id(1L).ticket(Ticket.builder().id(10L).build()).uploaderId("u1").fileName("a.log").fileType("text/plain").content("x".getBytes()).build();
        when(attachmentService.uploadAttachment(10L, "u1", List.of("AGENT"), file)).thenReturn(saved);

        ResponseEntity<AttachmentDTO> response = attachmentController.uploadAttachment(10L, file, jwtWithRoles("u1", List.of("AGENT")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1L, response.getBody().getId());
    }

    @Test
    void getAttachments_returnsList() {
        Attachment a = Attachment.builder().id(2L).ticket(Ticket.builder().id(10L).build()).uploaderId("u1").fileName("b.log").fileType("text/plain").content("x".getBytes()).build();
        when(attachmentService.getTicketAttachments(10L, "u1", List.of("AGENT"))).thenReturn(List.of(a));

        ResponseEntity<List<AttachmentDTO>> response = attachmentController.getAttachments(10L, jwtWithRoles("u1", List.of("AGENT")));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void downloadAttachment_returnsBytesAndHeaders() {
        Attachment a = Attachment.builder().id(3L).fileName("c.pdf").fileType("application/pdf").content(new byte[]{1,2,3}).build();
        when(attachmentService.getAttachment(3L, "u1", List.of("AGENT"))).thenReturn(a);

        ResponseEntity<byte[]> response = attachmentController.downloadAttachment(3L, jwtWithRoles("u1", List.of("AGENT")));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().length);
    }

    @Test
    void deleteAttachment_callsServiceAndReturnsNoContent() {
        ResponseEntity<Void> response = attachmentController.deleteAttachment(4L, jwtWithRoles("u1", List.of("AGENT")));

        assertEquals(204, response.getStatusCode().value());
        verify(attachmentService).deleteAttachment(4L, "u1", List.of("AGENT"));
    }

    private Jwt jwtWithRoles(String subject, List<String> roles) {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", roles));
        return jwt;
    }
}
