package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Ticket;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bilet detay yanıtı — listeleme ve tekil sorgularda döner")
public class TicketResponseDTO {

    @Schema(description = "Biletin benzersiz kimliği", example = "42")
    private Long id;

    @Schema(description = "Biletin kısa başlığı", example = "E-posta gönderilemiyor")
    private String title;

    @Schema(description = "Sorunun detaylı açıklaması", example = "Outlook üzerinden dış adrese mail gönderilemiyor, hata: 550 Relay Denied")
    private String description;

    @Schema(description = "Biletin güncel durumu", example = "IN_PROGRESS", allowableValues = {"NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"})
    private String status;

    @Schema(description = "Öncelik seviyesi", example = "HIGH", allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    private String priority;

    @Schema(description = "Ürün/kategori ID'si", example = "1")
    private Long productId;

    @Schema(description = "Ürün/kategori adı (sunucu tarafında çözümlenir)", example = "CRM")
    private String productName;

    @Schema(description = "Bileti oluşturan müşterinin Keycloak ID'si", example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
    private String customerId;

    @Schema(description = "Müşterinin tam adı (sunucu tarafında çözümlenir)", example = "Ali Yılmaz")
    private String customerName;

    @Schema(description = "Bileti sahiplenen ajanın Keycloak ID'si (atanmamışsa null)", example = "f9e8d7c6-b5a4-3210-fedc-ba0987654321")
    private String assigneeId;

    @Schema(description = "Ajanın tam adı (sunucu tarafında çözümlenir)", example = "Mehmet Kaya")
    private String assigneeName;

    @Schema(description = "SLA son tarih/saat (jBPM tarafından hesaplanır)", example = "2026-04-22T17:00:00+03:00")
    private ZonedDateTime slaDeadline;

    @Schema(description = "SLA ihlal edildi mi?", example = "false")
    private Boolean slaBreached;

    @Schema(description = "Duraklatma öncesi birikmiş SLA süresi (milisaniye)", example = "3600000")
    private Long slaElapsedMs;

    @Schema(description = "SLA sayacının duraklatıldığı an (null ise sayaç aktif)", example = "2026-04-21T14:00:00+03:00")
    private ZonedDateTime slaPausedAt;

    @Schema(description = "Biletin oluşturulma tarihi", example = "2026-04-20T10:30:00+03:00")
    private ZonedDateTime createdAt;

    @Schema(description = "Biletin çözüldü olarak işaretlendiği tarih", example = "2026-04-21T15:45:00+03:00")
    private ZonedDateTime resolvedAt;

    @Schema(description = "Biletin kapatıldığı tarih", example = "2026-04-21T16:00:00+03:00")
    private ZonedDateTime closedAt;

    @Schema(description = "Bilete CSAT anketi doldurulmuş mu?", example = "true")
    private Boolean hasCsat;

    @Schema(description = "Gerçek zamanlı SLA bilgisi — deadlineTs (ms cinsinden Unix) veya remainingMs (duraklatıldıysa kalan ms)")
    private java.util.Map<String, Long> slaInfo;

    public static TicketResponseDTO fromEntity(Ticket ticket, boolean hasCsat, String productName, String customerName, String assigneeName) {
        return TicketResponseDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .productId(ticket.getProductId())
                .productName(productName)
                .customerId(ticket.getCustomerId())
                .customerName(customerName)
                .assigneeId(ticket.getAssigneeId())
                .assigneeName(assigneeName)
                .slaDeadline(ticket.getSlaDeadline())
                .slaBreached(ticket.getSlaBreached())
                .slaElapsedMs(ticket.getSlaElapsedMs())
                .slaPausedAt(ticket.getSlaPausedAt())
                .createdAt(ticket.getCreatedAt())
                .resolvedAt(ticket.getResolvedAt())
                .closedAt(ticket.getClosedAt())
                .hasCsat(hasCsat)
                .build();
    }

    public static TicketResponseDTO fromEntity(Ticket ticket, String productName, String customerName, String assigneeName) {
        return fromEntity(ticket, false, productName, customerName, assigneeName);
    }
}
