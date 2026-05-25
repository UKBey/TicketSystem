package com.ticketsystem.it_service_backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;


/**
 * Bir {@link Ticket}'a yüklenen ek dosya — içerik DB'de BYTEA olarak saklanır
 * (dosya sistemi / S3 yerine).
 *
 * <p>Müşteri veya agent yükleyebilir; {@code uploaderId} ilgili kullanıcının Keycloak
 * UUID'sidir. Bilete cascade ile bağlıdır — bilet silinirse ekler de düşer.
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

    /** Ham dosya içeriği — PostgreSQL BYTEA olarak DB'de tutulur. */
    @Column(name = "content", nullable = false, columnDefinition = "BYTEA")
    private byte[] content;

    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
    }
}
