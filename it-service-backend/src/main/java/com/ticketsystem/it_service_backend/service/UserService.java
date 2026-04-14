package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.entity.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public User syncUser(User user) {
        log.info("Kullanıcı senkronizasyon işlemi (Service). ID: {}, Email: {}", user.getId(), user.getEmail());
        // Ayni kimlik varsa gunceller, yoksa yeni kayit olusturur.
        User savedUser = userRepository.save(user);
        log.debug("Kullanıcı senkronize edildi. Rol: {}", savedUser.getRole());
        return savedUser;
    }

    public List<User> getAgents() {
        return userRepository.findByRole("AGENT");
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User getUserById(String id) {
        log.debug("Kullanıcı verisi çekiliyor. ID: {}", id);
        User user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Kullanıcı bulunamadı. ID: {}", id);
                    return new RuntimeException("Kullanıcı bulunamadı: " + id);
                });
        
        // Yetkili urun koleksiyonu, session kapanmadan once initialize edilir.
        int productCount = user.getAuthorizedProducts().size();
        log.debug("Kullanıcı çekildi: {}, Yetkili ürün sayısı: {}", user.getFullName(), productCount);
        
        return user;
    }

    @Transactional
    public User assignProductToUser(String userId, Long productId) {
        log.info("Ürün atama işlemi başlatıldı (Service). Kullanıcı: {}, Ürün ID: {}", userId, productId);
        User user = getUserById(userId);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> {
                    log.error("Atanacak ürün bulunamadı. ID: {}", productId);
                    return new RuntimeException("Ürün bulunamadı: " + productId);
                });

        boolean alreadyHasProduct = user.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(productId));

        if (!alreadyHasProduct) {
            log.info("Yeni ürün yetkisi ekleniyor: {}", product.getName());
            user.getAuthorizedProducts().add(product);
            userRepository.save(user);
        } else {
            log.debug("Kullanıcı zaten bu ürüne yetkili. İşlem atlanıyor.");
        }

        return user;
    }

    @Transactional
    public User removeProductFromUser(String userId, Long productId) {
        log.info("Ürün yetki kaldırma işlemi başlatıldı (Service). Kullanıcı: {}, Ürün ID: {}", userId, productId);
        User user = getUserById(userId);

        log.debug("Kullanıcının ürün listesinden kaldırılıyor. Ürün ID: {}", productId);
        user.getAuthorizedProducts().removeIf(p -> p.getId().equals(productId));
        User savedUser = userRepository.save(user);
        
        log.info("Ürün yetkisi başarıyla kaldırıldı. Kullanıcı ID: {}", userId);
        return savedUser;
    }
}