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

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Product> getAllProducts(String userId, List<String> roles) {
        log.info("Ürün listeleme isteği. Kullanıcı: {}, Roller: {}", userId, roles);

        if (roles.contains("AGENT_ADMIN")) {
            log.debug("Agent admin rolü algılandı, tüm ürünler getiriliyor.");
            return productRepository.findAll();
        }
        
        if (userId == null) {
            log.warn("Kullanıcı ID bulunamadı, boş liste dönülüyor.");
            return new ArrayList<>();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Kullanıcı veritabanında bulunamadı: {}", userId);
                    return new RuntimeException("Kullanıcı bulunamadı: " + userId);
                });

        List<Product> authProducts = user.getAuthorizedProducts();
        log.debug("Kullanıcı (ID: {}) için {} adet yetkili ürün bulundu.", userId, authProducts.size());
        return authProducts;
    }

    public Product createProduct(Product product) {
        log.info("Yeni ürün oluşturuluyor: {}", product.getName());
        if (product.getIsActive() == null) {
            product.setIsActive(true);
        }
        Product savedProduct = productRepository.save(product);
        log.info("Ürün başarıyla oluşturuldu. ID: {}", savedProduct.getId());
        return savedProduct;
    }

    public void deleteProduct(Long id) {
        log.info("Ürün siliniyor. ID: {}", id);
        productRepository.deleteById(id);
        log.info("Ürün başarıyla silindi. ID: {}", id);
    }

    public Product updateProduct(Long id, Product updatedProduct) {
        log.info("Ürün güncelleniyor. ID: {}", id);
        Product existingProduct = productRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Güncellenecek ürün bulunamadı. ID: {}", id);
                    return new RuntimeException("Ürün bulunamadı: " + id);
                });

        if (updatedProduct.getName() != null) {
            log.debug("İsim güncelleniyor: {} -> {}", existingProduct.getName(), updatedProduct.getName());
            existingProduct.setName(updatedProduct.getName());
        }
        if (updatedProduct.getIsActive() != null) {
            log.debug("Aktiflik durumu güncelleniyor: {} -> {}", existingProduct.getIsActive(), updatedProduct.getIsActive());
            existingProduct.setIsActive(updatedProduct.getIsActive());
        }
        Product savedProduct = productRepository.save(existingProduct);
        log.info("Ürün başarıyla güncellendi. ID: {}", savedProduct.getId());
        return savedProduct;
    }
}