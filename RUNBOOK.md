# RUNBOOK.md

On-call reference for the IT-service ticketing system. Brief, copy-paste oriented.

---

## 1. Service map

| Service               | Internal port   | External path     | Role                                      | Logs                                         |
|-----------------------|-----------------|-------------------|-------------------------------------------|----------------------------------------------|
| nginx-proxy           | 80              | `:80`             | Single ingress, all traffic passes here   | `docker logs nginx-proxy`                    |
| it-service-frontend   | 80              | `/`               | React SPA (Vite build, served by nginx)   | `docker logs it-service-frontend`            |
| it-service-backend    | 8081            | `/api/*`          | Spring Boot 4 main API                    | OpenSearch (Logstash); `docker logs it-service-backend` |
| llm-service           | 8082            | `/api/ai/*`       | Groq-powered summarization                | `docker logs llm-service`                    |
| keycloak-iam          | 8080            | `/auth/*`         | OIDC IdP, LDAP federation                 | `docker logs keycloak-iam`                   |
| openldap-server       | 389 (internal)  | (none)            | User directory                            | `docker logs openldap-server`                |
| it-service-db         | 5432            | `:5432` (dev)     | Postgres — `ticketdb` + `keycloakdb`      | `docker logs it-service-db`                  |
| jbpm-db               | 5432            | `:5433` (dev)     | KIE Server process state/history Postgres (persistent — survives KIE restart) | `docker logs jbpm-db`                        |
| kie-server            | 8080            | `:8180` (dev)     | jBPM workflow engine (7.61.0.Final)       | `docker logs kie-server`                     |
| redis                 | 6379            | `:6379` (dev)     | Rate-limit Bucket4j + cache               | `docker logs redis`                          |
| mailpit               | 1025/8025       | `:8025` (dev)     | Dev SMTP sink                             | `docker logs mailpit`                        |
| opensearch            | 9200            | `:9200` (dev)     | Log, trace and metric store               | `docker logs opensearch`                     |
| opensearch-dashboards | 5601            | `:5601` (dev)     | Log/trace/metric UI + dashboards          | `docker logs opensearch-dashboards`          |
| kafka                 | 9092            | `:9092` (dev)     | Log pipeline buffer (KRaft mode)          | `docker logs kafka-broker`                   |
| logstash              | -               | -                 | Kafka → OpenSearch                        | `docker logs logstash-consumer`              |
| otel-collector        | 4317/4318       | -                 | Trace/metric/log OTLP receiver            | `docker logs otel-collector`                 |
| data-prepper          | 21891           | -                 | OTLP metric → OpenSearch                  | `docker logs data-prepper`                   |
| sonarqube             | 9000            | `:9000` (dev)     | Code quality (CI only)                    | `docker logs sonarqube`                      |

K8s equivalents: instead of `docker logs <name>`, use `kubectl -n ticketsystem logs deploy/<name>` or `make k8s-logs s=<name>`.

---

## 2. Common ops procedures

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

Restore — first verify in a scratch container, then swap:
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
# The ConfigMap-mounted realm JSON is re-read on pod startup (KC_IMPORT wired)
```

### OpenLDAP LDIF re-import

```bash
docker exec -i openldap-server ldapmodify -x -D "cn=admin,dc=ticketsystem,dc=com" \
  -w "$LDAP_ADMIN_PASSWORD" < ldap-init/bootstrap.ldif
```

### Flyway repair (migration failed)

Symptom: `Migration V<n>__*.sql failed` in the backend log + pod CrashLoopBackoff, a `flyway_schema_history` row with `success=false`.

```bash
# 1. Stop the failed app
kubectl -n ticketsystem scale deploy/it-service-backend --replicas=0

# 2. Repair — a one-shot maven container or mvnw against the prod URL
cd it-service-backend
./mvnw flyway:repair \
  -Dflyway.url=jdbc:postgresql://<db-host>:5432/ticketdb \
  -Dflyway.user=ticketadmin \
  -Dflyway.password=$PASSWORD \
  -Dflyway.schemas=public

# 3. Write a corrective V<n+1>__*.sql, commit, rebuild, redeploy
kubectl -n ticketsystem scale deploy/it-service-backend --replicas=1
```

Do NOT manually DELETE ROWS from `flyway_schema_history`.

### Redis flush (rate-limit reset)

Per-bucket reset (preferred):
```bash
docker exec -it redis redis-cli --scan --pattern 'bucket4j*' | xargs -L 100 docker exec -i redis redis-cli DEL
```

Nuclear (entire keyspace — cache is affected too):
```bash
docker exec -it redis redis-cli FLUSHDB
```

### Cache flush (DB-8 — after env-driven SLA / config changes)

Clear the cache without waiting for a restart (admin role required — actuator is JWT-gated):
```bash
TOKEN=$(... admin token ...)
curl -fsS -X DELETE -H "Authorization: Bearer $TOKEN" \
  https://<host>/actuator/caches/prioritySlaMetrics
# Other caches to evict: dashboardSummary, agentPerformance, statusDistribution,
# ticketTimeline, productMetrics, csatMetrics, worklogCompletion, rateLimitConfigs
```

### KIE Server kjar redeploy

The `ticket-workflow` container is registered automatically on every `docker
compose up` by the **`kjar-deploy`** one-shot service: it waits for the KIE
Server REST API to become healthy, then `PUT`s the container
(`com.ticketsystem:ticket-workflow-kjar:1.0.5`). The kjar itself is compiled
from source (Java 8) and baked into the KIE image via `Dockerfile-kie`, so no
host `~/.m2` mount is needed. The backend `depends_on` the job's successful
completion, so it never starts before the workflow container exists.

Compose — to redeploy after editing the BPMN:
```bash
docker compose build kie-server         # recompiles the kjar into the image
docker compose up -d --force-recreate kie-server kjar-deploy
# kjar-deploy re-runs and re-registers the container (idempotent PUT)
```

K8s:
```bash
make k8s-redeploy-kjar
# deletes job/kjar-deploy, then recreates it via kubectl apply -k
```

Verify:
```bash
curl -u kieserver:kieserver1! http://localhost:8180/kie-server/services/rest/server/containers | jq
# Expect: one container "ticket-workflow", status "STARTED", release 1.0.5
```

### `JBPM_KIE_SERVER_CALLBACK_TOKEN` rotation

The token is shared in 3 places: backend env, llm-service env (`TICKET_SERVICE_INTERNAL_TOKEN`), kie-server callback config. ALL THREE must be updated ATOMICALLY.

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

### DB password rotation

```bash
# 1. Inside the DB
docker exec -it it-service-db psql -U postgres -c "ALTER USER ticketadmin WITH PASSWORD 'new-password';"

# 2. Update the secret
kubectl -n ticketsystem patch secret app-secrets \
  --type=json -p='[{"op":"replace","path":"/data/SPRING_DATASOURCE_PASSWORD","value":"'$(echo -n new-password | base64)'"}]'

# 3. Restart the consumers
kubectl -n ticketsystem rollout restart deploy/it-service-backend deploy/llm-service sts/keycloak-iam
```

The Hikari health check fails until the new pool comes up (~30s).

### Generate a new SealedSecret

```bash
# Generate a plain Secret locally (DO NOT COMMIT)
kubectl -n ticketsystem create secret generic app-secrets \
  --from-env-file=secrets.env --dry-run=client -o yaml > /tmp/app-secrets-plain.yaml

# Seal it against the cluster controller
kubeseal --controller-namespace kube-system \
         --controller-name sealed-secrets-controller \
         -f /tmp/app-secrets-plain.yaml -o yaml > k8s/overlays/prod/app-secrets.sealed.yaml

rm /tmp/app-secrets-plain.yaml
git add k8s/overlays/prod/app-secrets.sealed.yaml
```

### Changing the Keycloak hostname (OPS-8)

Single source of truth: `KEYCLOAK_FRONTEND_URL`. When it changes, update the following places IN SYNC:

- **Compose:** `KEYCLOAK_FRONTEND_URL` in `.env` (compose derives `JWT_ISSUER_URI` from it)
- **K8s base:** `k8s/base/configmap-app.yaml` — 4 lines (`KEYCLOAK_FRONTEND_URL`, `KC_HOSTNAME_URL`, `KC_HOSTNAME_ADMIN_URL`, `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`)
- **K8s overlay:** the relevant overlay (`overlays/local|prod/patches/configmap-app-*.yaml`) — the same 4 lines
- **Ingress (prod):** `k8s/overlays/prod/patches/ingress-prod.yaml` `tls.hosts` + `rules.host`
- **JWK_SET_URI:** uses the in-cluster service hostname (`http://keycloak-iam:8080/...`); NOT AFFECTED by a domain change — don't touch it.

---

## 3. Incident playbooks

### Backend 5xx spike

```bash
# 1. Health snapshot
curl -fsS https://<host>/actuator/health | jq
kubectl -n ticketsystem get pods -l app=it-service-backend

# 2. Hot logs
kubectl -n ticketsystem logs -f deploy/it-service-backend --tail=500 \
  | grep -iE 'ERROR|Exception|5[0-9][0-9]'

# 3. Hikari pool saturation (post DB-7: max=20 / idle=5)
curl -fsS https://<host>/actuator/metrics/hikaricp.connections.usage | jq
# If active==max persistently, increase SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE

# 4. Postgres slow queries
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT pid, now()-query_start AS age, state, query FROM pg_stat_activity
   WHERE state='active' AND now()-query_start > interval '5s' ORDER BY age DESC LIMIT 20;"

# 5. Kill the runaway query
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c "SELECT pg_terminate_backend(<pid>);"
```

Related alert (OPS-5): **Backend5xxRate** — 5xx rate above 2% over 5 minutes.

### SLA breach storm

Symptom: dashboards are red, `app.sla.policies` warnings are flooding the log, the **SLAWarningMailSpike** alert has fired.

```bash
# Does the config match the expected hours (env vs application.yml default)
kubectl -n ticketsystem exec deploy/it-service-backend -- env | grep ^SLA_

# Was there a priority/status mass-update in the last 24 hours
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT priority, COUNT(*) FROM tickets WHERE created_at > now()-interval '24 hours' GROUP BY 1;"

# Is there a recent SLA migration
ls -t it-service-backend/src/main/resources/db/migration/ | head -5

# SLA policy changed but cache is stale — DB-8 flush (with admin token)
curl -fsS -X DELETE -H "Authorization: Bearer $TOKEN" \
  https://<host>/actuator/caches/prioritySlaMetrics
```

### Pod CrashLoopBackoff

```bash
POD=$(kubectl -n ticketsystem get pod -l app=it-service-backend -o name | head -1)
kubectl -n ticketsystem describe $POD | tail -50
kubectl -n ticketsystem logs $POD --previous --tail=200

# Common causes:
# - The liveness probe fires before the application has started up; increase
#   initialDelaySeconds in the deployment yaml (start_period 60s in compose).
# - Flyway lock: another pod crashed during migration and the lock is stuck.
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT * FROM pg_locks WHERE NOT granted;"
# If stuck on the Flyway advisory lock — kill the holding session, then run mvn flyway:repair
```

### Keycloak login failure

```bash
# 1. Issuer URI mismatch — the most common (post OPS-8 fix, derivation: JWT_ISSUER = FRONTEND_URL + /realms/REALM)
kubectl -n ticketsystem exec deploy/it-service-backend -- env \
  | grep -E 'JWT_ISSUER_URI|JWK_SET_URI|KEYCLOAK_FRONTEND_URL'
# JWT_ISSUER_URI must start with the external host (same as KC_HOSTNAME_URL):
# JWT_ISSUER_URI = https://<host>/auth/realms/TicketSystemRealm
# JWK_SET_URI    = http://keycloak-iam:8080/auth/.../certs  (in-cluster, normal)

# 2. LDAP bind failure → "Could not connect to LDAP" in the Keycloak log
kubectl -n ticketsystem logs sts/keycloak-iam --tail=200 | grep -i ldap
docker exec -it openldap-server ldapsearch -x -D "cn=admin,dc=ticketsystem,dc=com" \
  -w "$LDAP_ADMIN_PASSWORD" -b "dc=ticketsystem,dc=com" "(uid=*)" uid

# 3. Discovery endpoint check
curl -fsS https://<host>/auth/realms/TicketSystemRealm/.well-known/openid-configuration | jq '.issuer'
```

### Mail is not being sent

A **MailSendFailureSpike** or **SecurityMailFailing** alert (OPS-5) has fired.

```bash
# Counter (post B-3)
curl -fsS https://<host>/actuator/metrics/mail_send_total | jq

# Status=failure breakdown
curl -fsS 'https://<host>/actuator/metrics/mail_send_total?tag=status:failure' | jq

# Category breakdown — especially security mails (password_reset, twofa_*)
curl -fsS 'https://<host>/actuator/metrics/mail_send_total?tag=category:password_reset' | jq

# Dev: open Mailpit
start http://localhost:8025  # Windows
# or: xdg-open http://localhost:8025

# Prod: SMTP creds + network egress
kubectl -n ticketsystem exec deploy/it-service-backend -- \
  sh -c 'echo "QUIT" | timeout 5 nc -v $MAIL_HOST $MAIL_PORT'

# JavaMail debug (temporary):
kubectl -n ticketsystem set env deploy/it-service-backend \
  SPRING_MAIL_PROPERTIES_MAIL_DEBUG=true
# Bounce, capture a failing send, then unset
```

### KIE Server unreachable

Expected behavior: the Resilience4j circuit breaker opens; ticket CRUD continues without workflow sync. The **KieServerCircuitBreakerOpen** alert has fired.

```bash
# Circuit state
curl -fsS https://<host>/actuator/metrics/resilience4j.circuitbreaker.state \
  | jq '.availableTags'

# State transitions
curl -fsS 'https://<host>/actuator/metrics/resilience4j.circuitbreaker.calls?tag=name:kieServer'

# Verify graceful degradation
curl -fsS -XPOST https://<host>/api/tickets -H "Authorization: Bearer $TOKEN" \
  -d '{"title":"smoke","description":"...","priority":"LOW","categoryId":1}' | jq '.id'
# Should return 201; process_instance_id will be NULL — reconciled later

# Recovery
kubectl -n ticketsystem rollout restart sts/kie-server
# Verify kjar registration
curl -u kieserver:$KIE_PASS http://<host>:8180/kie-server/services/rest/server/containers | jq
```

KIE Server process state is persisted to the dedicated **jbpm-db** Postgres
(datasource `java:jboss/datasources/jbpmDS`, `PostgreSQLDialect` — not the old
in-memory H2 `ExampleDS`), so in-flight workflows survive a kie-server
restart/rebuild. If a process instance is nonetheless gone (e.g. jbpm-db was
wiped), a stale `process_instance_id` is tolerated: a KIE **404** is ignored by
the circuit breaker and the DB transition is accepted anyway, so ticket status
changes do not block on a missing BPMN instance.

The BPMN is the authoritative state machine for **all** ticket status changes —
not only explicit transitions (status update / close) but also the side-effect
ones from claim / unclaim / assign (each drives the BPMN via a
`transition_<STATUS>` signal), so DB and BPMN stay consistent. A status change
returning **HTTP 400** can therefore mean the BPMN rejected the transition (the
process token's current state node does not listen for that signal), not a
validation error — check the `kie-server` logs and the ticket's
`process_instance_id` state.

### DB connection saturation

```bash
# Hikari + Postgres snapshot
curl -fsS https://<host>/actuator/metrics/hikaricp.connections.active | jq '.measurements[0].value'
curl -fsS https://<host>/actuator/metrics/hikaricp.connections.pending | jq '.measurements[0].value'

docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT count(*) FROM pg_stat_activity WHERE state != 'idle';"

# Pool defaults (application.yml — post DB-7):
#   max=20, idle=5, connection-timeout=5000ms, idle-timeout=300000ms, max-lifetime=1800000ms

# Find long-running queries
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c \
  "SELECT pid, now()-query_start AS age, state, left(query,80) FROM pg_stat_activity
   WHERE state='active' AND now()-xact_start > interval '30s' ORDER BY age DESC;"

# Kill (cancel first, terminate if needed)
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c "SELECT pg_cancel_backend(<pid>);"
docker exec -it it-service-db psql -U ticketadmin -d ticketdb -c "SELECT pg_terminate_backend(<pid>);"

# Long term: increase SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE in app-secrets + rollout.
# Does Postgres max_connections support it (default 100 — backend 20, Keycloak + llm-service share the rest)
```

Related alert (OPS-5): **DBPoolExhausted**.

---

## 4. Useful commands

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

### make k8s lifecycle

```bash
make k8s-stop        # pause: stops the kind node container — PVC + etcd state preserved (no data loss)
make k8s-start       # resume a stopped cluster (pods self-recover); k8s-down DELETES the cluster + all data
make k8s-seed-roles  # (re-)assign Keycloak realm roles to the federated LDAP users — k8s equivalent of `make seed-roles`
make k8s-redeploy-kjar  # re-register the BPMN container on the KIE Server
```

`make k8s-up` builds **6** local images (backend, llm, frontend, openldap,
keycloak, kie-server) and auto-runs two idempotent one-shot Jobs on apply:
`kjar-deploy` (registers the BPMN container) and `seed-roles` (assigns realm
roles). `make seed-roles` is **compose-only** and cannot reach the in-cluster
Keycloak — use `make k8s-seed-roles` on k8s.

### Flyway / Maven

```bash
cd it-service-backend
./mvnw flyway:info     -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...
./mvnw flyway:repair   -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...
./mvnw flyway:validate -Dflyway.url=... -Dflyway.user=... -Dflyway.password=...
./mvnw test            -Dtest=ClassName#method
./mvnw verify          # full suite + JaCoCo gate (75% LINE / 65% BRANCH — OPS-7)
```

### docker compose (per service)

```bash
docker compose up -d --no-deps --build it-service-backend
docker compose exec it-service-db psql -U ticketadmin -d ticketdb
docker compose exec redis redis-cli
docker compose logs -f --tail=200 it-service-backend
docker compose restart keycloak-iam
docker compose run --rm it-service-backend env | grep -i spring
docker compose down -v          # DANGER: deletes volumes (Postgres data, LDAP, etc.)
```

### Quick health dashboard

```bash
for svc in it-service-backend llm-service kie-server; do
  echo "=== $svc ==="
  kubectl -n ticketsystem get pod -l app=$svc -o jsonpath='{range .items[*]}{.metadata.name}{"\t"}{.status.phase}{"\t"}{.status.containerStatuses[0].ready}{"\n"}'
done
```

### Metric flow in OpenSearch

```bash
# Has the otel-metrics-* index been created and is it receiving documents
curl -fsS 'http://localhost:9200/_cat/indices/otel-metrics-*?v'

# Total metric document count (grows while the flow is live)
curl -fsS 'http://localhost:9200/otel-metrics-*/_count'

# If the flow is broken, check the chain in order: backend OTLP push → otel-collector → data-prepper → opensearch
docker logs --tail 50 data-prepper
```

### Dashboards (OpenSearch Dashboards)

All OpenSearch Dashboards saved objects in a single file:
`observability/opensearch-dashboards.ndjson` — 3 index patterns (`otel-logs*`,
`ss4o_traces-*`, `otel-metrics-*`), 9 visualizations and 1 combined dashboard.

In **dev** these are imported automatically: the `opensearch-dashboards-import`
one-shot service (in `docker-compose.override.yaml`, run by `make up`) waits for
OpenSearch Dashboards to be healthy, polls `/api/status` until the API is ready,
then POSTs the ndjson with `overwrite=true` and exits. It is idempotent, so it
re-runs harmlessly on every `make up`. Check it with
`docker logs opensearch-dashboards-import`.

For a clean OpenSearch, a manual re-import, or k8s (no importer service), run:

```bash
curl -s -X POST 'http://localhost:5601/api/saved_objects/_import?overwrite=true' \
  -H 'osd-xsrf: true' -F file=@observability/opensearch-dashboards.ndjson
```

Viewing: OpenSearch Dashboards → Dashboard → **Ticket System Observability**
(`http://localhost:5601` in compose, `/opensearch` in k8s) — metrics + traces + logs
in one panel. The 9 visualization titles, exactly as they appear in OpenSearch
Dashboards (five are in Turkish): Request Volume, API Response Time, Error Rate,
Service Health, İstek Hacmi, HTTP Hata Kodları, En Çok Kullanılan Endpoint'ler,
Ortalama Gecikme Tablosu, Log Seviyeleri.
