package com.ticketsystem.it_service_backend.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolutionNoteRequestDTO {

    private String note; // Zorunlu, boş olamaz
}
