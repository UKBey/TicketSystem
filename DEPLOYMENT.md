# DEPLOYMENT.md

IT-service ticketing monorepo için prod deploy rehberi. Docker Compose, kind (local k8s) ve Kubernetes prod overlay'lerini kapsar.

---

## 1. Önkoşullar

### Araçlar

| Araç           | Sürüm                | Kullanım                              |
|----------------|----------------------|---------------------------------------|
| Docker         | 24+                  | Compose, kind node image, image build |
| docker compose | v2 plugin            | `make up` / `make rebuild`            |
| Make           | GNU Make 4+          | Windows: GnuWin32; canonical entry    |
| kubectl        | 1.29+                | Tüm k8s işlemleri                     |
| kustomize      | kubectl 1.21+ ile dahil | Overlay render                     |
| kind           | 0.22+                | Local cluster                         |
| kubeseal       | controller ile uyumlu | SealedSecrets (prod)                  |
| Java           | 21 (Temurin)         | Backend yerel build                   |
| Node           | 20+                  | Frontend yerel build                  |

### Zorunlu secret'lar / env

`.env.example` (compose) ve `k8s/overlays/local/secrets.env.example` (kustomize) ayna ile dolduruluyor. Aşağıdaki tüm değerler deploy öncesi set edilmiş olmalı:

```text
# Postgres
POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB
SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD

# Keycloak
KEYCLOAK_DB_USERNAME, KEYCLOAK_DB_PASSWORD
KEYCLOAK_ADMIN, KEYCLOAK_ADMIN_PASSWORD
KEYCLOAK_FRONTEND_URL                   # OPS-8 tek kaynak: https://<domain>/auth
KEYCLOAK_REALM=TicketSystemRealm        # JWT_ISSUER_URI bundan türetilir
KEYCLOAK_ADMIN_CLIENT_ID=ticket-client
KEYCLOAK_ADMIN_CLIENT_SECRET

# LDAP
LDAP_ORGANISATION, LDAP_DOMAIN
LDAP_ADMIN_PASSWORD (bind admin),
LDAP_CUSTOMER_PASSWORD, LDAP_AGENT_PASSWORD, LDAP_LEAD_PASSWORD, LDAP_MANAGER_PASSWORD,
LDAP_ADMIN_USER_PASSWORD, LDAP_ADMINMANAGER_PASSWORD, LDAP_LEADMANAGER_PASSWORD, LDAP_SUPERADMIN_PASSWORD

# jBPM / KIE
JBPM_DB_USER, JBPM_DB_PASSWORD, JBPM_DB_NAME
JBPM_KIE_SERVER_URL, JBPM_KIE_SERVER_USERNAME, JBPM_KIE_SERVER_PASSWORD
JBPM_KIE_SERVER_CONTAINER_ID=ticket-workflow
JBPM_KIE_SERVER_PROCESS_ID=com.ticketsystem.workflow.ticket-lifecycle
JBPM_KIE_SERVER_CALLBACK_TOKEN          # internal token; TICKET_SERVICE_INTERNAL_TOKEN ile AYNI olmalı
JBPM_KIE_SERVER_CALLBACK_BASE_URL

# Redis (rate-limit + cache)
SPRING_DATA_REDIS_HOST, SPRING_DATA_REDIS_PORT, SPRING_DATA_REDIS_PASSWORD   # prod'da MUST be set

# LLM
GROQ_API_KEY, GROQ_MODEL, GROQ_MAX_TOKENS, GROQ_TIMEOUT_SECONDS
TICKET_SERVICE_INTERNAL_TOKEN           # JBPM_KIE_SERVER_CALLBACK_TOKEN ile AYNI olmalı

# Mail (prod SMTP; Mailpit yalnızca dev)
MAIL_HOST, MAIL_PORT, MAIL_FROM, MAIL_USERNAME, MAIL_PASSWORD

# CD
DOCKERHUB_USERNAME, IMAGE_TAG           # prod'da IMAGE_TAG commit SHA olmalı
```

**Prod kuralı:** `JBPM_KIE_SERVER_CALLBACK_TOKEN` ve `TICKET_SERVICE_INTERNAL_TOKEN` aynı olmalı, en az 32 karakter rastgele olmalı, dev placeholder asla tekrar kullanılmamalı.

### DNS

- **Local (kind):** `kind-config.yaml` portu doğrudan host'a açar; hosts dosyası girişi gerekmez. Eski ingress akışı için: `127.0.0.1 ticketsystem.local`
- **Prod:** ingress LoadBalancer IP'sine A kaydı. `KEYCLOAK_FRONTEND_URL` ve `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` aynı scheme+host kullanmalı — uyuşmazlık JWT validation'ı kırar (OPS-8 fix sonrası `JWT_ISSUER_URI` artık compose'ta `KEYCLOAK_FRONTEND_URL`'den türetilir).

---

## 2. Deployment yolları

### A. Docker Compose

```bash
cp .env.example .env                  # gerçek değerleri doldur
make rebuild                          # image build + tam stack
make ps                               # tümünün healthy olduğunu doğrula
```

Günlük ops:
```bash
make up                               # mevcut image'larla başlat
make down                             # durdur (veri korunur)
make logs s=it-service-backend        # tek servis log tail
make restart s=keycloak-iam           # tek servis bounce
make build-only s=it-service-backend  # tek image rebuild (--no-deps)
```

Smoke test:
```bash
curl -fsS http://localhost/actuator/health
curl -fsS http://localhost/auth/realms/TicketSystemRealm/.well-known/openid-configuration | head
```

### B. Kubernetes — local kind

```bash
cp k8s/overlays/local/secrets.env.example k8s/overlays/local/secrets.env   # gitignored
make k8s-build                        # local/<name>:latest olarak image'ları derle
make k8s-up                           # cluster oluştur + overlay apply
make k8s-load-images                  # lokal image'ları kind içine yükle
kubectl -n ticketsystem get pods -w
```

Kod değişiklikleri sonrası iterate:
```bash
make k8s-rebuild                      # cluster'ı ayağa kaldır + build + load + apply + restart + kjar redeploy
make k8s-logs s=it-service-backend
make k8s-down                         # cluster + PVC'leri SİLER
```

Erişim: `http://localhost` (kind-config 80'i host'a yönlendirir).

### C. Kubernetes — prod overlay

**Image tag stratejisi (OPS-1 fix):** Asla `:latest` + `IfNotPresent` deploy edilmez. CD pipeline `k8s/overlays/prod/kustomization.yaml`'da her image için commit SHA tag'ini günceller. Immutable tag + `IfNotPresent` = sessiz downgrade yok.

CD pipeline adımı (`cd.yml`, `deploy-k8s` job — altı image'ın tamamını SHA tag'iyle override eder):
```bash
cd k8s/overlays/prod
kustomize edit set image \
  local/it-service-backend=${REGISTRY}/it-service-backend:${SHA} \
  local/llm-service=${REGISTRY}/llm-service:${SHA} \
  local/it-service-frontend=${REGISTRY}/it-service-frontend:${SHA} \
  local/openldap-server=${REGISTRY}/openldap-server:${SHA} \
  local/keycloak-iam=${REGISTRY}/keycloak-iam:${SHA} \
  local/kie-server=${REGISTRY}/kie-server:${SHA}
```

**TLS:** `cert-manager` + `ClusterIssuer` (Let's Encrypt). Prod `Ingress` patch'i (`patches/ingress-prod.yaml`) `tls:` bloğu + `cert-manager.io/cluster-issuer` annotation'ı tutar.

**Secrets:** `kube-system` içinde SealedSecrets controller. Bootstrap:
```bash
kubectl -n ticketsystem create secret generic app-secrets \
  --from-env-file=secrets.env --dry-run=client -o yaml > /tmp/app-secrets-plain.yaml

kubeseal --controller-namespace kube-system \
         --controller-name sealed-secrets-controller \
         -f /tmp/app-secrets-plain.yaml -o yaml > k8s/overlays/prod/app-secrets.sealed.yaml

rm /tmp/app-secrets-plain.yaml
# overlays/prod/kustomization.yaml resources listesine ekle
```

**HPA:** `hpa-backend.yaml` `it-service-backend`'i autoscale eder. Cluster'da metrics-server kurulu olmalı.

Apply:
```bash
kubectl apply -k k8s/overlays/prod
```

---

## 3. Pre-flight checklist

Her prod deploy öncesi:

- [ ] `cd it-service-backend && ./mvnw verify` yeşil (unit + integration + JaCoCo gate %75 LINE / %65 BRANCH — OPS-7)
- [ ] `cd it-service-frontend && npm run lint && npm test && npm run build` yeşil
- [ ] CI badge deploy edilen SHA için yeşil (`gh run list --branch main`)
- [ ] Flyway migration kontrolü:
  - Yeni `V<n>__*.sql` numarası `llm-service`'in `flyway_schema_history_llm` ile çakışmıyor
  - `out-of-order: true` etkin ama prod sırası için güvenme — yeni migration'ı en yüksek versiyon olarak ekle
  - Önceden commit edilmiş hiçbir migration düzenlenmemiş (Flyway checksum fail eder)
- [ ] Realm değiştiyse Keycloak realm export güncel (`keycloak-init/` JSON commit edilmiş)
- [ ] DB backup doğrulandı — son 24 saatte `pg_dump ticketdb` alındı VE bir scratch container'da restore-test edildi
- [ ] Image tag commit SHA (`:latest` DEĞİL); registry'de gerçekten var
- [ ] Cluster'daki secret'lar yeni image beklentilerine uyuyor (yeni env var eklendiyse SealedSecret resealed)
- [ ] Metrik akışı doğrulandı — OpenSearch'te `otel-metrics-*` index'i doküman alıyor (Backend → OTel Collector → Data Prepper → OpenSearch)

---

## 4. Rolling update prosedürü (k8s prod)

```bash
SHA=$(git rev-parse --short HEAD)
REGISTRY=docker.io/<org>

# 1. Image push (genelde CI/CD yapar)
docker push ${REGISTRY}/it-service-backend:${SHA}

# 2. Overlay güncelle
cd k8s/overlays/prod
kustomize edit set image local/it-service-backend=${REGISTRY}/it-service-backend:${SHA}
git commit -am "chore(deploy): backend → ${SHA}"
git push

# 3. Apply
kubectl apply -k k8s/overlays/prod

# 4. Rollout'u izle (başarılı veya fail edene kadar bekler)
kubectl -n ticketsystem rollout status deployment/it-service-backend --timeout=5m

# 5. Smoke test
curl -fsS https://<host>/actuator/health
curl -fsS https://<host>/api/tickets -H "Authorization: Bearer $TOKEN" | jq '.[0]'
```

`llm-service`, `it-service-frontend` için de tekrarla. Migration içeriyorsa backend ilk gider (diğer servisler şemaya bağımlı).

---

## 5. Rollback prosedürü

**Hızlı** (10 dakika içinde, önceki ReplicaSet hâlâ canlıyken):
```bash
kubectl -n ticketsystem rollout undo deployment/it-service-backend
kubectl -n ticketsystem rollout status deployment/it-service-backend
```

**Git-driven** (tercih edilen — audit trail bırakır):
```bash
git revert <bump-commit-sha>
git push
kubectl apply -k k8s/overlays/prod
```

**Flyway:** forward-only. `flyway_schema_history`'den manuel satır SİLME. Migration gerçekten bug'ın kaynağıysa:

1. Deployment'ı geri al (yukarısı) — `success=false` satırı varken pod CrashLoopBackoff olabilir
2. Prod DB'ye `mvn flyway:repair` (detay RUNBOOK'ta)
3. Yeni `V<n+1>__fix_*.sql` corrective migration yaz
4. Yeniden deploy

---

## 6. Post-deploy doğrulama

```bash
# Sağlık
curl -fsS https://<host>/actuator/health | jq '.status'             # UP

# JWT zinciri (OPS-8 ile KEYCLOAK_FRONTEND_URL tek kaynak)
curl -fsS https://<host>/auth/realms/TicketSystemRealm/.well-known/openid-configuration | jq '.issuer'

# Smoke ticket flow (geçerli bearer token ile)
TOKEN="$(...)"
curl -fsS -XPOST https://<host>/api/tickets \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"title":"smoke","description":"deploy verify","priority":"LOW","categoryId":1}' | jq '.id'

# Dönen ticket id üzerinde claim + comment endpoint'leri tetikle

# OpenSearch Dashboards yükleniyor mu — metrik/trace/log dashboard'ları burada
curl -fsS https://<host>/opensearch/api/status | jq '.status.overall.state'   # green
# Metrik akışı: Dashboards > Discover > otel-metrics-* pattern'inde yeni doküman görünmeli

# Mail counter — B-3 sonrası mail_send_total emit ediliyor
curl -fsS https://<host>/actuator/metrics/mail_send_total | jq '.measurements'

# Cache flush erişimi (DB-8 — yalnız admin)
# DELETE /actuator/caches/{name} 200 dönmeli admin rolü ile
```

15 dakika izle:
```bash
kubectl -n ticketsystem logs -f deploy/it-service-backend --tail=200 | grep -iE 'error|exception|warn'
```
