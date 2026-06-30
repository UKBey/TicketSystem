package com.ticketsystem.it_service_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Springdoc / OpenAPI 3 document configuration.
 *
 * <p>Defines the metadata shown in Swagger UI (title, description, version,
 * servers, tags) and the global security scheme ({@code bearerAuth} — JWT).
 * At runtime it produces the {@code /v3/api-docs} JSON and the
 * {@code /swagger-ui.html} interface.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Builds the OpenAPI 3 document for the whole service.
     *
     * <p>The {@code bearerAuth} HTTP-bearer security scheme is marked
     * globally; the "Authorize" button in Swagger UI attaches the Keycloak
     * JWT to every endpoint.
     */
    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("IT Service Desk — Ticket Management API")
                        .description("""
                                Kurumsal IT destek bilet yönetim sistemi REST API'sidir.
                                
                                ## Özellikler
                                - **Bilet Yaşam Döngüsü**: Oluşturma → Atama → Çözüm → Kapatma
                                - **SLA Yönetimi**: jBPM tabanlı gerçek zamanlı SLA geri sayımı ve ihlal bildirimi
                                - **Yorum Sistemi**: Müşteriye yanıt (EXTERNAL) ve dahili notlar (INTERNAL)
                                - **Dosya Ekleri**: Biletlere dosya yükleme ve indirme
                                - **İş Kaydı (Worklog)**: Agent/Manager süre takibi
                                - **Çözüm Notu**: Bilet çözülmeden önce zorunlu çözüm açıklaması
                                - **CSAT Anketi**: Bilet kapanışında müşteri memnuniyet puanlaması
                                - **Rol Tabanlı Erişim**: Keycloak OAuth2 + JWT ile CUSTOMER / AGENT / MANAGER
                                
                                ## Kimlik Doğrulama
                                Tüm endpoint'ler **Bearer JWT** token gerektirir. Token, Keycloak `TicketSystemRealm`
                                üzerinden alınır. Swagger UI'da sağ üstteki **Authorize** butonuna tıklayarak
                                `Bearer <token>` formatında girebilirsiniz.
                                
                                ## Durum Makinesi (State Machine)
                                ```
                                NEW → IN_PROGRESS → WAITING_FOR_CUSTOMER → IN_PROGRESS
                                                  → RESOLVED → IN_PROGRESS (reopen)
                                                             → CLOSED
                                                  → CLOSED (doğrudan kapatma)
                                ```
                                """)
                        .version("1.19.1")
                        .contact(new Contact()
                                .name("IT Service Desk Ekibi")
                                .email("support@ticketsystem.local")
                                .url("https://github.com/UKBey/TicketSystem"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("GitHub Repository & Proje Dokümantasyonu")
                        .url("https://github.com/UKBey/TicketSystem"))
                .servers(List.of(
                        new Server().url("http://localhost:8081").description("Yerel Geliştirme Ortamı"),
                        new Server().url("http://it-service-backend:8081").description("Docker Ağı (Konteynerler arası)")
                ))
                .tags(List.of(
                        new Tag().name("Bilet Yönetimi").description("Destek biletlerinin oluşturulması, listelenmesi, sahiplenilmesi, durum güncellenmesi ve silinmesi"),
                        new Tag().name("Yorum Yönetimi").description("Biletlere yapılan müşteri yanıtları ve dahili notların yönetimi"),
                        new Tag().name("Dosya Yönetimi").description("Biletlere dosya eki yükleme, listeleme, indirme ve silme işlemleri"),
                        new Tag().name("Ticket Worklog").description("Agent ve Manager'ların bilet üzerinde harcadıkları sürenin takibi"),
                        new Tag().name("Ticket Resolution Note").description("Bilet çözüldüğünde yazılması zorunlu olan çözüm notları"),
                        new Tag().name("Ticket CSAT").description("Bilet kapanışında doldurulan müşteri memnuniyet anketleri (1-5 puan)"),
                        new Tag().name("Ürün Yönetimi").description("Destek kategorilerinin (ürün) CRUD işlemleri ve agent yetkilendirmesi"),
                        new Tag().name("Kullanıcı Yönetimi").description("Keycloak senkronizasyonu, kullanıcı listeleme ve ürün yetki atamaları"),
                        new Tag().name("Workflow Callback").description("jBPM KIE Server'dan gelen dahili SLA ihlali ve süreç olayları (Internal API)")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Keycloak TicketSystemRealm'den alınan JWT access token. "
                                                + "Token içindeki `realm_access.roles` alanı CUSTOMER, AGENT veya MANAGER rollerini taşır.")));
    }
}
