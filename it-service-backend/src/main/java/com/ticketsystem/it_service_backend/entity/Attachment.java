package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;


/**
 * An attachment uploaded to a {@link Ticket} — the content is stored in the DB as
 * BYTEA (rather than on the filesystem or S3).
 *
 * <p>Either the customer or an agent may upload one; {@code uploaderId} is the
 * corresponding user's Keycloak UUID. Bound to the ticket via cascade — if the ticket
 * is deleted, its attachments are removed too.
 */
@Entity
@Table(name = "ticket_attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    @JsonIgnore
    private Ticket ticket;

    @Column(name = "uploader_id", nullable = false, length = 36)
    private String uploaderId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_type", nullable = false)
    private String fileType;

    /** Raw file content — stored in the DB as PostgreSQL BYTEA. */
    @Column(name = "content", nullable = false, columnDefinition = "BYTEA")
    private byte[] content;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
