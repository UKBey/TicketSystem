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

@Log4j2
@Service
@RequiredArgsConstructor
public class TicketTopicService {

    private final TicketTopicRepository topicRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<TicketTopic> listByProduct(Long productId, boolean activeOnly) {
        ensureProductExists(productId);
        return activeOnly
                ? topicRepository.findByProductIdAndIsActiveTrueOrderByNameAsc(productId)
                : topicRepository.findByProductIdOrderByNameAsc(productId);
    }

    @Transactional(readOnly = true)
    public TicketTopic getById(Long id) {
        return topicRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.topic.not.found"));
    }

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
