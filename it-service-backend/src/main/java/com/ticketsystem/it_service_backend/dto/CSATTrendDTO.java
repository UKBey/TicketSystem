package com.ticketsystem.it_service_backend.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "CSAT puan trendi — bu ay ile geçen ay karşılaştırması")
public class CSATTrendDTO {

    @Schema(description = "Bu ayın CSAT ortalaması", example = "4.42")
    private double thisMonth;

    @Schema(description = "Geçen ayın CSAT ortalaması", example = "4.12")
    private double lastMonth;

    @Schema(description = "Trend yönü: UP, DOWN veya STABLE", example = "UP")
    private String trend;
}
