package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(String userId);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId")
    void markAllAsReadByUserId(@Param("userId") String userId);

    /** Kullanıcının tüm bildirimlerini siler. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);

    /** Belirli bir bildirimi yalnızca sahibi silebilir — sahiplik kontrolü sorgu içinde. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);

    /** Okunmuş ve belirtilen tarihten önce oluşturulmuş bildirimleri siler (otomatik temizlik). */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND n.createdAt < :cutoff")
    int deleteReadBefore(@Param("cutoff") ZonedDateTime cutoff);

    /** Okunmamış ve belirtilen tarihten önce oluşturulmuş bildirimleri siler (otomatik temizlik). */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.isRead = false AND n.createdAt < :cutoff")
    int deleteUnreadBefore(@Param("cutoff") ZonedDateTime cutoff);
}
