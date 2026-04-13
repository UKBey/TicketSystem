package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findByTicketId(Long ticketId);
    void deleteByTicketId(Long ticketId);}
