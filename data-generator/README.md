# Ticket System — Data Generator

Sunum ve test için sisteme gerçekçi veri basan standalone Java uygulaması.
**Tek bir `agent_admin` hesabıyla** çalışır; geri kalan kullanıcılar, ürünler,
topic'ler, sıkça karşılaşılan sorunlar ve biletler bu hesap üzerinden idempotent
şekilde oluşturulur.

## Gereksinimler

- Java 17+
- Çalışan bir Ticket System stack'i (`docker compose up -d` veya `make up`)
- PostgreSQL'e erişim (5432 portu açık olmalı — tarih backfill için doğrudan bağlantı)

## Hızlı Başlangıç

### 1. agent_admin bilgilerini ayarla

`src/main/java/com/ticketsystem/generator/config/GeneratorConfig.java`:

```java
public static final String ADMIN_AGENT_USERNAME = "aatest";
public static final String ADMIN_AGENT_PASSWORD = "321654";
```

> Bu kullanıcının Keycloak'ta `AGENT_ADMIN` rolünde tanımlı ve sisteme **en az bir
> kez giriş yapmış** olması gerekir. Generator, diğer tüm kullanıcıları
> (agent + customer) bu hesap üzerinden oluşturur.

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

`src/main/resources/setup.json` içinde tanımlanan agent ve customer
kullanıcıları sırayla işlenir:

- Login denenir. Başarılıysa → kullanıcı zaten mevcut, atlanır.
- Login başarısızsa → admin endpoint (`POST /api/users/admin/create`) ile
  **kalıcı şifreyle** oluşturulur (`temporaryPassword: false`).
- Oluşturulan/var olan kullanıcı `/users/sync` ile DB'ye taşınır.

Default kullanıcılar:

| Rol | Kullanıcı adları | Şifre |
|-----|-------------------|-------|
| AGENT | agent1.gen, agent2.gen, agent3.gen | 321654 |
| CUSTOMER | customer1.gen, customer2.gen, customer3.gen, customer4.gen | 321654 |

> `setup.json` içine yeni kullanıcı eklemen yeterli, kod değişikliği gerekmez.

### 2. Ürünler / topic'ler / sıkça karşılaşılan sorunlar

`setup.json`'daki `products` listesinden:

- **5 ürün** (VPN ve Ağ, E-posta ve İletişim, Donanım ve Altyapı,
  Kurumsal Yazılım, Bulut Hizmetleri) — `name` eşleşmesiyle idempotent.
- Her ürün altında **5 topic** — `(productId, name)` eşleşmesiyle idempotent.
- Her topic için **en az 2 known-issue** kaydı (başlık + içerik) —
  topic içinde başlık eşleşmesiyle idempotent.

### 3. Yetkilendirme

Tüm agent ve customer'lara tüm ürünlerin yetkisi atanır.
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
    ├── setup.json                 ← Kullanıcılar + 5 ürün × 5 topic × 2+ known-issue
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

## Tüm Ayarlar (`GeneratorConfig.java`)

| Ayar | Varsayılan | Açıklama |
|------|-----------|----------|
| `BASE_URL` | `http://localhost` | Uygulamanın adresi |
| `ADMIN_AGENT_USERNAME` | `aatest` | agent_admin kullanıcı adı |
| `ADMIN_AGENT_PASSWORD` | `321654` | agent_admin şifresi |
| `DELAY_MS` | `600` | İstekler arası bekleme (ms) |
| `COMMENT_DELAY_MS` | `5500` | Yorum turu arası bekleme (ms) |
| `RATE_LIMIT_BACKOFF_MS` | `6000` | 429 sonrası bekleme |
| `RATE_LIMIT_RETRY_COUNT` | `3` | 429 sonrası deneme sayısı |
| `DATE_SPREAD_DAYS` | `7` | Tarihlerin yayıldığı gün aralığı |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ticketdb` | PostgreSQL bağlantısı |
| `DB_USER` | `ticketadmin` | DB kullanıcısı |
| `DB_PASSWORD` | `321654` | DB şifresi |

---

## Sorun Giderme

**"agent_admin oturumu açılamadı"**
→ `ADMIN_AGENT_USERNAME` / `ADMIN_AGENT_PASSWORD` yanlış veya kullanıcı
Keycloak'ta yok. Önce `http://localhost` üzerinden bir kez giriş yap.

**"Kullanıcı oluşturuldu ama oturum açılamadı"**
→ Backend'in `temporaryPassword: false` desteklemesi gerekir
(`CreateUserRequest`). Eski backend sürümü varsa güncel image'a geç.

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
