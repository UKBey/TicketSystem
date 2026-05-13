package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.SlaPolicy;
import com.ticketsystem.it_service_backend.repository.SlaPolicyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

import static com.ticketsystem.it_service_backend.config.CacheConfig.SLA_POLICIES;

@Log4j2
@Service
@RequiredArgsConstructor
public class SlaPolicyService {

    private final SlaPolicyJpaRepository slaPolicyJpaRepository;

    /** Tüm SLA politikalarını döner (öncelik sırasıyla). */
    @Cacheable(SLA_POLICIES)
    public List<SlaPolicy> getAllPolicies() {
        return slaPolicyJpaRepository.findAll()
                .stream()
                .sorted((a, b) -> priorityOrder(a.getPriority()) - priorityOrder(b.getPriority()))
                .toList();
    }

    /**
     * Verilen öncelik için SLA süresini milisaniye cinsinden döner.
     * Cache'den okur; DB'de kayıt yoksa güvenli varsayılan değer kullanılır.
     */
    @Cacheable(SLA_POLICIES)
    public long getSlaDurationMs(String priority) {
        if (priority == null) return defaultMs("MEDIUM");
        return slaPolicyJpaRepository.findByPriority(priority.toUpperCase())
                .filter(p -> p.getTargetResolutionHours() > 0)
                .map(p -> (long) p.getTargetResolutionHours() * 3_600_000L)
                .orElseGet(() -> defaultMs(priority.toUpperCase()));
    }

    /**
     * Verilen öncelik için uyarı eşiğini saat cinsinden döner.
     * DB'de kayıt yoksa varsayılan 2 saat kullanılır.
     */
    @Cacheable(SLA_POLICIES)
    public int getWarningThresholdHours(String priority) {
        if (priority == null) return 2;
        return slaPolicyJpaRepository.findByPriority(priority.toUpperCase())
                .map(SlaPolicy::getWarningThresholdHours)
                .orElse(2);
    }

    /** SLA politikasını günceller ve cache'i temizler. */
    @Transactional
    @CacheEvict(value = SLA_POLICIES, allEntries = true)
    public SlaPolicy updatePolicy(Long id, int targetResolutionHours, int warningThresholdHours) {
        SlaPolicy policy = slaPolicyJpaRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("SLA politikası bulunamadı: id=" + id));

        policy.setTargetResolutionHours(targetResolutionHours);
        policy.setWarningThresholdHours(warningThresholdHours);
        SlaPolicy saved = slaPolicyJpaRepository.save(policy);

        log.info("SLA politikası güncellendi: priority={}, targetHours={}, warningHours={}",
                saved.getPriority(), saved.getTargetResolutionHours(), saved.getWarningThresholdHours());
        return saved;
    }

    // -------------------------------------------------------------------------
    // Yardımcı metotlar
    // -------------------------------------------------------------------------

    private long defaultMs(String priority) {
        return switch (priority) {
            case "CRITICAL" ->  1L * 3_600_000L;
            case "HIGH"     ->  4L * 3_600_000L;
            case "MEDIUM"   -> 12L * 3_600_000L;
            case "LOW"      -> 24L * 3_600_000L;
            default         -> 12L * 3_600_000L;
        };
    }

    private int priorityOrder(String priority) {
        return switch (priority) {
            case "CRITICAL" -> 1;
            case "HIGH"     -> 2;
            case "MEDIUM"   -> 3;
            case "LOW"      -> 4;
            default         -> 5;
        };
    }
}
