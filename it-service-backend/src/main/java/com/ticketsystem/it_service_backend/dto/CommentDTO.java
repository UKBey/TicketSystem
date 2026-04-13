package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDTO {
    private Long id;
    private String authorId;
    private String authorName;
    private String message;
    private String type;
    private ZonedDateTime createdAt;

    public static CommentDTO fromEntity(Comment comment, String authorName) {
        return CommentDTO.builder()
                .id(comment.getId())
                .authorId(comment.getAuthorId())
                .authorName(authorName)
                .message(comment.getMessage())
                .type(comment.getType())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
