package com.ticketsystem.it_service_backend.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPreferenceTest {

    @Test
    void builderDefaults_allTogglesEnabled() {
        NotificationPreference p = NotificationPreference.builder().userId("u-1").build();

        assertThat(p.getUserId()).isEqualTo("u-1");
        assertThat(p.getEmailOnTicketCreated()).isTrue();
        assertThat(p.getEmailOnTicketAssigned()).isTrue();
        assertThat(p.getEmailOnStatusChanged()).isTrue();
        assertThat(p.getEmailOnCommentAdded()).isTrue();
        assertThat(p.getEmailOnSlaWarning()).isTrue();
        assertThat(p.getEmailOnSlaBreached()).isTrue();
        assertThat(p.getEmailOnTicketResolved()).isTrue();
        assertThat(p.getNotifyOnTicketCreated()).isTrue();
        assertThat(p.getNotifyOnTicketAssigned()).isTrue();
        assertThat(p.getNotifyOnStatusChanged()).isTrue();
        assertThat(p.getNotifyOnCommentAdded()).isTrue();
        assertThat(p.getNotifyOnSlaWarning()).isTrue();
        assertThat(p.getNotifyOnSlaBreached()).isTrue();
        assertThat(p.getNotifyOnTicketResolved()).isTrue();
    }

    @Test
    void setters_overrideDefaults() {
        NotificationPreference p = NotificationPreference.builder().userId("u-1").build();
        p.setEmailOnCommentAdded(false);
        p.setNotifyOnSlaBreached(false);

        assertThat(p.getEmailOnCommentAdded()).isFalse();
        assertThat(p.getNotifyOnSlaBreached()).isFalse();
    }

    @Test
    void onCreate_stampsCreatedAndUpdated() {
        NotificationPreference p = NotificationPreference.builder().userId("u-1").build();
        p.onCreate();

        assertThat(p.getCreatedAt()).isNotNull();
        assertThat(p.getUpdatedAt()).isNotNull();
    }

    @Test
    void onUpdate_refreshesUpdatedAt() {
        NotificationPreference p = NotificationPreference.builder().userId("u-1").build();
        p.onUpdate();

        assertThat(p.getUpdatedAt()).isNotNull();
    }
}
