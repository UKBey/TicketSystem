package com.ticketsystem.it_service_backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketsystem.it_service_backend.entity.CommentType;
import com.ticketsystem.it_service_backend.entity.Product;
import com.ticketsystem.it_service_backend.entity.TicketStatus;
import com.ticketsystem.it_service_backend.entity.TicketTopic;
import com.ticketsystem.it_service_backend.entity.User;
import com.ticketsystem.it_service_backend.repository.CommentRepository;
import com.ticketsystem.it_service_backend.repository.ProductRepository;
import com.ticketsystem.it_service_backend.repository.TicketRepository;
import com.ticketsystem.it_service_backend.repository.TicketTopicRepository;
import com.ticketsystem.it_service_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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
 * End-to-end integration test of the ticket lifecycle.
 *
 * <p>This test class runs against a real PostgreSQL container
 * (Testcontainers) and verifies the following scenario:
 * <ol>
 *     <li>A user with the CUSTOMER role creates a new support ticket.</li>
 *     <li>The ticket is saved to the database with status {@code NEW}.</li>
 *     <li>The ticket's description is stored as the first comment.</li>
 *     <li>A user with the AGENT role claims the ticket.</li>
 *     <li>The ticket transitions to {@code IN_PROGRESS} and the assignee
 *         is set correctly.</li>
 * </ol>
 *
 * <p><strong>Note:</strong> The test class is not {@code @Transactional}.
 * Each MockMvc call runs in its own transaction, exactly as in production.
 * This way events triggered after a transaction commits (e.g.
 * {@code TicketCreatedEvent}) are exercised realistically.
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
    private TicketTopicRepository ticketTopicRepository;

    @Autowired
    private CommentRepository commentRepository;

    // =========================================================================
    // Test Verileri — Her test oncesinde yeniden olusturulur
    // =========================================================================

    private static final String CUSTOMER_ID = UUID.randomUUID().toString();
    private static final String AGENT_ID = UUID.randomUUID().toString();

    private Product testProduct;
    private TicketTopic testTopic;

    // =========================================================================
    // Test Yapilandirmasi — Tohum Veri Olusturma
    // =========================================================================

    /**
     * Inserts test data before every test. Business tables are cleaned by
     * {@link BaseIntegrationTest#truncateBusinessTables()} in the parent
     * class ({@code @BeforeEach} runs parent-first).
     */
    @BeforeEach
    void seedTestData() {
        // Test urunu olustur
        testProduct = productRepository.save(
                Product.builder()
                        .nameEn("IT Support")
                        .isActive(true)
                        .build()
        );

        // 2b. Urune bagli aktif bir talep konusu olustur (bilet olusturma artik konu gerektiriyor)
        testTopic = ticketTopicRepository.save(
                TicketTopic.builder()
                        .productId(testProduct.getId())
                        .nameTr("Diğer")
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
                "productId", testProduct.getId(),
                "topicId", testTopic.getId()
        ));

        MvcResult createResult = mockMvc.perform(
                        post("/api/v1/tickets")
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
                .andExpect(jsonPath("$.productNameEn").value("IT Support"))
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
                .isEqualTo(TicketStatus.NEW);
        assertThat(savedTicket.get().getCustomerId())
                .as("Bilet musteri kimligine ait olmalidir")
                .isEqualTo(CUSTOMER_ID);
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
                .isEqualTo(CommentType.EXTERNAL);

        // =====================================================================
        // ADIM 2: AGENT rolundeki kullanici bileti sahiplenir (claim)
        // =====================================================================

        mockMvc.perform(
                        put("/api/v1/tickets/{id}/claim", ticketId)
                                .with(jwtForAgent(AGENT_ID))
                )
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.claimers[0].agentId").value(AGENT_ID));

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
                .isEqualTo(TicketStatus.IN_PROGRESS);
        int claimCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_claims WHERE ticket_id = ? AND agent_id = ?",
                Integer.class, ticketId, AGENT_ID);
        assertThat(claimCount).as("ticket_claims tablosunda ajan kaydı bulunmalıdır").isEqualTo(1);
        assertThat(claimedTicket.get().getCustomerId())
                .as("Musteri kimligi claim sonrasi degismemelidir")
                .isEqualTo(CUSTOMER_ID);
    }

    // =========================================================================
    // JWT Yardimci Metotlari — Mock Keycloak Token Olusturma
    // =========================================================================

    /**
     * Builds a mock JWT token for the CUSTOMER role.
     *
     * <p>The produced token carries the following claims:
     * <ul>
     *     <li>{@code sub}: the user's unique id</li>
     *     <li>{@code realm_access.roles}: {@code ["CUSTOMER"]}</li>
     * </ul>
     *
     * <p>The {@code authorities} are set separately because in production
     * {@code SecurityConfig.jwtAuthenticationConverter()} performs that
     * conversion. The Spring Security Test framework consumes the JWT
     * directly without decoding it, so the authorities must be declared
     * explicitly here.
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
     * Builds a mock JWT token for the AGENT role.
     * Identical in shape to {@code jwtForCustomer}, only the role differs.
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
