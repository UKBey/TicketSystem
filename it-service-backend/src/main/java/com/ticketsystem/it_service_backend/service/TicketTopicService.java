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
 * CRUD management for ticket topics belonging to a product.
 *
 * <p>Topic names are unique within a product (case-insensitive). Listing is open,
 * so authorization is enforced at the controller level; management
 * (create/update/delete) is restricted to ADMIN / MANAGER roles.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketTopicService {

    private final TicketTopicRepository topicRepository;
    private final ProductRepository productRepository;

    /**
     * Returns the ticket topics under the given product, sorted by name.
     *
     * @param productId target product ID
     * @param activeOnly when {@code true}, lists only active topics
     * @return list of topics (may be empty)
     * @throws ResponseStatusException 404 if the product is not found
     */
    @Transactional(readOnly = true)
    public List<TicketTopic> listByProduct(Long productId, boolean activeOnly) {
        ensureProductExists(productId);
        return activeOnly
                ? topicRepository.findByProductIdAndIsActiveTrueOrderByNameAsc(productId)
                : topicRepository.findByProductIdOrderByNameAsc(productId);
    }

    /**
     * Returns a single topic record by ID.
     *
     * @param id topic ID
     * @return the matching {@link TicketTopic}
     * @throws ResponseStatusException 404 if the topic is not found
     */
    @Transactional(readOnly = true)
    public TicketTopic getById(Long id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.topic.not.found"));
    }

    /**
     * Creates a new ticket topic.
     *
     * <p>The name is trimmed and a case-insensitive duplicate check is performed
     * within the same product.
     *
     * @param productId ID of the product the topic belongs to
     * @param name topic name; cannot be blank
     * @param isActive active/inactive flag; defaults to {@code true} if {@code null}
     * @return the created record
     * @throws ResponseStatusException 404 if product is missing, 400 if name is blank,
     *         409 if the name conflicts
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
     * Partially updates an existing topic record; only non-{@code null} fields are
     * modified. When the name changes, a case-insensitive duplicate check is applied.
     *
     * @param id topic ID to update
     * @param name new name (optional)
     * @param isActive new active/inactive flag (optional)
     * @return the updated record
     * @throws ResponseStatusException 404 if not found, 400 if name is blank,
     *         409 if the name conflicts
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
     * Deletes the topic record. The {@code topicId} field of any tickets referencing
     * the topic is handled according to the DB referential integrity constraint.
     *
     * @param id topic ID to delete
     * @throws ResponseStatusException 404 if the topic is not found
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
