# Ticket System — Data Generator

Standalone Java application that seeds the system with realistic demo/test data
for presentations and testing. It runs with **a single bootstrap admin account**
(`superadmin`); seed users, products, topics, known issues, canned responses and
tickets are all created idempotently through that account. In the additive role
model this account holds the `ADMIN + LEAD_AGENT + MANAGER` roles (it replaces the
deprecated `aatest` super-admin account), so it has both configuration and
product-content/assignment authority.

Agent, lead-agent and customer users are **created by the generator itself** via
the backend's Agent Admin API (temporary password → forced first-login change →
final password). The only accounts that must already exist are the three named
accounts: the bootstrap admin, the Keycloak master admin, and the database user.

## Requirements

- Java 17+
- A running Ticket System stack (`docker compose up -d` or `make up`)
- Access to PostgreSQL (port 5432 must be reachable — the date backfill connects directly)

## Quick start

### 1. Prepare users.json

Copy `data-generator/users.example.json` to `data-generator/users.json` and adjust
the passwords for your environment. This single file lists the three named login
accounts (bootstrap admin, Keycloak master admin, DB user) **plus the full
definitions of every seed user** the generator should create (lead agents, agents,
customers):

```bash
cd data-generator
cp users.example.json users.json       # Linux/Mac
copy users.example.json users.json     # Windows
```

`users.json` structure:

```json
{
  "adminAgent":    { "username": "superadmin",  "password": "321654" },
  "keycloakAdmin": { "username": "admin",       "password": "321654" },
  "database":      { "username": "ticketadmin", "password": "321654" },
  "agents": [
    { "username": "agent1.gen", "email": "agent1.gen@ticketsystem.local",
      "firstName": "Alice", "lastName": "Turner", "password": "321654Aa!" }
  ],
  "leads": [
    { "username": "lead1.gen", "email": "lead1.gen@ticketsystem.local",
      "firstName": "Dylan", "lastName": "Archer", "password": "321654Aa!" }
  ],
  "customers": [
    { "username": "customer1.gen", "email": "customer1.gen@ticketsystem.local",
      "firstName": "Michael", "lastName": "Shaw", "password": "321654Aa!" }
  ]
}
```

> `users.json` is gitignored; it is never committed. `users.example.json` always
> stays in the repo (with placeholder passwords).
>
> - The three **named accounts** (`adminAgent` / `keycloakAdmin` / `database`) are
>   objects with `username` + `password`. They must already exist:
>   - `superadmin` must hold the `ADMIN + LEAD_AGENT + MANAGER` roles (LDAP seed +
>     `make seed-roles`) and must have **logged in at least once**.
>   - `admin` is the Keycloak master-realm admin (used only to clear required
>     actions on freshly created users).
>   - `ticketadmin` is the PostgreSQL user (used for the date backfill).
> - The **seed users** (`agents` / `leads` / `customers`) are arrays of full user
>   objects (`username`, `email`, `firstName`, `lastName`, `password`). The
>   generator creates each one; existing ones (HTTP 409) are reused. Passwords must
>   satisfy the Keycloak realm password policy (e.g. `321654Aa!`).

If `users.json` is missing, or an account/field is omitted, the defaults in
`GeneratorConfig.java` are used (all passwords fall back to `321654`). The seed-user
arrays have no default — if they are empty the run aborts (at least one agent and
one customer are required).

### 2. Build and run

```bash
cd data-generator
..\it-service-backend\mvnw.cmd package -q        # Windows
../it-service-backend/mvnw package -q             # Linux/Mac
java -jar target/data-generator-1.0.0.jar
```

From the repo root: `make gen` (Docker — builds and runs the `data-generator`
compose service) or `make gen-host` (host JVM — `gen-build` + `gen-run`).

---

## What it does

On every run the generator applies the following steps in order. Each step is
**idempotent** and does not touch existing records (except the cleanup in step 2,
which only removes products it created in a previous run).

### 1. Users

The agents, lead agents and customers defined in `users.json` are **created** via
the backend Agent Admin API (`POST /api/users/admin/create`):

- Each user is created with a temporary password (`TEMP_PASSWORD`, forced change on
  first login). The generator then completes that first-login change by setting the
  user's final password (from `users.json`) and clearing the required action, then
  logs in.
- Already-existing users return HTTP 409 and are reused (password reset + required
  action cleared, then login).
- Lead agents are created with the `LEAD_AGENT` realm role (a Keycloak composite
  that includes `AGENT`), so they also operate as agents and are added to the agent
  pool for claim/assignment.
- If a user cannot be created or logged in, it is skipped with a warning and the run
  continues with the rest. After login each user is pushed to the DB via
  `/users/sync`.

> The run aborts if **no** agent or **no** customer could be logged in — at least
> one of each is required.

### 2. Products / topics / known issues

First, any products this generator created in a previous run (matched by the names
in `setup.json`) are **deleted** so each run starts clean — the backend's product
delete cascades to the attached tickets/comments/worklogs/CSAT. Products created by
anything other than the generator are left untouched.

Then, from the `products` list in `src/main/resources/setup.json`:

- **5 products** (VPN & Network, Email & Communication, Hardware & Infrastructure,
  Enterprise Software, Cloud Services) — idempotent by `name`.
- **5 topics** under each product (25 total) — idempotent by `(productId, name)`.
- ~12 **known-issue** records per topic (~300 total, title + content) — idempotent
  by title within the product.

### 3. Canned responses

From the `cannedResponses` block in `setup.json`, shared (`SHARED`) canned-response
templates are created idempotently (by title):

- **10 global** templates (no product) — visible in every product context.
- **5 per-product** templates × 5 products. The `{product}` token in the title and
  content is replaced with the product name.

### 4. Product authorization

Every agent, lead agent and customer that logged in is granted access to all
products. HTTP 409 (already assigned) is skipped silently.

### 5. Ticket generation (JSON template based)

The **51 ticket templates** in `src/main/resources/tickets/ticket-NNN.json` are
processed. Each file declaratively describes a ticket's full lifecycle:

```json
{
  "title": "VPN connection keeps dropping",
  "description": "Detailed description...",
  "priority": "HIGH",
  "productName": "VPN & Network",
  "topicName": "VPN Connection",
  "status": "RESOLVED",
  "reasonCode": "SOLUTION_PROVIDED",
  "worklogs":  [{ "minutes": 25, "description": "..." }],
  "comments":  [{ "author": "agent", "type": "EXTERNAL", "message": "..." }],
  "resolutionNote": "...",
  "csat":     { "rating": 5, "comment": "..." }
}
```

Templates are processed in the order CLOSED → RESOLVED → WAITING_FOR_CUSTOMER →
IN_PROGRESS → NEW so the per-agent active-claim limit is not exhausted before all
types are created. The 51 templates break down as:

| Status | Count |
|--------|-------|
| CLOSED | 15 |
| RESOLVED | 10 |
| IN_PROGRESS | 10 |
| WAITING_FOR_CUSTOMER | 8 |
| NEW | 8 |

> `ticket-051.json` is a deliberately long, **23-comment** CLOSED case (an Outlook /
> Exchange disconnect saga with 6 worklogs and a 5★ CSAT) — a stress-test of the
> comment-wave pacing and a showcase of a realistic, multi-turn conversation thread.

Generation runs in three phases so the per-user comment cooldown overlaps across all
tickets instead of being paid ticket-by-ticket:

1. **Setup** — create every ticket (customer), claim it (agent keeps the claim) and
   add worklogs; comments are only collected, not sent yet.
2. **Comment waves** — every ticket's comments are flushed in global rounds (one
   comment per ticket per wave, skipping any author already used this wave), waiting
   one `COMMENT_DELAY_MS` cooldown between waves.
3. **Finish** — apply each ticket's target status transition + CSAT.

Per status, the steps applied are:

| Status | Steps |
|--------|-------|
| NEW | create only |
| IN_PROGRESS | create → claim → worklogs → comments queued |
| WAITING_FOR_CUSTOMER | + status change |
| RESOLVED | + status change to RESOLVED (`reasonCode` + `note`) |
| CLOSED | + CSAT (customer; submitting CSAT auto-moves RESOLVED → CLOSED) |

Customer and agent assignments are round-robin.

### 6. Date backfill

After the tickets are created through the API, the generator connects directly to
PostgreSQL and spreads `created_at`, `sla_deadline`, `resolved_at`, `closed_at` and
the SLA elapsed/paused/resumed fields over the last `DATE_SPREAD_DAYS` days,
appropriate to each status.

Dates are computed relative to **the time the generator runs**:

| Status | Backfill logic |
|--------|----------------|
| NEW | created within the last 0–80%·duration so the SLA has not breached |
| IN_PROGRESS | historical creation + agent claimed recently |
| WAITING_FOR_CUSTOMER | SLA paused, 20–75% of the budget spent |
| RESOLVED | resolved_at = created_at + active duration (breach mix ~30%) |
| CLOSED | closed_at = resolved_at + 1–24 hours |

SLA durations are derived from priority (CRITICAL 1h, HIGH 4h, MEDIUM 24h, LOW 72h)
and **must mirror** the backend SLA policy, otherwise the backfilled deadlines/breach
flags disagree with the live SLA computation and the dashboard.

#### Child-record timestamps (chronological history)

A ticket's comments, worklogs, CSAT survey and audit-log rows are all created through
the API at generation time, so their `created_at` would otherwise collapse onto "now"
— a CLOSED ticket dated five days ago would show comments posted today. After the
ticket dates are written, the generator **redistributes every child timestamp across
the ticket's real timeline** so the history reads like a genuine ticket:

| Child rows | Placed at |
|------------|-----------|
| `ticket_comments` | spread in **id / conversation order** between creation and the resolution (or pause / "now" for active tickets) — the question/answer flow stays intact |
| `ticket_worklogs` | spread the same way across the active work window (`created_at` **and** `updated_at`) |
| `csat_surveys` | at the **close moment** (the customer's rating is what closes a RESOLVED ticket) |
| `ticket_audit_logs` | anchored to the action each row records — `CREATED` at creation, `CLAIM`/`ASSIGN` shortly after, the `RESOLVED`/`CLOSED`/`WAITING_FOR_CUSTOMER` status changes at their moments — then forced strictly increasing by id so the recorded order is never violated |

The result: a closed ticket from last week shows its first reply minutes after it was
opened, the back-and-forth spread over hours, the resolution at the end, and the CSAT
right at close — instead of a burst of "just now" activity.

---

## Resource layout

```
data-generator/
├── users.json                    ← login credentials + seed-user definitions (gitignored)
├── users.example.json            ← template (committed, placeholder passwords)
└── src/main/resources/
    ├── setup.json                ← 5 products × 5 topics × ~12 known-issues + canned responses
    └── tickets/
        ├── ticket-001.json       ← 51 templates; processed CLOSED→RESOLVED→WAITING→IN_PROGRESS→NEW
        ├── ...
        ├── ticket-050.json
        └── ticket-051.json       ← long 23-comment showcase case
```

> To add a new ticket, just drop in a new `tickets/ticket-NNN.json` — no code change
> needed. The generator scans `001..200` in order and processes whichever files exist.

---

## All settings

**Credentials** (overridable via `users.json`; see "Quick start" above for details):

| Setting | Default | Description |
|---------|---------|-------------|
| `adminAgent.username` | `superadmin` | bootstrap admin username (ADMIN + LEAD_AGENT + MANAGER seed user) |
| `adminAgent.password` | `321654` | bootstrap admin password |
| `keycloakAdmin.username` | `admin` | Keycloak master-realm admin username |
| `keycloakAdmin.password` | `321654` | Keycloak master-realm admin password |
| `database.username` | `ticketadmin` | PostgreSQL username |
| `database.password` | `321654` | PostgreSQL password |
| `agents[]` | — | agent users to create (username/email/firstName/lastName/password) |
| `leads[]` | — | lead-agent users to create (created with the LEAD_AGENT composite role) |
| `customers[]` | — | customer users to create |

**Fixed settings** (changed only in `GeneratorConfig.java`; some have env overrides):

| Setting | Default | Env override | Description |
|---------|---------|--------------|-------------|
| `BASE_URL` | `http://localhost` | `GEN_BASE_URL` | application base URL |
| `KEYCLOAK_URL` | `${BASE_URL}/auth` | — | Keycloak root URL |
| `KEYCLOAK_REALM` | `TicketSystemRealm` | — | realm name |
| `KEYCLOAK_CLIENT` | `ticket-frontend` | — | public client used to obtain tokens |
| `MASTER_ADMIN_CLIENT` | `admin-cli` | — | master-realm token client |
| `TEMP_PASSWORD` | `Temp321654!` | — | temporary password set at user creation (forced change on first login) |
| `DELAY_MS` | `600` | — | delay between API requests (ms) |
| `COMMENT_COOLDOWN_SECONDS` | `3` | `COMMENT_COOLDOWN_SECONDS` | backend per-user comment cooldown (sec); **sourced from `.env` first** — keep in sync with the backend's `app.comments.cooldown-seconds` |
| `COMMENT_DELAY_MS` | `cooldown·1000 + 500` | — | delay between comment waves (ms); derived from `COMMENT_COOLDOWN_SECONDS` + a safety margin |
| `RATE_LIMIT_BACKOFF_MS` | `6000` | — | wait after a 429 |
| `RATE_LIMIT_RETRY_COUNT` | `3` | — | retries after a 429 |
| `TOKEN_REFRESH_THRESHOLD_SEC` | `30` | — | token refresh threshold |
| `DATE_SPREAD_DAYS` | `7` | — | how many days back dates are spread over |
| `DB_URL` | `jdbc:postgresql://localhost:5432/ticketdb` | `GEN_DB_URL` | PostgreSQL connection |
| `DB_USER` | `database.username` | `GEN_DB_USER` | PostgreSQL user (env > users.json > fallback) |
| `DB_PASSWORD` | `database.password` | `GEN_DB_PASSWORD` | PostgreSQL password (env > users.json > fallback) |

> The `GEN_*` env overrides exist so a containerized run (`make gen`) can point at
> the compose service names — e.g. `GEN_BASE_URL=http://nginx-proxy` and
> `GEN_DB_URL=jdbc:postgresql://it-service-db:5432/ticketdb` — while a plain
> `java -jar` on the host keeps using `localhost`.

---

## Troubleshooting

**"admin oturumu açılamadı" (admin login failed)**
→ `adminAgent` username/password is wrong, or the user does not exist in Keycloak
(`superadmin` must hold the `ADMIN + LEAD_AGENT + MANAGER` roles, assigned via
`make seed-roles`). Log in once through `http://localhost` first.

**"Kullanıcı oluşturuldu ancak oturum açılamadı" / "Login başarısız → kullanıcı atlanıyor"**
→ A seed user was created but could not log in. The generator already clears the
forced-change required action and retries once on "Account is not fully set up";
if it still fails, check that the user's password matches `users.json` and satisfies
the realm password policy. Other users continue.

**"Setup başarısız: en az bir agent ve bir customer gerekli"**
→ The `agents`/`customers` arrays in `users.json` are empty. Populate them (see
`users.example.json`).

**"Şablonda geçen ürün bulunamadı" (product in template not found)**
→ The `productName` in a ticket JSON must match a product `name` in `setup.json`
exactly.

**"429 Too Many Requests"**
→ The comment-wave pacing follows `COMMENT_COOLDOWN_SECONDS` (from `.env`, default 3)
plus a safety margin. If you still see 429s, make sure `COMMENT_COOLDOWN_SECONDS`
matches the backend's `app.comments.cooldown-seconds`, or relax the backend rate-limit
config.

**"Veritabanı bağlantısı kurulamadı" (DB connection failed, backfill)**
→ PostgreSQL port 5432 must be reachable. Check with `docker compose ps`.

**"known-issue duplicate" / title collision**
→ Re-runs are safe, so this should not happen; if it does, a topic or issue title in
`setup.json` is not unique.
</content>
</invoke>
