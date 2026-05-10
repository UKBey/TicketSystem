package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketService ticketService;
    @Mock
    private TicketRepository ticketRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(10L).name("ERP").isActive(true).build();
    }

    @Test
    void getAllProducts_managerGetsEverything() {
        when(productRepository.findAll()).thenReturn(List.of(product));

        // Manager role no longer has full product access; use AGENT_ADMIN
        List<Product> result = productService.getAllProducts("admin-1", List.of("AGENT_ADMIN"));

        assertEquals(1, result.size());
        assertEquals("ERP", result.get(0).getName());
    }

    @Test
    void getAllProducts_userGetsAuthorizedProducts() {
        User user = User.builder().id("agent-1").authorizedProducts(List.of(product)).build();
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));

        List<Product> result = productService.getAllProducts("agent-1", List.of("AGENT"));

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }

    @Test
    void getAllProducts_missingUserThrows() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> productService.getAllProducts("missing", List.of("AGENT")));

        assertTrue(ex.getMessage().contains("Kullanıcı bulunamadı"));
    }

    @Test
    void createProduct_setsActiveTrueWhenNull() {
        Product input = Product.builder().name("CRM").isActive(null).build();
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product saved = productService.createProduct(input);

        assertEquals(true, saved.getIsActive());
        assertEquals("CRM", saved.getName());
    }

    @Test
    void updateProduct_updatesOnlyProvidedFields() {
        Product existing = Product.builder().id(10L).name("Old").isActive(true).build();
        Product patch = Product.builder().name("New").build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = productService.updateProduct(10L, patch);

        assertEquals("New", updated.getName());
        assertEquals(true, updated.getIsActive());
    }

    @Test
    void updateMaxActiveTickets_setsLimit() {
        Product existing = Product.builder().id(11L).name("CRM").isActive(true).build();

        when(productRepository.findById(11L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = productService.updateMaxActiveTickets(11L, 5);

        assertEquals(5, updated.getMaxActiveTickets());
        assertEquals(11L, updated.getId());
    }

    @Test
    void updateMaxActiveTickets_nullClearsLimit() {
        Product existing = Product.builder().id(12L).name("ERP").isActive(true).maxActiveTickets(3).build();

        when(productRepository.findById(12L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = productService.updateMaxActiveTickets(12L, null);

        assertNull(updated.getMaxActiveTickets());
    }

    @Test
    void deleteProduct_deletesById() {
        when(ticketRepository.findByProductId(10L)).thenReturn(List.of());
        productService.deleteProduct(10L);
        verify(productRepository).deleteById(10L);
    }

    @Test
    void deleteProduct_cascadesTicketDeletion() {
        Ticket t1 = Ticket.builder().id(101L).productId(10L).build();
        Ticket t2 = Ticket.builder().id(102L).productId(10L).build();
        when(ticketRepository.findByProductId(10L)).thenReturn(List.of(t1, t2));

        productService.deleteProduct(10L);

        verify(ticketService).deleteTicket(101L);
        verify(ticketService).deleteTicket(102L);
        verify(productRepository).deleteById(10L);
    }
}
