package com.ticketsystem.it_service_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;

/**
 * Tüm ticket listeleme endpoint'lerinde kullanılan filtre parametrelerini taşır.
 * Null değerler "filtre uygulanmadı" anlamına gelir.
 */
@Data
@Builder
public class TicketFilterDTO {

    /** Başlıkta aranacak metin (case-insensitive, LIKE %search%) */
    private String search;

    /** Bilet durumu (NEW, IN_PROGRESS, vb.) */
    private String status;

    /** Öncelik seviyesi (CRITICAL, HIGH, MEDIUM, LOW) */
    private String priority;

    /** Ürün ID filtresi */
    private Long productId;

    /** Atanan agent'ın Keycloak ID'si */
    private String agentId;

    /** SLA durumu: BREACHED | ACTIVE | PAUSED */
    private String slaStatus;

    /** Oluşturulma tarihi başlangıcı (dahil) */
    private ZonedDateTime createdAtFrom;

    /** Oluşturulma tarihi bitişi (dahil) */
    private ZonedDateTime createdAtTo;
}
