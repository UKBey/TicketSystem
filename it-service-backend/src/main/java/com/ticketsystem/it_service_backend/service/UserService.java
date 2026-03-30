package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public User syncUser(User user) {
        // Kullanıcı varsa günceller, yoksa yeni kaydeder (Keycloak'tan gelen veriyle)
        return userRepository.save(user);
    }

    public List<User> getAgents() {
        return userRepository.findByRole("AGENT");
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + id));
        
        // Lazy loading (Hibernate) json serialize edilirken session kapalı olduğu için
        // patlamaması adına listeyi initialize ediyoruz.
        user.getAuthorizedProducts().size();
        
        return user;
    }

    @Transactional
    public User assignProductToUser(String userId, Long productId) {
        User user = getUserById(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Ürün bulunamadı: " + productId));

        boolean alreadyHasProduct = user.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(productId));

        if (!alreadyHasProduct) {
            user.getAuthorizedProducts().add(product);
            userRepository.save(user);
        }

        return user;
    }

    @Transactional
    public User removeProductFromUser(String userId, Long productId) {
        User user = getUserById(userId);

        user.getAuthorizedProducts().removeIf(p -> p.getId().equals(productId));
        userRepository.save(user);

        return user;
    }
}