# Kubernetes manifests

Kustomize-based deployment for the IT Service Desk stack.

## Layout

```
k8s/
├── base/         # Environment-agnostic manifests (single source of truth)
└── overlays/
    ├── local/    # kind cluster — single replica, small PVs, localhost ingress
    └── prod/     # Managed K8s — HPA, cert-manager, sealed-secrets
```

All resources live in the `ticketsystem` namespace and carry the
`app.kubernetes.io/part-of: ticketsystem` label.

## Quick start (local)

```bash
# 1) Render manifests
kubectl kustomize k8s/overlays/local

# 2) Apply to a kind cluster
kubectl apply -k k8s/overlays/local
```

The repo's `Makefile` exposes `make k8s-up` / `make k8s-down` wrappers.

## Secrets

Local secrets are sourced from `k8s/overlays/local/secrets.env` (gitignored).
Copy from `secrets.env.example` and fill in dev values; production uses
SealedSecrets committed under `k8s/overlays/prod/`.
