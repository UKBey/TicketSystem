package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.AccessRequestDTO;
import com.ticketsystem.it_service_backend.entity.AccessRequest;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.AccessRequestRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Log4j2
@Service
@RequiredArgsConstructor
public class AccessRequestService {

    private final AccessRequestRepository accessRequestRepository;
    private final UserRepository userRepository;

    /**
     * Kullanıcı adına yeni bir erişim talebi oluşturur.
     * Kullanıcının zaten bekleyen bir talebi varsa yeni talep yine de oluşturulur
     * (birden fazla talep gönderilebilir).
     */
    @Transactional
    public AccessRequestDTO createRequest(String userId, String message) {
        log.info("Erişim talebi oluşturuluyor. Kullanıcı: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Kullanıcı bulunamadı: " + userId));

        AccessRequest request = AccessRequest.builder()
                .user(user)
                .message(message.trim())
                .build();

        AccessRequest saved = accessRequestRepository.save(request);
        log.info("Erişim talebi oluşturuldu. ID: {}, Kullanıcı: {}", saved.getId(), userId);
        return AccessRequestDTO.fromEntity(saved);
    }

    /**
     * Tüm erişim taleplerini en yeniden eskiye döner (admin için).
     */
    @Transactional(readOnly = true)
    public List<AccessRequestDTO> getAllRequests() {
        return accessRequestRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AccessRequestDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Belirli bir kullanıcının taleplerini döner.
     */
    @Transactional(readOnly = true)
    public List<AccessRequestDTO> getRequestsByUser(String userId) {
        return accessRequestRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(AccessRequestDTO::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * Talebi siler.
     */
    @Transactional
    public void deleteRequest(Long requestId) {
        log.info("Erişim talebi siliniyor. ID: {}", requestId);
        if (!accessRequestRepository.existsById(requestId)) {
            throw new EntityNotFoundException("Talep bulunamadı: " + requestId);
        }
        accessRequestRepository.deleteById(requestId);
        log.info("Erişim talebi silindi. ID: {}", requestId);
    }
}
