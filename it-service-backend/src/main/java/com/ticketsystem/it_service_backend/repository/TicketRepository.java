package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Müşterinin kendi biletlerini bulmak için
    List<Ticket> findByCustomerId(String customerId);

    // Ajanın kendi üzerine aldığı biletleri bulmak için
    List<Ticket> findByAssigneeId(String assigneeId);

    // Havuzdaki (atanmamış ve "NEW" statüsündeki) biletleri bulmak için
    List<Ticket> findByStatus(String status);
    
    // Ajana tanımlanmış ürünlerin biletlerini havuza getirmek için
    List<Ticket> findByStatusAndProductIdIn(String status, List<Long> productIds);

    // Ajanın kendi ürünlerine ait tüm biletlerini (statüsü farketmeksizin) listelemek için
    List<Ticket> findByProductIdIn(List<Long> productIds);

    // Hem kendi biletlerini hem de yetkili olduğu ürün biletlerini bir arada getirmek için (AGENT + CUSTOMER senaryosu)
    List<Ticket> findByCustomerIdOrProductIdIn(String customerId, List<Long> productIds);
}