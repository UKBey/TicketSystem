package com.ticketsystem.it_service_backend.dto;

import com.ticketsystem.it_service_backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private String id;
    private String email;
    private String fullName;
    private String role;
    private Boolean isActive;
    private ZonedDateTime createdAt;
    private List<ProductDTO> authorizedProducts;

    public static UserDTO fromEntity(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .authorizedProducts(user.getAuthorizedProducts() != null ? 
                    user.getAuthorizedProducts().stream().map(ProductDTO::fromEntity).collect(Collectors.toList()) : null)
                .build();
    }
}
