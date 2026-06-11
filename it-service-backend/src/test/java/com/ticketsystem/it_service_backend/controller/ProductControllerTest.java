package com.ticketsystem.it_service_backend.controller;

import com.ticketsystem.it_service_backend.dto.ProductDTO;
import com.ticketsystem.it_service_backend.dto.ProductLimitUpdateRequestDTO;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    private ProductController productController;

    @BeforeEach
    void setUp() {
        productController = new ProductController(productService);
    }

    @Test
    void getAllProducts_withAgentAdminRole_returnsMappedDtos() {
        Product product = Product.builder().id(11L).nameEn("ERP").isActive(true).build();
        when(productService.getAllProducts("admin-1", List.of("ADMIN"))).thenReturn(List.of(product));

        ResponseEntity<List<ProductDTO>> response = productController.getAllProducts(jwtWithRoles("admin-1", "ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("ERP", response.getBody().get(0).getNameEn());
    }

    @Test
    void getAllProducts_withManagerRole_returnsMappedDtos() {
        Product product = Product.builder().id(12L).nameEn("CRM").isActive(true).build();
        when(productService.getAllProducts("manager-1", List.of("MANAGER"))).thenReturn(List.of(product));

        ResponseEntity<List<ProductDTO>> response = productController.getAllProducts(jwtWithRoles("manager-1", "MANAGER"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("CRM", response.getBody().get(0).getNameEn());
    }

    @Test
    void createProduct_returnsCreatedDto() {
        Product input = Product.builder().nameEn("CRM").build();
        Product created = Product.builder().id(12L).nameEn("CRM").isActive(true).build();
        when(productService.createProduct(input)).thenReturn(created);

        ResponseEntity<ProductDTO> response = productController.createProduct(input);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(12L, response.getBody().getId());
    }

    @Test
    void updateProduct_returnsUpdatedDto() {
        Product patch = Product.builder().nameEn("New Name").build();
        Product updated = Product.builder().id(13L).nameEn("New Name").isActive(false).build();
        when(productService.updateProduct(13L, patch)).thenReturn(updated);

        ResponseEntity<ProductDTO> response = productController.updateProduct(13L, patch);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("New Name", response.getBody().getNameEn());
        assertEquals(false, response.getBody().getIsActive());
    }

    @Test
    void updateProductLimit_returnsUpdatedDto() {
        ProductLimitUpdateRequestDTO request = ProductLimitUpdateRequestDTO.builder().maxActiveTickets(8).build();
        Product updated = Product.builder().id(15L).nameEn("ERP").isActive(true).maxActiveTickets(8).build();
        when(productService.updateMaxActiveTickets(15L, 8)).thenReturn(updated);

        ResponseEntity<ProductDTO> response = productController.updateProductLimit(15L, request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(8, response.getBody().getMaxActiveTickets());
    }

    @Test
    void deleteProduct_returnsNoContent() {
        ResponseEntity<Void> response = productController.deleteProduct(14L);

        assertEquals(204, response.getStatusCode().value());
        verify(productService).deleteProduct(14L);
    }

    @Test
    void getProductById_withNonNullJwt_returnsOk() {
        Product product = Product.builder().id(10L).nameEn("ERP").isActive(true).build();
        when(productService.getProductById(10L, "admin-1", List.of("ADMIN"))).thenReturn(product);

        ResponseEntity<ProductDTO> response = productController.getProductById(10L, jwtWithRoles("admin-1", "ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(10L, response.getBody().getId());
    }

    @Test
    void getProductById_withNullJwt_callsServiceWithNullAndEmptyRoles() {
        Product product = Product.builder().id(10L).nameEn("ERP").isActive(true).build();
        when(productService.getProductById(10L, null, List.of())).thenReturn(product);

        ResponseEntity<ProductDTO> response = productController.getProductById(10L, null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    @Test
    void getAllProducts_withNullJwt_callsServiceWithNullAndEmptyRoles() {
        when(productService.getAllProducts(null, List.of())).thenReturn(List.of());

        ResponseEntity<List<ProductDTO>> response = productController.getAllProducts(null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().size());
    }

    private Jwt jwtWithRoles(String subject, String... roles) {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", List.of(roles)));
        return jwt;
    }
}
