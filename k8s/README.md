# Kubernetes manifests

Kustomize-based deployment for the IT Service Desk stack.

## Layout

```
k8s/
├── base/             # Environment-agnostic manifests (single source of truth)
├── kind-config.yaml  # kind cluster config — exposes hostPort 80/443 for ingress
└── overlays/
    ├── local/        # kind cluster — single replica, small PVs, http://localhost
    └── prod/         # Managed K8s — HPA, cert-manager, sealed-secrets
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
# 1) Build the 5 images k8s needs (backend, llm, frontend, openldap, keycloak)
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

### First-time Keycloak setup

`realm-export.json` ships with the `ticket-client` confidential client's secret
masked (`"**********"`). After the first bringup you must regenerate it:

1. Open <http://localhost/auth/admin> (admin / value from `KEYCLOAK_ADMIN_PASSWORD`)
2. Realm switcher → `TicketSystemRealm`
3. **Clients → ticket-client → Credentials → Regenerate** → copy the secret
4. Write it to `k8s/overlays/local/secrets.env` as `KEYCLOAK_ADMIN_CLIENT_SECRET=...`
5. Re-apply secrets and restart the backend:
   ```powershell
   kubectl --context kind-ticketsystem kustomize k8s/overlays/local --load-restrictor=LoadRestrictionsNone | kubectl --context kind-ticketsystem apply -f -
   kubectl -n ticketsystem rollout restart deploy/it-service-backend
   ```

### LDAP user federation

The LDAP `bindCredential` is also masked in the realm export. In Keycloak admin
UI: **User Federation → ldap → Bind credential** = your `LDAP_ADMIN_PASSWORD`
(default `321654`) → Save → **Action: Sync all users**. Eight users will appear:
`customer`, `agent`, `lead`, `manager`, `admin`, `adminmanager`, `leadmanager`,
`superadmin` (all password `321654`). Their realm roles are not stored in LDAP —
they are assigned by the in-cluster `seed-roles` Job, which runs automatically as
part of `make k8s-up`. To (re-)run it manually use **`make k8s-seed-roles`**
(NOT `make seed-roles`, which is the docker-compose variant and cannot reach the
in-cluster Keycloak).

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

Local secrets are sourced from `k8s/overlays/local/secrets.env` (gitignored).
Production uses SealedSecrets committed under `k8s/overlays/prod/`
(`kustomization.yaml` has the bootstrap instructions).
