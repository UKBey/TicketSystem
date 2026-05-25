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
import com.ticketsystem.it_service_backend.dto.ProductLimitUpdateRequestDTO;
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

/**
 * Destek kategorisi (ürün) CRUD ve eşzamanlı bilet limiti REST kontrolcüsü.
 *
 * <p>Listeleme/detay her kimliği doğrulanmış kullanıcıya açıktır ve rol bazında filtrelenir;
 * yazma operasyonları yalnızca {@code AGENT_ADMIN}/{@code MANAGER} rolüne aittir.
 * İş kuralları {@link ProductService} içinde uygulanır.
 */
@Log4j2
@Tag(name = "Ürün Yönetimi", description = "Destek kategorilerinin (ürün) CRUD işlemleri ve agent yetkilendirmesi")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Belirtilen ürünün detayını döner; kullanıcı ürüne yetkili değilse {@code 403} döner.
     *
     * @param id ürün kimliği
     * @return ürün DTO'su
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
     * Kullanıcının rolüne göre erişebileceği ürünlerin listesini döner.
     *
     * @return rol bazlı filtrelenmiş ürün DTO listesi
     */
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
        
        log.debug("Ürünleri listeleme isteği. Kullanıcı ID: {}", userId);
        log.debug("Kullanıcının rolleri: {}", roles);

        List<Product> products = productService.getAllProducts(userId, roles);

        log.debug("Toplam {} ürün listelendi.", products.size());

        return ResponseEntity.ok(products.stream()
                .map(ProductDTO::fromEntity)
                .collect(Collectors.toList()));
    }

    /**
     * Sisteme yeni bir destek kategorisi/ürün ekler; varsayılan olarak {@code isActive=true} olur.
     *
     * @param product oluşturulacak ürünün alanları
     * @return oluşturulan ürün DTO'su
     */
    @Operation(summary = "Yeni ürün oluştur",
            description = "Sisteme yeni bir destek kategorisi/ürün ekler. Oluşturulan ürün varsayılan olarak aktif (`isActive=true`) olur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün başarıyla oluşturuldu",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER ürün oluşturabilir")
    })
    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
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

    /**
     * Belirtilen ürünü kalıcı olarak siler.
     *
     * <p>Ürüne bağlı biletler varsa referans bütünlüğü bozulabilir; servis katmanında
     * önlem alınmadığı sürece çağıran bu durumdan haberdar olmalıdır.
     *
     * @param id silinecek ürünün kimliği
     * @return {@code 204 No Content}
     */
    @Operation(summary = "Ürünü sil",
            description = "Belirtilen ürünü sistemden kalıcı olarak kaldırır. **Dikkat:** Ürüne bağlı biletler varsa referans bütünlüğü bozulabilir.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ürün başarıyla silindi"),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER ürün silebilir"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteProduct(
            @Parameter(description = "Silinecek ürünün ID'si", example = "1", required = true)
            @PathVariable Long id) {
        log.info("Ürün silme isteği. ID: {}", id);

        productService.deleteProduct(id);
        
        log.info("Ürün başarıyla silindi. ID: {}", id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Var olan bir ürünün adını ve aktiflik durumunu günceller.
     *
     * @param id güncellenecek ürünün kimliği
     * @param product yeni alan değerleri
     * @return güncellenmiş ürün DTO'su
     */
    @Operation(summary = "Ürünü güncelle",
            description = "Var olan bir ürünün adını ve aktiflik durumunu günceller.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca MANAGER ürün güncelleyebilir"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
    public ResponseEntity<ProductDTO> updateProduct(
            @Parameter(description = "Güncellenecek ürünün ID'si", example = "1", required = true)
            @PathVariable Long id,
            @RequestBody Product product) {
        log.info("Ürün güncelleme isteği. ID: {}, Yeni İsim: {}", id, product.getName());

        Product updated = productService.updateProduct(id, product);
        
        log.info("Ürün başarıyla güncellendi. ID: {}", updated.getId());

        return ResponseEntity.ok(ProductDTO.fromEntity(updated));
    }

    /**
     * Belirtilen ürünün varsayılan eşzamanlı bilet limitini günceller; {@code null} limiti kaldırır.
     *
     * @param id limit güncellenecek ürünün kimliği
     * @param request yeni {@code maxActiveTickets} değerini içeren istek (null ise limit kaldırılır)
     * @return güncellenmiş ürün DTO'su
     */
    @Operation(summary = "Ürünün maksimum eşzamanlı bilet limitini güncelle",
            description = "Belirtilen ürün için varsayılan eşzamanlı bilet limitini günceller. Null gönderilirse limit kaldırılır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ürün limiti başarıyla güncellendi",
                    content = @Content(schema = @Schema(implementation = ProductDTO.class))),
            @ApiResponse(responseCode = "403", description = "Yalnızca AGENT_ADMIN ürün limitini güncelleyebilir"),
            @ApiResponse(responseCode = "404", description = "Ürün bulunamadı")
    })
    @PatchMapping("/{id}/limit")
    @PreAuthorize("hasAnyRole('AGENT_ADMIN', 'MANAGER')")
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