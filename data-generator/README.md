# Ticket System — Data Generator

Sunum ve test için sisteme gerçekçi veri basan standalone Java uygulaması.
**Tek bir bootstrap admin hesabıyla** (`aatest`) çalışır; ürünler, topic'ler,
sıkça karşılaşılan sorunlar ve biletler bu hesap üzerinden idempotent şekilde
oluşturulur. Bu hesap, kullanımdan kaldırılan `agent_admin` rolünü taşır —
`{admin, lead_agent, manager}` bileşiği (composite) olduğundan süper yönetici
gibi davranır; yeni eklemeli (additive) rol modelinde köprü (bridge) olarak
korunur. Lead agent, agent ve customer kullanıcıları **Keycloak'ta önceden
hazırlanmış olmalıdır**; generator kullanıcı oluşturmaz, yalnızca login dener.

## Gereksinimler

- Java 17+
- Çalışan bir Ticket System stack'i (`docker compose up -d` veya `make up`)
- PostgreSQL'e erişim (5432 portu açık olmalı — tarih backfill için doğrudan bağlantı)

## Hızlı Başlangıç

### 1. users.json dosyasını hazırla

`data-generator/users.example.json` dosyasını `data-generator/users.json` olarak
kopyala ve parolaları kendi ortamına göre güncelle. Generator'un login olacağı
**tüm kullanıcılar** (bootstrap admin, Keycloak master admin, DB kullanıcısı +
lead agent + agent + customer'lar) bu tek dosyada listelenir:

```bash
cd data-generator
cp users.example.json users.json       # Linux/Mac
copy users.example.json users.json     # Windows
```

`users.json` yapısı:

```json
{
  "adminAgent":    { "username": "aatest",      "password": "321654" },
  "keycloakAdmin": { "username": "admin",       "password": "321654" },
  "database":      { "username": "ticketadmin", "password": "321654" },
  "leadAgents": { "lead1.gen": "321654" },
  "agents":    { "agent1.gen": "321654", "agent2.gen": "321654", "agent3.gen": "321654" },
  "customers": { "customer1.gen": "321654", ... }
}
```

> `users.json` gitignore'da; commit'lenmez. `users.example.json` her zaman
> repo'da kalır (yer tutucu parolalarla).
>
> `aatest` kullanıcısı Keycloak'ta `agent_admin` (kullanımdan kaldırılan;
> `{admin, lead_agent, manager}` bileşiği — köprülenmiş süper yönetici) rolünde
> tanımlı ve sisteme **en az bir kez giriş yapmış** olmalıdır. Generator, diğer
> tüm kullanıcıları bu hesap üzerinden oluşturur.

Dosya yoksa veya bir hesap orada listelenmemişse, `GeneratorConfig.java`'daki
varsayılan değerler (hepsi `321654`) kullanılır.

### 2. Derle ve çalıştır

```bash
cd data-generator
..\it-service-backend\mvnw.cmd package -q        # Windows
../it-service-backend/mvnw package -q             # Linux/Mac
java -jar target/data-generator-1.0.0.jar
```

veya proje kökünden: `make gen`

---

## Ne yapar?

Generator her çalıştırmada şu adımları sırayla uygular. Her adım **idempotent**;
mevcut kayıtlara dokunmaz.

### 1. Kullanıcılar

`src/main/resources/setup.json` içinde tanımlanan lead agent, agent ve customer
kullanıcıları **sadece login edilir** — generator yeni kullanıcı oluşturmaz.

- Login denenir. Başarılıysa → `/users/sync` ile DB'ye taşınır, oturum kaydedilir.
- Login başarısızsa (kullanıcı yok ya da şifre eşleşmiyor) → uyarı loglanır
  ve kullanıcı atlanır. Bilet üretimi geri kalan kullanıcılarla devam eder.

> **Kullanıcı kurulumu sana ait.** Keycloak admin UI (`http://localhost/auth/admin`)
> veya backend'in `POST /api/users/admin/create` endpoint'i ile aşağıdaki
> hesapları önceden oluştur:

| Rol | Kullanıcı adları | Şifre |
|-----|-------------------|-------|
| agent_admin (kullanımdan kaldırılan; köprülenmiş süper yönetici) | aatest (config'te `ADMIN_AGENT_USERNAME`) | 321654 |
| lead_agent (`agent`'ın bileşiği) | lead1.gen | 321654Aa! |
| agent | agent1.gen, agent2.gen, agent3.gen | 321654Aa! |
| customer | customer1.gen, customer2.gen, customer3.gen, customer4.gen | 321654Aa! |

> `setup.json` içine yeni kullanıcı eklemek için Keycloak'ta da oluşturman gerekir.
> Var olmayan kullanıcılar sessizce atlanır.

### 2. Ürünler / topic'ler / sıkça karşılaşılan sorunlar

`setup.json`'daki `products` listesinden:

- **5 ürün** (VPN ve Ağ, E-posta ve İletişim, Donanım ve Altyapı,
  Kurumsal Yazılım, Bulut Hizmetleri) — `name` eşleşmesiyle idempotent.
- Her ürün altında **5 topic** — `(productId, name)` eşleşmesiyle idempotent.
- Her topic için **10-15 known-issue** kaydı (başlık + içerik) —
  topic içinde başlık eşleşmesiyle idempotent.

### 3. Yetkilendirme

Tüm lead agent, agent ve customer'lara tüm ürünlerin yetkisi atanır.
409 (zaten atanmış) sessizce geçilir.

### 4. Bilet üretimi (JSON şablon tabanlı)

`src/main/resources/tickets/ticket-NNN.json` dosyalarındaki **50 bilet
şablonu** sırayla işlenir. Her dosya bir biletin tüm yaşam döngüsünü deklaratif
olarak içerir:

```json
{
  "title": "VPN bağlantısı sürekli kopuyor",
  "description": "Detaylı açıklama...",
  "priority": "HIGH",
  "productName": "VPN ve Ağ",
  "topicName": "VPN Bağlantısı",
  "status": "RESOLVED",
  "worklogs":  [{ "minutes": 25, "description": "..." }],
  "comments":  [{ "author": "agent", "type": "EXTERNAL", "message": "..." }],
  "resolutionNote": "...",
  "csat":     { "rating": 5, "comment": "..." }
}
```

Status sırası: önce CLOSED (14) ve RESOLVED (10), sonra
WAITING_FOR_CUSTOMER (8) ve IN_PROGRESS (10), en son NEW (8).
Bu sayede agent limitleri dolmadan tüm tipler oluşur.

Her bilet için:

| Status | Çalıştırılan adımlar |
|--------|----------------------|
| NEW | sadece create |
| IN_PROGRESS | create → claim → worklogs → yorumlar kuyruğa |
| WAITING_FOR_CUSTOMER | + status değişikliği |
| RESOLVED | + resolution note + status değişikliği |
| CLOSED | + CSAT (customer; CSAT bileti otomatik CLOSED'a alır) |

Customer ve agent atamaları round-robin yapılır.

### 5. Yorum kuyruğu — round-robin

Yorumlar bilet oluşumu sırasında gönderilmez; kullanıcı başına biriken
kuyruktan sırayla atılır. N kullanıcı varsa her turda N yorum gönderilir
ve `COMMENT_DELAY_MS` (default 5.5sn) beklenir. Toplam yorum süresi
N kat azalır.

### 6. Tarih backfill

Biletler API üzerinden oluşturulduktan sonra PostgreSQL'e doğrudan bağlanılır
ve `created_at`, `sla_deadline`, `resolved_at`, `closed_at`, SLA elapsed/
paused/resumed alanları status'a uygun şekilde son `DATE_SPREAD_DAYS` gün
içine yayılır.

Tarihler **generator'un çalıştığı saate göre** relatif hesaplanır:

| Status | Backfill mantığı |
|--------|------------------|
| NEW | SLA dolmamış olacak şekilde son 0–80%·duration içinde oluşturuldu |
| IN_PROGRESS | Tarihsel oluşturma + agent kısa süre önce claim almış |
| WAITING_FOR_CUSTOMER | SLA duraklatılmış, bütçenin %20–75'i harcanmış |
| RESOLVED | resolved_at = created_at + 1–48 saat |
| CLOSED | closed_at = resolved_at + 1–24 saat |

SLA süreleri öncelikten türetilir (CRITICAL 1h, HIGH 4h, MEDIUM 12h, LOW 24h).

---

## Resource yapısı

```
data-generator/
└── src/main/resources/
    ├── setup.json                 ← Kullanıcılar + 5 ürün × 5 topic × 10-15 known-issue
    └── tickets/
        ├── ticket-001.json        ← NEW   (8 dosya)
        ├── ticket-002.json
        ├── ...
        ├── ticket-009.json        ← IN_PROGRESS   (10 dosya)
        ├── ...
        ├── ticket-019.json        ← WAITING_FOR_CUSTOMER (8 dosya)
        ├── ...
        ├── ticket-027.json        ← RESOLVED      (10 dosya)
        ├── ...
        └── ticket-037.json        ← CLOSED        (14 dosya)
            └── ... ticket-050.json
```

> Yeni şablon eklemek için sadece yeni bir `tickets/ticket-NNN.json` ekle —
> kod değişikliği gerekmez. Generator 1..200 sırayla tarar, var olanları işler.

---

## Tüm Ayarlar

**Kullanıcı bilgileri** (`users.json` ile override edilebilir; ayrıntı için yukarıdaki
"Hızlı Başlangıç" bölümüne bakın):

| Ayar | Varsayılan | Açıklama |
|------|-----------|----------|
| `adminAgent.username` | `aatest` | bootstrap admin kullanıcı adı (agent_admin — köprülenmiş süper yönetici) |
| `adminAgent.password` | `321654` | bootstrap admin şifresi |
| `keycloakAdmin.username` | `admin` | Keycloak master realm admin kullanıcı adı |
| `keycloakAdmin.password` | `321654` | Keycloak master realm admin şifresi |
| `database.username` | `ticketadmin` | PostgreSQL kullanıcı adı |
| `database.password` | `321654` | PostgreSQL şifresi |
| `leadAgents.<username>` | `321654` | Lead agent kullanıcılarının şifreleri |
| `agents.<username>` | `321654` | Agent kullanıcılarının şifreleri |
| `customers.<username>` | `321654` | Customer kullanıcılarının şifreleri |

**Sabit ayarlar** (yalnızca `GeneratorConfig.java` üzerinden değiştirilir):

| Ayar | Varsayılan | Açıklama |
|------|-----------|----------|
| `BASE_URL` | `http://localhost` | Uygulamanın adresi |
| `KEYCLOAK_URL` | `${BASE_URL}/auth` | Keycloak kök URL'i |
| `KEYCLOAK_REALM` | `TicketSystemRealm` | Realm adı |
| `KEYCLOAK_CLIENT` | `ticket-frontend` | Token alınacak public client |
| `MASTER_ADMIN_CLIENT` | `admin-cli` | Master realm token client'ı |
| `DELAY_MS` | `600` | İstekler arası bekleme (ms) |
| `COMMENT_DELAY_MS` | `5500` | Yorum turu arası bekleme (ms) |
| `RATE_LIMIT_BACKOFF_MS` | `6000` | 429 sonrası bekleme |
| `RATE_LIMIT_RETRY_COUNT` | `3` | 429 sonrası deneme sayısı |
| `TOKEN_REFRESH_THRESHOLD_SEC` | `30` | Token yenileme eşiği |
| `DATE_SPREAD_DAYS` | `7` | Tarihlerin yayıldığı gün aralığı |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ticketdb` | PostgreSQL bağlantısı |

---

## Sorun Giderme

**"bootstrap admin oturumu açılamadı"**
→ `ADMIN_AGENT_USERNAME` / `ADMIN_AGENT_PASSWORD` yanlış veya kullanıcı
Keycloak'ta yok (`aatest`, köprülenmiş `agent_admin` rolünde olmalı). Önce
`http://localhost` üzerinden bir kez giriş yap.

**"Kullanıcı atlanıyor: ... login başarısız"**
→ Generator artık kullanıcı oluşturmaz. setup.json'daki bu hesabı Keycloak'ta
  manuel oluşturup şifresinin setup.json ile eşleştiğinden emin ol.
  Yeni kullanıcılarda `Authentication > Required Actions` listesinde
  `Update Password`'ün enable olmadığından, ya da kullanıcının
  `requiredActions` listesinin boş olduğundan emin ol (aksi halde
  Direct Access Grants login fails: "Account is not fully set up").

**"Şablonda geçen ürün bulunamadı"**
→ Bilet JSON'undaki `productName` setup.json'daki ad ile birebir aynı olmalı.

**"429 Too Many Requests"**
→ `COMMENT_DELAY_MS` değerini artır (örn. `6500`) veya
backend'deki rate-limit yapılandırmasını gevşet.

**"Veritabanı bağlantısı kurulamadı" (backfill)**
→ PostgreSQL 5432 portu dışarıya açık olmalı. `docker compose ps` ile kontrol et.

**"Talep konusu çakışıyor / known-issue duplicate"**
→ Re-run güvenli olduğu için olmamalı; gerçekten oluyorsa setup.json'daki
ilgili topic veya issue title'ı benzersiz değildir.
