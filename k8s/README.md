# Kubernetes manifests

Kustomize-based deployment for the IT Service Desk stack.

## Layout

```
k8s/
├── base/             # Environment-agnostic manifests (single source of truth)
├── kind-config.yaml  # kind cluster config — exposes hostPort 80/443 for ingress
└── overlays/
    ├── local/        # kind cluster — single replica, small PVs, http://localhost
    └── prod/         # Managed K8s — HPA, cert-manager, sealed-secrets,
                      #   Keycloak prod mode, hardened ingress, OpenSearch NetworkPolicy
```

All resources live in the `ticketsystem` namespace and carry the
`app.kubernetes.io/part-of: ticketsystem` label.

## Prerequisites

- Docker Desktop (kind runs in a container)
- `kind` (`winget install Kubernetes.kind`)
- `kubectl` (`winget install Kubernetes.kubectl`)

No `/etc/hosts` entry needed — the local overlay uses `http://localhost`
(required for browser secure-context so Keycloak JS adapter's Web Crypto API
works) and `kind-config.yaml` maps host port 80/443 into the cluster.

## Quick start (local)

```powershell
# 1) Build the 6 local images k8s needs (backend, llm, frontend, openldap, keycloak, kie-server)
make k8s-build

# 2) Copy secrets template and fill in dev values (placeholders are usable as-is)
cp k8s/overlays/local/secrets.env.example k8s/overlays/local/secrets.env

# 3) Create cluster + apply manifests
make k8s-up

# 4) Load images into kind so pods can pull them
make k8s-load-images

# 5) Watch pods come up
kubectl -n ticketsystem get pods -w
```

Site opens at <http://localhost>.

> **Keycloak Admin Console** is intentionally NOT served through the ingress
> (only `/auth/realms` and `/auth/resources` are routed; anything else under
> `/auth` falls through to the SPA). Reach it via port-forward:
>
> ```powershell
> kubectl -n ticketsystem port-forward svc/keycloak-iam 8080:8080
> # browser: http://localhost:8080/auth/admin/
> ```

> Pods stay `Pending` / `ImagePullBackOff` until `make k8s-load-images` (step 4) finishes —
> `kind load` needs the cluster to exist first, so it runs after `make k8s-up`.
>
> The apply also creates two one-shot Jobs: **`kjar-deploy`** (registers the BPMN container on
> the KIE Server) and **`seed-roles`** (assigns realm roles to the federated LDAP users). Both
> are idempotent; re-run role assignment anytime with **`make k8s-seed-roles`**.

### Day-2 workflow

`make k8s-rebuild` is the compose-`make rebuild` equivalent: idempotent, preserves
PVCs, handles every state (cluster missing, cluster container stopped, cluster
running). Use it after:

- A PC restart (Docker Desktop must be running first)
- Any code change in backend / llm / frontend / themes / kjar / LDAP seed
- Any change to `k8s/` manifests, configmaps, or `secrets.env`

Sequence: `k8s-ensure` (create or start) → `k8s-build` (all 6 images) →
`k8s-load-images` (push to kind) → `k8s-apply` (kustomize render + apply) →
`k8s-restart-all` (rolling restart deployments + statefulsets) →
`k8s-redeploy-kjar` (KIE H2 wipes container registration on restart).

For single-service iteration without full rebuild, target one image:
```powershell
docker compose build it-service-backend
kind load docker-image local/it-service-backend:latest --name ticketsystem
kubectl -n ticketsystem rollout restart deploy/it-service-backend
```

### Stopping & resuming

- **Pause without losing data:** `make k8s-stop` — stops the kind node container
  (`ticketsystem-control-plane`). All PVCs (Postgres `ticketdb`/`keycloakdb`, etc.) and
  cluster/etcd state live inside that container, so they survive. Resume with
  **`make k8s-start`**; pods recover on their own.
- **Tear down everything:** `make k8s-down` — **deletes** the kind cluster, including all
  PVCs and data. Use this only when you want a clean slate (next bringup re-seeds from scratch).

> After a full Docker Desktop / PC restart the kind container usually auto-starts; if not,
> `make k8s-start` (or `make k8s-rebuild`, which also handles a missing/stopped cluster).

### Keycloak secrets & LDAP federation

The realm import injects the `ticket-client` service-account secret and the LDAP
`bindCredential` straight from `app-secrets` (`KEYCLOAK_ADMIN_CLIENT_SECRET` and
`LDAP_ADMIN_PASSWORD`, set in `k8s/overlays/local/secrets.env`), so no Keycloak
admin-console configuration is needed — just make sure both are set before
`make k8s-up`.

The eight federated LDAP users — `customer`, `agent`, `lead`, `manager`, `admin`,
`adminmanager`, `leadmanager`, `superadmin` (all password `321654`) — get their
realm roles from the in-cluster `seed-roles` Job, which runs automatically as part
of `make k8s-up` (roles are not stored in LDAP). To (re-)run it manually use
**`make k8s-seed-roles`** (NOT `make seed-roles`, which is the docker-compose
variant and cannot reach the in-cluster Keycloak).

## Manifests intentionally excluded vs docker-compose

- **`data-generator`** — local-only demo seeding tool, never deployed to a
  cluster. Run via `make gen` directly on the host.
- **`jbpm-db`** — KIE Server runs against in-memory H2 (`JAVA_OPTS`
  `ExampleDS` + `H2Dialect`). No external Postgres for jBPM in k8s.
- **`nginx-proxy`** — replaced by ingress-nginx + the base `Ingress` resource.

## Custom images

`make k8s-build` produces six local images:

| Image                              | Source                                  |
|------------------------------------|-----------------------------------------|
| `local/it-service-backend:latest`  | `docker compose build` (backend)        |
| `local/llm-service:latest`         | `docker compose build` (llm)            |
| `local/it-service-frontend:latest` | `docker compose build` (frontend)       |
| `local/openldap-server:latest`     | `Dockerfile-ldap` (LDAP seed gomulu)    |
| `local/keycloak-iam:latest`        | `Dockerfile-keycloak` (custom tema)     |
| `local/kie-server:latest`          | `Dockerfile-kie` (ticket-workflow kjar) |

The last three replace bind mounts that docker-compose uses:
- LDAP seed is baked into the openldap image (compose mounts `ldap-init/`)
- Keycloak `it-service-desk` theme is baked in (compose mounts `keycloak-themes/`)
- KIE Server's Maven repo gets the kjar baked in (compose mounts `~/.m2/repository`)

`make k8s-build` also runs `mvn package` for `ticket-workflow-kjar/` before
building `local/kie-server:latest`, so the kjar JAR is always fresh.

## Secrets

Local secrets are sourced from `k8s/overlays/local/secrets.env` (gitignored);
create it by copying `k8s/overlays/local/secrets.env.example`, which is also
the canonical list of keys the `app-secrets` Secret must provide.
Production uses SealedSecrets committed under `k8s/overlays/prod/`
(`kustomization.yaml` has the bootstrap instructions) — seal a plain Secret
built from the same key list.

## Production overlay

`k8s/overlays/prod/` layers production-only hardening on top of `base/`:

- **Keycloak prod mode** (`patches/keycloak-prod.yaml`) — overrides the base
  `start-dev` args with `start --import-realm`. TLS terminates at the ingress;
  Keycloak listens on HTTP (`KC_HTTP_ENABLED=true`) and trusts forwarded headers,
  with `KC_HOSTNAME_STRICT=false` so admin/redirect URLs aren't forced to HTTPS.
  `--optimized` is intentionally not used (stock image auto-builds on `start`).
- **Hardened ingress** — the JSON6902 patch in `kustomization.yaml` removes the
  dev/observability paths (`/swagger-ui`, `/v3/api-docs`, `/mailpit`,
  `/opensearch`) from the public host that base defines for dev, so they aren't
  reachable unauthenticated in prod.
- **OpenSearch isolation** (`patches/networkpolicy-opensearch.yaml`) — since the
  OpenSearch security plugin is disabled in base, a `NetworkPolicy` restricts
  `:9200` to known observability clients (otel-collector, data-prepper, logstash,
  opensearch-dashboards). Requires a NetworkPolicy-aware CNI (Calico/Cilium).
- **Immutable image tags** — the CD pipeline runs `kustomize edit set image` to
  pin each image to the git commit SHA, avoiding silent `:latest` downgrades.
