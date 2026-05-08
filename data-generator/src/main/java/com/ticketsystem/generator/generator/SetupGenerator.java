package com.ticketsystem.generator.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.ticketsystem.generator.client.ApiClient;
import com.ticketsystem.generator.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Veri üretiminden önce sistemi hazırlar:
 * 1. Ürünleri oluşturur (yoksa)
 * 2. Agent'lara ürün yetkisi atar
 * 3. Customer'lara ürün yetkisi atar
 */
public class SetupGenerator {

    private static final Logger log = LoggerFactory.getLogger(SetupGenerator.class);

    private static final List<String> DEFAULT_PRODUCTS = List.of(
        "[GEN] VPN & Ağ",
        "[GEN] E-Posta & İletişim",
        "[GEN] Donanım & Altyapı",
        "[GEN] Kurumsal Yazılım",
        "[GEN] ERP & CRM",
        "[GEN] Güvenlik & Erişim",
        "[GEN] Bulut Hizmetleri"
    );

    private final ApiClient api;
    private final UserSession adminSession;
    private final List<UserSession> agents;
    private final List<UserSession> customers;

    public SetupGenerator(ApiClient api, UserSession adminSession,
                          List<UserSession> agents, List<UserSession> customers) {
        this.api          = api;
        this.adminSession = adminSession;
        this.agents       = agents;
        this.customers    = customers;
    }

    /**
     * Sistemi hazırlar ve kullanılabilir ürün ID'lerini döner.
     */
    public List<Long> setup() throws IOException, InterruptedException {
        log.info("=== Sistem kurulumu başlıyor ===");

        deleteAllTickets();
        List<Long> productIds = ensureProducts();
        assignProductsToAgents(productIds);
        assignProductsToCustomers(productIds);

        log.info("=== Sistem kurulumu tamamlandı. Ürünler: {} ===", productIds);
        return productIds;
    }

    // ---------------------------------------------------------------
    // Tüm biletleri sil
    // ---------------------------------------------------------------
    private void deleteAllTickets() throws IOException, InterruptedException {
        log.info("Mevcut biletler siliniyor...");
        List<Long> ticketIds = new ArrayList<>();
        try {
            // Admin tüm biletleri görebilir
            JsonNode tickets = api.get("/tickets", adminSession.getToken());
            if (tickets.isArray()) {
                for (JsonNode t : tickets) {
                    if (t.has("id")) ticketIds.add(t.get("id").asLong());
                }
            }
        } catch (Exception e) {
            log.warn("Biletler listelenemedi: {}", e.getMessage());
            return;
        }

        if (ticketIds.isEmpty()) {
            log.info("Silinecek bilet bulunamadı.");
            return;
        }

        log.info("{} bilet siliniyor...", ticketIds.size());
        int deleted = 0;
        for (Long id : ticketIds) {
            try {
                api.delete("/tickets/" + id, null, adminSession.getToken());
                deleted++;
                Thread.sleep(150);
            } catch (Exception e) {
                log.warn("Bilet silinemedi (ID: {}): {}", id, e.getMessage());
            }
        }
        log.info("{}/{} bilet silindi.", deleted, ticketIds.size());
    }

    // ---------------------------------------------------------------
    // Eski [GEN] ürünleri sil, yenilerini oluştur
    // ---------------------------------------------------------------
    private List<Long> ensureProducts() throws IOException, InterruptedException {
        // Mevcut [GEN] ürünleri bul ve sil
        deleteGeneratorProducts();

        // Yeni ürünleri oluştur
        log.info("{} ürün oluşturuluyor...", DEFAULT_PRODUCTS.size());
        List<Long> created = new ArrayList<>();

        for (String name : DEFAULT_PRODUCTS) {
            try {
                JsonNode resp = api.post("/products",
                    Map.of("name", name, "isActive", true, "maxActiveTickets", 50),
                    adminSession.getToken());
                long id = resp.get("id").asLong();
                created.add(id);
                log.info("Ürün oluşturuldu: '{}' (ID: {})", name, id);
                Thread.sleep(200);
            } catch (Exception e) {
                log.warn("Ürün oluşturulamadı '{}': {}", name, e.getMessage());
            }
        }

        if (created.isEmpty()) {
            log.error("Hiç ürün oluşturulamadı!");
        }
        return created;
    }

    private void deleteGeneratorProducts() throws IOException, InterruptedException {
        List<Long> genProductIds = new ArrayList<>();
        try {
            JsonNode products = api.get("/products", adminSession.getToken());
            if (products.isArray()) {
                for (JsonNode p : products) {
                    String name = p.has("name") ? p.get("name").asText("") : "";
                    if (name.startsWith("[GEN]")) {
                        genProductIds.add(p.get("id").asLong());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Ürünler listelenemedi: {}", e.getMessage());
            return;
        }

        if (genProductIds.isEmpty()) {
            log.info("Silinecek eski [GEN] ürünü bulunamadı.");
            return;
        }

        log.info("{} eski [GEN] ürünü siliniyor...", genProductIds.size());
        for (Long id : genProductIds) {
            try {
                api.delete("/products/" + id, null, adminSession.getToken());
                log.debug("Ürün silindi: ID {}", id);
                Thread.sleep(200);
            } catch (Exception e) {
                log.warn("Ürün silinemedi (ID: {}): {}", id, e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------
    // Agent'lara tüm ürünleri ata
    // ---------------------------------------------------------------
    private void assignProductsToAgents(List<Long> productIds) throws IOException, InterruptedException {
        log.info("Agent'lara ürün yetkileri atanıyor...");

        for (UserSession agent : agents) {
            if (agent.getUserId() == null) {
                log.warn("Agent userId null, atlanıyor: {}", agent.getUsername());
                continue;
            }
            for (Long productId : productIds) {
                try {
                    api.post("/users/" + agent.getUserId() + "/products/" + productId,
                        Map.of(), adminSession.getToken());
                    log.debug("Ürün atandı: {} → agent {}", productId, agent.getUsername());
                    Thread.sleep(100);
                } catch (ApiClient.ApiException e) {
                    // 409 = zaten atanmış, normal durum
                    if (e.getStatusCode() != 409) {
                        log.warn("Ürün atanamadı (agent: {}, product: {}): {}",
                            agent.getUsername(), productId, e.getMessage());
                    }
                }
            }
            log.info("Agent '{}' için {} ürün yetkisi atandı.", agent.getUsername(), productIds.size());
        }
    }

    // ---------------------------------------------------------------
    // Customer'lara tüm ürünleri ata
    // ---------------------------------------------------------------
    private void assignProductsToCustomers(List<Long> productIds) throws IOException, InterruptedException {
        log.info("Customer'lara ürün yetkileri atanıyor...");

        for (UserSession customer : customers) {
            if (customer.getUserId() == null) {
                log.warn("Customer userId null, atlanıyor: {}", customer.getUsername());
                continue;
            }
            for (Long productId : productIds) {
                try {
                    api.post("/users/" + customer.getUserId() + "/products/" + productId,
                        Map.of(), adminSession.getToken());
                    log.debug("Ürün atandı: {} → customer {}", productId, customer.getUsername());
                    Thread.sleep(100);
                } catch (ApiClient.ApiException e) {
                    if (e.getStatusCode() != 409) {
                        log.warn("Ürün atanamadı (customer: {}, product: {}): {}",
                            customer.getUsername(), productId, e.getMessage());
                    }
                }
            }
            log.info("Customer '{}' için {} ürün yetkisi atandı.", customer.getUsername(), productIds.size());
        }
    }
}
