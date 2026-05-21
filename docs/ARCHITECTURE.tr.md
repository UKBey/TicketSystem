# Architecture

**Türkçe** · [English](ARCHITECTURE.md)

**IT-Service Desk** platformunun teknik mimarisi — çok rollü bir BT Hizmet Yönetimi (ticket) sistemi. Bu doküman; sistem yapısını, ana çalışma zamanı akışlarını, güvenlik modelini ve bunların ardındaki temel tasarım kararlarını ele alır.

Kurulum ve komutlar için [README](../README.tr.md) dosyasına; operasyonel prosedürler için [RUNBOOK](../RUNBOOK.md) dosyasına bakın.

---

## 1. Genel Bakış

Platform; **müşterilerin** destek ticket'ı oluşturmasına, **temsilcilerin** bunları SLA kuralları çerçevesinde çözmesine ve **yöneticilerin** operasyonu panolar üzerinden izlemesine olanak tanır. API, yapay zekâ, kimlik, iş akışı, veri, mesajlaşma, gözlemlenebilirlik gibi her bir sorumluluğun bağımsız ve ayrı olarak dağıtılabilir bir servis olarak çalıştığı, **konteynerleştirilmiş ve çok dilli (polyglot) bir monorepo** olarak sunulur.

Tasarım hedefleri şunlardır: sorumlulukların net biçimde ayrılması, durumsuz (stateless) ve yatayda ölçeklenebilen uygulama servisleri, dışsallaştırılmış yapılandırma ve uçtan uca gözlemlenebilirlik.

---

## 2. Mimari Tarz

| Yön | Yaklaşım |
|--------|----------|
| **Topoloji** | Mikroservis eğilimli: tek bir ters proxy arkasında ayrık konteynerler |
| **Backend** | Klasik katmanlı mimari (Controller → Service → Repository) |
| **Frontend** | Tek Sayfa Uygulaması (SPA, React) + bir React Native mobil istemci |
| **İletişim** | İstemciler ve servisler arasında senkron REST (JSON); canlı güncellemeler için STOMP/WebSocket; iş akışı motorundan HTTP geri çağrıları |
| **Kimlik** | Keycloak'a dışsallaştırılmış — uygulamalar asla parola saklamaz |
| **Durum** | Uygulama servisleri **durumsuzdur** (JWT tabanlı, sunucu oturumu yok); tüm durum PostgreSQL / Redis içinde tutulur |
| **Yapılandırma** | 12-factor: ortam değişkenleri, Spring profilleri, `application.yml` |
| **Şema yönetimi** | Sürümlenmiş veritabanı migrasyonları (Flyway); Hibernate `validate` modunda çalışır |

---

## 3. Sistem Bağlamı

```mermaid
flowchart LR
    customer([Müşteri])
    agent([Temsilci / Temsilci Yöneticisi])
    manager([Yönetici])

    system[IT-Service Desk Platformu]

    groq[Groq API<br/>LLM sağlayıcısı]
    smtp[SMTP / Mailpit]

    customer --> system
    agent --> system
    manager --> system
    system --> groq
    system --> smtp
```

Platform, iki harici bağımlılıkla entegre olur: yapay zekâ özetlemesi için **Groq API** ve giden e-posta için bir **SMTP sunucusu** (geliştirmede Mailpit).

---

## 4. Konteyner Diyagramı

Tüm dış trafik, `80` numaralı port üzerindeki **nginx** üzerinden girer. Üretime yakın bir dağıtımda hiçbir uygulama servisi veya veri deposu doğrudan dışa açılmaz.

```mermaid
flowchart TB
    subgraph clients[İstemciler]
        web[Web SPA · React 19]
        mobile[Mobil Uygulama · React Native]
    end

    nginx[nginx-proxy · :80<br/>tek giriş noktası]

    subgraph apps[Uygulama Servisleri]
        be[it-service-backend<br/>Spring Boot 4 · :8081]
        llm[llm-service<br/>Spring Boot 3 · :8082]
        kc[Keycloak 24 · /auth]
    end

    kie[KIE Server 7.61<br/>jBPM iş akışı motoru]

    subgraph data[Veri Depoları]
        pg[(PostgreSQL 15<br/>ticketdb + keycloakdb)]
        jpg[(PostgreSQL 15<br/>jbpm-db)]
        redis[(Redis 7)]
        ldap[(OpenLDAP)]
    end

    subgraph obs[Gözlemlenebilirlik Hattı]
        kafka[Kafka]
        logstash[Logstash]
        otel[OTEL Collector]
        dp[Data Prepper]
        os[(OpenSearch + Dashboards)]
    end

    groq[Groq API]
    mail[Mailpit / SMTP]

    web --> nginx
    mobile --> nginx
    nginx --> be
    nginx --> llm
    nginx --> kc
    nginx --> web

    be --> pg
    be --> redis
    be --> kie
    be --> mail
    llm --> pg
    llm --> groq
    kc --> ldap
    kc --> pg
    kie --> jpg
    kie -. iş akışı geri çağrısı .-> be

    be --> kafka
    be --> otel
    llm --> otel
    kafka --> logstash --> os
    otel --> os
    otel --> dp --> os
```

### Yönlendirme (nginx)

| Yol | Hedef |
|------|--------|
| `/` | `it-service-frontend` (statik SPA) |
| `/api/` | `it-service-backend:8081` |
| `/api/ai/` | `llm-service:8082` |
| `/auth/` | `keycloak-iam:8080` |

---

## 5. Bileşenler ve Sorumluluklar

| Bileşen | Yığın | Sorumluluk |
|-----------|-------|----------------|
| **it-service-backend** | Spring Boot 4 / Java 21 | Çekirdek REST API — ticket'lar, SLA, kullanıcılar, yorumlar, ekler, bildirimler, panolar. `ticketdb` şemasının sahibi. |
| **llm-service** | Spring Boot 3 / Java 21 | Groq API aracılığıyla yapay zekâ destekli ticket özetlemesi. `ticketdb`'yi izole bir Flyway geçmiş tablosuyla paylaşır. |
| **it-service-frontend** | React 19 + Vite | Web SPA — müşteriler, temsilciler ve yöneticiler için rol kapsamlı arayüzler. |
| **it-service-mobile** | React Native + Expo | Web uygulamasıyla işlevsel paritede mobil istemci. |
| **ticket-workflow-kjar** | jBPM / BPMN 2.0 | KIE Server'a dağıtılan `ticket-lifecycle` süreç tanımı. |
| **Keycloak** | Keycloak 24 | Kimlik sağlayıcısı — OAuth2/OIDC, `TicketSystemRealm` realm'i, LDAP'tan federe edilen kullanıcılar. |
| **OpenLDAP** | OpenLDAP | Dizin sunucusu — kullanıcı hesapları için tek doğruluk kaynağı. |
| **KIE Server** | jBPM 7.61 (WildFly) | İş akışı sürecini barındırır; kendi PostgreSQL veritabanıyla desteklenir. |
| **PostgreSQL** | PostgreSQL 15 | `ticketdb` (uygulama) ve `keycloakdb` (Keycloak); `jbpm-db` ayrı bir örnektir. |
| **Redis** | Redis 7 | Dağıtık hız sınırı kovaları (rate-limit buckets); gelecekteki önbellek/kuyruk kullanımı için hazırlık alanı. |
| **Kafka + Logstash** | Kafka 3.7 | OpenSearch'e log taşıma tamponu ve tüketicisi. |
| **OTEL Collector + Data Prepper** | OpenTelemetry | Telemetri alımı; izleri/günlükleri/metrikleri OpenSearch'e dağıtır. |
| **OpenSearch** | OpenSearch 3.6 | Günlükler, izler ve metrikler için keşfe yönelik Dashboards içeren birleşik depo. |
| **nginx** | nginx | Ters proxy ve tek giriş noktası. |
| **data-generator** | Java | API aracılığıyla gerçekçi demo verisi oluşturan bağımsız araç. |

---

## 6. Backend İç Yapısı

Backend, `com.ticketsystem.it_service_backend` altında geleneksel bir Spring katmanlı mimarisini izler:

```
controller/   /api/** altındaki REST uç noktaları — doğrulama, HTTP eşleme
service/      İş mantığı (TicketService, SlaPolicyService, WorkflowService,
              NotificationService, EmailService, MetricsService, KeycloakAdminService...)
repository/   Spring Data JPA repository'leri
entity/       JPA entity'leri (Hibernate, ddl-auto = validate)
dto/          İstek/yanıt modelleri — entity'ler asla API sınırını geçmez
event/        @EventListener / @Async alan-olayı (domain-event) işleyicileri
scheduler/    Cron tabanlı görevler (SLA izleme, bildirimler)
filter/       Hız sınırlama filtresi
interceptor/  İstek günlükleme
websocket/    Canlı güncellemeler için STOMP yapılandırması
config/       Güvenlik, önbellek, Redis, yerelleştirme, OpenAPI yapılandırması
exception/    Genel istisna işleyicisi → standart API hata biçimi
```

Kesişen davranışlar merkezileştirilmiştir: bir `GlobalExceptionHandler` tutarlı bir hata zarfı üretir, Bean Validation mesajları yerelleştirilir ve metot düzeyinde güvenlik (`@PreAuthorize`) yetkilendirmeyi iş mantığına yakın bir noktada uygular.

---

## 7. Temel Çalışma Zamanı Akışları

### 7.1 Kimlik Doğrulama ve Yetkilendirme

```mermaid
sequenceDiagram
    actor User
    participant SPA as Web SPA
    participant KC as Keycloak
    participant LDAP as OpenLDAP
    participant API as Backend API
    participant DB as PostgreSQL

    User->>SPA: Uygulamayı aç
    SPA->>KC: Girişe yönlendir (OIDC, ui/kc_locale)
    KC->>LDAP: Kimlik bilgilerini doğrula
    KC-->>SPA: Yetkilendirme kodu → JWT (access + refresh)
    SPA->>API: POST /api/users/sync (Bearer JWT)
    API->>API: JWT imzasını doğrula (realm JWK seti)
    API->>API: realm_access.roles → ROLE_* yetkileri eşle
    API->>DB: Yerel kullanıcı kaydını upsert et
    API-->>SPA: UserDTO (rol, tercihler)
    SPA->>SPA: Role göre yönlendir (müşteri / temsilci / yönetici)
```

Backend saf bir **OAuth2 Resource Server**'dır: durumsuz, yalnızca JWT, imza Keycloak realm'inin JWK setine karşı doğrulanır. Sunucu tarafı oturum yoktur.

### 7.2 Ticket Oluşturma (iş akışı orkestrasyonu ile)

```mermaid
sequenceDiagram
    actor Customer
    participant API as Backend API
    participant DB as PostgreSQL
    participant KIE as KIE Server (jBPM)

    Customer->>API: POST /api/tickets
    API->>DB: Ticket'ı kalıcılaştır (durum NEW, SLA son tarihi hesaplanır)
    API->>KIE: Süreç örneğini başlat (ticket-lifecycle)
    KIE-->>API: processInstanceId
    API->>DB: processInstanceId'yi ticket'ta sakla
    API->>API: TicketCreatedEvent yayınla (asenkron bildirimler)
    API-->>Customer: 201 Created

    Note over API,KIE: Süreç ilerledikçe KIE, /api/internal/workflow/callback<br/>uç noktasını geri çağırır (X-Internal-Token)
```

KIE Server kullanılamıyorsa çağrı bir **devre kesici (circuit breaker)** ile sarılır — ticket yine de oluşturulur ve `processInstanceId` daha sonra mutabık kılınır; böylece iş akışı motoru çekirdek API'yi asla bloklamaz.

### 7.3 SLA İzleme

SLA son tarihi, ticket oluşturulduğunda önceliğe göre politikadan hesaplanır. Bir **zamanlayıcı** periyodik olarak çalışır ve:

- son tarihi geçmiş ticket'ları **ihlal edilmiş (breached)** olarak işaretler ve temsilcilere + yöneticilere bildirir;
- uyarı eşiğine yaklaşan ticket'ları işaretler ve atanan temsilcilere bildirir.

SLA sayacı, bir ticket `WAITING_FOR_CUSTOMER` durumundayken **duraklar** ve `IN_PROGRESS` durumuna dönüldüğünde yeniden başlar; böylece müşteri kaynaklı gecikmeler destek ekibinin aleyhine sayılmaz. Duraklatma/devam ettirme, sinyaller aracılığıyla jBPM sürecine de yansıtılır.

### 7.4 Yapay Zekâ Özeti

Frontend, `llm-service`'i (`/api/ai/` üzerinden) çağırır. Servis; ticket'ı, yorumlarını, çalışma kayıtlarını, çözüm notunu ve denetim geçmişini toplar, dile özgü bir komut istemi (prompt) oluşturur, Groq API'yi çağırır ve özeti kalıcılaştırır. Görece maliyetli olan bu uç noktayı, IP başına ayrılmış bir hız sınırı korur.

---

## 8. Güvenlik Modeli

| Konu | Uygulama |
|---------|----------------|
| **Kimlik doğrulama** | Keycloak (OAuth2/OIDC); resource server tarafından doğrulanan JWT (RS256) |
| **Kullanıcı federasyonu** | OpenLDAP — Keycloak'ın kullanıcı deposu; LDAP grupları realm rollerine eşlenir |
| **2FA** | Kullanıcı başına yapılandırılabilir TOTP (kimlik doğrulayıcı uygulama) |
| **Yetkilendirme — kullanıcı uç noktaları** | `realm_access.roles` → `ROLE_*` yetkileri; metot düzeyinde `@PreAuthorize` |
| **Yetkilendirme — dahili uç noktalar** | `/api/internal/**` JWT'yi atlar; paylaşılan bir `X-Internal-Token` başlığıyla korunur (yalnızca KIE Server geri çağrısı tarafından kullanılır) |
| **Roller** | `CUSTOMER`, `AGENT`, `AGENT_ADMIN`, `MANAGER` |
| **Oturum** | Durumsuz (`SessionCreationPolicy.STATELESS`); CSRF devre dışı (çerez yok) |
| **Anonim izin listesi** | Kimlik doğrulama uç noktaları, WebSocket el sıkışması, Swagger UI, `/actuator/health\|info\|metrics` |
| **Hız sınırlama** | Bucket4j token-bucket, Redis aracılığıyla dağıtık; çalışma zamanında yapılandırılabilir |
| **Girdi güvenliği** | Tüm DTO'larda Bean Validation; ek dosya türü/boyutu denetimleri ve hassas veri taraması |
| **Veri izolasyonu** | Müşteriler yalnızca kendi ticket'larına erişebilir; temsilciler yetkili oldukları ürünlerle sınırlandırılır |

---

## 9. Veri Mimarisi

- Tek bir PostgreSQL örneği, **`ticketdb`** (uygulama verisi) ve **`keycloakdb`** (Keycloak) veritabanlarını barındırır. jBPM motoru **ayrı** bir `jbpm-db` örneği kullanır — bu ikisi birbirine karıştırılmamalıdır.
- Şema değişiklikleri yalnızca **Flyway migrasyonları** (`V<n>__*.sql`, şu anda V1–V33) üzerinden yapılır. Hibernate `ddl-auto: validate` olarak çalışır — şemayı asla değiştirmez.
- `llm-service`, `ticketdb`'yi paylaşır ancak **izole bir Flyway geçmiş tablosu** (`flyway_schema_history_llm`, 0'dan baseline'lanmış) tutar; böylece migrasyonları backend'inkilerle çakışmadan bir arada bulunur.
- DTO'lar API sınırını oluşturur; JPA entity'leri asla doğrudan istemcilere serileştirilmez.

Çekirdek tablolar arasında `tickets`, `users`, `products`, `ticket_comments`, `ticket_worklogs`, `attachments`, `resolution_notes`, `csat`, `notifications`, `notification_preferences`, `sla_policies`, `ticket_claims`, `agent_product_limits`, `ticket_audit_logs`, `rate_limit_config`, `access_requests` ve `known_issues` yer alır.

---

## 10. İş Akışı Entegrasyonu (jBPM)

Her ticket, KIE Server konteyneri `ticket-workflow`'a bir kjar olarak dağıtılan `com.ticketsystem.workflow.ticket-lifecycle` jBPM **süreç örneği** ile desteklenir.

- **Backend → KIE:** `WorkflowService` / `KieServerAdapter`, süreçleri başlatmak, durum ve atamayı senkronize etmek ve SLA duraklat/devam et ile kapatma sinyallerini göndermek için KIE Server REST istemcisini kullanır.
- **KIE → Backend:** süreç, statik `X-Internal-Token` başlığıyla kimliği doğrulanan `/api/internal/workflow/callback` uç noktasını geri çağırır.
- **Dayanıklılık:** tüm KIE çağrıları bir Resilience4j **devre kesici (circuit breaker)** ile sarılır — iş akışı kesintileri zarif biçimde derecelenir ve ticket API'sini asla bloklamaz.

---

## 11. Gözlemlenebilirlik

Platform; **günlükler, izler ve metrikler** üretir ve bunların hepsi OpenSearch'te birleşir.

```mermaid
flowchart LR
    app[Uygulama servisleri<br/>Log4j2 + Micrometer/OTEL]

    app -->|günlükler| kafka[Kafka]
    kafka --> logstash[Logstash]
    logstash --> os[(OpenSearch)]

    app -->|günlükler + izler| otel[OTEL Collector]
    otel -->|izler, günlükler| os
    otel -->|metrikler| dp[Data Prepper]
    dp --> os

    os --> dash[OpenSearch Dashboards]
```

- **Günlükler** — Log4j2 yapılandırılmış JSON üretir; hem Kafka → Logstash üzerinden hem de OpenTelemetry log appender'ı aracılığıyla gönderilir.
- **İzler** — Micrometer Tracing, OpenTelemetry'ye köprü kurar; OTLP aracılığıyla dışa aktarılır ve örneklenir (geliştirmede 1.0).
- **Metrikler** — **delta zamansallığı (delta temporality)** ile dışa aktarılır ve Data Prepper üzerinden yönlendirilir; çünkü collector'ın OpenSearch dışa aktarıcısı metrikleri işlemez. Delta zamansallığı, OpenSearch `sum` agregasyonlarının Prometheus tarzı `rate()` olmadan çalışmasını sağlar.
- **Panolar** — birleşik bir "Ticket System Observability" panosu (metrikler + izler + günlükler), `observability/` içinde kayıtlı nesneler olarak sunulur.

> Not: Prometheus/Grafana bilinçli olarak **kullanılmaz** — OpenSearch tek gözlemlenebilirlik arka ucudur.

---

## 12. Asenkron İşleme ve Zamanlama

Backend, `@EnableAsync` ve `@EnableScheduling` özelliklerini etkinleştirir:

- **Alan olayları (domain events)** — ticket oluşturma gibi eylemler, asenkron olarak işlenen olaylar yayınlar (`@EventListener` + `@Async`); böylece bildirim ve e-posta işleri istek iş parçacığından (thread) uzak tutulur.
- **Zamanlanmış görevler** — cron tabanlı işler, SLA izlemeyi ve bildirim bakımını (ör. süresi dolmuş bildirimlerin temizlenmesi) yürütür.
- **Canlı güncellemeler** — STOMP/WebSocket, bağlı istemcilere ticket detayı olaylarını iletir.

---

## 13. Dayanıklılık ve Performans

| Mekanizma | Amaç |
|-----------|---------|
| **Devre kesici** (Resilience4j) | jBPM/KIE Server arızalarını çekirdek API'den izole eder |
| **Önbellekleme** (Caffeine) | Pano agregasyonları 5 dakikalık TTL ile önbelleğe alınır; ilgili yazmalarda geçersiz kılınır |
| **Hız sınırlama** (Bucket4j + Redis) | API'yi genel olarak korur ve maliyetli yapay zekâ çağrılarını kısıtlar |
| **Bağlantı havuzu** (HikariCP) | Sınırlanmış, ayarlanmış veritabanı bağlantı havuzu |
| **Durumsuz servisler** | Yapışkan (sticky) oturum olmadan yatay ölçeklemeyi mümkün kılar |
| **Zarif derecelenme (graceful degradation)** | İş akışı/yapay zekâ kesintileri, ticket sistemini çökertmeden işlevselliği azaltır |

İşlevsel olmayan hedef, normal yük altındaki tipik işlemler için ~2 saniyenin altında bir yanıt süresidir.

---

## 14. Uluslararasılaştırma ve Tema

- **Diller:** İngilizce ve Türkçe, uçtan uca — SPA (i18next), backend mesajları (`messages_*.properties`), bildirimler, e-postalar ve Keycloak giriş ekranları.
- Kullanıcının tercih ettiği dil kalıcılaştırılır (`users.preferred_language`) ve sunucu tarafı yerelleştirmeyi yönlendirir; bildirimler bir mesaj anahtarı + argümanlar saklar ve okuyanın o anki dilinde **okuma anında oluşturulur**.
- **Tema:** açık/koyu mod, alan adı kapsamlı (domain-scoped) bir çerez aracılığıyla SPA ile özel Keycloak giriş teması arasında paylaşılır; arayüz dili, `kc_locale` parametresi üzerinden Keycloak'a taşınır.

---

## 15. Dağıtım Topolojisi

Aynı imajlardan iki orkestrasyon yolu desteklenir:

- **Docker Compose** (`docker-compose.yaml`) — geliştirme ve demolar için varsayılan tek ana makine yolu.
- **Kubernetes** (`k8s/`) — ortamdan bağımsız bir `base/`, yerel bir kind kümesi için `overlays/local` ve HPA, cert-manager ile SealedSecrets ekleyen bir `overlays/prod` içeren Kustomize.

Her ikisi de `Makefile` (`make up` / `make k8s-up`) üzerinden yürütülür.

### CI/CD

```mermaid
flowchart LR
    pr[Pull Request] --> ci[CI · GitHub Actions]
    ci -->|backend verify| ci
    ci -->|llm-service build| ci
    ci -->|frontend lint/test/build| ci
    ci -->|main'e merge| cd[CD · GitHub Actions]
    cd --> hub[Docker imajlarını derle ve gönder]
```

- **CI** her pull request'te çalışır: backend `mvnw verify` (birim + entegrasyon testleri), llm-service derlemesi ve frontend lint + test + build.
- **CD**, `main` üzerinde başarılı CI sonrasında çalışır: Docker imajlarını derler ve gönderir; geri alma (rollback) için `latest` ve commit SHA etiketleriyle işaretlenir.

---

## 16. Temel Tasarım Kararları

| Karar | Gerekçe |
|----------|-----------|
| **Dışsallaştırılmış kimlik (Keycloak + LDAP)** | Kimlik doğrulamayı şirket içinde inşa etmeden standartlara dayalı SSO, 2FA ve federasyon; uygulamalar parolasız kalır. |
| **Durumsuz JWT resource server** | Yatay ölçeklenebilirlik ve kimlik sağlayıcısı ile API arasında temiz bir ayrım. |
| **Ticket yaşam döngüsü için jBPM** | Yaşam döngüsü, dağınık `if/else` durum mantığı yerine açık ve incelenebilir bir BPMN sürecidir. |
| **İş akışı motoru çevresinde devre kesici** | Çekirdek ticket API'si, KIE Server kapalı olsa bile kullanılabilir kalmalıdır. |
| **Tek gözlemlenebilirlik arka ucu (OpenSearch)** | Günlükler, izler ve metrikler tek bir yerde; hareketli parçaları azaltmak için Prometheus/Grafana kaldırıldı. |
| **Data Prepper üzerinden delta zamansallıklı metrikler** | OTEL collector'ın OpenSearch dışa aktarıcısı metrik üretemez; delta zamansallığı, OpenSearch agregasyonlarının `rate()` olmadan çalışmasını sağlar. |
| **`ddl-auto: validate` ile Flyway** | Şema sürümlenmiş, incelenebilir ve yeniden üretilebilir; Hibernate onu asla sessizce değiştiremez. |
| **Paylaşılan `ticketdb`, llm-service için izole Flyway geçmişi** | Yapay zekâ servisi, servisler arası bir çağrı olmadan alan verisini yeniden kullanır; migrasyonlar ise bağımsız kalır. |
| **Ayrı `jbpm-db`** | Süreç motoru durumu, uygulama verisinden izole edilir. |
| **Çok dilli (polyglot) monorepo** | Birçok hareketli parçası olan bir sistem için tek bir tutarlı geçmiş ve tek bir orkestrasyon giriş noktası. |

---

## 17. İşlevsel Olmayan Gereksinimler — Karşılanma Durumu

| Gereksinim | Nasıl karşılanıyor |
|-------------|---------------|
| **Konteynerleştirme** | Her bileşen izole bir konteyner olarak çalışır; Docker Compose **ve** Kubernetes orkestrasyonu. |
| **Katmanlı backend** | Controller → Service → Repository, dışsallaştırılmış yapılandırma ve standart bir hata biçimi ile. |
| **Kalıcılık (persistence)** | Normalleştirilmiş PostgreSQL şeması, JPA/Hibernate ORM, Flyway migrasyonları. |
| **Günlükleme** | Log4j2, yapılandırılmış JSON, anlamlı seviyeler, OpenSearch'e gönderim (doğrudan ve Kafka üzerinden). |
| **Gözlemlenebilirlik** | OpenTelemetry izleri + metrikleri; istek hacmi, gecikme, hata oranı ve servis sağlığı panoları. |
| **Güvenlik** | Keycloak + LDAP, RBAC, 2FA, web ve mobil için OAuth2/OIDC token oturumları. |
| **İş akışı** | Ticket durum geçişlerini yürüten jBPM süreç tanımı, depo içinde bir BPMN modeliyle. |
| **Performans** | Önbellekleme, havuzlama ve hız sınırlama, normal yük altında 2 saniyenin altında yanıt hedefler. |
| **Test** | Birim testleri (JUnit 5/Mockito), entegrasyon testleri (Testcontainers), JaCoCo kapsamı, SonarQube. |
| **DevOps** | İmajları derleyip yayınlayan GitHub Actions CI/CD hattı. |
| **Dokümantasyon** | Bu doküman, README, RUNBOOK ve etkileşimli bir OpenAPI/Swagger API referansı. |
