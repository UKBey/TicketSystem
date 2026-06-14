package com.ticketsystem.it_service_backend.service;

import com.ticketsystem.it_service_backend.dto.CannedResponseDTO;
import com.ticketsystem.it_service_backend.entity.CannedResponse;
import com.ticketsystem.it_service_backend.entity.CannedResponseFavorite;
import com.ticketsystem.it_service_backend.entity.CannedResponseScope;
import com.ticketsystem.it_service_backend.entity.CannedResponseVisibility;
import com.ticketsystem.it_service_backend.repository.CannedResponseFavoriteRepository;
import com.ticketsystem.it_service_backend.repository.CannedResponseRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CannedResponseServiceTest {

    @Mock private CannedResponseRepository repository;
    @Mock private CannedResponseFavoriteRepository favoriteRepository;
    @Mock private ProductRepository productRepository;
    @InjectMocks private CannedResponseService service;

    private static final List<String> AGENT = List.of("AGENT");
    private static final List<String> ADMIN = List.of("ADMIN");

    private CannedResponse personalOf(String owner) {
        return CannedResponse.builder()
                .id(1L).title("Greeting").shortcut("hi")
                .contentTr("Merhaba").contentEn("Hello")
                .scope(CannedResponseScope.PERSONAL).ownerAgentId(owner)
                .visibility(CannedResponseVisibility.BOTH).build();
    }

    private CannedResponse shared(Long productId) {
        return CannedResponse.builder()
                .id(2L).title("VPN steps").shortcut("vpn")
                .contentTr("VPN adımları").contentEn("VPN steps")
                .scope(CannedResponseScope.SHARED).ownerAgentId("admin-1")
                .productId(productId).visibility(CannedResponseVisibility.EXTERNAL).build();
    }

    private CannedResponseDTO dto(String scope, String tr, String en) {
        return CannedResponseDTO.builder()
                .title("Title").scope(scope).contentTr(tr).contentEn(en).build();
    }

    // ----------------------------------------------------------------
    // listVisible
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("listVisible()")
    class ListVisible {

        @BeforeEach
        void noFavorites() {
            when(favoriteRepository.findFavoriteIdsByUser("agent-1")).thenReturn(List.of());
        }

        @Test
        @DisplayName("productId verilince product-scoped sorgu kullanılır")
        void withProduct_usesProductQuery() {
            when(repository.findVisibleForProduct("agent-1", 10L)).thenReturn(List.of(personalOf("agent-1"), shared(10L)));

            List<CannedResponseDTO> result = service.listVisible("agent-1", 10L, null, null, null);

            assertThat(result).hasSize(2);
            verify(repository, never()).findVisibleToUser(any());
        }

        @Test
        @DisplayName("productId yoksa tüm görünür şablonlar (yönetim) sorgusu kullanılır")
        void withoutProduct_usesAllQuery() {
            when(repository.findVisibleToUser("agent-1")).thenReturn(List.of(personalOf("agent-1")));

            List<CannedResponseDTO> result = service.listVisible("agent-1", null, null, null, null);

            assertThat(result).hasSize(1);
            verify(repository, never()).findVisibleForProduct(any(), any());
        }

        @Test
        @DisplayName("scope filtresi yalnız ilgili kapsamı döner")
        void scopeFilter_applied() {
            when(repository.findVisibleToUser("agent-1")).thenReturn(List.of(personalOf("agent-1"), shared(null)));

            List<CannedResponseDTO> result = service.listVisible("agent-1", null, "SHARED", null, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getScope()).isEqualTo("SHARED");
        }

        @Test
        @DisplayName("visibility=INTERNAL filtresi EXTERNAL şablonu eler ama BOTH'u tutar")
        void visibilityFilter_keepsBoth_dropsExternal() {
            when(repository.findVisibleToUser("agent-1")).thenReturn(List.of(personalOf("agent-1"), shared(null)));

            List<CannedResponseDTO> result = service.listVisible("agent-1", null, null, "INTERNAL", null);

            // personal is BOTH (kept), shared is EXTERNAL (dropped)
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getVisibility()).isEqualTo("BOTH");
        }

        @Test
        @DisplayName("arama başlık/kısayol/içerik üzerinde çalışır (büyük-küçük harf duyarsız)")
        void search_matchesContent() {
            when(repository.findVisibleToUser("agent-1")).thenReturn(List.of(personalOf("agent-1"), shared(null)));

            List<CannedResponseDTO> result = service.listVisible("agent-1", null, null, null, "VPN");

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getShortcut()).isEqualTo("vpn");
        }

        @Test
        @DisplayName("favori bayrağı kullanıcıya göre set edilir")
        void favoriteFlag_set() {
            when(favoriteRepository.findFavoriteIdsByUser("agent-1")).thenReturn(List.of(2L));
            when(repository.findVisibleToUser("agent-1")).thenReturn(List.of(personalOf("agent-1"), shared(null)));

            List<CannedResponseDTO> result = service.listVisible("agent-1", null, null, null, null);

            assertThat(result).filteredOn(r -> r.getId().equals(2L)).singleElement()
                    .extracting(CannedResponseDTO::getFavorite).isEqualTo(true);
            assertThat(result).filteredOn(r -> r.getId().equals(1L)).singleElement()
                    .extracting(CannedResponseDTO::getFavorite).isEqualTo(false);
        }
    }

    @Test
    @DisplayName("listVisible — geçersiz scope filtresi 400 fırlatır (sorgudan önce)")
    void listVisible_invalidScope_badRequest() {
        assertThatThrownBy(() -> service.listVisible("agent-1", null, "GARBAGE", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository, never()).findVisibleToUser(any());
    }

    @Test
    @DisplayName("listVisible — geçersiz visibility filtresi 400 fırlatır")
    void listVisible_invalidVisibility_badRequest() {
        assertThatThrownBy(() -> service.listVisible("agent-1", null, null, "FOO", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ----------------------------------------------------------------
    // create
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("Agent kişisel şablon oluşturabilir ve bir ürüne bağlayabilir")
        void agentCreatesPersonalWithProduct() {
            when(productRepository.existsById(99L)).thenReturn(true);
            when(repository.save(any(CannedResponse.class))).thenAnswer(i -> i.getArgument(0));
            CannedResponseDTO body = dto("PERSONAL", "Merhaba", null);
            body.setProductId(99L); // personal templates may now be product-scoped

            CannedResponseDTO saved = service.create(body, "agent-1", AGENT);

            assertThat(saved.getScope()).isEqualTo("PERSONAL");
            assertThat(saved.getOwnerAgentId()).isEqualTo("agent-1");
            assertThat(saved.getProductId()).isEqualTo(99L);
            assertThat(saved.getFavorite()).isFalse();
        }

        @Test
        @DisplayName("Kişisel şablon ürünsüz (global) de oluşturulabilir")
        void agentCreatesPersonalGlobal() {
            when(repository.save(any(CannedResponse.class))).thenAnswer(i -> i.getArgument(0));

            CannedResponseDTO saved = service.create(dto("PERSONAL", "Merhaba", null), "agent-1", AGENT);

            assertThat(saved.getScope()).isEqualTo("PERSONAL");
            assertThat(saved.getProductId()).isNull();
        }

        @Test
        @DisplayName("Scope verilmezse PERSONAL'a düşer")
        void nullScope_defaultsPersonal() {
            when(repository.save(any(CannedResponse.class))).thenAnswer(i -> i.getArgument(0));

            CannedResponseDTO saved = service.create(dto(null, "x", null), "agent-1", AGENT);

            assertThat(saved.getScope()).isEqualTo("PERSONAL");
        }

        @Test
        @DisplayName("Agent SHARED oluşturmaya çalışırsa 403")
        void agentSharedForbidden() {
            assertThatThrownBy(() -> service.create(dto("SHARED", "x", null), "agent-1", AGENT))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Admin SHARED + ürün oluşturabilir; ürün doğrulanır")
        void adminCreatesSharedWithProduct() {
            when(productRepository.existsById(10L)).thenReturn(true);
            when(repository.save(any(CannedResponse.class))).thenAnswer(i -> i.getArgument(0));
            CannedResponseDTO body = dto("SHARED", null, "Hello");
            body.setProductId(10L);

            CannedResponseDTO saved = service.create(body, "admin-1", ADMIN);

            assertThat(saved.getScope()).isEqualTo("SHARED");
            assertThat(saved.getProductId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("Hiç içerik yoksa 400")
        void noContent_badRequest() {
            assertThatThrownBy(() -> service.create(dto("PERSONAL", "  ", null), "agent-1", AGENT))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Boş başlık 400")
        void blankTitle_badRequest() {
            CannedResponseDTO body = dto("PERSONAL", "x", null);
            body.setTitle("   ");
            assertThatThrownBy(() -> service.create(body, "agent-1", AGENT))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Kısayol normalize edilir (slash atılır, küçük harfe çevrilir)")
        void shortcutNormalized() {
            when(repository.save(any(CannedResponse.class))).thenAnswer(i -> i.getArgument(0));
            CannedResponseDTO body = dto("PERSONAL", "x", null);
            body.setShortcut("/VPN");

            service.create(body, "agent-1", AGENT);

            ArgumentCaptor<CannedResponse> captor = ArgumentCaptor.forClass(CannedResponse.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getShortcut()).isEqualTo("vpn");
        }
    }

    // ----------------------------------------------------------------
    // update / delete
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("update() & delete()")
    class UpdateDelete {

        @Test
        @DisplayName("Sahip kendi kişisel şablonunu güncelleyebilir")
        void ownerUpdatesPersonal() {
            when(repository.findById(1L)).thenReturn(Optional.of(personalOf("agent-1")));
            when(repository.save(any(CannedResponse.class))).thenAnswer(i -> i.getArgument(0));
            when(favoriteRepository.existsByUserIdAndCannedResponseId("agent-1", 1L)).thenReturn(false);

            CannedResponseDTO body = dto("PERSONAL", "Yeni", null);
            CannedResponseDTO saved = service.update(1L, body, "agent-1", AGENT);

            assertThat(saved.getContentTr()).isEqualTo("Yeni");
        }

        @Test
        @DisplayName("Başkasının kişisel şablonunu güncellemeye çalışmak 403")
        void nonOwnerUpdate_forbidden() {
            when(repository.findById(1L)).thenReturn(Optional.of(personalOf("other-agent")));

            assertThatThrownBy(() -> service.update(1L, dto("PERSONAL", "x", null), "agent-1", AGENT))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Agent paylaşılan şablonu güncelleyemez (403)")
        void agentUpdatesShared_forbidden() {
            when(repository.findById(2L)).thenReturn(Optional.of(shared(null)));

            assertThatThrownBy(() -> service.update(2L, dto("SHARED", "x", null), "agent-1", AGENT))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Agent kendi kişiselini SHARED'e yükseltemez (403)")
        void agentPromoteToShared_forbidden() {
            when(repository.findById(1L)).thenReturn(Optional.of(personalOf("agent-1")));

            assertThatThrownBy(() -> service.update(1L, dto("SHARED", "x", null), "agent-1", AGENT))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("Sahip kişisel şablonu silebilir")
        void ownerDeletesPersonal() {
            when(repository.findById(1L)).thenReturn(Optional.of(personalOf("agent-1")));

            service.delete(1L, "agent-1", AGENT);

            verify(repository).delete(any(CannedResponse.class));
        }

        @Test
        @DisplayName("Agent paylaşılan şablonu silemez (403)")
        void agentDeletesShared_forbidden() {
            when(repository.findById(2L)).thenReturn(Optional.of(shared(null)));

            assertThatThrownBy(() -> service.delete(2L, "agent-1", AGENT))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
            verify(repository, never()).delete(any());
        }

        @Test
        @DisplayName("Olmayan şablon güncellemede 404")
        void update_missing_notFound() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99L, dto("PERSONAL", "x", null), "agent-1", AGENT))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ----------------------------------------------------------------
    // favorites
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("favorites")
    class Favorites {

        @Test
        @DisplayName("Görünür şablon favorilenebilir (idempotent)")
        void addFavorite_persistsOnce() {
            when(repository.findById(2L)).thenReturn(Optional.of(shared(null)));
            when(favoriteRepository.existsByUserIdAndCannedResponseId("agent-1", 2L)).thenReturn(false);

            service.addFavorite(2L, "agent-1");

            verify(favoriteRepository).save(any(CannedResponseFavorite.class));
        }

        @Test
        @DisplayName("Zaten favoriyse tekrar kaydetmez")
        void addFavorite_alreadyExists_noop() {
            when(repository.findById(2L)).thenReturn(Optional.of(shared(null)));
            when(favoriteRepository.existsByUserIdAndCannedResponseId("agent-1", 2L)).thenReturn(true);

            service.addFavorite(2L, "agent-1");

            verify(favoriteRepository, never()).save(any());
        }

        @Test
        @DisplayName("Başkasının kişiselini favorilemek 403")
        void favoriteOthersPersonal_forbidden() {
            when(repository.findById(1L)).thenReturn(Optional.of(personalOf("other-agent")));

            assertThatThrownBy(() -> service.addFavorite(1L, "agent-1"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        @DisplayName("removeFavorite repository delegasyonu yapar")
        void removeFavorite_delegates() {
            service.removeFavorite(2L, "agent-1");

            verify(favoriteRepository).deleteByUserIdAndCannedResponseId("agent-1", 2L);
        }
    }

    // =====================================================================
    // Validation / normalization dalları (create / update / listVisible)
    // =====================================================================

    private CannedResponseDTO.CannedResponseDTOBuilder validDto() {
        return CannedResponseDTO.builder()
                .title("Greeting").scope("PERSONAL").contentEn("Hello").visibility("BOTH");
    }

    @Test
    void create_invalidScope_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(validDto().scope("WEIRD").build(), "agent-1", AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_sharedWithoutAdmin_forbidden() {
        assertThatThrownBy(() -> service.create(validDto().scope("SHARED").build(), "agent-1", AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void create_blankTitle_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(validDto().title("   ").build(), "agent-1", AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_titleTooLong_throwsBadRequest() {
        String longTitle = "x".repeat(151);
        assertThatThrownBy(() -> service.create(validDto().title(longTitle).build(), "agent-1", AGENT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void create_noContent_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(validDto().contentEn(null).contentTr(null).build(), "agent-1", AGENT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void create_contentTooLong_throwsBadRequest() {
        String longContent = "y".repeat(2001);
        assertThatThrownBy(() -> service.create(validDto().contentEn(longContent).build(), "agent-1", AGENT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void create_invalidVisibility_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(validDto().visibility("MAYBE").build(), "agent-1", AGENT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void create_shortcutTooLong_throwsBadRequest() {
        String longShortcut = "/" + "s".repeat(51);
        assertThatThrownBy(() -> service.create(validDto().shortcut(longShortcut).build(), "agent-1", AGENT))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void create_productNotFound_throwsNotFound() {
        when(productRepository.existsById(99L)).thenReturn(false);
        assertThatThrownBy(() -> service.create(validDto().productId(99L).build(), "agent-1", AGENT))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_validPersonal_normalizesShortcutAndDefaults() {
        when(repository.save(any())).thenAnswer(inv -> {
            CannedResponse c = inv.getArgument(0);
            c.setId(77L);
            return c;
        });
        // scope null → PERSONAL, visibility null → BOTH, shortcut "/Hi/" → "hi"
        CannedResponseDTO dto = CannedResponseDTO.builder()
                .title("  Hello  ").contentEn("Hi there").shortcut("//Hi").scope(null).visibility(null).build();

        CannedResponseDTO result = service.create(dto, "agent-1", AGENT);

        ArgumentCaptor<CannedResponse> captor = ArgumentCaptor.forClass(CannedResponse.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("Hello");
        assertThat(captor.getValue().getShortcut()).isEqualTo("hi");
        assertThat(captor.getValue().getScope()).isEqualTo(CannedResponseScope.PERSONAL);
        assertThat(captor.getValue().getVisibility()).isEqualTo(CannedResponseVisibility.BOTH);
        assertThat(result).isNotNull();
    }

    @Test
    void listVisible_invalidScopeFilter_throwsBadRequest() {
        assertThatThrownBy(() -> service.listVisible("agent-1", null, "BOGUS", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listVisible_invalidVisibilityFilter_throwsBadRequest() {
        assertThatThrownBy(() -> service.listVisible("agent-1", null, null, "BOGUS", null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listVisible_withSearchAndFilters_appliesPredicates() {
        CannedResponse a = personalOf("agent-1");          // visibility BOTH, title "Greeting"
        CannedResponse b = shared(10L);                     // visibility EXTERNAL, title "VPN steps"
        when(repository.findVisibleToUser("agent-1")).thenReturn(List.of(a, b));
        when(favoriteRepository.findFavoriteIdsByUser("agent-1")).thenReturn(List.of(1L));

        // search "vpn" + visibility EXTERNAL → yalnızca shared (b) eşleşir.
        List<CannedResponseDTO> res = service.listVisible("agent-1", null, "SHARED", "EXTERNAL", "vpn");

        assertThat(res).hasSize(1);
        assertThat(res.get(0).getTitle()).isEqualTo("VPN steps");
    }
}
