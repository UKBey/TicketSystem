package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.AccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, Long> {

    /** Tüm talepleri en yeniden eskiye sıralar */
    List<AccessRequest> findAllByOrderByCreatedAtDesc();

    /** Belirli bir kullanıcının taleplerini döner */
    List<AccessRequest> findByUserIdOrderByCreatedAtDesc(String userId);

    /** Kullanıcının bekleyen talebi var mı? */
    boolean existsByUserId(String userId);
}
