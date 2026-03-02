# 🎫 TicketSystem (v0.1.0)

IT Service Management (Ticket) uygulaması — müşterilerin teknik sorun bildirebildiği, destek ekibinin SLA kurallarıyla yönettiği full-stack bir platformdur.

## 🏗️ Tech Stack

| Katman | Teknoloji |
|---|---|
| Backend | Java 21 · Spring Boot 4 · Spring Security · OAuth2 / JWT |
| Frontend | React · React Router · Axios |
| Veritabanı | PostgreSQL · Spring Data JPA / Hibernate (code-first) |
| Kimlik Yönetimi | Keycloak · OpenLDAP (User Federation) |
| Container | Docker · Docker Compose |

## 🚀 Hızlı Başlangıç

Docker Desktop açık olduğundan emin olun, ardından:

```bash
docker-compose up --build
```

| Servis | Adres |
|---|---|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8081 |
| Keycloak | http://localhost:8080 |

## 🔐 Keycloak Admin

```
Kullanıcı adı : admin
Şifre         : 321654
Realm         : TicketSystem
```

## 👥 Roller

| Rol | Yetki |
|---|---|
| `CUSTOMER` | Ticket açma, kendi ticket'larını görme |
| `AGENT` | Tüm ticket'ları yönetme, atama, yorum ve worklog ekleme |
| `MANAGER` | Raporlar, SLA takibi |

## 📁 Proje Yapısı

```
TicketSystem/
├── backend/          # Spring Boot API
├── frontend/         # React uygulaması
└── docker-compose.yaml
```

## 📌 Geliştirme Durumu

- [x] Docker Compose altyapısı
- [x] Keycloak + OpenLDAP entegrasyonu
- [x] Veritabanı entity tasarımı (code-first)
- [ ] Ticket API'leri (CRUD, SLA, yorumlar)
- [ ] Frontend sayfaları
- [ ] Loglama & OpenTelemetry
- [ ] jBPM iş akışı