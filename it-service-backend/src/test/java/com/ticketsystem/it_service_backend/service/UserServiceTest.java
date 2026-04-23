package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private UserService userService;

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder().id(55L).name("Support").isActive(true).build();
        user = User.builder()
                .id("agent-1")
                .email("agent@example.com")
                .fullName("Agent One")
                .role("AGENT")
                .authorizedProducts(new ArrayList<>())
                .build();
    }

    @Test
    void syncUser_savesAndReturnsUser() {
        when(userRepository.save(any(User.class))).thenReturn(user);

        User result = userService.syncUser(user);

        assertEquals("agent-1", result.getId());
        verify(userRepository).save(user);
    }

    @Test
    void assignProductToUser_addsProductWhenMissing() {
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(productRepository.findById(55L)).thenReturn(Optional.of(product));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.assignProductToUser("agent-1", 55L);

        assertEquals(1, result.getAuthorizedProducts().size());
        assertEquals(55L, result.getAuthorizedProducts().get(0).getId());
        verify(userRepository).save(user);
    }

    @Test
    void assignProductToUser_doesNotDuplicateProduct() {
        user.setAuthorizedProducts(new ArrayList<>(List.of(product)));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(productRepository.findById(55L)).thenReturn(Optional.of(product));

        User result = userService.assignProductToUser("agent-1", 55L);

        assertEquals(1, result.getAuthorizedProducts().size());
    }

    @Test
    void removeProductFromUser_removesMatchingProduct() {
        user.setAuthorizedProducts(new ArrayList<>(List.of(product)));
        when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.removeProductFromUser("agent-1", 55L);

        assertEquals(0, result.getAuthorizedProducts().size());
        verify(userRepository).save(user);
    }

    @Test
    void getUserById_whenMissing_throwsRuntimeException() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> userService.getUserById("missing"));
    }
}
