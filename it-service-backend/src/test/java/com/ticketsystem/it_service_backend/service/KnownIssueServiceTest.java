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
                .titleTr("VPN bağlantısı kopuyor")
                .titleEn("VPN connection drops")
                .contentTr("Ağ ayarlarınızı kontrol edin")
                .contentEn("Check your network settings")
                .isActive(true)
                .build();
    }

    private User userWithProduct(Long productId) {
        Product p = Product.builder().id(productId).nameEn("Test").isActive(true).build();
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
            Product p = Product.builder().id(99L).nameEn("Other").isActive(true).build();
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
                    TicketTopic.builder().id(20L).productId(10L).nameTr("Şifre").isActive(true).build()));
            when(knownIssueRepository.save(any(KnownIssue.class))).thenAnswer(i -> i.getArgument(0));

            KnownIssue saved = service.create(10L, 20L, " Başlık ", null, " İçerik ", null, null, "admin-1");

            assertThat(saved.getTitleTr()).isEqualTo("Başlık");
            assertThat(saved.getTitleEn()).isNull();
            assertThat(saved.getContentTr()).isEqualTo("İçerik");
            assertThat(saved.getIsActive()).isTrue();
            assertThat(saved.getCreatedBy()).isEqualTo("admin-1");
        }

        @Test
        @DisplayName("Hicbir dilde baslik yoksa 400 firlatir")
        void blankTitle_throwsBadRequest() {
            when(productRepository.existsById(10L)).thenReturn(true);
            assertThatThrownBy(() -> service.create(10L, null, "   ", "  ", "İçerik", null, true, "admin-1"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Topic baska bir urune aitse 400 firlatir")
        void topicMismatch_throwsBadRequest() {
            when(productRepository.existsById(10L)).thenReturn(true);
            when(topicRepository.findById(20L)).thenReturn(Optional.of(
                    TicketTopic.builder().id(20L).productId(999L).nameTr("X").isActive(true).build()));

            assertThatThrownBy(() -> service.create(10L, 20L, "T", null, "C", null, true, "admin-1"))
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

        KnownIssue saved = service.update(1L, null, "Yeni Başlık", null, "Yeni İçerik", null, null);

        assertThat(saved.getTitleTr()).isEqualTo("Yeni Başlık");
        assertThat(saved.getContentTr()).isEqualTo("Yeni İçerik");
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
                .id(5L).productId(10L).topicId(50L)
                .titleTr("Eski").titleEn("Old")
                .contentTr("Eski içerik").contentEn("Old content").isActive(true).build();
    }

    @Test
    void update_blankTitle_throwsBadRequest() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        assertThatThrownBy(() -> service.update(5L, null, "   ", "  ", null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void update_titleTooLong_throwsBadRequest() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        assertThatThrownBy(() -> service.update(5L, null, "t".repeat(256), null, null, null, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_blankContent_throwsBadRequest() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        assertThatThrownBy(() -> service.update(5L, null, null, null, "  ", "  ", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_contentTooLong_throwsBadRequest() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        assertThatThrownBy(() -> service.update(5L, null, null, null, "c".repeat(10001), null, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void update_validFields_trimsAndSaves() {
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(existingIssue()));
        when(knownIssueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnownIssue result = service.update(5L, null, "  Yeni Başlık  ", null, "  Yeni içerik  ", null, false);

        assertThat(result.getTitleTr()).isEqualTo("Yeni Başlık");
        assertThat(result.getContentTr()).isEqualTo("Yeni içerik");
        assertThat(result.getIsActive()).isFalse();
    }

    @Test
    void update_allNull_noFieldChangesButSaves() {
        KnownIssue issue = existingIssue();
        when(knownIssueRepository.findById(5L)).thenReturn(Optional.of(issue));
        when(knownIssueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KnownIssue result = service.update(5L, null, null, null, null, null, null);

        assertThat(result.getTitleTr()).isEqualTo("Eski");
        assertThat(result.getContentTr()).isEqualTo("Eski içerik");
        assertThat(result.getIsActive()).isTrue();
    }

    // ----------------------------------------------------------------
    // getActiveForTicket — internal/LLM bundle helper
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getActiveForTicket()")
    class GetActiveForTicket {

        @Test
        @DisplayName("Ürün geneli (topic'siz) + bilet topic'ine ait kayıtlar döner; başka topic elenir")
        void includesTopicAndProductWide_excludesOtherTopic() {
            KnownIssue topicIssue = KnownIssue.builder()
                    .id(1L).productId(10L).topicId(5L).titleEn("Auth token expires")
                    .contentEn("Re-issue").isActive(true).build();
            KnownIssue productWide = KnownIssue.builder()
                    .id(2L).productId(10L).topicId(null).titleEn("CRM slow")
                    .contentEn("Clear cache").isActive(true).build();
            KnownIssue otherTopic = KnownIssue.builder()
                    .id(3L).productId(10L).topicId(999L).titleEn("Unrelated")
                    .contentEn("n/a").isActive(true).build();
            when(knownIssueRepository.findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(10L))
                    .thenReturn(List.of(topicIssue, productWide, otherTopic));

            List<com.ticketsystem.it_service_backend.dto.KnownIssueDTO> result =
                    service.getActiveForTicket(10L, 5L);

            assertThat(result).extracting(com.ticketsystem.it_service_backend.dto.KnownIssueDTO::getId)
                    .containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("productId null ise boş liste döner ve repository'ye gidilmez")
        void nullProductId_returnsEmpty() {
            List<com.ticketsystem.it_service_backend.dto.KnownIssueDTO> result =
                    service.getActiveForTicket(null, 5L);

            assertThat(result).isEmpty();
            verify(knownIssueRepository, never()).findByProductIdAndIsActiveTrueOrderByCreatedAtDesc(any());
        }
    }
}
