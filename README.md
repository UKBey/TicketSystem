# 🎫 IT-Service Desk

**English** · [Türkçe](README.tr.md)

> A full-stack, multi-role **IT Service Management (ticketing) platform** — Keycloak-secured, jBPM-orchestrated, AI-assisted, and fully observable.

![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![React Native](https://img.shields.io/badge/React%20Native-Expo-000020?logo=expo&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql&logoColor=white)
![Keycloak](https://img.shields.io/badge/Keycloak-24-4D4D4D?logo=keycloak&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)
![Kubernetes](https://img.shields.io/badge/Kubernetes-kind-326CE5?logo=kubernetes&logoColor=white)

Customers report technical problems, support agents resolve them under **SLA** rules, and managers monitor the operation through **live dashboards**. The system is built as a containerised, polyglot monorepo that demonstrates a production-shaped full-stack architecture: identity federation, workflow orchestration, asynchronous processing, observability, and an AI summarisation service.

---

## 📑 Table of Contents

- [Key Features](#-key-features)
- [Architecture at a Glance](#-architecture-at-a-glance)
- [Technology Stack](#-technology-stack)
- [Getting Started](#-getting-started)
- [Local Development](#-local-development)
- [Project Structure](#-project-structure)
- [Testing & Quality](#-testing--quality)
- [Deployment](#-deployment)
- [Screenshots](#-screenshots)
- [Documentation](#-documentation)
- [License](#-license)

---

## ✨ Key Features

### Ticketing & Lifecycle
- Full ticket lifecycle as a state machine: `NEW → IN_PROGRESS → WAITING_FOR_CUSTOMER → RESOLVED → CLOSED`
- Each ticket runs as a **jBPM process instance** — every status transition is driven through the workflow engine, including the side-effect transitions caused by claim / unclaim / assignment
- **Claim/pool model** — agents pull tickets from a product-scoped pool; multiple agents can collaborate on one ticket
- Manual assignment with agent capacity checks, resolution notes, and a full **audit trail**

### SLA Management
- Per-priority SLA policies (`CRITICAL` / `HIGH` / `MEDIUM` / `LOW`) with configurable resolution and warning thresholds
- SLA clock **pauses** while a ticket is `WAITING_FOR_CUSTOMER` or `RESOLVED`, resumes on `IN_PROGRESS` with the paused time preserved (only active time counts down), and stops permanently once the ticket is `CLOSED`
- A scheduler flags approaching and breached SLAs; the UI shows colour-coded countdown badges

### Communication & Files
- Chat-style ticket conversation with **internal** (agent-only) and **external** (customer-visible) comments
- Attachment upload with **content validation** — file type/size checks, keyword scan, and sensitive-data detection (tokens, private keys)
- Worklog time tracking per agent
- **PDF export** of ticket detail — configurable sections (comments, worklogs, attachments, audit log), language and orientation; preferences persisted per user so the modal reopens with last-used selections

### Notifications
- Multi-channel notifications (in-app feed + email simulation via Mailpit) for ticket, status, comment and SLA events
- Per-user notification preferences — **scoped to the user's role union** so only relevant event rows are shown — and **fully localised** notification content (rendered in the recipient's current language)

### Personalization
- **Light / dark theme** and **English / Turkish** UI, both shared with the Keycloak login screens
- **User-selectable date format** applied to *every* date across the UI (`31/12/2026`, `12/31/2026`, `2026-12-31`, `31.12.2026`, or a localized month name) — persisted per user and synced across devices
- Theme, language, date format and notification preferences are all set from the **profile** page

### AI Assistance
- Dedicated `llm-service` generates **AI ticket summaries** (Groq / Llama 3.1) in Turkish or English

### Dashboards & Reporting
- **Role-scoped dashboards** — a personal **Overview** for customers and a **My Performance** dashboard for agents, plus the global manager dashboard; admins, managers and leads can **drill into** any user's, agent's or product's dashboard (leads stay product-scoped)
- KPI summary, status distribution, ticket timeline, priority-SLA breakdown, agent performance leaderboard, product metrics, CSAT analytics (distribution / trend), daily-worklog charts and **configurable stuck-ticket alerts** — KPIs scope to a selected **date range**, all Caffeine-cached

### Security & Identity
- **Keycloak** SSO with users federated from **OpenLDAP**, OAuth2/OIDC, JWT, **2FA (TOTP)** and "remember me"
- **Additive multi-role** access control: a user holds a *set* of roles and their effective permissions are the **union**. Five roles — `customer`, `agent`, `lead_agent`, `admin`, `manager` — span the operational, configuration and oversight axes; `lead_agent` is a Keycloak composite of `agent`. `customer` is a **singleton** role — mutually exclusive with the staff roles — while the staff roles combine freely. Roles live in Keycloak and are cached in the `user_roles` table (Flyway V37), synced on `/users/sync`.
- Distributed **rate limiting** (Bucket4j + Redis), method-level authorization (`@PreAuthorize` + `AuthRoles` helpers), internal service-to-service token auth

### Platform
- Web (React) **and** mobile (React Native / Expo) clients with functional parity
- Internationalisation (English / Turkish) and light/dark theming across the SPA **and** the Keycloak login screens
- End-to-end **observability**: structured logs, distributed traces and metrics in OpenSearch

---

## 🏗 Architecture at a Glance

All external traffic enters through a single **nginx** reverse proxy on port `80`.

```mermaid
flowchart TB
    web[Web SPA<br/>React 19] --> nginx
    mobile[Mobile App<br/>React Native] --> nginx

    nginx[nginx-proxy · :80<br/>single entry point]

    nginx --> be[it-service-backend<br/>Spring Boot 4 · :8081]
    nginx --> llm[llm-service<br/>Spring Boot 3 · :8082]
    nginx --> kc[Keycloak 24<br/>/auth]

    be --> pg[(PostgreSQL<br/>ticketdb)]
    be --> redis[(Redis)]
    be --> kie[KIE Server<br/>jBPM workflow]
    be --> mail[Mailpit / SMTP]
    llm --> pg
    llm --> groq[Groq API · LLM]
    kc --> ldap[(OpenLDAP)]
    kc --> kcdb[(PostgreSQL<br/>keycloakdb)]
    kie --> jbpmdb[(PostgreSQL<br/>jbpm-db)]
    kie -. workflow callback .-> be

    be --> obs[Observability pipeline<br/>Kafka · OTEL Collector · Logstash<br/>Data Prepper → OpenSearch]
    llm --> obs
```

> A detailed breakdown — container diagram, request flows, security model and design decisions — lives in **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)**.

---

## 🛠 Technology Stack

| Layer | Technologies |
|-------|--------------|
| **Backend API** | Java 21, Spring Boot 4, Spring Security (OAuth2 Resource Server), Spring Data JPA, Flyway, WebSocket/STOMP, Caffeine, Bucket4j, Resilience4j |
| **AI Service** | Java 21, Spring Boot 3, Groq API (Llama 3.1) |
| **Web Frontend** | React 19, Vite, Tailwind CSS 4, React Router 7, i18next, keycloak-js, Recharts |
| **Mobile** | React Native, Expo |
| **Workflow** | jBPM / KIE Server 7.61, BPMN 2.0 |
| **Data** | PostgreSQL 15, Redis 7 |
| **Identity** | Keycloak 24, OpenLDAP |
| **Messaging / Logs** | Apache Kafka, Logstash |
| **Observability** | OpenTelemetry, OpenSearch + Dashboards, Data Prepper, Log4j2 |
| **Infrastructure** | Docker Compose, Kubernetes (kind + Kustomize), nginx |
| **Quality / CI** | JUnit 5, Mockito, Testcontainers, Vitest, JaCoCo, SonarQube, GitHub Actions |

---

## 🚀 Getting Started

> **Running on Kubernetes instead?** This guide covers the Docker Compose path (the default for local development & demos). To deploy on Kubernetes (kind + Kustomize — `make k8s-up`, in-cluster Keycloak seeding with `make k8s-seed-roles`, etc.), follow **[k8s/README.md](k8s/README.md)**.

### Prerequisites

- **Docker** & Docker Compose
- **Make**
  - **Windows:** install via [GnuWin32 Make](http://gnuwin32.sourceforge.net/packages/make.htm) or `choco install make`. The Makefile uses Windows-friendly commands (`mvnw.cmd`, `cmd /k`, `rmdir`).
  - **macOS / Linux:** pre-installed or `brew install make` / `apt install make`. Replace `mvnw.cmd` with `./mvnw` in the targets you invoke directly.
- For local (non-Docker) development: **JDK 21**, **Node.js 22+**
- A **Groq API key** ([console.groq.com/keys](https://console.groq.com/keys)) — only needed for the AI summary feature

### 1. Configure the environment

```bash
cp .env.example .env
```

Fill in the placeholder values in `.env` (database passwords, LDAP/Keycloak passwords, `GROQ_API_KEY`, etc.). Every variable is documented inline in `.env.example`.

### 2. Start the full stack

```bash
make up        # start everything in Docker
make ps        # list running containers
make logs s=it-service-backend   # tail one service
```

The first start pulls images, runs Flyway migrations and imports the Keycloak realm — give it a couple of minutes. Use `make rebuild` after changing application code, and `make down` to stop.

> **Dev vs. prod compose.** Compose is split into three files following the standard override pattern:
> - `docker-compose.yaml` — environment-agnostic **base** (pinned images, core config).
> - `docker-compose.override.yaml` — **dev** layer, *auto-loaded* by `make up` / `docker compose up`: host-published ports, build-from-source, the dev tools (mailpit, phpldapadmin, OpenSearch Dashboards, SonarQube) and dev settings (Swagger, SQL logging, mailpit SMTP). So `make up` stays pure dev — nothing changes.
> - `docker-compose.prod.yaml` — **prod** layer, applied explicitly with `make up-prod` (`-f docker-compose.yaml -f docker-compose.prod.yaml`): pulls pinned images, exposes **only** nginx (80 + 443/TLS), drops every dev tool, runs Keycloak in production mode and adds `restart` policies. Images must be in a registry first (the CD pipeline pushes them to Docker Hub on `main`; selected via `DOCKERHUB_USERNAME`/`IMAGE_TAG`) and a prod `.env` prepared (KC hostname, real SMTP, strong secrets). For full production (autoscaling, secrets, cert-manager) use the **[k8s prod overlay](k8s/README.md)**.

### 3. Assign realm roles to the seed users (one-time, after the first `make up`)

> **No Keycloak admin-console setup required.** The realm import injects the **LDAP bind credential** and the **`ticket-client` service-account secret** straight from your `.env` (`LDAP_ADMIN_PASSWORD`, `KEYCLOAK_ADMIN_CLIENT_SECRET`) — just make sure both are set before the first `make up`.

The only remaining one-time step: LDAP users land in Keycloak without realm roles — **roles are not stored in LDAP**. Run the idempotent seeder job, which pulls the users in from LDAP **and** assigns their roles in one shot:

```bash
make seed-roles    # one-shot keycloak-seeder (tools profile); imports LDAP users + assigns roles; safe to re-run
```

This maps every seed user to the role(s) below. Roles are **additive** — a user may hold several, and effective permissions are their union.

| Username | Realm role(s) |
|----------|---------------|
| `customer`     | `customer` |
| `agent`        | `agent` |
| `lead`         | `lead_agent` (composite of `agent`) |
| `manager`      | `manager` |
| `admin`        | `admin` |
| `adminmanager` | `admin` + `manager` |
| `leadmanager`  | `lead_agent` + `manager` |
| `superadmin`   | `admin` + `lead_agent` + `manager` |

You can now log in at http://localhost.

### 4. Access points

| Service | URL |
|---------|-----|
| **Web application** | http://localhost |
| API (Swagger UI) | http://localhost/swagger-ui/index.html |
| Keycloak | http://localhost/auth |
| Keycloak Admin Console | http://localhost/auth/admin — login: `admin` / `KEYCLOAK_ADMIN_PASSWORD` (from `.env`) |
| Mailpit (captured e-mails) | http://localhost:8025 |
| OpenSearch Dashboards | http://localhost:5601 |
| phpLDAPadmin | http://localhost:8085 — login: `cn=admin,dc=ticketsystem,dc=com` / `LDAP_ADMIN_PASSWORD` |
| KIE Server (jBPM workflow API) | http://localhost:8180/kie-server/docs |
| SonarQube (opt-in) | http://localhost:9000 |

### 5. Seed demo data

```bash
make gen       # build + run the data generator (products, tickets, history)
```

> **Tip:** the generator runs fastest with the backend global rate limit disabled (it fires
> requests with no pacing). Before `make gen`, set `RATE_LIMIT_GLOBAL_MAX_REQUESTS=10000000`
> (or `RATE_LIMIT_GLOBAL_ENABLED=false`) in `.env` and restart the backend. It still works with
> the limit on — 429s are auto-retried — just slower. See [data-generator/README.md](data-generator/README.md#requirements).

### 6. Demo users

Users are seeded into OpenLDAP and federated into Keycloak; their realm **roles are assigned by `make seed-roles`** (see step 3) — roles are not stored in LDAP. All seed users share the password `321654`. Roles are **additive** — a user holds a set of roles, and their effective permissions are the union of them.

| Username | Password | Role(s) | Lands on | Capabilities |
|----------|----------|---------|----------|--------------|
| `customer`     | `321654` | `customer` | Overview | Personal **Overview** dashboard; raise & track own tickets (product-scoped), comment, attach files, submit CSAT |
| `agent`        | `321654` | `agent` | My Performance | Personal **My Performance** dashboard; claim tickets and act only on claimed tickets (product-scoped); change status, worklog, internal notes, AI summary |
| `lead`         | `321654` | `lead_agent` | My Performance + Team | Composite of `agent` **+** assign tickets to agents, act on tickets without claiming, manage product content (topics / known-issues / shared canned-responses) and a product-scoped team dashboard |
| `manager`      | `321654` | `manager` | Dashboard | Oversight (global, read-only): all dashboards, reports and full read visibility — no operational actions, no system config |
| `admin`        | `321654` | `admin` | Workspace + Admin | System configuration (global): create users, assign roles, create/manage products, grant product access, agent limits, SLA / cache |
| `adminmanager` | `321654` | `admin` + `manager` | Workspace + Admin | Admin configuration **+** manager oversight (union) |
| `leadmanager`  | `321654` | `lead_agent` + `manager` | My Performance + Team + Dashboard | Lead-agent operations **+** manager oversight (union) |
| `superadmin`*  | `321654` | `admin` + `lead_agent` + `manager` | Workspace + Admin | Full super-admin: union of admin config, lead-agent operations and manager oversight |

> *The data-generator bootstrap account is `superadmin`, which holds `admin` + `lead_agent` + `manager` and therefore behaves as a super-admin. A "super-admin" is simply a user that holds all three of these roles — there is no dedicated super-admin role.

---

## 💻 Local Development

For hot-reload development, run the infrastructure in Docker and the apps on the host:

```bash
make infra         # start only infra containers (DB, Keycloak, Redis, jBPM, OpenSearch...)
make dev-backend   # run the backend on the host  (Spring Boot :8081)
make dev-frontend  # run the web frontend on the host (Vite :3000)
make dev-mobile    # start the Expo dev server for the mobile app
```

The Vite dev server proxies `/api/v1` → `localhost:8081` and the WebSocket endpoint accordingly.

---

## 📂 Project Structure

```text
TicketSystemProject/
├── it-service-backend/      # Spring Boot 4 — main REST API (:8081)
├── llm-service/             # Spring Boot 3 — AI summarisation service (:8082)
├── it-service-frontend/     # React 19 + Vite — web SPA
├── it-service-mobile/       # React Native + Expo — mobile app
├── ticket-workflow-kjar/    # jBPM BPMN process definition (ticket-lifecycle)
├── data-generator/          # Standalone demo-data seeder
├── keycloak-init/           # Keycloak realm import
├── keycloak-themes/         # Custom Keycloak login theme
├── ldap-init/               # OpenLDAP bootstrap (seed users)
├── nginx/                   # Reverse proxy configuration
├── observability/           # OpenSearch dashboards / saved objects
├── data-prepper/            # Metrics pipeline configuration
├── k8s/                     # Kubernetes manifests (Kustomize base + overlays)
├── dev_plans/               # Design & planning documents
├── docs/                    # Architecture & technical documentation
├── docker-compose.yaml      # Full-stack orchestration (base)
├── docker-compose.override.yaml  # Dev overlay (auto-loaded: ports, build, dev tools)
├── docker-compose.prod.yaml      # Prod overlay (make up-prod: pinned images, nginx-only)
├── Makefile                 # Canonical command entry point
└── RUNBOOK.md               # Operations & incident playbooks
```

---

## 🧪 Testing & Quality

```bash
make test            # backend unit tests + frontend tests
make verify          # backend unit + integration tests (Testcontainers) + JaCoCo report
make lint            # frontend ESLint
make ci              # full local CI gate: verify + frontend tests + lint
make sonar-up        # start SonarQube, then: make sonar
```

- **Backend** — JUnit 5 + Mockito unit tests; `*IT.java` integration tests run against a real PostgreSQL via **Testcontainers**. Coverage report: `it-service-backend/target/site/jacoco/index.html`.
- **Frontend** — Vitest + Testing Library (jsdom).
- **CI** — GitHub Actions runs backend `verify`, llm-service build, and frontend lint/test/build on every pull request.

---

## 📦 Deployment

| Path | Command | Notes |
|------|---------|-------|
| **Docker Compose (dev)** | `make up` / `make rebuild` | Single-host dev/demo; auto-loads `docker-compose.override.yaml` (ports, build, dev tools) |
| **Docker Compose (prod)** | `make up-prod` | base + `docker-compose.prod.yaml`: pinned images, only nginx exposed (80/443), no dev tools, Keycloak prod mode, `restart` policies |
| **Kubernetes** | `make k8s-up` | kind cluster + Kustomize (`k8s/overlays/local`); a `prod` overlay adds HPA, cert-manager and SealedSecrets |
| **CI/CD** | GitHub Actions | CI on every PR; CD builds and pushes Docker Hub images on `main` |

See **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** for deployment topology and **[RUNBOOK.md](RUNBOOK.md)** for operational procedures.

---

## 📸 Screenshots

### Authentication

![Login screen with language and light/dark theme switcher](docs/screenshots/login.png)
*Login — language and light/dark theme switcher.*

![TOTP 2FA prompt during sign-in](docs/screenshots/2FA.png)
*TOTP 2FA prompt during sign-in.*

### Customer

![Customer My Tickets](docs/screenshots/my_tickets.png)
*My Tickets — the customer's own tickets with status / priority filters.*

![Create ticket modal](docs/screenshots/create_ticket.png)
*Create-ticket modal — product, topic and priority selection.*

![Customer ticket detail](docs/screenshots/ticket_detail_customer.png)
*Ticket detail — conversation timeline, SLA countdown badge and attachments.*

![CSAT survey after resolution](docs/screenshots/CSAT.png)
*CSAT survey shown after a ticket is RESOLVED — 1-5 rating + comment.*

### Agent / Lead Agent

![Agent Workspace](docs/screenshots/workspace.png)
*Workspace — tickets the agent has claimed.*

![Ticket pool](docs/screenshots/pool.png)
*Pool — unclaimed tickets ready to be picked up.*

![Agent ticket detail](docs/screenshots/ticket_detail_agent.png)
*Ticket detail from the agent's perspective — internal note tab and worklog panel.*

![AI summary modal](docs/screenshots/ai_summary.png)
*AI summary modal — Groq-generated ticket summary.*

![Canned responses](docs/screenshots/canned_responses.png)
*Canned responses — reusable reply templates (personal + shared, product-scoped).*

### Admin

![Admin panel overview](docs/screenshots/admin_panel.png)
*Admin panel — entry point to user / product / SLA / rate-limit management.*

![User management](docs/screenshots/user_management.png)
*User management — multi-role assignment, agent capacity and product authorizations.*

![Product & topic configuration](docs/screenshots/product_panel.png)
*Product & topic configuration — CRUD on the categories customers can file tickets against.*

![Products catalogue](docs/screenshots/products.png)
*Products — the catalogue of products customers can raise tickets against.*

![Known issues knowledge base](docs/screenshots/known_issues.png)
*Known issues editor — the knowledge base attached to every product/topic.*

### Manager

![Dashboard KPI cards](docs/screenshots/dashboard1.png)
*Dashboard — KPI cards and completion meters (date-range scoped).*

![Dashboard charts](docs/screenshots/dsahboard2.png)
*Dashboard — status distribution, CSAT distribution/trend and ticket-timeline charts.*

### Account & Notifications

![Profile page](docs/screenshots/profile.png)
*Profile — language / theme preferences, password change and 2FA management.*

![Notification dropdown](docs/screenshots/in_app_notifications.png)
*In-app notification feed (bell icon) — live STOMP updates with unread badge.*

![Mailpit captured e-mail](docs/screenshots/mailpit_mail.png)
*Captured ticket / SLA e-mail in Mailpit (the dev SMTP catcher).*

---

## 📚 Documentation

| Document | Purpose |
|----------|---------|
| **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** | System architecture, request flows, security model, design decisions |
| **[docs/API.md](docs/API.md)** | REST API reference — endpoints, parameters and example responses |
| **[docs/WORKFLOW.md](docs/WORKFLOW.md)** | jBPM / BPMN ticket-lifecycle workflow design |
| **[docs/CICD.md](docs/CICD.md)** | CI/CD pipeline design (GitHub Actions) |
| **[RUNBOOK.md](RUNBOOK.md)** | Operations & incident playbooks (DB backup, Keycloak re-import, Flyway repair...) |
| **API reference** | Interactive OpenAPI / Swagger UI at `http://localhost/swagger-ui/index.html` |

---

## 📄 License

© 2026 Ukbe Taha ŞAHİNKAYA. All rights reserved.

This project was developed as an **educational and portfolio project**. It is shared **solely for evaluation and learning purposes**. It may **not** be used, copied, modified, distributed, or deployed — in whole or in part — for any **commercial or for-profit purpose** without the prior written permission of the author.

See the [LICENSE](LICENSE) file for the full terms.
