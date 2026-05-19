package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Ticket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import java.time.ZonedDateTime;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    // Musterinin olusturdugu biletleri listeler.
    List<Ticket> findByCustomerId(String customerId);

    // Havuzdaki (NEW) ve henuz sahiplenilmemis kayitlari getirir.
    List<Ticket> findByStatus(String status);

    // Agentin yetkili oldugu urunlere ait NEW biletleri getirir.
    List<Ticket> findByStatusAndProductIdIn(String status, List<Long> productIds);

    // Belirtilen urun listesine ait tum biletleri statuden bagimsiz dondurur.
    List<Ticket> findByProductIdIn(List<Long> productIds);

    // Tek bir urune ait tum biletleri dondurur (urun detay sayfasi icin).
    List<Ticket> findByProductId(Long productId);

    // Musteri + urun kombinasyonuna ait biletleri dondurur.
    List<Ticket> findByCustomerIdAndProductId(String customerId, Long productId);

    // Karma rolde kullanicinin hem sahip oldugu hem yetkili oldugu urun biletlerini birlestirir.
    List<Ticket> findByCustomerIdOrProductIdIn(String customerId, List<Long> productIds);

    // Agentin yetkili oldugu urunlerde NEW olmayan ve CLOSED olmayan aktif biletleri dondurur.
    @Query("SELECT t FROM Ticket t WHERE t.productId IN :productIds AND t.status NOT IN ('NEW', 'CLOSED')")
    List<Ticket> findActiveByProductIdIn(@Param("productIds") List<Long> productIds);

    // =========================================================================
    // Genel filtreli sorgular — tüm yeni filtre parametrelerini destekler
    // (searchPattern, status, priority, productId, agentId, slaStatus, dateFrom, dateTo)
    // NOT: searchPattern Java tarafında '%' + search.toLowerCase() + '%' olarak hazırlanır.
    // =========================================================================

    /**
     * Müşteri biletleri — tüm filtreler.
     * slaStatus: BREACHED | ACTIVE | PAUSED | null
     */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.customer_id = CAST(:customerId AS text)
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true, countQuery = """
        SELECT COUNT(*) FROM tickets t
        WHERE t.customer_id = CAST(:customerId AS text)
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """)
    Page<Ticket> findByCustomerIdFullFiltered(
            @Param("customerId")    String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Pool (NEW) biletleri — yetkili ürünler + tüm filtreler */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.status = 'NEW'
          AND t.product_id IN :productIds
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findPoolTicketsFullFiltered(
            @Param("productIds")    List<Long> productIds,
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Pool (NEW) biletleri — AGENT_ADMIN, tüm ürünler + tüm filtreler */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.status = 'NEW'
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findAllPoolTicketsFullFiltered(
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Ajanın claim aldığı biletler — tüm filtreler */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.id IN :ticketIds
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findClaimedTicketsFullFiltered(
            @Param("ticketIds")     List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Takım biletleri — yetkili ürünler + tüm filtreler */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.product_id IN :productIds
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findTeamTicketsFullFiltered(
            @Param("productIds")    List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Takım biletleri — AGENT_ADMIN, tüm ürünler + tüm filtreler */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (t.priority IN (:priorities))
          AND (t.product_id IN (:filterProductIds))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findAllTeamTicketsFullFiltered(
            @Param("priorities") List<String> priorities,
            @Param("filterProductIds") List<Long> filterProductIds,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Ürün biletleri — agent/admin + tüm filtreler */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.product_id = CAST(:productId AS bigint)
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findByProductIdFullFiltered(
            @Param("productId")     Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    /** Ürün biletleri — müşteri + tüm filtreler */
    @Query(value = """
        SELECT * FROM tickets t
        WHERE t.product_id = CAST(:productId AS bigint)
          AND t.customer_id = CAST(:customerId AS text)
          AND (t.status IN (:statuses))
          AND (t.priority IN (:priorities))
          AND (CAST(:searchPattern AS text) IS NULL OR LOWER(t.title) LIKE CAST(:searchPattern AS text))
          AND (CAST(:dateFrom AS timestamptz) IS NULL OR t.created_at >= CAST(:dateFrom AS timestamptz))
          AND (CAST(:dateTo AS timestamptz) IS NULL OR t.created_at <= CAST(:dateTo AS timestamptz))
          AND (('BREACHED' IN (:slaStatuses) AND t.sla_breached = true OR 'ACTIVE' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NULL OR 'PAUSED' IN (:slaStatuses) AND t.sla_breached = false AND t.sla_paused_at IS NOT NULL))
          AND (:agentFilterActive = FALSE
               OR EXISTS (SELECT 1 FROM ticket_claims tc WHERE tc.ticket_id = t.id AND tc.agent_id IN (:agentIds)))
          AND (:topicFilterActive = FALSE OR t.topic_id IN (:topicIds))
        """, nativeQuery = true)
    Page<Ticket> findByProductIdAndCustomerIdFullFiltered(
            @Param("productId")     Long productId,
            @Param("customerId")    String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            @Param("searchPattern") String searchPattern,
            @Param("slaStatuses") List<String> slaStatuses,
            @Param("agentFilterActive") Boolean agentFilterActive,
            @Param("agentIds")      List<String> agentIds,
            @Param("topicFilterActive") Boolean topicFilterActive,
            @Param("topicIds")      List<Long> topicIds,
            @Param("dateFrom")      ZonedDateTime dateFrom,
            @Param("dateTo")        ZonedDateTime dateTo,
            Pageable pageable);

    // Musteri biletleri — status ve priority filtresi ile sayfalama
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findByCustomerIdFiltered(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Musteri biletleri — SLA urgency sirasi ile
    // Grup 0: expired (slaBreached=true) → slaDeadline ASC (en uzun suredir expired = en kucuk deadline = en urgent)
    // Grup 1: aktif sayac (slaBreached=false, slaPausedAt IS NULL) → slaDeadline ASC (en az suresi kalan = en urgent)
    // Grup 2: dondurulmus (slaBreached=false, slaPausedAt IS NOT NULL) → slaDeadline ASC
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findByCustomerIdFilteredOrderBySlaUrgencyAsc(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findByCustomerIdFilteredOrderBySlaUrgencyDesc(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findByCustomerIdFilteredOrderByPriorityAsc(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findByCustomerIdFilteredOrderByPriorityDesc(
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Havuz (NEW) biletleri — yetkili urunler + priority filtresi ile sayfalama
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findPoolTicketsFiltered(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findPoolTicketsFilteredOrderByPriorityAsc(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findPoolTicketsFilteredOrderByPriorityDesc(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Havuz (NEW) biletleri — SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findPoolTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND t.productId IN :productIds
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findPoolTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("productIds") List<Long> productIds,
            @Param("priorities") List<String> priorities,
            Pageable pageable);
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findAllPoolTicketsFiltered(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findAllPoolTicketsFilteredOrderByPriorityAsc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findAllPoolTicketsFilteredOrderByPriorityDesc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Ajanin claim aldigi biletler — status ve priority filtresi ile sayfalama
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findClaimedTicketsFiltered(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findClaimedTicketsFilteredOrderByPriorityAsc(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findClaimedTicketsFilteredOrderByPriorityDesc(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Takim biletleri (aktif) — yetkili urunler + priority filtresi ile sayfalama
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findTeamTicketsFiltered(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findTeamTicketsFilteredOrderByPriorityAsc(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findTeamTicketsFilteredOrderByPriorityDesc(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Takim biletleri — AGENT_ADMIN icin tum urunler
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findAllTeamTicketsFiltered(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findAllTeamTicketsFilteredOrderByPriorityAsc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findAllTeamTicketsFilteredOrderByPriorityDesc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Urun biletleri — agent/admin icin status + priority filtresi ile sayfalama
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findByProductIdFiltered(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findByProductIdFilteredOrderByPriorityAsc(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findByProductIdFilteredOrderByPriorityDesc(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Urun biletleri — musteri icin (sadece kendi biletleri)
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        """)
    Page<Ticket> findByProductIdAndCustomerIdFiltered(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END ASC
        """)
    Page<Ticket> findByProductIdAndCustomerIdFilteredOrderByPriorityAsc(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE t.priority WHEN 'CRITICAL' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END DESC
        """)
    Page<Ticket> findByProductIdAndCustomerIdFilteredOrderByPriorityDesc(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Havuz (NEW) biletleri — AGENT_ADMIN icin tum urunler, SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findAllPoolTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status = 'NEW'
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findAllPoolTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Ajanin claim aldigi biletler — SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findClaimedTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.id IN :ticketIds
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findClaimedTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("ticketIds") List<Long> ticketIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Takim biletleri — yetkili urunler, SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findTeamTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId IN :productIds
          AND t.status IN :statuses
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findTeamTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("productIds") List<Long> productIds,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Takim biletleri — AGENT_ADMIN icin tum urunler, SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findAllTeamTicketsFilteredOrderBySlaUrgencyAsc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.status NOT IN ('NEW', 'CLOSED')
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findAllTeamTicketsFilteredOrderBySlaUrgencyDesc(
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Urun biletleri — agent/admin icin SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findByProductIdFilteredOrderBySlaUrgencyAsc(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findByProductIdFilteredOrderBySlaUrgencyDesc(
            @Param("productId") Long productId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // Urun biletleri — musteri icin SLA urgency sirasi ile
    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END ASC,
          t.slaDeadline ASC NULLS LAST
        """)
    Page<Ticket> findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyAsc(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    @Query("""
        SELECT t FROM Ticket t
        WHERE t.productId = :productId
          AND t.customerId = :customerId
          AND (:statuses IS NULL OR t.status IN :statuses)
          AND (:priorities IS NULL OR t.priority IN :priorities)
        ORDER BY
          CASE WHEN t.slaBreached = true THEN 0
               WHEN t.slaPausedAt IS NULL THEN 1
               ELSE 2 END DESC,
          t.slaDeadline DESC NULLS LAST
        """)
    Page<Ticket> findByProductIdAndCustomerIdFilteredOrderBySlaUrgencyDesc(
            @Param("productId") Long productId,
            @Param("customerId") String customerId,
            @Param("statuses") List<String> statuses,
            @Param("priorities") List<String> priorities,
            Pageable pageable);

    // -------------------------------------------------------------------------

    // Tum ticket durumlarinin dagilimini doner.
    @Query("SELECT t.status, COUNT(t) FROM Ticket t GROUP BY t.status")
    List<Object[]> countTicketsGroupedByStatus();

    // Acik biletlerin toplam sayisi
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses")
    Long countByStatusIn(@Param("statuses") List<String> statuses);

    // Acik biletler arasinda SLA ihlali yapanlarin sayisi
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true")
    Long countSlaBreachedByStatusIn(@Param("statuses") List<String> statuses);

    // Son 24 saat icinde acik biletler arasinda olusturulanlarin sayisi
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND t.createdAt >= :since")
    Long countCreatedSinceByStatusIn(@Param("statuses") List<String> statuses,
                                      @Param("since") java.time.ZonedDateTime since);

    // Acik biletlerin priority dagilimi
    @Query("SELECT t.priority, COUNT(t) FROM Ticket t WHERE t.status IN :statuses GROUP BY t.priority")
    List<Object[]> countByStatusInGroupByPriority(@Param("statuses") List<String> statuses);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL", nativeQuery = true)
    Double findAvgResolutionHoursForResolved();

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.createdAt >= :since")
    long countCreatedSince(@Param("since") ZonedDateTime since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'RESOLVED' AND t.resolvedAt >= :since")
    long countResolvedSince(@Param("since") ZonedDateTime since);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = 'CLOSED' AND t.closedAt >= :since")
    long countClosedSince(@Param("since") ZonedDateTime since);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (t.resolved_at - t.created_at)) / 3600.0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since AND t.created_at IS NOT NULL AND t.resolved_at IS NOT NULL", nativeQuery = true)
    Double avgResolutionHoursSince(@Param("since") ZonedDateTime since);

    @Query(value = "SELECT (COUNT(CASE WHEN t.sla_breached = false THEN 1 END) * 100.0) / NULLIF(COUNT(t.id), 0) FROM tickets t WHERE t.status = 'RESOLVED' AND t.resolved_at >= :since", nativeQuery = true)
    Double slaComplianceRateSince(@Param("since") ZonedDateTime since);

    // Alert sorgulari
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = true ORDER BY t.slaDeadline ASC")
    List<Ticket> findBreachedOpenTickets(@Param("statuses") List<String> statuses, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.slaBreached = false AND t.slaPausedAt IS NULL AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before ORDER BY t.slaDeadline ASC")
    List<Ticket> findUpcomingBreachTickets(@Param("statuses") List<String> statuses, @Param("before") ZonedDateTime before, Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.priority IN :priorities AND t.slaBreached = false AND t.slaPausedAt IS NULL AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before ORDER BY t.slaDeadline ASC")
    List<Ticket> findUpcomingBreachTicketsByPriority(@Param("statuses") List<String> statuses, @Param("priorities") List<String> priorities, @Param("before") ZonedDateTime before, Pageable pageable);

    /**
     * SLA "yaklaşıyor" uyarı maili henüz gönderilmemiş, threshold içindeki biletler.
     * Scheduler her cycle'de bunları seçer ve mail atınca sla_warning_sent_at set eder
     * — bu sayede aynı bilete birden fazla mail gitmez.
     */
    @Query("SELECT t FROM Ticket t WHERE t.status IN :statuses AND t.priority IN :priorities "
         + "AND t.slaBreached = false AND t.slaPausedAt IS NULL "
         + "AND t.slaWarningSentAt IS NULL "
         + "AND t.slaDeadline IS NOT NULL AND t.slaDeadline <= :before "
         + "ORDER BY t.slaDeadline ASC")
    List<Ticket> findPendingWarningTicketsByPriority(@Param("statuses") List<String> statuses,
                                                     @Param("priorities") List<String> priorities,
                                                     @Param("before") ZonedDateTime before,
                                                     Pageable pageable);

    @Query("SELECT t FROM Ticket t WHERE t.status = 'WAITING_FOR_CUSTOMER' AND t.createdAt <= :since ORDER BY t.createdAt ASC")
    List<Ticket> findWaitingTooLongTickets(@Param("since") ZonedDateTime since, Pageable pageable);

    // Claim olmayan biletler = status NEW
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status IN :statuses AND NOT EXISTS (SELECT 1 FROM TicketClaim tc WHERE tc.ticket = t)")
    long countUnassignedByStatusIn(@Param("statuses") List<String> statuses);

    long countByStatus(String status);

    @Query("SELECT t FROM Ticket t WHERE t.slaBreached = false AND t.slaDeadline IS NOT NULL AND t.slaDeadline < :now AND t.status IN :statuses")
    List<Ticket> findOverdueUnmarkedTickets(@Param("now") ZonedDateTime now,
                                            @Param("statuses") List<String> statuses);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - t.created_at)) / 3600.0) FROM tickets t WHERE t.status IN (:statuses) AND t.created_at IS NOT NULL", nativeQuery = true)
    Double avgWaitingHoursForOpen(@Param("statuses") List<String> statuses);

    @Query(value = """
        WITH date_range AS (
            SELECT DATE(CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - INTERVAL '1 day' * i AS metric_date
            FROM generate_series(0, ?1 - 1) AS i
        ),
        daily_metrics AS (
            SELECT
                COALESCE(DATE(t.created_at AT TIME ZONE 'UTC'), dr.metric_date) AS metric_date,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS created_count,
                COUNT(CASE WHEN DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS resolved_count,
                COUNT(CASE WHEN DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date THEN 1 END) AS closed_count,
                COUNT(CASE WHEN DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date AND t.sla_breached = true THEN 1 END) AS sla_breach_count
            FROM date_range dr
            LEFT JOIN tickets t ON
                DATE(t.created_at AT TIME ZONE 'UTC') = dr.metric_date OR
                DATE(t.resolved_at AT TIME ZONE 'UTC') = dr.metric_date OR
                DATE(t.closed_at AT TIME ZONE 'UTC') = dr.metric_date
            GROUP BY dr.metric_date, COALESCE(DATE(t.created_at AT TIME ZONE 'UTC'), dr.metric_date)
        )
        SELECT
            metric_date,
            SUM(created_count)::BIGINT,
            SUM(resolved_count)::BIGINT,
            SUM(closed_count)::BIGINT,
            SUM(sla_breach_count)::BIGINT
        FROM daily_metrics
        GROUP BY metric_date
        ORDER BY metric_date DESC
        """, nativeQuery = true)
    List<Object[]> getTicketTimelineMetrics(int days);
}
