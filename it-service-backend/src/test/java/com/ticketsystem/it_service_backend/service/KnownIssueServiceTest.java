package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.entity.KnownIssue;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.KnownIssueRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnownIssueServiceTest {

    @Mock private KnownIssueRepository knownIssueRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TicketTopicRepository topicRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private KnownIssueService service;

    private KnownIssue existing;

    @BeforeEach
    void setUp() {
        existing = KnownIssue.builder()
                .id(1L)
                .productId(10L)
                .topicId(20L)
                .title("VPN bağlantısı kopuyor")
                .content("Ağ ayarlarınızı kontrol edin")
                .isActive(true)
                .build();
    }

    private User userWithProduct(Long productId) {
        Product p = Product.builder().id(productId).name("Test").isActive(true).build();
        return User.builder()
                .id("agent-1")
                .email("a@example.com")
                .fullName("Agent One")
                .role("AGENT")
                .authorizedProducts(new java.util.ArrayList<>(List.of(p)))
                .build();
    }

    // ----------------------------------------------------------------
    // Read — listByProduct
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("listByProduct()")
    class ListByProduct {

        @Test
        @DisplayName("Aktif kayitlar yetkili kullaniciya doner")
        void authorized_activeOnly_returns() {
            when(productRepository.existsById(10L)).thenReturn(true);
            when(userRepository.findById("agent-1")).thenReturn(Optional.of(userWithProduct(10L)));
            when(knownIssueRepository.findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(existing));

            List<KnownIssue> result = service.listByProduct(10L, null, true, "agent-1", List.of("AGENT"));

            assertThat(result).containsExactly(existing);
        }

        @Test
        @DisplayName("topicId verilirse topic'e filtrelenmis kayitlar doner")
        void filteredByTopic_returnsScoped() {
            when(productRepository.existsById(10L)).thenReturn(true);
            when(userRepository.findById("agent-1")).thenReturn(Optional.of(userWithProduct(10L)));
            when(knownIssueRepository.findByProductIdAndTopicIdAndIsActiveTrueOrderByCreatedAtDesc(10L, 20L))
                    .thenReturn(List.of(existing));

            List<KnownIssue> result = service.listByProduct(10L, 20L, true, "agent-1", List.of("AGENT"));

            assertThat(result).containsExactly(existing);
            verify(knownIssueRepository, never())
                    .findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("ADMIN icin yetki sorgulanmaz, dogrudan listeler")
        void adminBypassesAuthorization() {
            when(productRepository.existsById(10L)).thenReturn(true);
            when(knownIssueRepository.findByProductIdOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(existing));

            List<KnownIssue> result = service.listByProduct(10L, null, false, "admin-1", List.of("ADMIN"));

            assertThat(result).containsExactly(existing);
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Yetkisiz kullanici 403 alir")
        void unauthorizedUser_throwsForbidden() {
            Product p = Product.builder().id(99L).name("Other").isActive(true).build();
            User user = User.builder().id("agent-1").role("AGENT")
                    .authorizedProducts(new java.util.ArrayList<>(List.of(p))).build();
            when(productRepository.existsById(10L)).thenReturn(true);
            when(userRepository.findById("agent-1")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> service.listByProduct(10L, null, true, "agent-1", List.of("AGENT")))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Olmayan urun icin 404")
        void unknownProduct_throwsNotFound() {
            when(productRepository.existsById(99L)).thenReturn(false);

            assertThatThrownBy(() -> service.listByProduct(99L, null, true, "agent-1", List.of("AGENT")))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ----------------------------------------------------------------
    // create
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("Gecerli istek kayit olusturur")
        void valid_persists() {
            when(productRepository.existsById(10L)).thenReturn(true);
            when(topicRepository.findById(20L)).thenReturn(Optional.of(
                    TicketTopic.builder().id(20L).productId(10L).name("Şifre").isActive(true).build()));
            when(knownIssueRepository.save(any(KnownIssue.class))).thenAnswer(i -> i.getArgument(0));

            KnownIssue saved = service.create(10L, 20L, " Başlık ", " İçerik ", null, "admin-1");

            assertThat(saved.getTitle()).isEqualTo("Başlık");
            assertThat(saved.getContent()).isEqualTo("İçerik");
            assertThat(saved.getIsActive()).isTrue();
            assertThat(saved.getCreatedBy()).isEqualTo("admin-1");
        }

        @Test
        @DisplayName("Bos baslik 400 firlatir")
        void blankTitle_throwsBadRequest() {
            when(productRepository.existsById(10L)).thenReturn(true);
            assertThatThrownBy(() -> service.create(10L, null, "   ", "İçerik", true, "admin-1"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Topic baska bir urune aitse 400 firlatir")
        void topicMismatch_throwsBadRequest() {
            when(productRepository.existsById(10L)).thenReturn(true);
            when(topicRepository.findById(20L)).thenReturn(Optional.of(
                    TicketTopic.builder().id(20L).productId(999L).name("X").isActive(true).build()));

            assertThatThrownBy(() -> service.create(10L, 20L, "T", "C", true, "admin-1"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    // ----------------------------------------------------------------
    // update / delete
    // ----------------------------------------------------------------

    @Test
    @DisplayName("update — title ve content guncellenir, diger alanlar korunur")
    void update_partial() {
        when(knownIssueRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(knownIssueRepository.save(any(KnownIssue.class))).thenAnswer(i -> i.getArgument(0));

        KnownIssue saved = service.update(1L, null, "Yeni Başlık", "Yeni İçerik", null);

        assertThat(saved.getTitle()).isEqualTo("Yeni Başlık");
        assertThat(saved.getContent()).isEqualTo("Yeni İçerik");
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("delete — kayit silinir")
    void delete_existing_deletes() {
        when(knownIssueRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(knownIssueRepository).delete(existing);
    }

    @Test
    @DisplayName("delete — olmayan kayit 404 firlatir")
    void delete_missing_throwsNotFound() {
        when(knownIssueRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    // =====================================================================
    // update() — alan bazlı validation dalları
    // =====================================================================

    private KnownIssue existingIssue() {
        return KnownIssue.builder()
                .id(5L).productId(10L).topicId(50L).title("Eski").content("Eski içerik").isActive(true).build();
    }

    @Test
    void update_blankTitle_throwsBadRequest() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        assertThatThrownBy(() -> service.update(5L, null, "   ", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_titleTooLong_throwsBadRequest() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        assertThatThrownBy(() -> service.update(5L, null, "t".repeat(256), null, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_blankContent_throwsBadRequest() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        assertThatThrownBy(() -> service.update(5L, null, null, "  ", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_contentTooLong_throwsBadRequest() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        assertThatThrownBy(() -> service.update(5L, null, null, "c".repeat(10001), null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_validFields_trimsAndSaves() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        when(knownIssueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnownIssue result = service.update(5L, null, "  Yeni Başlık  ", "  Yeni içerik  ", false);

        assertThat(result.getTitle()).isEqualTo("Yeni Başlık");
        assertThat(result.getContent()).isEqualTo("Yeni içerik");
        assertThat(result.getIsActive()).isFalse();
    }

    @Test
    void update_allNull_noFieldChangesButSaves() {
        KnownIssue existing = existingIssue();
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(knownIssueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnownIssue result = service.update(5L, null, null, null, null);

        assertThat(result.getTitle()).isEqualTo("Eski");
        assertThat(result.getContent()).isEqualTo("Eski içerik");
        assertThat(result.getIsActive()).isTrue();
    }
}
