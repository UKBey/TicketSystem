package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import com.ticketsystem.it_service_backend.dto.ProductDTO;
import com.ticketsystem.it_service_backend.dto.ProductLimitUpdateRequestDTO;
import com.ticketsystem.it_service_backend.util.JwtUtils;
import com.ticketsystem.it_service_backend.util.LocalizedText;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;

/**
 * REST controller for support category (product) CRUD and concurrent ticket limits.
 *
 * <p>Listing and detail endpoints are open to any authenticated user and are filtered
 * by role; write operations are restricted to the {@code ADMIN} role.
 * Business rules are enforced inside {@link ProductService}.
 */
@Log4j2
@Tag(name = "Ürün Yönetimi", description = "Destek kategorilerinin (ürün) CRUD işlemleri ve agent yetkilendirmesi")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Returns the detail of the specified product; returns {@code 403} if the user is not authorized for it.
     *
     * @param id product identifier
     * @return product DTO
     */
    @Operation(summary = "Ürün detayı getir", description = "Belirtilen ürünü döner. Kullanıcı yetkili değilse 403 döner.")
    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt != null ? jwt.getSubject() : null;
        List<String> roles = jwt != null ? JwtUtils.extractRoles(jwt) : List.of();
        log.debug("Ürün detayı isteği. ID: {}, Kullanıcı: {}", id, userId);
        Product product = productService.getProductById(id, userId, roles);
        return ResponseEntity.ok(ProductDTO.fromEntity(product));
    }

    /**
     * Returns the list of products the user can access based on their role.
     *
     * @return list of product DTOs filtered by role
     */
    @Operation(summary = "Tüm ürünleri listele",
            description = """
                    Kullanıcının rolüne göre erişebileceği ürün/kategori listesini döner:
                    - **CUSTOMER**: Yalnızca yetkili olduğu ürünler
                    - **AGENT**: Yalnızca yetkili olduğu ürünler
                    - **ADMIN / MANAGER**: Tüm ürünler
                    
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
        
        log.debug("Ürünleri listeleme isteği. Kullanıcı ID: {}", userId);
        log.debug("Kullanıcının rolleri: {}", roles);

        List<Product> products = productService.getAllProducts(userId, roles);

        log.debug("Toplam {} ürün listelendi.", products.size());

        return ResponseEntity.ok(products.stream()
                .map(ProductDTO::fromEntity)
                .toList());
    }

    /**
     * Paginated + filtered + sorted product list for the management panel. Same role-based
     * visibility as {@link #getAllProducts}; filtering/sorting/paging happen server-side.
     *
     * @param search optional name filter (matches either language)
     * @param sortBy {@code id} | {@code name} | {@code status} | {@code maxActiveTickets}
     * @param sortDir {@code asc} | {@code desc}
     * @param lang active UI language ({@code tr}/{@code en}) for the localized-name sort
     * @param page zero-based page index
     * @param size page size
     * @return one page of product DTOs
     */
    @Operation(summary = "Ürünleri sayfalı + filtreli listele (yönetim paneli)")
    @GetMapping("/paged")
    public ResponseEntity<Page<ProductDTO>> getAllProductsPaged(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "sortBy", defaultValue = "id") String sortBy,
            @RequestParam(name = "sortDir", defaultValue = "asc") String sortDir,
            @RequestParam(name = "lang", required = false) String lang,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        String userId = jwt != null ? jwt.getSubject() : null;
        List<String> roles = jwt != null ? JwtUtils.extractRoles(jwt) : List.of();
        Page<Product> products = productService.getAllProductsPaged(
                userId, roles, search, sortBy, sortDir, lang, page, size);
        return ResponseEntity.ok(products.map(ProductDTO::fromEntity));
    }

    /**
     * Adds a new support category/product to the system; defaults to {@code isActive=true}.
     *
     * @param product fields of the product to create
     * @return DTO of the created product
     */
    @Operation(summary = "Yeni ürün oluştur",
            description = "Sisteme yeni bir destek kategorisi/ürün ekler. Oluşturulan ürün varsayılan olarak aktif (`isActive=true`) olur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün başarıyla oluşturuldu",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN ürün oluşturabilir")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> createProduct(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Oluşturulacak ürün bilgileri (nameTr/nameEn'den en az biri zorunlu)",
                    content = @Content(schema = @Schema(example = "{\"nameTr\": \"Kurumsal Kaynak Planlama\", \"nameEn\": \"ERP\", \"isActive\": true}")))
            @RequestBody Product product) {
        log.info("Yeni ürün oluşturma isteği: {}", LocalizedText.label(product.getNameTr(), product.getNameEn()));
        
        Product created = productService.createProduct(product);
        
        log.info("Ürün başarıyla oluşturuldu. ID: {}", created.getId());

        return ResponseEntity.ok(ProductDTO.fromEntity(created));
    }

    /**
     * Permanently deletes the specified product.
     *
     * <p>If the product is referenced by existing tickets, referential integrity may break;
     * unless the service layer guards against this, the caller must be aware of it.
     *
     * @param id identifier of the product to delete
     * @return {@code 204 No Content}
     */
    @Operation(summary = "Ürünü sil",
            description = "Belirtilen ürünü sistemden kalıcı olarak kaldırır. **Dikkat:** Ürüne bağlı biletler varsa referans bütünlüğü bozulabilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ürün başarıyla silindi"),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN ürün silebilir"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "Silinecek ürünün ID'si", example = "1", required = true)
            @PathVariable Long id) {
        log.info("Ürün silme isteği. ID: {}", id);

        productService.deleteProduct(id);
        
        log.info("Ürün başarıyla silindi. ID: {}", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Updates the name and active status of an existing product.
     *
     * @param id identifier of the product to update
     * @param product new field values
     * @return DTO of the updated product
     */
    @Operation(summary = "Ürünü güncelle",
            description = "Var olan bir ürünün adını ve aktiflik durumunu günceller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN ürün güncelleyebilir"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> updateProduct(
            @Parameter(description = "Güncellenecek ürünün ID'si", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody Product product) {
        log.info("Ürün güncelleme isteği. ID: {}, Yeni İsim: {}", id,
                LocalizedText.label(product.getNameTr(), product.getNameEn()));

        Product updated = productService.updateProduct(id, product);
        
        log.info("Ürün başarıyla güncellendi. ID: {}", updated.getId());

        return ResponseEntity.ok(ProductDTO.fromEntity(updated));
    }

    /**
     * Updates the default concurrent ticket limit for the specified product; {@code null} removes the limit.
     *
     * @param id identifier of the product whose limit is being updated
     * @param request request containing the new {@code maxActiveTickets} value (null to remove the limit)
     * @return DTO of the updated product
     */
    @Operation(summary = "Ürünün maksimum eşzamanlı bilet limitini güncelle",
            description = "Belirtilen ürün için varsayılan eşzamanlı bilet limitini günceller. Null gönderilirse limit kaldırılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün limiti başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca ADMIN ürün limitini güncelleyebilir"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı")
    })
    @PatchMapping("/{id}/limit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDTO> updateProductLimit(
            @Parameter(description = "Limit güncellenecek ürünün ID'si", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody ProductLimitUpdateRequestDTO request) {
        log.info("Ürün eşzamanlı bilet limiti güncelleme isteği. ID: {}, Yeni limit: {}", id, request.getMaxActiveTickets());

        Product updated = productService.updateMaxActiveTickets(id, request.getMaxActiveTickets());

        log.info("Ürün limiti başarıyla güncellendi. ID: {}", updated.getId());

        return ResponseEntity.ok(ProductDTO.fromEntity(updated));
    }
}