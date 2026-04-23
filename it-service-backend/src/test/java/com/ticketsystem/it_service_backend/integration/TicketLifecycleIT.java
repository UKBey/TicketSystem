package com.ticketsystem.it_service_backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bilet yasam dongusunun uctan uca entegrasyon testi.
 *
 * <p>Bu test sinifi gercek bir PostgreSQL container (Testcontainers) uzerinde
 * calisir ve asagidaki senaryoyu dogrular:
 * <ol>
 *     <li>CUSTOMER rolundeki kullanici yeni bir destek bileti olusturur.</li>
 *     <li>Biletin veritabaninda {@code NEW} statusuyle kaydedildigi dogrulanir.</li>
 *     <li>Biletin aciklama metninin ilk yorum olarak kaydedildigi dogrulanir.</li>
 *     <li>AGENT rolundeki kullanici bileti sahiplenir (claim).</li>
 *     <li>Biletin {@code IN_PROGRESS} statusune gecisi ve assigneeId atamasi dogrulanir.</li>
 * </ol>
 *
 * <p><strong>Not:</strong> Test sinifi {@code @Transactional} degildir. Her MockMvc
 * cagrisi uretim ortaminda oldugu gibi kendi transaction'inda calisir.
 * Bu yaklasim, transaction commit sonrasi tetiklenen event'lerin
 * (orn. {@code TicketCreatedEvent}) gercekci bicimde test edilmesini saglar.
 */
class TicketLifecycleIT extends BaseIntegrationTest {

    // =========================================================================
    // Bagimliliklar
    // =========================================================================

        private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =========================================================================
    // Test Verileri — Her test oncesinde yeniden olusturulur
    // =========================================================================

    private static final String CUSTOMER_ID = UUID.randomUUID().toString();
    private static final String AGENT_ID = UUID.randomUUID().toString();

    private Product testProduct;

    // =========================================================================
    // Test Yapilandirmasi — Temizlik ve Tohum Veri Olusturma
    // =========================================================================

    /**
     * Her testten once veritabanindaki is verilerini temizler ve
     * yeni test verileri ekler. {@code sla_policies} tablosu Flyway
     * tarafindan yonetildigi icin dokunulmaz.
     *
     * <p>TRUNCATE ... CASCADE kullanilarak FK bagimlilik sirasi
     * otomatik olarak cozumlenir.
     */
    @BeforeEach
    void resetDatabaseAndSeedTestData() {
        // 1. Onceki test verisini temizle (FK CASCADE ile baglantili tablolar da temizlenir)
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    ticket_comments,
                    ticket_worklogs,
                    ticket_attachments,
                    csat_surveys,
                    ticket_resolution_notes,
                    notifications,
                    tickets,
                    user_products,
                    users,
                    products
                CASCADE
                """);

        // 2. Test urunu olustur
        testProduct = productRepository.save(
                Product.builder()
                        .name("IT Support")
                        .isActive(true)
                        .build()
        );

        // 3. Musteri kullanicisi olustur ve urune yetkilendir
        User customer = User.builder()
                .id(CUSTOMER_ID)
                .email("customer@test.com")
                .fullName("Test Customer")
                .role("CUSTOMER")
                .build();
        customer.getAuthorizedProducts().add(testProduct);
        userRepository.save(customer);

        // 4. Ajan kullanicisi olustur ve ayni urune yetkilendir
        User agent = User.builder()
                .id(AGENT_ID)
                .email("agent@test.com")
                .fullName("Test Agent")
                .role("AGENT")
                .build();
        agent.getAuthorizedProducts().add(testProduct);
        userRepository.save(agent);
    }

    // =========================================================================
    // Test Senaryosu: Bilet Olustur → Sahiplen (Create → Claim)
    // =========================================================================

    @Test
    @DisplayName("Tam Yasam Dongusu: Musteri bilet olusturur (NEW) → Ajan sahiplenir (IN_PROGRESS)")
    void fullTicketLifecycle_createAndClaim() throws Exception {

        // =====================================================================
        // ADIM 1: CUSTOMER rolundeki kullanici yeni bilet olusturur
        // =====================================================================

        String ticketRequestJson = objectMapper.writeValueAsString(Map.of(
                "title", "VPN baglantisi kurulamiyor",
                "description", "Sabahtan beri kurumsal VPN'e baglanamiyorum. Hata kodu: ERR_TIMEOUT",
                "priority", "HIGH",
                "productId", testProduct.getId()
        ));

        MvcResult createResult = mockMvc.perform(
                        post("/api/tickets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ticketRequestJson)
                                .with(jwtForCustomer(CUSTOMER_ID))
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.title").value("VPN baglantisi kurulamiyor"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.customerId").value(CUSTOMER_ID))
                .andExpect(jsonPath("$.customerName").value("Test Customer"))
                .andExpect(jsonPath("$.productName").value("IT Support"))
                .andReturn();

        // Yanit govdesinden bilet ID'sini cikar
        JsonNode responseBody = objectMapper.readTree(
                createResult.getResponse().getContentAsString()
        );
        Long ticketId = responseBody.get("id").asLong();

        // =====================================================================
        // DOGRULAMA 1A: Biletin veritabaninda NEW statusuyle kaydedildigi kontrol edilir
        // =====================================================================

        var savedTicket = ticketRepository.findById(ticketId);
        assertThat(savedTicket)
                .as("Olusturulan bilet veritabaninda bulunmalidir")
                .isPresent();
        assertThat(savedTicket.get().getStatus())
                .as("Yeni biletin statusu NEW olmalidir")
                .isEqualTo("NEW");
        assertThat(savedTicket.get().getCustomerId())
                .as("Bilet musteri kimligine ait olmalidir")
                .isEqualTo(CUSTOMER_ID);
        assertThat(savedTicket.get().getAssigneeId())
                .as("Yeni biletin henuz bir atanansi olmamalidir")
                .isNull();
        assertThat(savedTicket.get().getProductId())
                .as("Bilet dogru urune bagli olmalidir")
                .isEqualTo(testProduct.getId());

        // =====================================================================
        // DOGRULAMA 1B: Bilet aciklama metninin ilk yorum olarak kaydedildigi kontrol edilir
        // =====================================================================

        var comments = commentRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        assertThat(comments)
                .as("Bilet olusturulunca otomatik bir EXTERNAL yorum kaydedilmelidir")
                .hasSize(1);
        assertThat(comments.get(0).getMessage())
                .as("Ilk yorum bileti aciklama metniyle ayni olmalidir")
                .isEqualTo("Sabahtan beri kurumsal VPN'e baglanamiyorum. Hata kodu: ERR_TIMEOUT");
        assertThat(comments.get(0).getType())
                .as("Otomatik yorum tipi EXTERNAL olmalidir")
                .isEqualTo("EXTERNAL");

        // =====================================================================
        // ADIM 2: AGENT rolundeki kullanici bileti sahiplenir (claim)
        // =====================================================================

        mockMvc.perform(
                        put("/api/tickets/{id}/claim", ticketId)
                                .with(jwtForAgent(AGENT_ID))
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assigneeId").value(AGENT_ID));

        // =====================================================================
        // DOGRULAMA 2: Veritabaninda biletin IN_PROGRESS'e gecisi ve
        //              ajan atamasinin dogru yapildigi kontrol edilir
        // =====================================================================

        var claimedTicket = ticketRepository.findById(ticketId);
        assertThat(claimedTicket)
                .as("Sahiplenilen bilet veritabaninda bulunmalidir")
                .isPresent();
        assertThat(claimedTicket.get().getStatus())
                .as("Claim sonrasi bilet statusu IN_PROGRESS olmalidir")
                .isEqualTo("IN_PROGRESS");
        assertThat(claimedTicket.get().getAssigneeId())
                .as("Bilet ajanin kimligine atanmis olmalidir")
                .isEqualTo(AGENT_ID);
        assertThat(claimedTicket.get().getCustomerId())
                .as("Musteri kimligi claim sonrasi degismemelidir")
                .isEqualTo(CUSTOMER_ID);
    }

    // =========================================================================
    // JWT Yardimci Metotlari — Mock Keycloak Token Olusturma
    // =========================================================================

    /**
     * CUSTOMER rolunde bir mock JWT token olusturur.
     *
     * <p>Uretilen token su claim'leri icerir:
     * <ul>
     *     <li>{@code sub}: Kullanicinin benzersiz kimligi</li>
     *     <li>{@code realm_access.roles}: {@code ["CUSTOMER"]}</li>
     * </ul>
     *
     * <p>{@code authorities} ayri olarak set edilir cunku
     * {@code SecurityConfig.jwtAuthenticationConverter()} uretim ortaminda
     * bu donusumu yapar. Test ortaminda Spring Security Test framework'u
     * JWT'yi decode etmeden dogrudan kullanir, bu yuzden authority'lerin
     * acikca belirtilmesi gerekir.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor
    jwtForCustomer(String userId) {
        return jwt()
                .jwt(builder -> builder
                        .subject(userId)
                        .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                )
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    /**
     * AGENT rolunde bir mock JWT token olusturur.
     * Yapisi {@code jwtForCustomer} ile aynidir, yalnizca rol farkliligi vardir.
     */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor
    jwtForAgent(String userId) {
        return jwt()
                .jwt(builder -> builder
                        .subject(userId)
                        .claim("realm_access", Map.of("roles", List.of("AGENT")))
                )
                .authorities(new SimpleGrantedAuthority("ROLE_AGENT"));
    }
}
