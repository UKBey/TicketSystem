package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Attachment;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.repository.AttachmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private AttachmentService attachmentService;

    private Ticket ticket;

    @BeforeEach
    void setUp() {
        ticket = Ticket.builder().id(10L).customerId("customer-1").status("IN_PROGRESS").build();
    }

    @Test
    void uploadAttachment_validFile_savesAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "error.log", "text/plain", "ERROR happened".getBytes());
        when(ticketService.validateMutationAccess(10L, "agent-1", List.of("AGENT"))).thenReturn(ticket);
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment a = invocation.getArgument(0);
            a.setId(1L);
            return a;
        });

        Attachment saved = attachmentService.uploadAttachment(10L, "agent-1", List.of("AGENT"), file);

        assertEquals(1L, saved.getId());
        assertEquals("error.log", saved.getFileName());
    }

    @Test
    void uploadAttachment_txtWithoutRequiredKeywords_throwsIllegalArgument() {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "all good".getBytes());
        when(ticketService.validateMutationAccess(10L, "customer-1", List.of("CUSTOMER"))).thenReturn(ticket);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.uploadAttachment(10L, "customer-1", List.of("CUSTOMER"), file));

        assertEquals("error.attachment.txt.missing.keywords", ex.getMessage());
    }

    @Test
    void uploadAttachment_sensitiveInfoInTextFile_throwsIllegalArgument() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "debug.log",
        "text/plain",
        "password=SuperSecret123\nERROR something happened".getBytes());
    when(ticketService.validateMutationAccess(10L, "agent-1", List.of("AGENT"))).thenReturn(ticket);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> attachmentService.uploadAttachment(10L, "agent-1", List.of("AGENT"), file));

    assertEquals("error.attachment.sensitive.data", ex.getMessage());
    }

    @Test
    void uploadAttachment_overSizeLimit_throwsIllegalArgument() {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "large.log",
        "text/plain",
        new byte[(int) (10 * 1024 * 1024L + 1)]);
    when(ticketService.validateMutationAccess(10L, "agent-1", List.of("AGENT"))).thenReturn(ticket);

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
        () -> attachmentService.uploadAttachment(10L, "agent-1", List.of("AGENT"), file));

    assertEquals("error.attachment.size.exceeded", ex.getMessage());
    }

    @Test
    void deleteAttachment_nonOwnerNonManager_throwsForbidden() {
        Attachment attachment = Attachment.builder().id(5L).uploaderId("owner-1").fileName("a.log").fileType("text/plain").content("x".getBytes()).build();
        when(attachmentRepository.findById(5L)).thenReturn(Optional.of(attachment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> attachmentService.deleteAttachment(5L, "other-user", List.of("AGENT")));

        assertEquals(403, ex.getStatusCode().value());
        verify(attachmentRepository, never()).delete(any());
    }

    @Test
    void deleteAttachment_managerCanDelete() {
        Attachment attachment = Attachment.builder().id(6L).uploaderId("owner-1").fileName("a.log").fileType("text/plain").content("x".getBytes()).build();
        when(attachmentRepository.findById(6L)).thenReturn(Optional.of(attachment));

        // Only AGENT_ADMIN can delete arbitrary attachments now
        attachmentService.deleteAttachment(6L, "admin-1", List.of("AGENT_ADMIN"));

        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void deleteAttachment_owner_canDelete() {
        Attachment attachment = Attachment.builder().id(7L).uploaderId("uploader").fileName("a.log").fileType("text/plain").content("x".getBytes()).build();
        when(attachmentRepository.findById(7L)).thenReturn(Optional.of(attachment));

        attachmentService.deleteAttachment(7L, "uploader", List.of("AGENT"));

        verify(attachmentRepository).delete(attachment);
    }

    @Test
    void getAttachment_missing_throws() {
        when(attachmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.getAttachment(99L));
    }

    @Test
    void getTicketAttachments_returnsList() {
        when(attachmentRepository.findByTicketId(10L)).thenReturn(List.of(
                Attachment.builder().id(1L).fileName("a.log").build()
        ));

        List<Attachment> result = attachmentService.getTicketAttachments(10L);

        assertEquals(1, result.size());
    }

    @Test
    void uploadAttachment_unsupportedExtension_throws() {
        MockMultipartFile file = new MockMultipartFile("file", "evil.exe", "application/x-msdownload", "x".getBytes());
        when(ticketService.validateMutationAccess(10L, "agent-1", List.of("AGENT"))).thenReturn(ticket);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.uploadAttachment(10L, "agent-1", List.of("AGENT"), file));
        assertEquals("error.attachment.unsupported.type", ex.getMessage());
    }

    @Test
    void uploadAttachment_fileWithNoExtension_throwsUnsupported() {
        MockMultipartFile file = new MockMultipartFile("file", "noext", "application/octet-stream", "x".getBytes());
        when(ticketService.validateMutationAccess(10L, "agent-1", List.of("AGENT"))).thenReturn(ticket);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.uploadAttachment(10L, "agent-1", List.of("AGENT"), file));
        assertEquals("error.attachment.unsupported.type", ex.getMessage());
    }

    @Test
    void uploadAttachment_pdfBinaryFile_savesWithoutTextChecks() throws Exception {
        // PDF is binary — not text-based, so no keyword/sensitivity checks fire
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "binary-pdf-data".getBytes());
        when(ticketService.validateMutationAccess(10L, "agent-1", List.of("AGENT"))).thenReturn(ticket);
        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(i -> {
            Attachment a = i.getArgument(0); a.setId(99L); return a;
        });

        Attachment saved = attachmentService.uploadAttachment(10L, "agent-1", List.of("AGENT"), file);

        assertEquals(99L, saved.getId());
    }

    @Test
    void uploadAttachment_textWithBearerToken_throwsSensitive() {
        MockMultipartFile file = new MockMultipartFile("file", "trace.log", "text/plain",
                ("ERROR auth failed; Bearer abcdefghijklmnop12345").getBytes());
        when(ticketService.validateMutationAccess(10L, "agent-1", List.of("AGENT"))).thenReturn(ticket);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.uploadAttachment(10L, "agent-1", List.of("AGENT"), file));
        assertEquals("error.attachment.sensitive.data", ex.getMessage());
    }

    @Test
    void uploadAttachment_textWithPrivateKeyBlock_throwsSensitive() {
        String content = "ERROR\n-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----";
        MockMultipartFile file = new MockMultipartFile("file", "leak.log", "text/plain", content.getBytes());
        when(ticketService.validateMutationAccess(10L, "agent-1", List.of("AGENT"))).thenReturn(ticket);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attachmentService.uploadAttachment(10L, "agent-1", List.of("AGENT"), file));
        assertEquals("error.attachment.sensitive.data", ex.getMessage());
    }
}
