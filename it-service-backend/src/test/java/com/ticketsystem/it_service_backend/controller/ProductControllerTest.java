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
        Product product = Product.builder().id(11L).name("ERP").isActive(true).build();
        when(productService.getAllProducts("admin-1", List.of("AGENT_ADMIN"))).thenReturn(List.of(product));

        ResponseEntity<List<ProductDTO>> response = productController.getAllProducts(jwtWithRoles("admin-1", "AGENT_ADMIN"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("ERP", response.getBody().get(0).getName());
    }

    @Test
    void getAllProducts_withManagerRole_returnsMappedDtos() {
        Product product = Product.builder().id(12L).name("CRM").isActive(true).build();
        when(productService.getAllProducts("manager-1", List.of("MANAGER"))).thenReturn(List.of(product));

        ResponseEntity<List<ProductDTO>> response = productController.getAllProducts(jwtWithRoles("manager-1", "MANAGER"));

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("CRM", response.getBody().get(0).getName());
    }

    @Test
    void createProduct_returnsCreatedDto() {
        Product input = Product.builder().name("CRM").build();
        Product created = Product.builder().id(12L).name("CRM").isActive(true).build();
        when(productService.createProduct(input)).thenReturn(created);

        ResponseEntity<ProductDTO> response = productController.createProduct(input);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(12L, response.getBody().getId());
    }

    @Test
    void updateProduct_returnsUpdatedDto() {
        Product patch = Product.builder().name("New Name").build();
        Product updated = Product.builder().id(13L).name("New Name").isActive(false).build();
        when(productService.updateProduct(13L, patch)).thenReturn(updated);

        ResponseEntity<ProductDTO> response = productController.updateProduct(13L, patch);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("New Name", response.getBody().getName());
        assertEquals(false, response.getBody().getIsActive());
    }

    @Test
    void updateProductLimit_returnsUpdatedDto() {
        ProductLimitUpdateRequestDTO request = ProductLimitUpdateRequestDTO.builder().maxActiveTickets(8).build();
        Product updated = Product.builder().id(15L).name("ERP").isActive(true).maxActiveTickets(8).build();
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

    private Jwt jwtWithRoles(String subject, String... roles) {
        Jwt jwt = org.mockito.Mockito.mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(subject);
        lenient().when(jwt.getClaimAsMap("realm_access")).thenReturn(Map.of("roles", List.of(roles)));
        return jwt;
    }
}
