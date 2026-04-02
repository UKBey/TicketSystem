package com.ticketsystem.it_service_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsatDTO {
    
    private Integer rating; // 1-5 (Mandatory check in Service)
    
    private String comment; // Optional
}
