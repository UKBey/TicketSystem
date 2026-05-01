package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Musterinin olusturdugu biletleri listeler.
    List<Ticket> findByCustomerId(String customerId);

    // Agentin uzerine atanmis biletleri listeler.
    List<Ticket> findByAssigneeId(String assigneeId);

    // Havuzdaki NEW ve henuz sahiplenilmemis kayitlari getirir.
    List<Ticket> findByStatus(String status);
    
    // Agentin yetkili oldugu urunlere ait NEW biletleri getirir.
    List<Ticket> findByStatusAndProductIdIn(String status, List<Long> productIds);

    // Belirtilen urun listesine ait tum biletleri statuden bagimsiz dondurur.
    List<Ticket> findByProductIdIn(List<Long> productIds);

    // Karma rolde kullanicinin hem sahip oldugu hem yetkili oldugu urun biletlerini birlestirir.
    List<Ticket> findByCustomerIdOrProductIdIn(String customerId, List<Long> productIds);

    // Tum ticket durumlarinin dagilimini doner.
    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countTicketsGroupedByStatus();
}
