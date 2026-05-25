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

/**
 * JPA repository for {@link Notification} — provides per-user paged listing,
 * mark-as-read operations and scheduler-driven retention/cleanup queries.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Pages the user's notifications newest-first ({@link Pageable}). */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(String userId);

    /** Marks all of the user's notifications as read (single UPDATE). */
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.userId = :userId")
    void markAllAsReadByUserId(@Param("userId") String userId);

    /** Deletes all notifications belonging to the user. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.userId = :userId")
    void deleteAllByUserId(@Param("userId") String userId);

    /** Only the owner may delete a given notification — the ownership check is in the query. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.id = :id AND n.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") String userId);

    /** Deletes notifications that are read and were created before the given cutoff (auto-cleanup). */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.isRead = true AND n.createdAt < :cutoff")
    int deleteReadBefore(@Param("cutoff") ZonedDateTime cutoff);

    /** Deletes notifications that are unread and were created before the given cutoff (auto-cleanup). */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.isRead = false AND n.createdAt < :cutoff")
    int deleteUnreadBefore(@Param("cutoff") ZonedDateTime cutoff);
}
