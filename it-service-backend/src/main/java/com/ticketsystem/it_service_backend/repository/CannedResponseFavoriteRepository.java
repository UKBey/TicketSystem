package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.CannedResponseFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * JPA repository for {@link CannedResponseFavorite}. Used to mark which templates the
 * current user has starred and to toggle favorites.
 */
public interface CannedResponseFavoriteRepository
        extends JpaRepository<CannedResponseFavorite, CannedResponseFavorite.FavoriteId> {

    boolean existsByUserIdAndCannedResponseId(String userId, Long cannedResponseId);

    void deleteByUserIdAndCannedResponseId(String userId, Long cannedResponseId);

    /** IDs of the templates the given user has favorited — used to flag DTOs in a list. */
    @Query("SELECT f.cannedResponseId FROM CannedResponseFavorite f WHERE f.userId = :userId")
    List<Long> findFavoriteIdsByUser(@Param("userId") String userId);
}
