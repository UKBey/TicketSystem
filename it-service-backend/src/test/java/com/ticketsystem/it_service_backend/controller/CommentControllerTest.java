package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.CommentDTO;
import com.ticketsystem.it_service_backend.dto.CommentRequestDTO;
import com.ticketsystem.it_service_backend.entity.Comment;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    @Mock
    private CommentService commentService;
    @Mock
    private UserRepository userRepository;

    private CommentController commentController;

    @BeforeEach
    void setUp() {
        commentController = new CommentController(commentService, userRepository);
    }

    @Test
    void addComment_returnsDtoWithAuthorName() {
        Comment saved = Comment.builder()
                .id(1L)
                .authorId("customer-1")
                .message("Need help")
                .type("EXTERNAL")
                .createdAt(ZonedDateTime.now())
                .build();

        when(commentService.addComment(100L, "Need help", "EXTERNAL", "customer-1", List.of("CUSTOMER"))).thenReturn(saved);
        when(userRepository.findById("customer-1")).thenReturn(Optional.of(
                User.builder().id("customer-1").fullName("Customer One").build()));

        ResponseEntity<CommentDTO> response = commentController.addComment(
                100L,
                CommentRequestDTO.builder().message("Need help").type("EXTERNAL").build(),
                jwtWithRoles("customer-1", List.of("CUSTOMER"))
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("Customer One", response.getBody().getAuthorName());
    }

    @Test
    void getComments_returnsMappedDtoList() {
        Comment c1 = Comment.builder().id(1L).authorId("u1").message("msg1").type("EXTERNAL").createdAt(ZonedDateTime.now()).build();
        Comment c2 = Comment.builder().id(2L).authorId("u2").message("msg2").type("INTERNAL").createdAt(ZonedDateTime.now()).build();

        when(commentService.getCommentsByTicketId(100L, "agent-1", List.of("AGENT"))).thenReturn(List.of(c1, c2));
        when(userRepository.findById("u1")).thenReturn(Optional.of(User.builder().id("u1").fullName("User One").build()));
        when(userRepository.findById("u2")).thenReturn(Optional.of(User.builder().id("u2").fullName("User Two").build()));

        ResponseEntity<List<CommentDTO>> response = commentController.getComments(
                100L,
                jwtWithRoles("agent-1", List.of("AGENT"))
        );

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("User One", response.getBody().get(0).getAuthorName());
        assertEquals("User Two", response.getBody().get(1).getAuthorName());
    }

    private Jwt jwtWithRoles(String subject, List<String> roles) {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", roles));
        return jwt;
    }
}
