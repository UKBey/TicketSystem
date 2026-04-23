package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.stream.Collectors;
import com.ticketsystem.it_service_backend.dto.ProductDTO;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Tag(name = "Ürün Yönetimi", description = "Destek kategorilerinin (ürün) CRUD işlemleri ve agent yetkilendirmesi")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Tüm ürünleri listele",
            description = """
                    Kullanıcının rolüne göre erişebileceği ürün/kategori listesini döner:
                    - **CUSTOMER**: Yalnızca yetkili olduğu ürünler
                    - **AGENT**: Yalnızca yetkili olduğu ürünler
                    - **AGENT_ADMIN**: Tüm ürünler
                    
                    Bilet oluşturma formunda ürün seçimi için kullanılır.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün listesi başarıyla döndü"),
            @ApiResponse(responseCode = "401", description = "Geçersiz veya eksik JWT token")
    })
    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAllProducts(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt != null ? jwt.getSubject() : null;
        List<String> roles = jwt != null ? JwtUtils.extractRoles(jwt) : List.of();
        
        log.info("Ürünleri listeleme isteği. Kullanıcı ID: {}", userId);
        log.debug("Kullanıcının rolleri: {}", roles);

        List<Product> products = productService.getAllProducts(userId, roles);
        
        log.info("Toplam {} ürün listelendi.", products.size());

        return ResponseEntity.ok(products.stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    @Operation(summary = "Yeni ürün oluştur",
            description = "Sisteme yeni bir destek kategorisi/ürün ekler. Oluşturulan ürün varsayılan olarak aktif (`isActive=true`) olur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün başarıyla oluşturuldu",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER ürün oluşturabilir")
    })
    @PostMapping
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<ProductDTO> createProduct(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Oluşturulacak ürün bilgileri",
                    content = @Content(schema = @Schema(example = "{\"name\": \"ERP\", \"isActive\": true}")))
            @RequestBody Product product) {
        log.info("Yeni ürün oluşturma isteği: {}", product.getName());
        
        Product created = productService.createProduct(product);
        
        log.info("Ürün başarıyla oluşturuldu. ID: {}", created.getId());

        return ResponseEntity.ok(ProductDTO.fromEntity(created));
    }

    @Operation(summary = "Ürünü sil",
            description = "Belirtilen ürünü sistemden kalıcı olarak kaldırır. **Dikkat:** Ürüne bağlı biletler varsa referans bütünlüğü bozulabilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ürün başarıyla silindi"),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER ürün silebilir"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "Silinecek ürünün ID'si", example = "1", required = true)
            @PathVariable Long id) {
        log.info("Ürün silme isteği. ID: {}", id);

        productService.deleteProduct(id);
        
        log.info("Ürün başarıyla silindi. ID: {}", id);

        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Ürünü güncelle",
            description = "Var olan bir ürünün adını ve aktiflik durumunu günceller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER ürün güncelleyebilir"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('AGENT_ADMIN')")
    public ResponseEntity<ProductDTO> updateProduct(
            @Parameter(description = "Güncellenecek ürünün ID'si", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody Product product) {
        log.info("Ürün güncelleme isteği. ID: {}, Yeni İsim: {}", id, product.getName());

        Product updated = productService.updateProduct(id, product);
        
        log.info("Ürün başarıyla güncellendi. ID: {}", updated.getId());

        return ResponseEntity.ok(ProductDTO.fromEntity(updated));
    }
}