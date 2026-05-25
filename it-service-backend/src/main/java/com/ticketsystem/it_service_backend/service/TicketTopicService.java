package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Bir ürüne ait talep konularının (ticket topic) CRUD yönetimi.
 *
 * <p>Konu adı ürün içinde benzersizdir (case-insensitive). Listeleme açık olduğu
 * için yetki kontrolü controller seviyesinde yapılır; yönetim (create/update/delete)
 * yalnızca AGENT_ADMIN / MANAGER rolündedir.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketTopicService {

    private final TicketTopicRepository topicRepository;
    private final ProductRepository productRepository;

    /**
     * Verilen ürünün altındaki talep konularını ada göre sıralı döner.
     *
     * @param productId hedef ürün ID
     * @param activeOnly {@code true} ise sadece aktif konular listelenir
     * @return konu listesi (boş olabilir)
     * @throws ResponseStatusException 404 — ürün bulunamazsa
     */
    @Transactional(readOnly = true)
    public List<TicketTopic> listByProduct(Long productId, boolean activeOnly) {
        ensureProductExists(productId);
        return activeOnly
                ? topicRepository.findByProductIdAndIsActiveTrueOrderByNameAsc(productId)
                : topicRepository.findByProductIdOrderByNameAsc(productId);
    }

    /**
     * Tek bir konu kaydını ID üzerinden getirir.
     *
     * @param id konu ID
     * @return ilgili {@link TicketTopic}
     * @throws ResponseStatusException 404 — konu bulunamazsa
     */
    @Transactional(readOnly = true)
    public TicketTopic getById(Long id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.topic.not.found"));
    }

    /**
     * Yeni bir talep konusu oluşturur.
     *
     * <p>Ad trim edilir, aynı ürünün altında case-insensitive duplicate kontrolü yapılır.
     *
     * @param productId konunun ait olacağı ürün ID
     * @param name konu adı; boş bırakılamaz
     * @param isActive aktif/pasif durumu; {@code null} ise varsayılan {@code true}
     * @return oluşturulan kayıt
     * @throws ResponseStatusException 404 ürün yoksa, 400 ad boşsa, 409 ad çakışırsa
     */
    @Transactional
    public TicketTopic create(Long productId, String name, Boolean isActive) {
        ensureProductExists(productId);
        String trimmed = name == null ? null : name.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.topic.name.empty");
        }
        topicRepository.findByProductIdAndNameIgnoreCase(productId, trimmed).ifPresent(t -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "error.topic.name.duplicate");
        });

        TicketTopic topic = TicketTopic.builder()
                .productId(productId)
                .name(trimmed)
                .isActive(isActive == null ? Boolean.TRUE : isActive)
                .build();
        TicketTopic saved = topicRepository.save(topic);
        log.info("Talep konusu oluşturuldu. Ürün: {}, Konu ID: {}, Ad: {}", productId, saved.getId(), saved.getName());
        return saved;
    }

    /**
     * Mevcut bir konu kaydını kısmi olarak günceller; yalnızca {@code null} olmayan
     * alanlar değiştirilir. Ad değişiyorsa case-insensitive duplicate kontrolü uygulanır.
     *
     * @param id güncellenecek konu ID
     * @param name yeni ad (opsiyonel)
     * @param isActive yeni aktif/pasif durumu (opsiyonel)
     * @return güncellenmiş kayıt
     * @throws ResponseStatusException 404 yoksa, 400 ad boşsa, 409 ad çakışırsa
     */
    @Transactional
    public TicketTopic update(Long id, String name, Boolean isActive) {
        TicketTopic existing = getById(id);
        if (name != null) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.topic.name.empty");
            }
            if (!trimmed.equalsIgnoreCase(existing.getName())) {
                topicRepository.findByProductIdAndNameIgnoreCase(existing.getProductId(), trimmed).ifPresent(t -> {
                    if (!t.getId().equals(id)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "error.topic.name.duplicate");
                    }
                });
            }
            existing.setName(trimmed);
        }
        if (isActive != null) {
            existing.setIsActive(isActive);
        }
        TicketTopic saved = topicRepository.save(existing);
        log.info("Talep konusu güncellendi. ID: {}", saved.getId());
        return saved;
    }

    /**
     * Konu kaydını siler. Konuya bağlı biletlerin {@code topicId} alanları
     * (referential integrity) DB constraint'ine göre davranır.
     *
     * @param id silinecek konu ID
     * @throws ResponseStatusException 404 — konu bulunamazsa
     */
    @Transactional
    public void delete(Long id) {
        TicketTopic existing = getById(id);
        topicRepository.delete(existing);
        log.info("Talep konusu silindi. ID: {}", id);
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.product.not.found");
        }
    }
}
