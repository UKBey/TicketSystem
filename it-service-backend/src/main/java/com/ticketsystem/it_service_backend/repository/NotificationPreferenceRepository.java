package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link NotificationPreference} için JPA repository — kullanıcının bildirim tercihlerini
 * Keycloak UUID'siyle (PK) okur.
 */
@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

    Optional<NotificationPreference> findByUserId(String userId);
}
