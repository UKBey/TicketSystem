# Ticket System — Data Generator

Sunum ve test için sisteme gerçekçi veri basan standalone Java uygulaması.

## Gereksinimler

- Java 17+
- Çalışan bir Ticket System stack'i (`docker compose up -d`)
- PostgreSQL'e erişim (5432 portu açık olmalı)

## Hızlı Başlangıç

### 1. Kullanıcı bilgilerini ayarla

`src/main/java/com/ticketsystem/generator/config/GeneratorConfig.java` dosyasını aç ve
Keycloak'taki gerçek kullanıcı adı/şifrelerini gir:

```java
public static final String[][] CUSTOMERS = {
    {"customer1", "sifre"},
    {"customer2", "sifre"},
};

public static final String[][] AGENTS = {
    {"agent1", "sifre"},
    {"agent2", "sifre"},
};

public static final String[][] AGENT_ADMINS = {
    {"agentadmin1", "sifre"},
};
```

> Kullanıcıların Keycloak'ta tanımlı ve sisteme **en az bir kez giriş yapmış** olması gerekir.
> Giriş yapmamışlarsa önce `http://localhost` adresinden her kullanıcıyla bir kez giriş yap.

### 2. Veri miktarını ve dağılımını ayarla

```java
public static final int TICKET_COUNT = 150;  // Toplam bilet sayısı

// Durum dağılımı — toplamı 100 olmalı
public static final int PCT_NEW         = 20;  // Havuzda bekleyen
public static final int PCT_IN_PROGRESS = 30;  // Agent üzerinde
public static final int PCT_RESOLVED    = 20;  // Çözüldü, CSAT bekliyor
public static final int PCT_CLOSED      = 30;  // Tamamen kapandı
```

### 3. Tarih yayılımını ayarla

```java
// Biletler son kaç gün içine rastgele dağıtılsın
public static final int DATE_SPREAD_DAYS = 90;
```

### 4. Derle ve çalıştır

```bash
cd data-generator
..\it-service-backend\mvnw.cmd package -q   # Windows
../it-service-backend/mvnw package -q        # Linux/Mac
java -jar target/data-generator-1.0.0.jar
```

---

## Ne yapar?

Generator her çalıştırmada şu adımları sırayla uygular:

### Temizlik
- Sistemdeki **tüm biletleri** siler (bağlı yorumlar, CSAT, çözüm notları cascade ile silinir)
- `[GEN]` prefix'li eski ürünleri siler

### Kurulum
- 7 yeni `[GEN]` ürünü oluşturur
- Tüm agent'lara ve customer'lara bu ürünleri atar

### Bilet üretimi

Her bilet durumu için tam yaşam döngüsü simüle edilir:

| Durum | Yapılan işlemler |
|-------|-----------------|
| `NEW` | Bilet oluşturulur, havuzda bekler |
| `IN_PROGRESS` | Bilet oluşturulur → agent claim alır → yorum eklenir |
| `RESOLVED` | + çözüm notu yazılır → RESOLVED yapılır |
| `CLOSED` | + customer CSAT gönderir → bilet kapanır |

### Yorum optimizasyonu
Rate limit kullanıcı başına uygulandığından yorumlar **round-robin** ile gönderilir:
her turda tüm kullanıcılardan birer yorum atılır, sonra 5.5 saniye beklenir.
5 kullanıcı varsa bekleme süresi 5 kat azalır.

### Tarih geriye çekme
Biletler oluşturulduktan sonra PostgreSQL'e direkt bağlanarak `created_at`,
`resolved_at`, `closed_at` ve `sla_deadline` alanları `DATE_SPREAD_DAYS` gün
geriye rastgele dağıtılır. SLA süreleri önceliğe göre hesaplanır:

| Öncelik | SLA Süresi |
|---------|-----------|
| CRITICAL | 1 saat |
| HIGH | 4 saat |
| MEDIUM | 12 saat |
| LOW | 48 saat |

---

## Tüm Ayarlar (`GeneratorConfig.java`)

| Ayar | Varsayılan | Açıklama |
|------|-----------|----------|
| `BASE_URL` | `http://localhost` | Uygulamanın adresi |
| `TICKET_COUNT` | `150` | Toplam bilet sayısı |
| `PCT_NEW` | `20` | NEW bilet yüzdesi |
| `PCT_IN_PROGRESS` | `30` | IN_PROGRESS bilet yüzdesi |
| `PCT_RESOLVED` | `20` | RESOLVED bilet yüzdesi |
| `PCT_CLOSED` | `30` | CLOSED bilet yüzdesi |
| `DELAY_MS` | `800` | İstekler arası bekleme (ms) |
| `COMMENT_DELAY_MS` | `5500` | Yorum turu arası bekleme (ms) |
| `DATE_SPREAD_DAYS` | `90` | Tarihlerin yayıldığı gün aralığı |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ticketdb` | PostgreSQL bağlantısı |
| `DB_USER` | `ticketadmin` | DB kullanıcısı |
| `DB_PASSWORD` | — | DB şifresi |

---

## Sorun Giderme

**"Oturum açılamadı" hatası**
→ Kullanıcı adı/şifre yanlış veya kullanıcı Keycloak'ta yok.

**"Claim alınamadı" uyarısı**
→ Agent'ın ürüne yetkisi yok — generator kurulum aşamasında otomatik atar, tekrar çalıştır.

**"Çözüm notu içeriği boş olamaz" hatası**
→ `note` field'ı boş geçilmiş, güncel jar'ı kullandığından emin ol.

**Tarih geriye çekilmiyor**
→ PostgreSQL 5432 portu dışarıya açık olmalı. `docker compose ps` ile kontrol et.

**429 Too Many Requests**
→ `COMMENT_DELAY_MS` değerini artır (örn. `6500`).
