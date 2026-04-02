package com.ticketsystem.it_service_backend.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorklogRequestDTO {

    private Integer minutes; // Zorunlu, pozitif olmalı

    private String description; // Opsiyonel, max 500 karakter
}
