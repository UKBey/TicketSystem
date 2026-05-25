package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.repository.AgentProductLimitRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import com.ticketsystem.it_service_backend.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.log4j.Log4j2;

/**
 * Ürün CRUD'u ve kullanıcı bazlı ürün erişim kontrolü.
 *
 * <p>Müşteri/ajan kullanıcısı yalnızca {@code authorized_products} ilişkisinde
 * yer alan ürünleri görebilir; AGENT_ADMIN ve MANAGER tüm ürünleri görür. Ürün
 * silme cascade davranır: bağlı tüm biletler {@link TicketService#deleteTicket}
 * çağrılarıyla silinir ve ajan-özel limit kayıtları temizlenir.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final TicketService ticketService;
    private final TicketRepository ticketRepository;
    private final AgentProductLimitRepository agentProductLimitRepository;

    /**
     * Ürünü ID üzerinden, çağıran kullanıcının yetki kontrolü ile döner.
     * AGENT_ADMIN / MANAGER bypass eder; diğer roller için
     * {@code user.authorizedProducts} listesinde bulunma şartı aranır.
     *
     * @param id ürün ID
     * @param userId istek yapan kullanıcı
     * @param roles kullanıcının rolleri
     * @return ürün
     * @throws ResponseStatusException 404 ürün/kullanıcı yoksa, 403 yetki yoksa
     */
    @Transactional(readOnly = true)
    public Product getProductById(Long id, String userId, List<String> roles) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.product.not.found"));

        if (roles.contains("AGENT_ADMIN") || roles.contains("MANAGER")) return product;

        if (userId == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.forbidden");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.user.not.found"));

        boolean authorized = user.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(id));
        if (!authorized) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.product.access.forbidden");

        return product;
    }

    /**
     * Kullanıcının görebildiği tüm ürünleri döner. AGENT_ADMIN / MANAGER tüm
     * ürünleri alır; diğer roller yalnızca kendi {@code authorizedProducts}
     * listelerindekileri alır. Kullanıcı ID'si yoksa boş liste döner.
     *
     * @param userId istek yapan kullanıcı (null olabilir)
     * @param roles kullanıcının rolleri
     * @return görülebilen ürünler
     */
    @Transactional(readOnly = true)
    public List<Product> getAllProducts(String userId, List<String> roles) {
        log.debug("Ürün listeleme isteği. Kullanıcı: {}, Roller: {}", userId, roles);

        if (roles.contains("AGENT_ADMIN") || roles.contains("MANAGER")) {
            log.debug("Yönetici rolü algılandı, tüm ürünler getiriliyor.");
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

    /**
     * Yeni bir ürün oluşturur. {@code isActive} null gelirse {@code true} atanır.
     *
     * @param product oluşturulacak ürün (id eşittir DB tarafından üretilir)
     * @return persist edilmiş ürün
     */
    public Product createProduct(Product product) {
        log.info("Yeni ürün oluşturuluyor: {}", product.getName());
        if (product.getIsActive() == null) {
            product.setIsActive(true);
        }
        Product savedProduct = productRepository.save(product);
        log.info("Ürün başarıyla oluşturuldu. ID: {}", savedProduct.getId());
        return savedProduct;
    }

    /**
     * Ürünü ve ona bağlı tüm bilet/limit kayıtlarını cascade silinir.
     *
     * <p>Önce ürüne ait biletlerin ID'leri toplanır ve her biri
     * {@link TicketService#deleteTicket} ile (yorum, attachment, claim vb.
     * temizlenmesi için) silinir; ardından ajan-özel limit kayıtları ve
     * ürünün kendisi silinir.
     *
     * @param id silinecek ürün ID
     */
    @Transactional
    public void deleteProduct(Long id) {
        log.info("Ürün siliniyor. ID: {}", id);

        // Ürüne bağlı tüm biletleri ve onlara ait tüm verileri sil
        List<Long> ticketIds = ticketRepository.findByProductId(id)
                .stream().map(t -> t.getId()).toList();

        if (!ticketIds.isEmpty()) {
            log.info("Ürüne bağlı {} bilet cascade siliniyor. Ürün ID: {}", ticketIds.size(), id);
            for (Long ticketId : ticketIds) {
                ticketService.deleteTicket(ticketId);
            }
        }

        agentProductLimitRepository.deleteByProductId(id);
        productRepository.deleteById(id);
        log.info("Ürün başarıyla silindi. ID: {}", id);
    }

    /**
     * Ürünü kısmi olarak günceller. Yalnızca {@code null} olmayan alanlar uygulanır;
     * {@code maxActiveTickets} özellikle {@code null} gönderilirse limit kaldırılır.
     *
     * @param id güncellenecek ürün ID
     * @param updatedProduct kısmi yeni değerler
     * @return güncellenmiş ürün
     * @throws RuntimeException ürün bulunamazsa
     */
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
        if (updatedProduct.getMaxActiveTickets() != null) {
            log.debug("Maksimum eşzamanlı bilet limiti güncelleniyor: {} -> {}", existingProduct.getMaxActiveTickets(), updatedProduct.getMaxActiveTickets());
            existingProduct.setMaxActiveTickets(updatedProduct.getMaxActiveTickets());
        } else if (updatedProduct.getMaxActiveTickets() == null && existingProduct.getMaxActiveTickets() != null) {
            log.debug("Maksimum eşzamanlı bilet limiti kaldırılıyor");
            existingProduct.setMaxActiveTickets(null);
        }
        Product savedProduct = productRepository.save(existingProduct);
        log.info("Ürün başarıyla güncellendi. ID: {}", savedProduct.getId());
        return savedProduct;
    }

    /**
     * Ürünün varsayılan eşzamanlı bilet limitini değiştirir; {@code null} ise limit
     * tamamen kaldırılır. Pozitif olmayan değer kabul edilmez.
     *
     * @param productId ürün ID
     * @param limit yeni limit veya {@code null} (limitsiz)
     * @return güncellenmiş ürün
     * @throws IllegalArgumentException limit 1'in altındaysa
     * @throws ResponseStatusException 404 — ürün bulunamazsa
     */
    @Transactional
    public Product updateMaxActiveTickets(Long productId, Integer limit) {
        log.info("Ürün eşzamanlı bilet limiti güncelleniyor. ID: {}, Yeni limit: {}", productId, limit);

        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("error.product.limit.minimum");
        }

        Product existingProduct = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.product.not.found"));

        existingProduct.setMaxActiveTickets(limit);

        Product savedProduct = productRepository.save(existingProduct);
        log.info("Ürün eşzamanlı bilet limiti güncellendi. ID: {}, Limit: {}", savedProduct.getId(), savedProduct.getMaxActiveTickets());
        return savedProduct;
    }
}