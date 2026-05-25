package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.KnownIssue;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.KnownIssueRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Sıkça karşılaşılan sorun (known issue) yönetimi.
 *
 * <p>Görüntüleme: kullanıcı ürüne yetkili olmalı; admin/manager her ürünü görür.
 * Yönetim (create/update/delete): yalnızca AGENT_ADMIN ve MANAGER. Bu kural
 * controller seviyesinde {@code @PreAuthorize} ile uygulanır.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class KnownIssueService {

    private final KnownIssueRepository knownIssueRepository;
    private final ProductRepository productRepository;
    private final TicketTopicRepository topicRepository;
    private final UserRepository userRepository;

    // --------------------------------------------------------------------
    // Read
    // --------------------------------------------------------------------

    /**
     * Bir ürüne (opsiyonel olarak belirli bir konuya) ait known issue kayıtlarını
     * en yeni → en eski sırada döner. Müşteri/agent kullanıcısı için ürün yetkisi
     * doğrulanır; admin/manager bypass eder.
     *
     * @param productId hedef ürün ID
     * @param topicId opsiyonel konu filtresi
     * @param activeOnly {@code true} ise sadece aktif kayıtlar
     * @param userId yetki doğrulanacak kullanıcı (admin/manager için yoksayılır)
     * @param roles kullanıcının rol listesi
     * @return known issue listesi (boş olabilir)
     * @throws ResponseStatusException 404 ürün yoksa, 403 kullanıcı yetkili değilse
     */
    @Transactional(readOnly = true)
    public List<KnownIssue> listByProduct(Long productId, Long topicId, boolean activeOnly,
                                          String userId, List<String> roles) {
        ensureProductExists(productId);
        ensureProductAccess(productId, userId, roles);

        if (topicId != null) {
            return activeOnly
                    ? knownIssueRepository.findByProductIdAndTopicIdAndIsActiveTrueOrderByCreatedAtDesc(productId, topicId)
                    : knownIssueRepository.findByProductIdAndTopicIdOrderByCreatedAtDesc(productId, topicId);
        }
        return activeOnly
                ? knownIssueRepository.findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(productId)
                : knownIssueRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    /**
     * Tek bir known issue kaydını getirir; çağıran kullanıcının ürün yetkisi denetlenir.
     *
     * @param id kayıt ID
     * @param userId yetki doğrulanacak kullanıcı
     * @param roles kullanıcının rolleri
     * @return ilgili {@link KnownIssue}
     * @throws ResponseStatusException 404 yoksa, 403 ürün yetkisi olmazsa
     */
    @Transactional(readOnly = true)
    public KnownIssue getById(Long id, String userId, List<String> roles) {
        KnownIssue issue = findOrThrow(id);
        ensureProductAccess(issue.getProductId(), userId, roles);
        return issue;
    }

    // --------------------------------------------------------------------
    // Write
    // --------------------------------------------------------------------

    /**
     * Yeni bir known issue kaydı oluşturur.
     *
     * <p>Topic verilirse ürünle eşleşmesi zorunludur. Başlık ve içerik trim edilir
     * ve uzunluk limitleri (başlık 255, içerik 10.000) uygulanır.
     *
     * @param productId ait olduğu ürün ID
     * @param topicId opsiyonel konu ID (varsa ürünün altında olmalı)
     * @param title başlık (boş olamaz, max 255)
     * @param content içerik (boş olamaz, max 10.000)
     * @param isActive aktif/pasif (null ise varsayılan {@code true})
     * @param createdBy oluşturan kullanıcının ID'si (audit için)
     * @return kaydedilen kayıt
     * @throws ResponseStatusException 404 ürün/konu yoksa, 400 doğrulama hataları
     */
    @Transactional
    public KnownIssue create(Long productId, Long topicId, String title, String content,
                             Boolean isActive, String createdBy) {
        ensureProductExists(productId);
        validateTopicBelongsToProduct(productId, topicId);
        validateTitle(title);
        validateContent(content);

        KnownIssue issue = KnownIssue.builder()
                .productId(productId)
                .topicId(topicId)
                .title(title.trim())
                .content(content.trim())
                .isActive(isActive == null ? Boolean.TRUE : isActive)
                .createdBy(createdBy)
                .build();
        KnownIssue saved = knownIssueRepository.save(issue);
        log.info("Sıkça karşılaşılan sorun oluşturuldu. ID: {}, Ürün: {}", saved.getId(), productId);
        return saved;
    }

    /**
     * Kısmi güncelleme; yalnızca {@code null} olmayan alanlar değiştirilir.
     * Topic değişiyorsa aynı ürünün altında olmak zorundadır.
     *
     * @param id güncellenecek kayıt ID
     * @param topicId yeni konu (opsiyonel)
     * @param title yeni başlık (opsiyonel)
     * @param content yeni içerik (opsiyonel)
     * @param isActive yeni aktif durumu (opsiyonel)
     * @return güncellenmiş kayıt
     * @throws ResponseStatusException 404 kayıt/konu yoksa, 400 doğrulama hataları
     */
    @Transactional
    public KnownIssue update(Long id, Long topicId, String title, String content, Boolean isActive) {
        KnownIssue existing = findOrThrow(id);
        if (topicId != null) {
            validateTopicBelongsToProduct(existing.getProductId(), topicId);
            existing.setTopicId(topicId);
        }
        if (title != null) {
            validateTitle(title);
            existing.setTitle(title.trim());
        }
        if (content != null) {
            validateContent(content);
            existing.setContent(content.trim());
        }
        if (isActive != null) {
            existing.setIsActive(isActive);
        }
        KnownIssue saved = knownIssueRepository.save(existing);
        log.info("Sıkça karşılaşılan sorun güncellendi. ID: {}", saved.getId());
        return saved;
    }

    /**
     * Known issue kaydını siler.
     *
     * @param id silinecek kayıt ID
     * @throws ResponseStatusException 404 — kayıt bulunamazsa
     */
    @Transactional
    public void delete(Long id) {
        KnownIssue existing = findOrThrow(id);
        knownIssueRepository.delete(existing);
        log.info("Sıkça karşılaşılan sorun silindi. ID: {}", id);
    }

    // --------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------

    private KnownIssue findOrThrow(Long id) {
        return knownIssueRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.known-issue.not.found"));
    }

    private void ensureProductExists(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.product.not.found");
        }
    }

    /**
     * Kullanici icin urun erisim kontrolu. Admin/manager bypass eder; diger roller
     * urunu authorizedProducts listesinde tasimak zorundadir.
     */
    private void ensureProductAccess(Long productId, String userId, List<String> roles) {
        if (roles != null && (roles.contains("AGENT_ADMIN") || roles.contains("MANAGER"))) return;
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.forbidden");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "error.user.not.found"));
        boolean authorized = user.getAuthorizedProducts().stream()
                .anyMatch(p -> p.getId().equals(productId));
        if (!authorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "error.product.access.forbidden");
        }
    }

    private void validateTopicBelongsToProduct(Long productId, Long topicId) {
        if (topicId == null) return;
        topicRepository.findById(topicId).ifPresentOrElse(
                topic -> {
                    if (!topic.getProductId().equals(productId)) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.topic.mismatch");
                    }
                },
                () -> { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "error.topic.not.found"); }
        );
    }

    private void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.title.empty");
        }
        if (title.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.title.too-long");
        }
    }

    private void validateContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.content.empty");
        }
        if (content.trim().length() > 10000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "error.known-issue.content.too-long");
        }
    }
}
