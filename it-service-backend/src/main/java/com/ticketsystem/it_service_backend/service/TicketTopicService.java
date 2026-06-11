package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import com.ticketsystem.it_service_backend.util.LocalizedText;
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
 * <p>Topic names are bilingual (tr/en): at least one variant must be non-blank and
 * each variant is unique within its product (case-insensitive). Listing is open,
 * so authorization is enforced at the controller level; management
 * (create/update/delete) is restricted to LEAD_AGENT / ADMIN roles.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class TicketTopicService {

    private final TicketTopicRepository topicRepository;
    private final ProductRepository productRepository;

    /**
     * Returns the ticket topics under the given product in stable (id) order; the
     * client sorts by the display name of its active language.
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
                ? topicRepository.findByProductIdAndIsActiveTrueOrderByIdAsc(productId)
                : topicRepository.findByProductIdOrderByIdAsc(productId);
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
     * <p>Names are trimmed; at least one language variant must be non-blank. Each
     * provided variant is checked (case-insensitively) for duplicates within the
     * same product.
     *
     * @param productId ID of the product the topic belongs to
     * @param nameTr Turkish name (optional when {@code nameEn} is present)
     * @param nameEn English name (optional when {@code nameTr} is present)
     * @param isActive active/inactive flag; defaults to {@code true} if {@code null}
     * @return the created record
     * @throws ResponseStatusException 404 if product is missing, 400 if both names are blank,
     *         409 if either name conflicts
     */
    @Transactional
    public TicketTopic create(Long productId, String nameTr, String nameEn, Boolean isActive) {
        ensureProductExists(productId);
        String tr = normalize(nameTr);
        String en = normalize(nameEn);
        if (tr == null && en == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.topic.name.empty");
        }
        ensureNamesAvailable(productId, tr, en, null);

        TicketTopic topic = TicketTopic.builder()
                .productId(productId)
                .nameTr(tr)
                .nameEn(en)
                .isActive(isActive == null ? Boolean.TRUE : isActive)
                .build();
        TicketTopic saved = topicRepository.save(topic);
        log.info("Talep konusu oluşturuldu. Ürün: {}, Konu ID: {}, Ad: {}", productId, saved.getId(),
                LocalizedText.label(saved.getNameTr(), saved.getNameEn()));
        return saved;
    }

    /**
     * Partially updates an existing topic record. When at least one name field is
     * present the pair is treated as the new full name set (a blank variant clears
     * that language); when both are {@code null} the names are left untouched, so
     * isActive-only updates remain possible.
     *
     * @param id topic ID to update
     * @param nameTr new Turkish name (optional)
     * @param nameEn new English name (optional)
     * @param isActive new active/inactive flag (optional)
     * @return the updated record
     * @throws ResponseStatusException 404 if not found, 400 if both names end up blank,
     *         409 if either name conflicts
     */
    @Transactional
    public TicketTopic update(Long id, String nameTr, String nameEn, Boolean isActive) {
        TicketTopic existing = getById(id);
        if (nameTr != null || nameEn != null) {
            String tr = normalize(nameTr);
            String en = normalize(nameEn);
            if (tr == null && en == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.topic.name.empty");
            }
            ensureNamesAvailable(existing.getProductId(), tr, en, id);
            existing.setNameTr(tr);
            existing.setNameEn(en);
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

    /** Trims and converts blank to {@code null} so "cleared" and "absent" collapse to one state. */
    private String normalize(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Case-insensitive per-language duplicate check within the product;
     * {@code selfId} excludes the record being updated.
     */
    private void ensureNamesAvailable(Long productId, String tr, String en, Long selfId) {
        if (tr != null) {
            topicRepository.findByProductIdAndNameTrIgnoreCase(productId, tr).ifPresent(t -> {
                if (!t.getId().equals(selfId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "error.topic.name.duplicate");
                }
            });
        }
        if (en != null) {
            topicRepository.findByProductIdAndNameEnIgnoreCase(productId, en).ifPresent(t -> {
                if (!t.getId().equals(selfId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "error.topic.name.duplicate");
                }
            });
        }
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.product.not.found");
        }
    }
}
