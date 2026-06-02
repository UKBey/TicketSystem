# RBAC Yeniden Tasarımı — Uygulama Planı

> Durum: ONAYLANDI · uygulama başladı. 4 rol (customer/agent/agent_admin/manager) → **5 rol, additive çoklu rol** (customer/agent/lead_agent/admin/manager). Etkin yetki = sahip olunan rollerin birleşimi.

## Kilitlenen kararlar
- `lead_agent` ⊇ `agent` (operasyonel merdiven kümülatif).
- Bootstrap süper-admin: seed'de 1 kullanıcı = `{lead_agent, admin, manager}`.
- `customer` ↔ staff dışlaması (kişi ya müşteri ya personel).
- Ürün yetkisi verme = **admin-only**.
- manager kullanıcı listesini salt-okur görür; agent kapasite-limiti = admin-only; ticket silme = admin-only.

## Roller
| Rol | Eksen | Scope |
|---|---|---|
| customer | operasyonel-müşteri | 🔵 ürün + 👤 |
| agent | operasyonel | 🔵 |
| lead_agent (⊇ agent) | operasyonel-lider | 🔵 yetkili ürünler |
| admin | konfigürasyon | 🟢 global |
| manager | gözetim (salt-okur) | 🟢 global |

## Yetki matrisi (özet — tam envanter sohbet geçmişinde)
- **lead_agent yeni:** ticket atama · claim'siz işlem (scoped) · topic/known-issue/shared-canned CRUD (kendi ürünleri) · takım dashboard'u · ticket sil **HAYIR** (admin).
- **admin:** user/role/product CRUD · ürün yetkisi ver · agent limit · SLA cache · topic/known-issue/canned global · ticket sil · ticket ata (global).
- **manager:** tüm `/metrics/*` (tam) · tüm-csat/worklog raporları · ticket/attachment/worklog read · user list read. Operasyon/config YOK.
- **canned-response'tan MANAGER çıkar** (operasyonel değil).

## Fazlar
- [ ] **Faz 1 — Keycloak:** realm-export.json'a `lead_agent`,`admin` rolleri; `agent_admin` emekli; mevcut kullanıcı geçişi.
- [ ] **Faz 2 — DB:** `user_roles` (user_id, role) join tablosu (Flyway V<n>); backfill `agent_admin→{lead_agent,admin}`, `manager→{manager}` vb.; `users.role` emekliye.
- [ ] **Faz 3 — Backend rol modeli:** User entity rol kümesi; UserService `resolveHighestRole` kaldır → küme senkron; KeycloakAdminService.
- [ ] **Faz 4 — Backend @PreAuthorize:** ~40 nokta matrise göre güncelle (AGENT_ADMIN→admin/lead_agent, MANAGER→manager).
- [ ] **Faz 5 — Backend servis katmanı:** `validateMutationAccess` lead_agent claim'siz; scope (admin/manager global, lead_agent 🔵); assignTicket lead_agent; rol kontrollerini `AuthorityService`'te merkezîleştir.
- [ ] **Faz 6 — Frontend:** AuthContext rol-kümesi + `can()`; komposit nav; ProtectedRoute; lead_agent/admin/manager bölümleri.
- [ ] **Faz 7 — Seed:** data-generator rol kümeleri + bootstrap süper-admin.
- [ ] **Faz 8 — Test & docs:** çok-rol + lead_agent scope/claim'siz testleri; CLAUDE.md auth.

## Riskler
- `agent_admin→{lead_agent,admin}` herkesi ikisi birden yapar (gerekirse elle ayarla).
- "En yüksek rol" varsayan kod yolları temizlenmeli.
- En büyük UI işi: tek-rol ağacı → komposit nav.
