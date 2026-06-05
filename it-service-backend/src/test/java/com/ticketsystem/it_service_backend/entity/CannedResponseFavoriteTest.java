package com.ticketsystem.it_service_backend.entity;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CannedResponseFavoriteTest {

    @Test
    void entity_builderAndAccessors() {
        OffsetDateTime now = OffsetDateTime.now();
        CannedResponseFavorite fav = CannedResponseFavorite.builder()
                .userId("u-1").cannedResponseId(7L).createdAt(now).build();

        assertThat(fav.getUserId()).isEqualTo("u-1");
        assertThat(fav.getCannedResponseId()).isEqualTo(7L);
        assertThat(fav.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void favoriteId_equalAndHashCode_whenSameComponents() {
        CannedResponseFavorite.FavoriteId a = new CannedResponseFavorite.FavoriteId("u-1", 7L);
        CannedResponseFavorite.FavoriteId b = new CannedResponseFavorite.FavoriteId("u-1", 7L);

        assertThat(a).isEqualTo(b);
        assertThat(a).hasSameHashCodeAs(b);
        assertThat(a).isEqualTo(a); // identity branch
    }

    @Test
    void favoriteId_notEqual_whenComponentsDiffer() {
        CannedResponseFavorite.FavoriteId a = new CannedResponseFavorite.FavoriteId("u-1", 7L);

        assertThat(a).isNotEqualTo(new CannedResponseFavorite.FavoriteId("u-2", 7L));
        assertThat(a).isNotEqualTo(new CannedResponseFavorite.FavoriteId("u-1", 9L));
        assertThat(a).isNotEqualTo("not-an-id"); // wrong type branch
        assertThat(a).isNotEqualTo(null);
    }

    @Test
    void favoriteId_settersAndNoArgsCtor() {
        CannedResponseFavorite.FavoriteId id = new CannedResponseFavorite.FavoriteId();
        id.setUserId("u-9");
        id.setCannedResponseId(3L);

        assertThat(id.getUserId()).isEqualTo("u-9");
        assertThat(id.getCannedResponseId()).isEqualTo(3L);
    }
}
