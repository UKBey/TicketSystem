package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.Ticket;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock
    private AgentProductLimitRepository agentProductLimitRepository;

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

        // ADMIN is a global role and sees every product.
        List<Product> result = productService.getAllProducts("admin-1", List.of("ADMIN"));

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

    // -------------------------------------------------------------------------
    // getProductById — branch coverage
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getProductById → ADMIN rolü → yetki kontrolü atlanır")
    void getProductById_agentAdminRole_returnsDirectly() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        Product result = productService.getProductById(10L, "admin-1", List.of("ADMIN"));

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getProductById → ürün bulunamazsa 404")
    void getProductById_notFound_throws404() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L, "user-1", List.of("AGENT")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getProductById → userId null → FORBIDDEN")
    void getProductById_nullUserId_throwsForbidden() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.getProductById(10L, null, List.of("AGENT")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getProductById → kullanıcı yetkisizse FORBIDDEN")
    void getProductById_userNotAuthorized_throwsForbidden() {
        User userWithNoProducts = User.builder().id("user-1").authorizedProducts(new ArrayList<>()).build();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(userWithNoProducts));

        assertThatThrownBy(() -> productService.getProductById(10L, "user-1", List.of("CUSTOMER")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("getProductById → kullanıcı yetkili → ürünü döner")
    void getProductById_userAuthorized_returnsProduct() {
        User authorizedUser = User.builder().id("user-1").authorizedProducts(List.of(product)).build();
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(authorizedUser));

        Product result = productService.getProductById(10L, "user-1", List.of("CUSTOMER"));

        assertThat(result.getId()).isEqualTo(10L);
    }

    // -------------------------------------------------------------------------
    // getAllProducts — null userId branch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getAllProducts → userId null → boş liste döner")
    void getAllProducts_nullUserId_returnsEmpty() {
        List<Product> result = productService.getAllProducts(null, List.of("AGENT"));

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // updateProduct — maxActiveTickets null-override branch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateProduct → mevcut maxActiveTickets varken null patch → limit kaldırılır")
    void updateProduct_existingLimitWithNullPatch_clearsLimit() {
        Product existing = Product.builder().id(10L).name("ERP").isActive(true).maxActiveTickets(5).build();
        Product patch = Product.builder().build(); // name=null, isActive=null, maxActiveTickets=null

        when(productRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(10L, patch);

        assertNull(updated.getMaxActiveTickets());
    }

    // -------------------------------------------------------------------------
    // updateMaxActiveTickets — limit < 1 branch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("updateMaxActiveTickets → limit < 1 → IllegalArgumentException")
    void updateMaxActiveTickets_belowMinimum_throwsIllegalArgument() {
        assertThatThrownBy(() -> productService.updateMaxActiveTickets(10L, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getProductById → MANAGER rolü → yetki kontrolü atlanır")
    void getProductById_manager_skipsAuthCheck() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        Product result = productService.getProductById(10L, "manager-1", List.of("MANAGER"));

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getProductById → kullanıcı DB'de yok → NOT_FOUND")
    void getProductById_userNotFound_throws() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(10L, "ghost", List.of("AGENT")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getAllProducts → MANAGER rolü → tüm ürünler")
    void getAllProducts_manager_returnsAll() {
        when(productRepository.findAll()).thenReturn(List.of(product));

        List<Product> result = productService.getAllProducts(null, List.of("MANAGER"));

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getAllProducts → kullanıcı bulunamaz → RuntimeException")
    void getAllProducts_userNotFound_throws() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getAllProducts("ghost", List.of("AGENT")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("createProduct → isActive null → varsayılan true atanır")
    void createProduct_nullIsActive_defaultsToTrue() {
        Product input = Product.builder().name("X").build();
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.createProduct(input);

        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("createProduct → isActive false → değiştirilmez")
    void createProduct_explicitFalse_keepsFalse() {
        Product input = Product.builder().name("X").isActive(false).build();
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.createProduct(input);

        assertThat(result.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("deleteProduct → ürüne bağlı bilet yok → cascade delete atlanır")
    void deleteProduct_noTickets_skipsCascade() {
        when(ticketRepository.findByProductId(10L)).thenReturn(List.of());

        productService.deleteProduct(10L);

        verify(ticketService, org.mockito.Mockito.never()).deleteTicket(org.mockito.ArgumentMatchers.any());
        verify(productRepository).deleteById(10L);
    }

    @Test
    @DisplayName("deleteProduct → bağlı biletler var → her bilet için cascade")
    void deleteProduct_withTickets_cascadesEach() {
        com.ticketsystem.it_service_backend.entity.Ticket t1 =
                com.ticketsystem.it_service_backend.entity.Ticket.builder().id(101L).build();
        com.ticketsystem.it_service_backend.entity.Ticket t2 =
                com.ticketsystem.it_service_backend.entity.Ticket.builder().id(102L).build();
        when(ticketRepository.findByProductId(10L)).thenReturn(List.of(t1, t2));

        productService.deleteProduct(10L);

        verify(ticketService).deleteTicket(101L);
        verify(ticketService).deleteTicket(102L);
        verify(agentProductLimitRepository).deleteByProductId(10L);
        verify(productRepository).deleteById(10L);
    }

    @Test
    @DisplayName("updateProduct → ürün yok → RuntimeException")
    void updateProduct_missing_throws() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateProduct(99L, Product.builder().build()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("updateProduct → name + isActive + maxActiveTickets patch → her üçü de güncellenir")
    void updateProduct_fullPatch_updatesAllFields() {
        Product existing = Product.builder().id(10L).name("Old").isActive(true).maxActiveTickets(3).build();
        Product patch = Product.builder().name("New").isActive(false).maxActiveTickets(7).build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product updated = productService.updateProduct(10L, patch);

        assertThat(updated.getName()).isEqualTo("New");
        assertThat(updated.getIsActive()).isFalse();
        assertThat(updated.getMaxActiveTickets()).isEqualTo(7);
    }

    @Test
    @DisplayName("updateMaxActiveTickets → ürün yok → NOT_FOUND")
    void updateMaxActiveTickets_productMissing_throws() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.updateMaxActiveTickets(99L, 5))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("updateMaxActiveTickets → null limit → temizler")
    void updateMaxActiveTickets_null_clears() {
        Product existing = Product.builder().id(10L).name("X").isActive(true).maxActiveTickets(5).build();
        when(productRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateMaxActiveTickets(10L, null);

        assertNull(result.getMaxActiveTickets());
    }
}
