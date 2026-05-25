package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

/**
 * JPA repository for {@link Attachment} — CRUD over ticket attachments plus
 * per-ticket listing and bulk deletion.
 */
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTicketId(Long ticketId);
    void deleteByTicketId(Long ticketId);}
