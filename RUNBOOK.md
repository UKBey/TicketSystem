# RUNBOOK.md

IT-service ticketing sistemi oncall referansı. Kısa, kopyala-yapıştır odaklı.

---

## 1. Servis haritası

| Servis                | Iç port         | Dış path          | Görev                                     | Loglar                                       |
|-----------------------|-----------------|-------------------|-------------------------------------------|----------------------------------------------|
| nginx-proxy           | 80              | `:80`             | Tek ingress, tüm trafik buradan           | `docker logs nginx-proxy`                    |
| it-service-frontend   | 80              | `/`               | React SPA (Vite build, nginx ile servis)  | `docker logs it-service-frontend`            |
| it-service-backend    | 8081            | `/api/*`          | Spring Boot 4 ana API                     | OpenSearch (Logstash); `docker logs it-service-backend` |
| llm-service           | 8082            | `/api/ai/*`       | Groq destekli özetleme                    | `docker logs llm-service`                    |
| keycloak-iam          | 8080            | `/auth/*`         | OIDC IdP, LDAP federation                 | `docker logs keycloak-iam`                   |
| openldap-server       | 389 (internal)  | (yok)             | Kullanıcı dizini                          | `docker logs openldap-server`                |
| it-service-db         | 5432            | `:5432` (dev)     | Postgres — `ticketdb` + `keycloakdb`      | `docker logs it-service-db`                  |
| jbpm-db               | 5432            | `:5433` (dev)     | KIE Server process history Postgres'i     | `docker logs jbpm-db`                        |
| kie-server            | 8080            | `:8180` (dev)     | jBPM workflow engine (7.61.0.Final)       | `docker logs kie-server`                     |
| redis                 | 6379            | `:6379` (dev)     | Rate-limit Bucket4j + cache               | `docker logs redis`                          |
| mailpit               | 1025/8025       | `:8025` (dev)     | Dev SMTP sink                             | `docker logs mailpit`                        |
| opensearch            | 9200            | `:9200` (dev)     | Log, trace ve metrik store                | `docker logs opensearch`                     |
| opensearch-dashboards | 5601            | `/opensearch`     | Log/trace/metrik UI + dashboard'lar       | `docker logs opensearch-dashboards`          |
| kafka                 | 9092            | `:9092` (dev)     | Log pipeline tampon (KRaft mode)          | `docker logs kafka-broker`                   |
| logstash              | -               | -                 | Kafka → OpenSearch                        | `docker logs logstash-consumer`              |
| otel-collector        | 4317/4318       | -                 | Trace/metrik/log OTLP alıcı               | `docker logs otel-collector`                 |
| data-prepper          | 21891           | -                 | OTLP metrik → OpenSearch                  | `docker logs data-prepper`                   |
| sonarqube             | 9000            | `:9000` (dev)     | Code quality (yalnız CI)                  | `docker logs sonarqube`                      |

K8s karşılıkları: `docker logs <name>` yerine `kubectl -n ticketsystem logs deploy/<name>` veya `make k8s-logs s=<name>`.

---

## 2. Sık yapılan ops işlemleri

### DB backup / restore

Backup (compose):
```bash
docker exec -t it-service-db pg_dump -U ticketadmin -F c ticketdb > ticketdb-$(date +%F).dump
docker exec -t it-service-db pg_dump -U ticketadmin -F c keycloakdb > keycloakdb-$(date +%F).dump
```

Backup (k8s):
```bash
kubectl -n ticketsystem exec -it sts/it-service-db -- \
  pg_dump -U ticketadmin -F c ticketdb > ticketdb-$(date +%F).dump
```

Restore — önce scratch container'da doğrula, sonra swap:
```bash
docker run -d --name pgrestore -e POSTGRES_PASSWORD=verify -p 55432:5432 postgres:15-alpine
docker exec -i pgrestore createdb -U postgres ticketdb
docker exec -i pgrestore pg_restore -U postgres -d ticketdb < ticketdb-2026-05-19.dump
docker exec -it pgrestore psql -U postgres -d ticketdb -c "select count(*) from tickets;"
```

### Keycloak realm re-import

```bash
docker compose stop keycloak-iam
docker compose run --rm keycloak-iam \
  /opt/keycloak/bin/kc.sh import --file /opt/keycloak/data/import/realm-export.json --override true
docker compose up -d keycloak-iam
```

K8s:
```bash
kubectl -n ticketsystem rollout restart sts/keycloak-iam
# Pod açılışında ConfigMap-mounted realm JSON yeniden okunur (KC_IMPORT wired)
```

### OpenLDAP LDIF re-import

```bash
docker exec -i openldap-server ldapmodify -x -D "cn=admin,dc=ticketsystem,dc=com" \
  -w "$LDAP_ADMIN_PASSWORD" < ldap-init/bootstrap.ldif
```

### Flyway repair (migration fail oldu)

Belirti: backend log'unda `Migration V<n>__*.sql failed` + pod CrashLoopBackoff, `flyway_schema_history` satırında `success=false`.

```bash
# 1. Fail eden app'i durdur
kubectl -n ticketsystem scale deploy/it-service-backend --replicas=0

# 2. Repair — one-shot maven container veya prod URL'sine karşı mvnw
cd it-service-backend
./mvnw flyway:repair \
  -Dflyway.url=jdbc:postgresql://<db-host>:5432/ticketdb \
  -Dflyway.user=ticketadmin \
  -Dflyway.password=$PASSWORD \
  -Dflyway.schemas=public

# 3. Corrective V<n+1>__*.sql yaz, commit, rebuild, redeploy
kubectl -n ticketsystem scale deploy/it-service-backend --replicas=1
```

`flyway_schema_history`'den manuel SATIR SİLME.

### Redis flush (rate-limit reset)

Bucket bazlı reset (tercih edilen):
```bash
docker exec -it redis redis-cli --scan --pattern 'bucket4j*' | xargs -L 100 docker exec -i redis redis-cli DEL
```

Nükleer (tüm keyspace — cache de etkilenir):
```bash
docker exec -it redis redis-cli FLUSHDB
```

### Cache flush (DB-8 — env-driven SLA / config sonrası)

Restart beklemeden cache temizle (admin rolü gerekli — actuator JWT-gated):
```bash
TOKEN=$(... AGENT_ADMIN veya MANAGER token ...)
curl -fsS -X DELETE -H "Authorization: Bearer $TOKEN" \
  https://<host>/actuator/caches/prioritySlaMetrics
# Eviktelenecek diğer cache'ler: dashboardSummary, agentPerformance, statusDistribution,
# ticketTimeline, productMetrics, csatMetrics, worklogCompletion, rateLimitConfigs
```

### KIE Server kjar redeploy

Compose:
```bash
docker compose build kie-server     # Dockerfile-kie değiştiyse
docker compose up -d --force-recreate kie-server
# KIE dev'de H2 → state silinir; kjar açılışta baked-in script ile fresh register olur
```

K8s:
```bash
make k8s-redeploy-kjar
# job/kjar-deploy'ı siler, sonra kubectl apply -k ile yeniden oluşturur
```

Doğrula:
```bash
curl -u kieserver:kieserver1! http://localhost:8180/kie-server/services/rest/server/containers | jq
```

### `JBPM_KIE_SERVER_CALLBACK_TOKEN` rotasyonu

Token 3 yerde paylaşılır: backend env, llm-service env (`TICKET_SERVICE_INTERNAL_TOKEN`), kie-server callback config. ÜÇÜ ATOMİK güncellenmeli.

```bash
NEW=$(openssl rand -hex 32)

# Compose
sed -i "s/^JBPM_KIE_SERVER_CALLBACK_TOKEN=.*/JBPM_KIE_SERVER_CALLBACK_TOKEN=$NEW/" .env
sed -i "s/^TICKET_SERVICE_INTERNAL_TOKEN=.*/TICKET_SERVICE_INTERNAL_TOKEN=$NEW/" .env
docker compose up -d --force-recreate it-service-backend llm-service kie-server

# K8s
kubectl -n ticketsystem create secret generic app-secrets \
  --from-env-file=k8s/overlays/local/secrets.env --dry-run=client -o yaml \
  | kubectl apply -f -
kubectl -n ticketsystem rollout restart deploy/it-service-backend deploy/llm-service sts/kie-server
```

### DB şifresi rotasyonu

```bash
# 1. DB içinde
docker exec -it it-service-db psql -U postgres -c "ALTER USER ticketadmin WITH PASSWORD 'new-password';"

# 2. Secret güncelle
kubectl -n ticketsystem patch secret app-secrets \
  --type=json -p='[{"op":"replace","path":"/data/SPRING_DATASOURCE_PASSWORD","value":"'$(echo -n new-password | base64)'"}]'

# 3. Tüketicileri restart et
kubectl -n ticketsystem rollout restart deploy/it-service-backend deploy/llm-service sts/keycloak-iam
```

Hikari sağlık kontrolü yeni pool kalkana kadar fail eder (~30s).

### Yeni SealedSecret üret

```bash
# Local'de plain Secret üret (COMMIT ETME)
kubectl -n ticketsystem create secret generic app-secrets \
  --from-env-file=secrets.env --dry-run=client -o yaml > /tmp/app-secrets-plain.yaml

# Cluster controller'a karşı seal et
kubeseal --controller-namespace kube-system \
         --controller-name sealed-secrets-controller \
         -f /tmp/app-secrets-plain.yaml -o yaml > k8s/overlays/prod/app-secrets.sealed.yaml

rm /tmp/app-secrets-plain.yaml
git add k8s/overlays/prod/app-secrets.sealed.yaml
```

### Keycloak hostname değiştirme (OPS-8)

Tek doğruluk kaynağı: `KEYCLOAK_FRONTEND_URL`. Değişikliğinde aşağıdaki yerleri SENKRON güncelle:

- **Compose:** `.env` içinde `KEYCLOAK_FRONTEND_URL` (compose `JWT_ISSUER_URI`'yi türetiyor)
- **K8s base:** `k8s/base/configmap-app.yaml` — 4 satır (`KEYCLOAK_FRONTEND_URL`, `KC_HOSTNAME_URL`, `KC_HOSTNAME_ADMIN_URL`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`)
- **K8s overlay:** ilgili overlay (`overlays/local|prod/patches/configmap-app-*.yaml`) — aynı 4 satır
- **Ingress (prod):** `k8s/overlays/prod/patches/ingress-prod.yaml` `tls.hosts` + `rules.host`
- **JWK_SET_URI:** in-cluster service hostname kullanır (`http://keycloak-iam:8080/...`); domain değişikliğinden ETKİLENMEZ — dokunma.

---

## 3. Incident playbook'ları

### Backend 5xx sıçraması

```bash
# 1. Sağlık snapshot
curl -fsS https://<host>/actuator/health | jq
kubectl -n ticketsystem get pods -l app=it-service-backend

# 2. Sıcak log'lar
kubectl -n ticketsystem logs -f deploy/it-service-backend --tail=500 \
  | grep -iE 'ERROR|Exception|5[0-9][0-9]'

# 3. Hikari pool saturation (DB-7 sonrası max=20 / idle=5)
curl -fsS https://<host>/actuator/metrics/hikaricp.connections.usage | jq
# active==max sürekliyse SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE'ı artır

# 4. Postgres yavaş sorgular
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT pid, now()-query_start AS age, state, query FROM pg_stat_activity
   WHERE state='active' AND now()-query_start > interval '5s' ORDER BY age DESC LIMIT 20;"

# 5. Runaway sorguyu kill et
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c "SELECT pg_terminate_backend(<pid>);"
```

İlgili alert (OPS-5): **Backend5xxRate** — 5dk %2 üzerinde 5xx oranı.

### SLA breach fırtınası

Belirti: dashboard'lar kırmızı, `app.sla.policies` warning'leri log'a akıyor, **SLAWarningMailSpike** alert'i tetiklendi.

```bash
# Config beklenen saatlerle örtüşüyor mu (env vs application.yml default)
kubectl -n ticketsystem exec deploy/it-service-backend -- env | grep ^SLA_

# Priority/status mass-update son 24 saatte var mı
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT priority, COUNT(*) FROM tickets WHERE created_at > now()-interval '24 hours' GROUP BY 1;"

# Son SLA migration var mı
ls -t it-service-backend/src/main/resources/db/migration/ | head -5

# SLA policy değişti ama cache stale — DB-8 flush (admin token ile)
curl -fsS -X DELETE -H "Authorization: Bearer $TOKEN" \
  https://<host>/actuator/caches/prioritySlaMetrics
```

### Pod CrashLoopBackoff

```bash
POD=$(kubectl -n ticketsystem get pod -l app=it-service-backend -o name | head -1)
kubectl -n ticketsystem describe $POD | tail -50
kubectl -n ticketsystem logs $POD --previous --tail=200

# Yaygın sebepler:
# - Liveness probe uygulama açılmadan ateşliyor; deployment yaml'ında
#   initialDelaySeconds'ı artır (compose'da start_period 60s).
# - Flyway lock: başka pod migration sırasında crash etti, lock takıldı.
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT * FROM pg_locks WHERE NOT granted;"
# Flyway advisory lock'unda takıldıysa — holding session'ı kill et, sonra mvn flyway:repair
```

### Keycloak login fail

```bash
# 1. Issuer URI uyuşmazlığı — en yaygın (OPS-8 fix sonrası türetme: JWT_ISSUER = FRONTEND_URL + /realms/REALM)
kubectl -n ticketsystem exec deploy/it-service-backend -- env \
  | grep -E 'JWT_ISSUER_URI|JWK_SET_URI|KEYCLOAK_FRONTEND_URL'
# JWT_ISSUER_URI dış host ile başlamalı (KC_HOSTNAME_URL ile aynı):
# JWT_ISSUER_URI = https://<host>/auth/realms/TicketSystemRealm
# JWK_SET_URI    = http://keycloak-iam:8080/auth/.../certs  (in-cluster, normal)

# 2. LDAP bind fail → Keycloak log'unda "Could not connect to LDAP"
kubectl -n ticketsystem logs sts/keycloak-iam --tail=200 | grep -i ldap
docker exec -it openldap-server ldapsearch -x -D "cn=admin,dc=ticketsystem,dc=com" \
  -w "$LDAP_ADMIN_PASSWORD" -b "dc=ticketsystem,dc=com" "(uid=*)" uid

# 3. Discovery endpoint kontrolü
curl -fsS https://<host>/auth/realms/TicketSystemRealm/.well-known/openid-configuration | jq '.issuer'
```

### Mail gönderilmiyor

**MailSendFailureSpike** veya **SecurityMailFailing** alert'i (OPS-5) tetiklendi.

```bash
# Counter (B-3 sonrası)
curl -fsS https://<host>/actuator/metrics/mail_send_total | jq

# Status=failure breakdown
curl -fsS 'https://<host>/actuator/metrics/mail_send_total?tag=status:failure' | jq

# Kategori breakdown — özellikle security mail'leri (password_reset, twofa_*)
curl -fsS 'https://<host>/actuator/metrics/mail_send_total?tag=category:password_reset' | jq

# Dev: Mailpit aç
start http://localhost:8025  # Windows
# veya: xdg-open http://localhost:8025

# Prod: SMTP creds + network egress
kubectl -n ticketsystem exec deploy/it-service-backend -- \
  sh -c 'echo "QUIT" | timeout 5 nc -v $MAIL_HOST $MAIL_PORT'

# JavaMail debug (geçici):
kubectl -n ticketsystem set env deploy/it-service-backend \
  SPRING_MAIL_PROPERTIES_MAIL_DEBUG=true
# Bounce, fail eden bir send'i yakala, sonra unset
```

### KIE Server ulaşılamıyor

Beklenen davranış: Resilience4j circuit breaker açılır; ticket CRUD workflow sync olmadan devam eder. **KieServerCircuitBreakerOpen** alert'i tetiklendi.

```bash
# Circuit state
curl -fsS https://<host>/actuator/metrics/resilience4j.circuitbreaker.state \
  | jq '.availableTags'

# State transition'lar
curl -fsS 'https://<host>/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:kieServer'

# Graceful degradation doğrula
curl -fsS -XPOST https://<host>/api/tickets -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"smoke","description":"...","priority":"LOW","categoryId":1}' | jq '.id'
# 201 dönmeli; process_instance_id NULL olur — sonradan reconcile edilir

# Recovery
kubectl -n ticketsystem rollout restart sts/kie-server
# kjar register doğrula
curl -u kieserver:$KIE_PASS http://<host>:8180/kie-server/services/rest/server/containers | jq
```

### DB bağlantı saturation

```bash
# Hikari + Postgres snapshot
curl -fsS https://<host>/actuator/metrics/hikaricp.connections.active | jq '.measurements[0].value'
curl -fsS https://<host>/actuator/metrics/hikaricp.connections.pending | jq '.measurements[0].value'

docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT count(*) FROM pg_stat_activity WHERE state != 'idle';"

# Pool default'ları (application.yml — DB-7 sonrası):
#   max=20, idle=5, connection-timeout=5000ms, idle-timeout=300000ms, max-lifetime=1800000ms

# Uzun sorguları bul
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT pid, now()-query_start AS age, state, left(query,80) FROM pg_stat_activity
   WHERE state='active' AND now()-xact_start > interval '30s' ORDER BY age DESC;"

# Kill (önce cancel, gerekirse terminate)
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c "SELECT pg_cancel_backend(<pid>);"
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c "SELECT pg_terminate_backend(<pid>);"

# Uzun vade: app-secrets'ta SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE artır + rollout.
# Postgres max_connections destekliyor mu (default 100 — backend 20, Keycloak + llm-service gerisini paylaşır)
```

İlgili alert (OPS-5): **DBPoolExhausted**.

---

## 4. Faydalı komutlar

### kubectl

```bash
kubectl -n ticketsystem get pods
kubectl -n ticketsystem describe pod <name>
kubectl -n ticketsystem logs -f deploy/<name> --tail=200
kubectl -n ticketsystem exec -it deploy/it-service-backend -- /bin/sh
kubectl -n ticketsystem port-forward svc/it-service-db 5432:5432
kubectl -n ticketsystem rollout status deploy/<name>
kubectl -n ticketsystem rollout undo deploy/<name>
kubectl -n ticketsystem rollout history deploy/<name>
kubectl -n ticketsystem scale deploy/<name> --replicas=0
kubectl -n ticketsystem top pods
kubectl -n ticketsystem get events --sort-by=.lastTimestamp | tail -30
```

### Flyway / Maven

```bash
cd it-service-backend
./mvnw flyway:info     -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...
./mvnw flyway:repair   -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...
./mvnw flyway:validate -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...
./mvnw test            -Dtest=ClassName#method
./mvnw verify          # tam suite + JaCoCo gate (%75 LINE / %65 BRANCH — OPS-7)
```

### docker compose (servis bazlı)

```bash
docker compose up -d --no-deps --build it-service-backend
docker compose exec it-service-db psql -U ticketadmin -d ticketdb
docker compose exec redis redis-cli
docker compose logs -f --tail=200 it-service-backend
docker compose restart keycloak-iam
docker compose run --rm it-service-backend env | grep -i spring
docker compose down -v          # TEHLİKE: volume'leri siler (Postgres data, LDAP, vb.)
```

### Hızlı sağlık panosu

```bash
for svc in it-service-backend llm-service kie-server; do
  echo "=== $svc ==="
  kubectl -n ticketsystem get pod -l app=$svc -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.phase}{"\t"}{.status.containerStatuses[0].ready}{"\n"}'
done
```

### OpenSearch'te metrik akışı

```bash
# otel-metrics-* index'i oluşmuş ve doküman alıyor mu
curl -fsS 'http://localhost:9200/_cat/indices/otel-metrics-*?v'

# Toplam metrik doküman sayısı (akış canlıysa artar)
curl -fsS 'http://localhost:9200/otel-metrics-*/_count'

# Akış kopuksa zinciri sırayla kontrol et: backend OTLP push → otel-collector → data-prepper → opensearch
docker logs --tail 50 data-prepper
```

### Metrik dashboard'ları (OpenSearch Dashboards)

Örnek dashboard'lar `observability/metrics-dashboards.ndjson` içinde saklı
(index pattern + 4 görsel: Request Volume, API Response Time, Error Rate, Service Health).
Temiz bir OpenSearch'e veya re-import için:

```bash
curl -s -X POST 'http://localhost:5601/api/saved_objects/_import?overwrite=true' \
  -H 'osd-xsrf: true' -F file=@observability/metrics-dashboards.ndjson
```

Görüntüleme: OpenSearch Dashboards → Dashboard → **OpenTelemetry Metrikleri**
(compose'da `http://localhost/opensearch`, k8s'te `/opensearch`).
