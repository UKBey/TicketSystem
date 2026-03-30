package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Product> getAllProducts(String userId, List<String> roles) {
        if (roles.contains("MANAGER")) {
            return productRepository.findAll();
        }
        
        if (userId == null) {
            return new ArrayList<>();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı: " + userId));

        return user.getAuthorizedProducts();
    }

    public Product createProduct(Product product) {
        if (product.getIsActive() == null) {
            product.setIsActive(true);
        }
        return productRepository.save(product);
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    public Product updateProduct(Long id, Product updatedProduct) {
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ürün bulunamadı: " + id));

        if (updatedProduct.getName() != null) {
            existingProduct.setName(updatedProduct.getName());
        }
        if (updatedProduct.getIsActive() != null) {
            existingProduct.setIsActive(updatedProduct.getIsActive());
        }
        return productRepository.save(existingProduct);
    }
}