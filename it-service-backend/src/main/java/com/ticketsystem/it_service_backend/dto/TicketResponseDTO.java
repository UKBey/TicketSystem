package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.Ticket;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * Ticket detail response — contains all fields shown in the UI (claimers, SLA status, audit log).
 * Returned by both list and single-ticket queries; the {@code fromEntity} helpers are populated from the caller layer.
 */
@Schema(description = "Bilet detay yanıtı — listeleme ve tekil sorgularda döner")
public class TicketResponseDTO {

    @Schema(description = "Biletin benzersiz kimliği", example = "42")
    private Long id;

    @Schema(description = "Biletin kısa başlığı", example = "E-posta gönderilemiyor")
    private String title;

    @Schema(description = "Sorunun detaylı açıklaması")
    private String description;

    @Schema(description = "Biletin güncel durumu", example = "IN_PROGRESS",
            allowableValues = {"NEW", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "RESOLVED", "CLOSED"})
    private String status;

    @Schema(description = "Öncelik seviyesi", example = "HIGH",
            allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    private String priority;

    @Schema(description = "Ürün/kategori ID'si", example = "1")
    private Long productId;

    @Schema(description = "Ürün/kategori adı", example = "CRM")
    private String productName;

    @Schema(description = "Seçilen talep konusunun ID'si", example = "12")
    private Long topicId;

    @Schema(description = "Konu adının bilet oluşturulduğu andaki anlık görüntüsü", example = "Şifre sıfırlama")
    private String topicName;

    @Schema(description = "Bileti oluşturan müşterinin Keycloak ID'si")
    private String customerId;

    @Schema(description = "Müşterinin tam adı", example = "Ali Yılmaz")
    private String customerName;

    @Schema(description = "Bileti sahiplenen ajanların listesi (boşsa kimse claim almamış)")
    private List<ClaimerDTO> claimers;

    @Schema(description = "SLA son tarih/saat")
    private ZonedDateTime slaDeadline;

    @Schema(description = "SLA ihlal edildi mi?", example = "false")
    private Boolean slaBreached;

    @Schema(description = "Duraklatma öncesi birikmiş SLA süresi (ms)", example = "3600000")
    private Long slaElapsedMs;

    @Schema(description = "SLA sayacının duraklatıldığı an (null ise sayaç aktif)")
    private ZonedDateTime slaPausedAt;

    @Schema(description = "Biletin oluşturulma tarihi")
    private ZonedDateTime createdAt;

    @Schema(description = "Biletin çözüldü olarak işaretlendiği tarih")
    private ZonedDateTime resolvedAt;

    @Schema(description = "Biletin kapatıldığı tarih")
    private ZonedDateTime closedAt;

    @Schema(description = "Bilete CSAT anketi doldurulmuş mu?", example = "true")
    private Boolean hasCsat;

    @Schema(description = "CSAT puanı (1-5). Yalnızca ADMIN/MANAGER görür; diğer rollerde null.", example = "4")
    private Integer csatRating;

    @Schema(description = "Gerçek zamanlı SLA bilgisi — slaState: active | paused | expired | completed")
    private java.util.Map<String, Object> slaInfo;

    @Schema(description = "Bilet aksiyon denetim günlüğü")
    private List<TicketAuditLogDTO> auditLogs;

    public static TicketResponseDTO fromEntity(Ticket ticket, boolean hasCsat,
                                               String productName, String customerName,
                                               List<ClaimerDTO> claimers) {
        return TicketResponseDTO.builder()
                .id(ticket.getId())
                .title(ticket.getTitle())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .priority(ticket.getPriority())
                .productId(ticket.getProductId())
                .productName(productName)
                .topicId(ticket.getTopicId())
                .topicName(ticket.getTopicNameSnapshot())
                .customerId(ticket.getCustomerId())
                .customerName(customerName)
                .claimers(claimers)
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

    public static TicketResponseDTO fromEntity(Ticket ticket, String productName,
                                               String customerName, List<ClaimerDTO> claimers) {
        return fromEntity(ticket, false, productName, customerName, claimers);
    }
}
