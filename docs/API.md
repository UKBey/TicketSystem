# IT Service Desk — API Reference

REST API reference for the IT-service ticketing system. This document is generated from the
actual Spring controllers and is the authoritative description of every endpoint the system
exposes. For an always-live, interactive copy use the Swagger UI:

- **Swagger UI:** `http://localhost/swagger-ui/index.html`
- **OpenAPI spec:** `http://localhost/v3/api-docs`

The system is made of two HTTP services:

| Service | Default port | Documented here |
|---|---|---|
| `it-service-backend` — main API | `8081` | Authentication, Tickets, Comments, Attachments, Worklogs, CSAT, Users, Notifications, Notification Preferences, Products, Topics, Known Issues, Agent-Product Limits, Dashboard Metrics, Internal/Workflow |
| `llm-service` — AI summaries | `8082` | AI Summaries |

---

## Conventions

### Base URL

All traffic normally enters through the nginx reverse proxy:

```
http://localhost/api/v1          # via nginx (recommended)
http://localhost:8081/api/v1     # it-service-backend, direct
http://localhost:8082/api/v1     # llm-service, direct
```

All backend endpoints are prefixed with `/api/v1`. Paths in this document are written relative
to the host (e.g. `/api/v1/tickets`).

### Authentication

The backend is a stateless OAuth2 **resource server**. Every non-public endpoint requires a
Keycloak-issued JWT access token in the `Authorization` header:

```
Authorization: Bearer <JWT>
```

Tokens are obtained from Keycloak directly (realm `TicketSystemRealm`); the frontend uses
`keycloak-js`. There is **no** username/password login endpoint on this API — `/api/v1/auth/login`
and `/api/v1/auth/register` are reserved/permit-listed paths handled by the Keycloak login flow,
not by a backend controller. The only backend `/api/v1/auth/**` endpoints are the anonymous
password-reset flow documented under [Authentication](#authentication-password-reset).

**Roles.** The JWT's `realm_access.roles` are mapped to Spring authorities `ROLE_<NAME>`.
Application roles: `customer`, `agent`, `agent_admin`, `manager`. In this document the `Role`
column lists who may call an endpoint:

- `Authenticated` — any valid JWT (role-specific filtering happens in the service layer).
- A specific role name — enforced by `@PreAuthorize` on the controller method.
- `Internal token` — not JWT; see below.

**Internal endpoints.** Paths under `/api/v1/internal/**` bypass JWT entirely. They require a
shared secret in the `X-Internal-Token` header (matching `jbpm.kie-server.callback-token`).
Used only for service-to-service calls (jBPM KIE Server, llm-service).

**Public (anonymous) endpoints.** `/api/v1/auth/forgot-password`, `/api/v1/auth/reset-password`,
`/api/v1/auth/reset-password/validate`, Swagger, and `/actuator/health|info|metrics`.

> The `llm-service` does not run Spring Security. Its `/api/v1/ai/**` endpoints are reached only
> over the internal Docker/K8s network and are not exposed through nginx to end users.

### Standard error response

All handled errors return a consistent JSON body (`ErrorResponse`):

```json
{
  "status": 400,
  "error": "TICKET_LIMIT_EXCEEDED",
  "message": "Active ticket limit reached for this product.",
  "fieldErrors": {
    "title": "This field cannot be blank."
  },
  "timestamp": 1700000000000
}
```

| Field | Type | Notes |
|---|---|---|
| `status` | int | HTTP status code. |
| `error` | string | Machine-readable error code. Present only for specific errors (`USER_ALREADY_EXISTS`, `WRONG_CURRENT_PASSWORD`, `INVALID_PASSWORD`, `TICKET_LIMIT_EXCEEDED`). Omitted otherwise. |
| `message` | string | Human-readable, localized to the caller's preferred language (`en`/`tr`). |
| `fieldErrors` | object | Field → message map. Present only for validation failures (`400`) and conflicts. |
| `timestamp` | long | Epoch milliseconds. |

Common status codes: `400` validation / business-rule violation, `401` missing or invalid
JWT, `403` authenticated but not permitted, `404` resource not found, `409` conflict
(duplicate user, ticket limit), `413` upload too large, `429` rate limit exceeded,
`500` unexpected error.

### Pagination

List endpoints that support paging accept these query parameters:

| Param | Type | Default | Notes |
|---|---|---|---|
| `page` | int | `0` | Zero-based page index. |
| `size` | int | `20` | Page size. Constrained to `1..500`. |
| `sortBy` | string | `createdAt` | Field to sort by (ticket lists). |
| `sortDir` | string | `desc` | `asc` or `desc` (ticket lists). |

Most paginated endpoints return a Spring `Page` envelope:

```json
{
  "content": [ /* array of items */ ],
  "pageable": { "pageNumber": 0, "pageSize": 20 },
  "totalElements": 137,
  "totalPages": 7,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false,
  "numberOfElements": 20,
  "empty": false
}
```

`GET /api/v1/users` returns a trimmed envelope instead:
`{ "content": [...], "totalElements", "totalPages", "page", "size" }`.

---

## Authentication (password reset)

`AuthController` — base path `/api/v1/auth`. All three endpoints are anonymous and rate-limited
per client IP (5 requests/hour for `forgot-password`).

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/auth/forgot-password` | Public | Request a password-reset link by email. |
| GET | `/api/v1/auth/reset-password/validate` | Public | Check whether a reset token is still valid. |
| POST | `/api/v1/auth/reset-password` | Public | Set a new password using a valid reset token. |

**POST `/api/v1/auth/forgot-password`** — Body `ForgotPasswordRequest`:

| Field | Type | Required | Notes |
|---|---|---|---|
| `email` | string | yes | Max 255 chars, valid email. |
| `language` | string | no | `en` or `tr` — overrides email language. |
| `theme` | string | no | `light` or `dark` — overrides email theme. |

Always returns `200 {"status":"ok"}` even if the email is not registered (anti-enumeration).
Returns `429 {"error":"RATE_LIMIT_EXCEEDED"}` when the IP quota is exhausted.

**GET `/api/v1/auth/reset-password/validate`** — Query `token` (string, required). Returns
`{"valid": true|false}`.

**POST `/api/v1/auth/reset-password`** — Body `ResetPasswordRequest`:

| Field | Type | Required | Notes |
|---|---|---|---|
| `token` | string | yes | One-time token from the email (16–512 chars). |
| `newPassword` | string | yes | 8–128 chars. Must satisfy the Keycloak realm policy. |
| `language` | string | no | `en` or `tr` for the confirmation email. |
| `theme` | string | no | `light` or `dark` for the confirmation email. |

Returns `200 {"status":"ok"}` on success; `400 {"error":"INVALID_OR_EXPIRED_TOKEN"}` or
`400 {"error":"PASSWORD_POLICY_VIOLATION","detail":"..."}` on failure.

Example request:

```json
POST /api/v1/auth/forgot-password
{
  "email": "user@example.com",
  "language": "en",
  "theme": "dark"
}
```

---

## Tickets

`TicketController` — base path `/api/v1/tickets`.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/tickets` | customer | Create a new ticket. |
| GET | `/api/v1/tickets` | customer, agent, agent_admin | List tickets (role-scoped, paged, filtered). |
| GET | `/api/v1/tickets/pool` | agent, agent_admin | Unclaimed `NEW` tickets in the agent's products. |
| GET | `/api/v1/tickets/my-assigned` | Authenticated | Tickets the calling agent has claimed. |
| GET | `/api/v1/tickets/team` | agent, agent_admin | Active tickets across the agent's authorized products. |
| GET | `/api/v1/tickets/all` | agent, agent_admin | All tickets (all statuses) in authorized products. |
| GET | `/api/v1/tickets/by-product/{productId}` | Authenticated | Tickets for one product (role-scoped). |
| GET | `/api/v1/tickets/{id}` | Authenticated | Get one ticket with full detail. |
| GET | `/api/v1/tickets/{id}/sla-timer` | customer, agent, agent_admin | Live SLA timer info for a ticket. |
| PUT | `/api/v1/tickets/{id}/claim` | agent, agent_admin | Claim a ticket in any status except `CLOSED`. |
| DELETE | `/api/v1/tickets/{id}/claim` | agent, agent_admin | Release the caller's own claim. |
| PUT | `/api/v1/tickets/{id}/assign` | agent_admin | Manually assign a ticket to a target agent. |
| PUT | `/api/v1/tickets/{id}/status` | customer, agent, agent_admin | Change ticket status. |
| PUT | `/api/v1/tickets/{id}/priority` | agent, agent_admin | Change ticket priority. |
| PUT | `/api/v1/tickets/{id}/topic` | agent, agent_admin | Change ticket topic. |
| PUT | `/api/v1/tickets/{id}/close` | agent, agent_admin | Close a ticket (note + reason code). |
| DELETE | `/api/v1/tickets/{id}` | agent_admin | Permanently delete a ticket. |

### Filtering query parameters

All list endpoints (`GET /api/v1/tickets`, `/pool`, `/my-assigned`, `/team`, `/all`,
`/by-product/{productId}`) accept the [pagination params](#pagination) plus these optional
filters (all repeatable list parameters):

| Param | Type | Description |
|---|---|---|
| `status` | string[] | Filter by status (`NEW`, `IN_PROGRESS`, `WAITING_FOR_CUSTOMER`, `RESOLVED`, `CLOSED`). Not accepted by `/pool`. |
| `priority` | string[] | Filter by priority (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`). |
| `search` | string | Free-text search over title/description. |
| `productId` | long[] | Filter by product ID. Not accepted by `/by-product/{productId}`. |
| `agentId` | string[] | Filter by claiming agent's Keycloak ID. |
| `topicId` | long[] | Filter by topic ID. |
| `slaStatus` | string[] | Filter by SLA state. |
| `dateFrom` | ISO date-time | Created-at lower bound. |
| `dateTo` | ISO date-time | Created-at upper bound. |

### POST `/api/v1/tickets` — Create ticket

Body `TicketRequestDTO`:

| Field | Type | Required | Notes |
|---|---|---|---|
| `title` | string | yes | Max 255 chars. |
| `description` | string | yes | Detailed description; also stored as the first comment. |
| `priority` | string | yes | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. |
| `productId` | long | yes | Product/category the ticket belongs to. |
| `topicId` | long | conditional | Topic ID; must be an active topic of `productId`. May be omitted/`null` only when the product has no active topics (a topicless ticket); otherwise required. |

Request:

```json
POST /api/v1/tickets
Authorization: Bearer <JWT>
{
  "title": "Cannot connect to VPN",
  "description": "VPN times out since this morning. Error: ERR_TIMEOUT",
  "priority": "HIGH",
  "productId": 1,
  "topicId": 12
}
```

Response `200 OK` (`TicketResponseDTO`):

```json
{
  "id": 42,
  "title": "Cannot connect to VPN",
  "description": "VPN times out since this morning. Error: ERR_TIMEOUT",
  "status": "NEW",
  "priority": "HIGH",
  "productId": 1,
  "productName": "CRM",
  "topicId": 12,
  "topicName": "Password reset",
  "customerId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerName": "Ali Yilmaz",
  "claimers": [],
  "slaDeadline": "2026-05-22T17:00:00+03:00",
  "slaBreached": false,
  "slaElapsedMs": 0,
  "slaPausedAt": null,
  "createdAt": "2026-05-21T09:30:00+03:00",
  "resolvedAt": null,
  "closedAt": null,
  "hasCsat": false,
  "slaInfo": { "slaState": "active", "remainingMs": 27000000 },
  "auditLogs": []
}
```

### GET `/api/v1/tickets` — List tickets

```json
GET /api/v1/tickets?page=0&size=20&status=NEW&status=IN_PROGRESS&priority=HIGH&sortBy=createdAt&sortDir=desc
```

Response is a [`Page` envelope](#pagination) whose `content` is an array of `TicketResponseDTO`.

### GET `/api/v1/tickets/{id}` — Ticket detail

Returns a single `TicketResponseDTO` (same shape as the create response, including
`claimers`, `slaInfo` and `auditLogs`). Path param `id` (long).

`auditLogs[]` items (`TicketAuditLogDTO`):

```json
{
  "id": 7,
  "actorId": "f9e8d7c6-b5a4-3210-fedc-ba0987654321",
  "actorName": "Mehmet Demir",
  "actionType": "CLAIM",
  "reasonCode": null,
  "note": null,
  "previousState": "NEW",
  "newState": "IN_PROGRESS",
  "createdAt": "2026-05-21T10:05:00+03:00"
}
```

### Mutation endpoints — request bodies

**PUT `/api/v1/tickets/{id}/claim`** — no body. Allowed in any status except `CLOSED`; claiming a `CLOSED` ticket returns `400`. The first claim auto-promotes a `NEW` ticket to `IN_PROGRESS`, advancing the BPMN state machine via a transition signal.

**DELETE `/api/v1/tickets/{id}/claim`** — Body `UnclaimRequestDTO`:
`reasonCode` (string, required), `note` (string, required when `reasonCode` is `OTHER`).
Releasing the last claim moves the ticket `IN_PROGRESS` → `NEW`, also driven through the BPMN.

**PUT `/api/v1/tickets/{id}/assign`** — Body `AssignTicketRequestDTO`:
`targetAgentId` (string, required — Keycloak ID), `note` (string, optional). Capacity of the
target agent is checked; returns `400`/`409` if the agent's limit is full. Like claim, assigning
a `NEW` ticket advances it to `IN_PROGRESS` through the BPMN state machine.

**PUT `/api/v1/tickets/{id}/status`** — Body `StatusUpdateRequestDTO`:
`status` (string, required), `reasonCode` (string — required when transitioning to `RESOLVED`),
`note` (string — required when `reasonCode` is `OTHER`). The BPMN process is the authoritative
state machine: a transition the BPMN does not accept is rejected with `400`.

```json
PUT /api/v1/tickets/42/status
{ "status": "RESOLVED", "reasonCode": "SOLUTION_PROVIDED", "note": "Fix sent by email." }
```

**PUT `/api/v1/tickets/{id}/priority`** — Body `PriorityChangeRequestDTO`:
`priority` (string, required), `reasonCode` (string, required), `note` (string — required when
`reasonCode` is `OTHER`).

**PUT `/api/v1/tickets/{id}/topic`** — Body `TopicChangeRequestDTO`:
`topicId` (long, required — an active topic of the same product), `reasonCode` (string,
required), `note` (string — required when `reasonCode` is `OTHER`).

**PUT `/api/v1/tickets/{id}/close`** — Body `CloseTicketRequestDTO`:
`reasonCode` (string, required), `note` (string — required when `reasonCode` is `OTHER`).

**DELETE `/api/v1/tickets/{id}`** — no body; returns `204 No Content`.

**GET `/api/v1/tickets/{id}/sla-timer`** — returns a JSON object describing the live SLA timer,
e.g. `{ "slaState": "active", "remainingMs": 27000000, "deadlineTimestamp": 1780346400000 }`
(`deadlineTimestamp` is epoch-ms, and is `-1` while paused/closed). `slaState` is
one of `active`, `paused`, `expired`, `completed`. The SLA counts only *active* time: while
paused (`WAITING_FOR_CUSTOMER`/`RESOLVED`) `remainingMs` is frozen (SLA budget minus accumulated
active elapsed) and does not decrease; on resume (back to `IN_PROGRESS`) the countdown continues
from that frozen value — time spent paused is not lost.

---

## Ticket Comments

`CommentController` — base path `/api/v1/tickets/{ticketId}/comments`. All endpoints require an
authenticated user; role-based filtering is applied in the service layer.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/tickets/{ticketId}/comments` | Authenticated | Add a comment to a ticket. |
| GET | `/api/v1/tickets/{ticketId}/comments` | Authenticated | List a ticket's comments (filtered by role). |

Comment types: `EXTERNAL` (visible to the customer), `INTERNAL` (agents/agent_admin only —
hidden from customers). Customers may only add `EXTERNAL` comments to their own tickets.

### POST `/api/v1/tickets/{ticketId}/comments`

Path param `ticketId` (long). Body `CommentRequestDTO`:

| Field | Type | Required | Notes |
|---|---|---|---|
| `message` | string | yes | Not blank, max 500 chars. |
| `type` | string | yes | `EXTERNAL` or `INTERNAL`. |

Request:

```json
POST /api/v1/tickets/42/comments
{ "message": "I checked your VPN config, try again.", "type": "EXTERNAL" }
```

Response `200 OK` (`CommentDTO`):

```json
{
  "id": 128,
  "authorId": "f9e8d7c6-b5a4-3210-fedc-ba0987654321",
  "authorName": "Mehmet Demir",
  "authorRole": "AGENT",
  "message": "I checked your VPN config, try again.",
  "type": "EXTERNAL",
  "createdAt": "2026-05-21T11:30:00+03:00"
}
```

### GET `/api/v1/tickets/{ticketId}/comments`

Returns a JSON array of `CommentDTO` ordered chronologically. Customers receive only
`EXTERNAL` comments; agents and agent_admin receive both types.

---

## Attachments

`AttachmentController` — base path `/api/v1`. File content is stored in the database as `BYTEA`.
Max file size 10 MB; text-based files are scanned for secret-like patterns.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/tickets/{ticketId}/attachments` | customer, agent, agent_admin | Upload a file to a ticket. |
| GET | `/api/v1/tickets/{ticketId}/attachments` | customer, agent, agent_admin | List a ticket's attachment metadata. |
| GET | `/api/v1/attachments/{id}` | customer, agent, agent_admin | Download a file's content. |
| DELETE | `/api/v1/attachments/{id}` | customer, agent, agent_admin | Delete a file. |

**POST `/api/v1/tickets/{ticketId}/attachments`** — `multipart/form-data` with a single part
`file`. Path param `ticketId` (long). Returns `200 OK` with `AttachmentDTO` metadata;
returns `413` when the file exceeds the size limit.

```json
{
  "id": 55,
  "fileName": "error_screenshot.png",
  "fileType": "image/png",
  "uploaderId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "createdAt": "2026-05-21T12:00:00+03:00"
}
```

**GET `/api/v1/tickets/{ticketId}/attachments`** — returns a JSON array of `AttachmentDTO`.

**GET `/api/v1/attachments/{id}`** — returns the raw bytes with the original `Content-Type` and a
`Content-Disposition: attachment; filename="..."` header. Path param `id` (long).

**DELETE `/api/v1/attachments/{id}`** — returns `204 No Content`. Customers may delete only
their own uploads; agents only on their assigned tickets; agent_admin any.

---

## Worklogs

`TicketWorklogController` — base path `/api/v1/tickets`. Tracks agent time spent on tickets.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/tickets/{id}/worklogs` | agent, agent_admin | Add a worklog entry. |
| GET | `/api/v1/tickets/{id}/worklogs` | agent, agent_admin | List a ticket's worklogs. |
| PUT | `/api/v1/tickets/{id}/worklogs/{worklogId}` | agent, agent_admin | Update a worklog entry. |
| DELETE | `/api/v1/tickets/{id}/worklogs/{worklogId}` | agent, agent_admin | Delete a worklog entry. |
| GET | `/api/v1/tickets/all-worklogs` | agent_admin | List every worklog in the system. |

**POST/PUT body `WorklogRequestDTO`:** `minutes` (int, required, ≥ 1), `description`
(string, optional, max 500 chars).

Request:

```json
POST /api/v1/tickets/42/worklogs
{ "minutes": 45, "description": "Reviewed firewall logs, updated port rules." }
```

Response `201 Created` (`WorklogResponseDTO`):

```json
{
  "id": 15,
  "ticketId": 42,
  "agentId": "f9e8d7c6-b5a4-3210-fedc-ba0987654321",
  "minutes": 45,
  "description": "Reviewed firewall logs, updated port rules.",
  "createdAt": "2026-05-21T14:00:00+03:00",
  "updatedAt": "2026-05-21T14:00:00+03:00"
}
```

`GET` endpoints return JSON arrays of `WorklogResponseDTO`. Agents may update/delete only
their own worklogs; agent_admin may delete any. `DELETE` returns `204 No Content`.

---

## CSAT (Customer Satisfaction)

`TicketCsatController` — base path `/api/v1/tickets`. Surveys filled in at ticket closing.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/tickets/{id}/csat` | customer | Submit a CSAT survey for a resolved ticket. |
| GET | `/api/v1/tickets/{id}/csat` | agent_admin | Get the CSAT result of a ticket. |
| GET | `/api/v1/tickets/all-csats` | agent_admin | List every CSAT result. |

**POST `/api/v1/tickets/{id}/csat`** — Body `CsatDTO`: `rating` (int, required, 1–5),
`comment` (string, optional). The ticket must be in `RESOLVED` status and owned by the caller;
submitting the survey transitions the ticket to `CLOSED`. One survey per ticket.

Request:

```json
POST /api/v1/tickets/42/csat
{ "rating": 5, "comment": "Resolved quickly, thanks!" }
```

Response `200 OK` (`Csat` entity):

```json
{
  "id": 9,
  "ticketId": 42,
  "rating": 5,
  "comment": "Resolved quickly, thanks!",
  "createdAt": "2026-05-21T16:00:00+03:00"
}
```

---

## Users

`UserController` — base path `/api/v1/users`.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/users/sync` | Authenticated | Sync the logged-in user from the JWT into the local DB. |
| GET | `/api/v1/users` | agent_admin, manager | List users (paged, searchable, role-filterable). |
| GET | `/api/v1/users/{id}` | Authenticated | Get a user by Keycloak ID. |
| GET | `/api/v1/users/agents` | Authenticated | List all `AGENT` users with their authorized products. |
| GET | `/api/v1/users/agents/capacity` | agent_admin, manager | List agents with current load/limit for a product. |
| PUT | `/api/v1/users/me` | Authenticated | Update the caller's profile (name, email). |
| POST | `/api/v1/users/me/password` | Authenticated | Change the caller's password. |
| PUT | `/api/v1/users/me/language` | Authenticated | Update the caller's preferred language. |
| PUT | `/api/v1/users/me/theme` | Authenticated | Update the caller's preferred theme. |
| GET | `/api/v1/users/me/2fa` | Authenticated | List the caller's registered TOTP devices. |
| DELETE | `/api/v1/users/me/2fa/{credentialId}` | Authenticated | Delete one of the caller's TOTP devices. |
| POST | `/api/v1/users/me/2fa/notify-added` | Authenticated | Trigger the "2FA device added" notification email. |
| POST | `/api/v1/users/{userId}/products/{productId}` | agent_admin, manager | Grant an agent access to a product. |
| DELETE | `/api/v1/users/{userId}/products/{productId}` | agent_admin, manager | Revoke an agent's product access. |
| PUT | `/api/v1/users/{userId}/status` | agent_admin, manager | Activate / deactivate a user. |
| PUT | `/api/v1/users/{userId}/roles` | agent_admin, manager | Replace a user's realm roles. |
| POST | `/api/v1/users/admin/create` | agent_admin, manager | Create a new Keycloak user. |
| GET | `/api/v1/users/admin/roles` | agent_admin, manager | List assignable realm roles. |

### POST `/api/v1/users/sync`

No body — the user is derived from the JWT. Returns a `UserDTO`:

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "email": "user@example.com",
  "fullName": "Ali Yilmaz",
  "role": "AGENT",
  "isActive": true,
  "preferredLanguage": "tr",
  "preferredTheme": "dark",
  "createdAt": "2026-01-15T09:00:00+03:00",
  "authorizedProducts": [
    { "id": 1, "name": "CRM", "isActive": true, "maxActiveTickets": 5 }
  ]
}
```

### GET `/api/v1/users`

Query params: `search` (string, optional), `role` (string[], optional), `page` (int, ≥ 0),
`size` (int, 1–500). Returns the trimmed envelope:
`{ "content": [UserDTO...], "totalElements", "totalPages", "page", "size" }`.

### Other request bodies / parameters

- **GET `/api/v1/users/agents/capacity`** — query `productId` (long, required). Returns
  `AgentCapacityDTO[]`.
- **PUT `/api/v1/users/me`** — Body `UpdateProfileRequest`: `firstName`, `lastName` (≤ 50 chars
  each), `email` (valid email). All required. Returns `UserDTO`. `409` if email is taken.
- **POST `/api/v1/users/me/password`** — Body `ChangePasswordRequest`: `currentPassword`
  (required), `newPassword` (required, ≥ 8 chars, must satisfy realm policy). Returns
  `204 No Content`; `400` if the current password is wrong or the new one violates policy.
- **PUT `/api/v1/users/me/language`** — query `lang` (string: `en` or `tr`). Returns `UserDTO`.
- **PUT `/api/v1/users/me/theme`** — query `theme` (string: `light` or `dark`). Returns `UserDTO`.
- **PUT `/api/v1/users/{userId}/status`** — query `active` (boolean). An admin cannot deactivate
  themselves (`400`). Returns `UserDTO`.
- **PUT `/api/v1/users/{userId}/roles`** — Body: JSON array of role strings (non-empty), e.g.
  `["AGENT","AGENT_ADMIN"]`. Returns `UserDTO`.
- **POST `/api/v1/users/admin/create`** — Body `CreateUserRequest` (see below). Returns
  `201 Created` with `UserCreationResponseDTO`. `409` if email/username already exists.

`CreateUserRequest`:

| Field | Type | Required | Notes |
|---|---|---|---|
| `username` | string | yes | Unique, 3–50 chars. |
| `email` | string | yes | Unique, valid email. |
| `firstName` | string | yes | ≤ 50 chars. |
| `lastName` | string | yes | ≤ 50 chars. |
| `password` | string | yes | ≥ 8 chars (temporary password by default). |
| `roles` | string[] | yes | At least one realm role. |
| `temporaryPassword` | boolean | no | Default `true`. |

```json
POST /api/v1/users/admin/create
{
  "username": "john.doe",
  "email": "john.doe@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "password": "Temp1234!",
  "roles": ["AGENT"],
  "temporaryPassword": true
}
```

Response `201 Created`:

```json
{
  "keycloakId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "username": "john.doe",
  "email": "john.doe@example.com",
  "fullName": "John Doe",
  "assignedRoles": ["AGENT"]
}
```

---

## Notifications

`NotificationController` — base path `/api/v1/notifications`. All endpoints require an
authenticated user and operate only on the caller's own notifications.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/api/v1/notifications` | Authenticated | List the caller's notifications (paged). |
| GET | `/api/v1/notifications/unread-count` | Authenticated | Count of unread notifications. |
| PATCH | `/api/v1/notifications/{id}/read` | Authenticated | Mark one notification as read. |
| POST | `/api/v1/notifications/read-all` | Authenticated | Mark all notifications as read. |
| DELETE | `/api/v1/notifications/{id}` | Authenticated | Delete one notification. |
| DELETE | `/api/v1/notifications` | Authenticated | Delete all of the caller's notifications. |

**GET `/api/v1/notifications`** — query `page` (int, ≥ 0), `size` (int, 1–500). Returns a
[`Page` envelope](#pagination) whose `content` is an array of `NotificationResponse`:

```json
{
  "id": 42,
  "userId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "message": "Ticket #12 has been assigned.",
  "isRead": false,
  "createdAt": "2026-05-21T13:00:00+03:00",
  "type": "TICKET_ASSIGNED",
  "referenceId": 12,
  "referenceType": "TICKET"
}
```

**GET `/api/v1/notifications/unread-count`** — returns `{"count": 3}`.

`PATCH`, `POST /read-all`, and the `DELETE` endpoints return `204 No Content`.

---

## Notification Preferences

`NotificationPreferenceController` — base path `/api/v1/notification-preferences`. Each user
reads and writes their own preferences.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/api/v1/notification-preferences` | Authenticated | Get the caller's notification preferences. |
| PUT | `/api/v1/notification-preferences` | Authenticated | Update the caller's notification preferences. |

Both return `NotificationPreferenceResponse`. If no preference row exists, all flags default
to `true`. On `PUT`, fields sent as `null` keep their current value (`UpdateNotificationPreferenceRequest`).

```json
{
  "emailOnTicketCreated": true,
  "emailOnTicketAssigned": true,
  "emailOnStatusChanged": true,
  "emailOnCommentAdded": true,
  "emailOnSlaWarning": true,
  "emailOnSlaBreached": true,
  "emailOnTicketResolved": true,
  "notifyOnTicketCreated": true,
  "notifyOnTicketAssigned": true,
  "notifyOnStatusChanged": true,
  "notifyOnCommentAdded": true,
  "notifyOnSlaWarning": true,
  "notifyOnSlaBreached": true,
  "notifyOnTicketResolved": true
}
```

---

## Products

`ProductController` — base path `/api/v1/products`. Products are the support categories.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/api/v1/products` | Authenticated | List products visible to the caller's role. |
| GET | `/api/v1/products/{id}` | Authenticated | Get a single product. |
| POST | `/api/v1/products` | agent_admin, manager | Create a product. |
| PUT | `/api/v1/products/{id}` | agent_admin, manager | Update a product's name / active flag. |
| PATCH | `/api/v1/products/{id}/limit` | agent_admin, manager | Update the product's default concurrent-ticket limit. |
| DELETE | `/api/v1/products/{id}` | agent_admin, manager | Delete a product. |

**GET `/api/v1/products`** — `CUSTOMER`/`AGENT` see only their authorized products;
`AGENT_ADMIN` sees all. Returns a JSON array of `ProductDTO`:

```json
[
  { "id": 1, "name": "CRM", "isActive": true, "maxActiveTickets": 5 },
  { "id": 2, "name": "ERP", "isActive": true, "maxActiveTickets": null }
]
```

**POST / PUT `/api/v1/products`** — Body is a `Product` entity, e.g.
`{ "name": "ERP", "isActive": true }`. Returns `ProductDTO`.

**PATCH `/api/v1/products/{id}/limit`** — Body `ProductLimitUpdateRequestDTO`:
`maxActiveTickets` (int, nullable — `null` removes the limit). Returns `ProductDTO`.

**DELETE `/api/v1/products/{id}`** — returns `204 No Content`.

---

## Ticket Topics

`TicketTopicController` — topics are sub-categories belonging to a product. (Controller has no
class-level base path; full paths are shown below.)

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/api/v1/products/{productId}/topics` | Authenticated | List a product's topics. |
| POST | `/api/v1/products/{productId}/topics` | agent_admin, manager | Create a topic under a product. |
| PUT | `/api/v1/topics/{id}` | agent_admin, manager | Update a topic's name / active flag. |
| DELETE | `/api/v1/topics/{id}` | agent_admin, manager | Delete a topic. |

**GET `/api/v1/products/{productId}/topics`** — query `includeInactive` (boolean, default
`false`). Returns a JSON array of `TicketTopicDTO`:

```json
[
  { "id": 12, "productId": 3, "name": "Password reset", "isActive": true }
]
```

**POST / PUT body `TicketTopicDTO`:** `name` (string, required, ≤ 255 chars),
`isActive` (boolean). Returns `TicketTopicDTO`. `DELETE` returns `204 No Content`.

---

## Known Issues

`KnownIssueController` — knowledge-base entries tied to a product (and optionally a topic).
(No class-level base path; full paths shown below.) List/detail require product authorization;
write operations require `agent_admin`/`manager`.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/api/v1/products/{productId}/known-issues` | Authenticated | List a product's known issues. |
| GET | `/api/v1/known-issues/{id}` | Authenticated | Get one known-issue entry. |
| POST | `/api/v1/products/{productId}/known-issues` | agent_admin, manager | Create a known-issue entry. |
| PUT | `/api/v1/known-issues/{id}` | agent_admin, manager | Update a known-issue entry. |
| DELETE | `/api/v1/known-issues/{id}` | agent_admin, manager | Delete a known-issue entry. |

**GET `/api/v1/products/{productId}/known-issues`** — query `topicId` (long, optional),
`includeInactive` (boolean, default `false`). Returns a JSON array of `KnownIssueDTO`.

**POST / PUT body `KnownIssueDTO`:**

| Field | Type | Required | Notes |
|---|---|---|---|
| `topicId` | long | no | Optional topic association. |
| `title` | string | yes | ≤ 255 chars. |
| `content` | string | yes | ≤ 10000 chars. |
| `isActive` | boolean | no | Whether shown to users. |

Response `KnownIssueDTO`:

```json
{
  "id": 42,
  "productId": 3,
  "topicId": 12,
  "title": "VPN connection drops",
  "content": "Check your network settings and ...",
  "isActive": true,
  "createdBy": "f9e8d7c6-b5a4-3210-fedc-ba0987654321",
  "createdAt": "2026-05-10T09:00:00+03:00",
  "updatedAt": "2026-05-12T11:00:00+03:00"
}
```

`DELETE` returns `204 No Content`.

---

## Agent-Product Limits

`AgentProductLimitController` — base path `/api/v1/agents/{agentId}/limits`. Per-agent overrides
of the product-level concurrent-ticket limit.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/api/v1/agents/{agentId}/limits` | agent_admin, manager | List all product-limit overrides for an agent. |
| PUT | `/api/v1/agents/{agentId}/limits/{productId}` | agent_admin, manager | Create / update an agent's limit for a product. |
| DELETE | `/api/v1/agents/{agentId}/limits/{productId}` | agent_admin, manager | Remove an agent/product override. |

**PUT body `AgentProductLimitRequestDTO`:** `useCustomLimit` (boolean),
`maxActiveTickets` (int, nullable).

Response `AgentProductLimitResponseDTO`:

```json
{
  "agentId": "f9e8d7c6-b5a4-3210-fedc-ba0987654321",
  "productId": 10,
  "productName": "CRM",
  "useCustomLimit": true,
  "maxActiveTickets": 3,
  "effectiveLimit": 3
}
```

`GET` returns a JSON array of the above; `DELETE` returns `204 No Content`.

---

## Dashboard Metrics

`MetricsController` — base path `/api/v1/metrics`. Aggregated KPIs and analytics. Most endpoints
require the `manager` role; results are Caffeine-cached (5-minute TTL).

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/api/v1/metrics/dashboard-summary` | manager | Headline KPIs (open tickets, SLA breach rate, response time, CSAT). |
| GET | `/api/v1/metrics/status-distribution` | manager | Ticket counts per status. |
| GET | `/api/v1/metrics/agent-performance` | manager, agent_admin | Agent leaderboard (load, resolution speed, CSAT, SLA). |
| GET | `/api/v1/metrics/ticket-timeline` | manager | Daily created/resolved/closed/breach trend. |
| GET | `/api/v1/metrics/priority-sla-metrics` | manager | SLA metrics broken down by priority. |
| GET | `/api/v1/metrics/product-metrics` | manager | Per-product ticket metrics. |
| GET | `/api/v1/metrics/csat-metrics` | manager | Detailed CSAT analytics. |
| GET | `/api/v1/metrics/alerts-backlog` | manager | SLA-breach alerts and backlog summary. |
| GET | `/api/v1/metrics/worklog-completion` | manager | Worklog totals and ticket-completion stats. |

Query parameters:

| Endpoint | Param | Type | Default |
|---|---|---|---|
| `/ticket-timeline` | `days` | int | `30` |
| `/priority-sla-metrics` | `days` | int (optional) | — |
| `/product-metrics` | `days` | int (optional) | — |
| `/csat-metrics` | `months` | int | `3` |
| `/worklog-completion` | `days` | int | `30` |

Each endpoint returns its dedicated DTO (`DashboardMetricsDTO`, `StatusDistributionDTO`,
`AgentPerformanceDTO`, `TicketTimelineDTO`, `PrioritySLAMetricsDTO`, `ProductMetricsDTO`,
`CSATMetricsDTO`, `AlertsBacklogDTO`, `WorklogCompletionDTO`). Example
`GET /api/v1/metrics/dashboard-summary`:

```json
{
  "openTickets": 137,
  "slaBreachRate": 4.2,
  "averageResponseTimeHours": 3.6,
  "averageCsat": 4.4,
  "priorityDistribution": { "LOW": 40, "MEDIUM": 60, "HIGH": 30, "CRITICAL": 7 }
}
```

> Field names of metric DTOs are illustrative — consult the Swagger UI / OpenAPI spec for the
> exact schema of each metrics response.

---

## AI Summaries

`AiSummaryController` — **served by `llm-service`** at base path `/api/v1/ai/summaries`
(port `8082`). These endpoints have no Spring Security; they are called service-to-service
(by `it-service-backend` / internal callers) and are not exposed to end users via nginx.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| POST | `/api/v1/ai/summaries` | Internal | Summarize a ticket from a supplied raw payload. |
| POST | `/api/v1/ai/summaries/tickets/{ticketId}/generate` | Internal | Fetch ticket data and generate a summary. |
| GET | `/api/v1/ai/summaries/tickets/{ticketId}/latest` | Internal | Get the most recent summary for a ticket. |
| GET | `/api/v1/ai/summaries/tickets/{ticketId}` | Internal | List all summaries for a ticket (newest first). |

**POST `/api/v1/ai/summaries`** — Body `SummarizeRequestDTO`: `ticketId` (long), `ticket`
(object), `comments` (array), `worklogs` (array), `resolutionNote` (object, optional),
`knownIssues` (array), `language` (string, `tr` or `en`, default `tr`).

**POST `/api/v1/ai/summaries/tickets/{ticketId}/generate`** — path param `ticketId` (long),
query `language` (string, default `tr`). `llm-service` pulls the ticket data from
`it-service-backend` (`GET /api/v1/internal/tickets/{ticketId}/full`), sends it to the Groq LLM,
and persists the result.

Response `AiSummaryResponseDTO`:

```json
{
  "id": 7,
  "ticketId": 42,
  "model": "llama-3.1-8b-instant",
  "promptTokens": 850,
  "completionTokens": 120,
  "summary": "The customer reported a VPN timeout. The agent identified a blocked port and ...",
  "createdAt": "2026-05-21T15:30:00+03:00"
}
```

`GET .../tickets/{ticketId}` returns a JSON array of `AiSummaryResponseDTO`.

---

## Internal / Workflow

These endpoints are authenticated by the `X-Internal-Token` header (not JWT). They live under
`/api/v1/internal/**` and are used only for service-to-service communication.

### Internal Tickets

`InternalTicketController` — base path `/api/v1/internal/tickets`.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/internal/tickets/{ticketId}/full` | `X-Internal-Token` | Full ticket bundle (ticket, comments, worklogs, known issues) — consumed by `llm-service`. |

Returns a JSON object: `{ "ticket": TicketResponseDTO, "comments": [CommentDTO],
"worklogs": [WorklogResponseDTO], "knownIssues": [KnownIssueDTO] }`.

### Workflow Callback

`WorkflowCallbackController` — base path `/api/v1/internal/workflow`.

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/internal/workflow/callback` | `X-Internal-Token` | jBPM KIE Server posts process events (SLA breach, process completion). |

**POST `/api/v1/internal/workflow/callback`** — header `X-Internal-Token` (required).
Body `WorkflowCallbackDTO`:

| Field | Type | Required | Notes |
|---|---|---|---|
| `ticketId` | long | yes | The ticket the event relates to. |
| `eventType` | string | yes | `SLA_BREACHED` or `PROCESS_COMPLETED`. |
| `processInstanceId` | long | no | jBPM process instance ID. |
| `additionalData` | string | no | Free-text payload. |

```json
POST /api/v1/internal/workflow/callback
X-Internal-Token: <shared-secret>
{
  "ticketId": 42,
  "eventType": "SLA_BREACHED",
  "processInstanceId": 1001,
  "additionalData": "SLA deadline was 2026-05-21T17:00:00Z"
}
```

Returns `200` with a plain-text body on success; `400` for an unknown `eventType`,
`401` for a missing/invalid token, `404` if the ticket does not exist.

---

## Endpoint summary

| Resource | Endpoints |
|---|---|
| Authentication (password reset) | 3 |
| Tickets | 16 |
| Ticket Comments | 2 |
| Attachments | 4 |
| Worklogs | 5 |
| CSAT | 3 |
| Users | 18 |
| Notifications | 6 |
| Notification Preferences | 2 |
| Products | 6 |
| Ticket Topics | 4 |
| Known Issues | 5 |
| Agent-Product Limits | 3 |
| Dashboard Metrics | 9 |
| AI Summaries (llm-service) | 4 |
| Internal / Workflow | 2 |
| **Total** | **92** |
