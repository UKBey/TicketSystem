package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.TicketClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link TicketClaim} için JPA repository — bilet ↔ agent çoka-çok bağ tablosu üzerinde
 * sahiplenme kayıtlarını sorgular, aktif yük (CLOSED hariç) sayımı ve toplu ID lookup'ları sağlar.
 */
public interface TicketClaimRepository extends JpaRepository<TicketClaim, Long> {

    List<TicketClaim> findByTicketId(Long ticketId);

    List<TicketClaim> findByAgentId(String agentId);

    Optional<TicketClaim> findByTicketIdAndAgentId(Long ticketId, String agentId);

    boolean existsByTicketIdAndAgentId(Long ticketId, String agentId);

    void deleteByTicketIdAndAgentId(Long ticketId, String agentId);

    void deleteByTicketId(Long ticketId);

    long countByTicketId(Long ticketId);

        /**
         * Bir ajanın belirli ürün altındaki aktif (CLOSED dışı) bilet sayısı.
         * {@code AgentProductLimit} kontrolünde ve claim öncesi limit doğrulamada kullanılır.
         */
        @Query("""
                        SELECT COUNT(tc)
                        FROM TicketClaim tc
                        JOIN tc.ticket t
                        WHERE tc.agentId = :agentId
                            AND t.productId = :productId
                            AND t.status <> 'CLOSED'
                        """)
        long countActiveTicketsByAgentAndProduct(@Param("agentId") String agentId,
                                                                                         @Param("productId") Long productId);

    /** Ajanın sahiplendiği tüm biletlerin ID listesini döner (ticket sayfalama sorgularında IN-list olarak). */
    @Query("SELECT tc.ticket.id FROM TicketClaim tc WHERE tc.agentId = :agentId")
    List<Long> findTicketIdsByAgentId(@Param("agentId") String agentId);

    /**
     * Birden fazla ajan için sahiplenilen bilet ID'lerini toplu döner.
     * Dönüş: her satır {@code [agent_id, ticket_id]}; N+1 sorgu kaçınmak için.
     */
    @Query("SELECT tc.agentId, tc.ticket.id FROM TicketClaim tc WHERE tc.agentId IN :agentIds")
    List<Object[]> findAgentIdAndTicketIdByAgentIdIn(@Param("agentIds") List<String> agentIds);
}
