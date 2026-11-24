# 05 — REST API Specification

There is **no API gateway**; each service is reached on its own port (routing is done by the ALB on AWS and
by the SvelteKit server locally). Local base URLs: user `:8081`, activity `:8082`, challenge `:8083`,
integration `:8084`, analytics `:8085`, recommendation `:8086`. All endpoints (except auth + webhooks)
require `Authorization: Bearer <JWT>`. The JWT carries `tenantId`, `userId`, `roles`; the tenant is
**never** taken from the request body — it is derived from the token server-side. Every service serves live
Swagger UI at `/swagger-ui.html` via springdoc-openapi. A ready-to-run **Postman collection** is in
[`postman/`](../postman/HabitLoop.postman_collection.json).

Standard error envelope (all services):

```json
{
  "timestamp": "2026-11-23T10:15:30Z",
  "path": "/api/habits",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "name must not be blank",
  "details": ["name: must not be blank"]
}
```

Common status codes: `400` validation, `401` missing/invalid JWT, `403` wrong role / cross-tenant,
`404` not found (or hidden cross-tenant), `409` conflict/duplicate, `422` business rule, `429` rate limit.

---

## 1. user-service (`/api/auth`, `/api/users`, `/api/tenants`)

| Method | Path | Role | Purpose |
|--------|------|------|---------|
| POST | `/api/auth/register` | public | register user (+ create/join tenant) |
| POST | `/api/auth/login` | public | authenticate, return JWT |
| POST | `/api/auth/refresh` | public | exchange refresh token |
| GET  | `/api/users/me` | USER | current profile |
| PUT  | `/api/users/me` | USER | update profile (display name, timezone, avatar) |
| GET  | `/api/users/{id}` | TENANT_ADMIN | fetch a tenant user |
| GET  | `/internal/users/{id}` | service | internal verify (same-tenant) |
| GET  | `/api/tenants/me` | TENANT_ADMIN | tenant details |

**Register — request**
```json
POST /api/auth/register
{ "email": "ria@acme.com", "password": "S3cure!pass", "displayName": "Ria",
  "timezone": "Asia/Kolkata", "tenantName": "Acme Wellness" }
```
**Register — response `201`**
```json
{ "userId": "9f1c...", "tenantId": "a21b...", "email": "ria@acme.com", "roles": ["TENANT_ADMIN"] }
```
**Login — response `200`**
```json
{ "accessToken": "eyJhbGciOi...", "refreshToken": "eyJ...", "expiresIn": 3600,
  "tokenType": "Bearer" }
```

---

## 2. activity-service (`/api/habits`, `/api/activity`)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/habits` | create habit |
| GET  | `/api/habits` | list my habits (paged) |
| GET  | `/api/habits/{id}` | get habit (+streak) |
| PUT  | `/api/habits/{id}` | update habit/schedule |
| DELETE | `/api/habits/{id}` | soft-delete habit |
| POST | `/api/habits/{id}/completions` | mark complete (today / date) |
| GET  | `/api/habits/{id}/completions` | completion history (paged) |
| GET  | `/api/activity` | activity log (filters: from, to, type, paged) |

**Create habit — request**
```json
POST /api/habits
{ "name": "Morning run", "description": "5k jog",
  "frequency": "CUSTOM",
  "schedule": { "daysOfWeek": [1,3,5], "targetTime": "07:00", "perWeek": 3 },
  "targetPerPeriod": 3 }
```
**Response `201`**
```json
{ "id": "4d2e...", "name": "Morning run", "frequency": "CUSTOM",
  "schedule": { "daysOfWeek": [1,3,5], "targetTime": "07:00", "perWeek": 3 },
  "active": true, "streak": { "current": 0, "longest": 0 } }
```
**Mark complete — request/response**
```json
POST /api/habits/4d2e.../completions   { "date": "2026-11-23", "note": "felt great" }
200 → { "completionId": "7a...", "date": "2026-11-23", "streak": { "current": 5, "longest": 12 } }
```
Duplicate same-day completion → `409 DUPLICATE_COMPLETION`.

---

## 3. challenge-service (`/api/challenges`, `/api/invitations`)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/challenges` | create challenge |
| GET  | `/api/challenges` | feed (public in tenant + mine); filters: status, visibility |
| GET  | `/api/challenges/{id}` | details + my progress + leaderboard |
| POST | `/api/challenges/{id}/join` | join a public challenge |
| POST | `/api/challenges/{id}/invites` | invite a user (private/public) |
| GET  | `/api/invitations` | my pending invitations |
| POST | `/api/invitations/{id}/accept` | accept |
| POST | `/api/invitations/{id}/reject` | reject |

**Create challenge — request**
```json
POST /api/challenges
{ "title": "30-day Move", "description": "Move every day for 30 days",
  "visibility": "PUBLIC", "metric": "DAYS_COMPLETED", "target": 30,
  "startsOn": "2026-12-01", "endsOn": "2026-12-30" }
```
**Details — response `200`**
```json
{ "id": "c3...", "title": "30-day Move", "visibility": "PUBLIC", "status": "ACTIVE",
  "target": 30, "myProgress": 8, "completed": false,
  "leaderboard": [ { "userId": "u9", "displayName": "Ria", "progress": 12 },
                   { "userId": "u4", "displayName": "Sam", "progress": 8 } ] }
```
Inviting a user not in your tenant → `404` (not revealed). Joining a `PRIVATE` challenge without an invite
→ `403`.

---

## 4. integration-service (`/api/integrations`, `/api/webhooks`)

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/integrations/connections` | JWT | create a partner connection (returns one-time API key) |
| GET  | `/api/integrations/connections` | JWT | list my connections + status |
| DELETE | `/api/integrations/connections/{id}` | JWT | revoke connection |
| POST | `/api/webhooks/{connectionId}` | HMAC | **partner** posts an event (no JWT) |

**Create connection — response `201`** (API key shown once)
```json
{ "connectionId": "b7...", "partner": "FITBIT", "status": "CONNECTED",
  "apiKey": "hl_live_8f3k...9", "webhookUrl": "https://.../api/webhooks/b7..." }
```
**Webhook — request** (headers `X-HL-Signature: <hex HMAC-SHA256>`, `X-HL-Event-Id: evt_123`)
```json
POST /api/webhooks/b7...
{ "externalEventId": "evt_123", "type": "STEPS", "value": 8450, "unit": "steps",
  "occurredAt": "2026-11-23T06:40:00Z", "userRef": "fitbit-user-55" }
```
**Responses:** `202 Accepted` (queued), `200` (duplicate, no-op), `401` (bad signature),
`400` (invalid payload), `409` only if a hard conflict. Body:
```json
202 → { "status": "ACCEPTED", "rawEventId": "r9...", "deduplicated": false }
```

---

## 5. analytics-service (`/api/analytics`)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/analytics/summary` | dashboard summary (streaks, today, week) |
| GET | `/api/analytics/daily?from=&to=` | daily stats series |
| GET | `/api/analytics/weekly?weeks=` | weekly trend |
| GET | `/api/analytics/challenges/{id}` | challenge progress rollup |
| GET | `/api/analytics/cohort` | TENANT_ADMIN: tenant-wide insights |

**Summary — response `200`**
```json
{ "activeHabits": 4, "completedToday": 2, "weeklyCompletionRate": 0.71,
  "longestStreak": 12, "currentStreak": 5,
  "trend": [ { "week": "2026-W45", "rate": 0.64 }, { "week": "2026-W46", "rate": 0.71 } ] }
```

---

## 6. recommendation-service (`/api/recommendations`)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/recommendations` | actionable recommendations for the user |
| GET | `/api/recommendations/habits/{id}` | per-habit frequency/timing suggestion |

**Response `200`**
```json
{ "generatedAt": "2026-11-23T10:00:00Z",
  "items": [
    { "type": "TIMING", "habitId": "4d2e...", "title": "Run earlier",
      "message": "You complete 'Morning run' most on days you start before 7:30am.",
      "action": { "label": "Set reminder 07:00", "suggestedTime": "07:00" }, "confidence": 0.82 },
    { "type": "FREQUENCY", "habitId": "9a...", "title": "Try 4×/week",
      "message": "Your 5×/week target is missed on Fridays; 4×/week fits your pattern.",
      "action": { "label": "Change target to 4", "suggestedPerWeek": 4 }, "confidence": 0.74 }
  ] }
```

---

## 7. OpenAPI / Swagger

Each service exposes its own `/v3/api-docs` (JSON) and `/swagger-ui.html` at runtime via springdoc-openapi
(e.g. activity-service at `http://localhost:8082/swagger-ui.html`). There is no aggregated gateway UI; the
services are independent. For a client-side alternative, import
[`postman/HabitLoop.postman_collection.json`](../postman/HabitLoop.postman_collection.json) — it covers all
six services (login stores the token automatically) and the HMAC-signed webhook.

---

## 8. Pagination, filtering, versioning

- **Pagination:** `?page=0&size=20&sort=createdAt,desc`; responses use a `PageResponse<T>` wrapper
  (`content`, `page`, `size`, `totalElements`, `totalPages`).
- **Filtering:** documented per endpoint (date ranges, status, type).
- **Versioning:** path-based (`/api/...` is v1); breaking changes would move to `/api/v2`.
- **Rate limiting:** not implemented in-app; intended to be enforced at the edge (AWS WAF rate-based rules
  on the ALB) — `429` on breach. Noted as a gap in [infra/README.md](../infra/README.md).
