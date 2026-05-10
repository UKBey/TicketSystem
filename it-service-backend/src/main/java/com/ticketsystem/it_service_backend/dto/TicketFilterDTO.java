package com.ticketsystem.it_service_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Tüm ticket listeleme endpoint'lerinde kullanılan filtre parametrelerini taşır.
 * Null değerler "filtre uygulanmadı" anlamına gelir.
 */
@Data
@Builder
public class TicketFilterDTO {

    /** Başlıkta aranacak metin (case-insensitive, LIKE %search%) */
    private String search;

    /**
     * Bilet durumu — çoklu seçim destekler (NEW, IN_PROGRESS, vb.).
     * Boş/null liste = filtre uygulanmaz.
     */
    private List<String> statuses;

    /**
     * Öncelik seviyesi — çoklu seçim destekler (CRITICAL, HIGH, MEDIUM, LOW).
     * Boş/null liste = filtre uygulanmaz.
     */
    private List<String> priorities;

    /**
     * Ürün ID filtresi — çoklu seçim destekler.
     * Boş/null liste = filtre uygulanmaz.
     */
    private List<Long> productIds;

    /** Atanan agent'ın Keycloak ID'si */
    private String agentId;

    /**
     * SLA durumu — çoklu seçim destekler (BREACHED, ACTIVE, PAUSED).
     * Boş/null liste = filtre uygulanmaz.
     */
    private List<String> slaStatuses;

    /** Oluşturulma tarihi başlangıcı (dahil) */
    private ZonedDateTime createdAtFrom;

    /** Oluşturulma tarihi bitişi (dahil) */
    private ZonedDateTime createdAtTo;

    // -------------------------------------------------------------------------
    // Geriye dönük uyumluluk — tek değer setter'ları
    // -------------------------------------------------------------------------

    /** Tek status değeri set eder (geriye dönük uyumluluk). */
    public void setStatus(String status) {
        this.statuses = (status != null && !status.isBlank())
                ? java.util.List.of(status) : null;
    }

    /** Tek priority değeri set eder (geriye dönük uyumluluk). */
    public void setPriority(String priority) {
        this.priorities = (priority != null && !priority.isBlank())
                ? java.util.List.of(priority) : null;
    }

    /** Tek productId değeri set eder (geriye dönük uyumluluk). */
    public void setProductId(Long productId) {
        this.productIds = (productId != null) ? java.util.List.of(productId) : null;
    }

    /** Tek slaStatus değeri set eder (geriye dönük uyumluluk). */
    public void setSlaStatus(String slaStatus) {
        this.slaStatuses = (slaStatus != null && !slaStatus.isBlank())
                ? java.util.List.of(slaStatus) : null;
    }

    /** Aktif status listesini null-safe döner. */
    public List<String> getStatuses() {
        return (statuses != null && !statuses.isEmpty()) ? statuses : null;
    }

    /** Aktif priority listesini null-safe döner. */
    public List<String> getPriorities() {
        return (priorities != null && !priorities.isEmpty()) ? priorities : null;
    }

    /** Aktif productId listesini null-safe döner. */
    public List<Long> getProductIds() {
        return (productIds != null && !productIds.isEmpty()) ? productIds : null;
    }

    /** Aktif slaStatus listesini null-safe döner. */
    public List<String> getSlaStatuses() {
        return (slaStatuses != null && !slaStatuses.isEmpty()) ? slaStatuses : null;
    }
}
