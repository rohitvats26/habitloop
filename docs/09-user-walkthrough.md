# 09 — User Walkthrough (end-to-end scenario)

A single, concrete story that exercises the whole platform: **Ria** signs up for *Acme Wellness*, creates a
habit, logs it over a few days (missed → done → completed), checks her analytics, opens the Recommendations
page, and **accepts a personalized timing recommendation**.

For each step: what Ria does in the UI, the API call behind it, and what happens across the microservices.

> **Prerequisite for the full loop.** The *activity → analytics → recommendation* chain is event-driven. Run
> the local stack with `KAFKA_ENABLED=true` (the Docker Compose default) so the events flow. See the note at
> the end about the AWS variant.

---

## Cast & setup
- **Tenant:** Acme Wellness (multi-tenant — every row Ria touches is scoped to her `tenantId`, taken from
  her JWT, never from the request body).
- **User:** Ria (TENANT_ADMIN of the tenant she creates).
- Base: local Docker Compose; each service on its own port (no gateway). The SvelteKit **static SPA** is
  what Ria actually sees (dev: `http://localhost:5173`; AWS: the S3 website URL); it calls the services from
  the browser with a Bearer token.

---

## Step 1 — Register
**UI:** `/register` → fills email, password, display name, tenant name → submit.
**API:** `POST {userBase}/api/auth/register`
```json
{ "email": "ria@acme.com", "password": "Password123!", "displayName": "Ria",
  "timezone": "Asia/Kolkata", "tenantName": "Acme Wellness" }
```
**Behind the scenes (user-service):** creates the `Tenant`, the `User` (BCrypt-hashed password), a
`UserProfile`, and assigns the `TENANT_ADMIN` role. The SPA then auto-logs-in and stores the JWT client-side
(`localStorage`), sending it as a `Authorization: Bearer` header on subsequent calls.

## Step 2 — Log in (if returning)
**UI:** `/login`. **API:** `POST {userBase}/api/auth/login` → `{ accessToken, refreshToken, expiresIn }`.
The JWT carries `tenantId`, `userId`, `roles`. The client stores it (`auth.svelte.ts`) and the `(app)` route
guard (`(app)/+layout.ts`) redirects to `/login` when there is no token.

## Step 3 — Create an activity (habit)
**UI:** `/habits` → "New habit" → name *Morning run*, schedule Mon/Wed/Fri at 07:00.
**API:** `POST {activityBase}/api/habits`
```json
{ "name": "Morning run", "frequency": "CUSTOM",
  "schedule": { "daysOfWeek": [1,3,5], "targetTime": "07:00", "perWeek": 3 }, "targetPerPeriod": 3 }
```
**Behind the scenes (activity-service):** persists the `Habit` (schedule stored as JSONB), initializes a
`HabitStreak` at 0. The jsonb schedule keeps the flexible shape without extra tables.

## Step 4 — Mark it over a few days: missed → done → completed
HabitLoop models three day-states for a scheduled habit:

| State | What it means | How it happens |
|-------|---------------|----------------|
| **missed** | a scheduled day with no completion | *implicit* — no completion row exists for that date; the streak calculator treats the gap as a miss |
| **done** | logged complete for a given day | `POST /api/habits/{id}/completions` with that `date` |
| **completed** | the habit's target for the period is met (e.g. 3×/week reached) | derived by the streak/target calculator once enough "done" days exist |

**UI:** on `/activity` (or the habit card) Ria taps "Mark done" for today.
**API:** `POST {activityBase}/api/habits/{habitId}/completions`
```json
{ "date": "2026-11-24", "note": "felt great" }
```
**Response** `200` → `{ "completionId": "...", "date": "2026-11-24", "streak": { "current": 5, "longest": 12 } }`

**Behind the scenes:**
1. activity-service writes a `HabitCompletion` (unique `(habit_id, date)` → a replayed tap returns
   `409 DUPLICATE_COMPLETION`, so the action is idempotent).
2. `StreakCalculator` recomputes current/longest streak, folding in the "missed" scheduled days.
3. On commit, activity-service publishes an **`activity.logged`** event (`@TransactionalEventListener`
   AFTER_COMMIT → Kafka).

> A missed day needs no API call — it's the absence of a completion on a scheduled date; the calculator and
> analytics both account for it.

## Step 5 — View analytics
**UI:** `/analytics`. **API:** `GET {analyticsBase}/api/analytics/summary` (+ `/daily`, `/weekly`).
```json
{ "activeHabits": 4, "completedToday": 2, "weeklyCompletionRate": 0.71,
  "longestStreak": 12, "currentStreak": 5,
  "trend": [ { "week": "2026-W45", "rate": 0.64 }, { "week": "2026-W46", "rate": 0.71 } ] }
```
**Behind the scenes:** analytics-service **consumed** the `activity.logged` event and upserted aggregates
into **DynamoDB** (single-table, idempotent via a PROCESSED marker so replays don't double-count). On commit
it publishes **`analytics.updated`**. The dashboard renders with Stencil components (`hl-stat-card`,
`hl-chart`, `hl-progress-ring`).

## Step 6 — Visit the Recommendations page
**UI:** `/recommendations`. **API:** `GET {recommendationBase}/api/recommendations`.
```json
{ "generatedAt": "2026-11-24T10:00:00Z", "items": [
  { "type": "TIMING", "habitId": "4d2e...", "title": "Run earlier",
    "message": "You complete 'Morning run' most on days you start before 7:30am.",
    "action": { "label": "Set reminder 07:00", "suggestedTime": "07:00" }, "confidence": 0.82 } ] }
```
**Behind the scenes (recommendation-service):** it consumed **`analytics.updated`**, read the user's
patterns from analytics (via `AnalyticsClient`, Resilience4j circuit-breaker protected), and ran its
heuristics (TIMING / FREQUENCY / CONSISTENCY). Results are cached briefly (5 min TTL). This is the
**Recommendation Microservice** the brief calls out — it never talks to the DB directly; it is purely
analytics-driven.

## Step 7 — Accept a personalized timing recommendation
**UI:** Ria clicks **"Set reminder 07:00"** on the `hl-recommendation-card`.
**API:** the accept action applies the suggestion to the habit —
`PUT {activityBase}/api/habits/{habitId}` with the suggested `targetTime: "07:00"` (a FREQUENCY
recommendation would instead set `perWeek`).
**Result:** the habit's schedule updates, a fresh `activity.logged`-style change propagates, and the next
analytics/recommendation cycle reflects the accepted change. Ria sees a toast confirming the update.

---

## The event loop, in one line
```
mark done → activity.logged → analytics upserts (DynamoDB) → analytics.updated
          → recommendation-service recomputes → Ria accepts → habit updated → (loop)
```

## Multi-tenancy & safety shown by this flow
- Every read/write is filtered by `tenant_id` (Hibernate filter driven by `TenantContext` from the JWT) —
  Ria can never see or touch Sam's or another tenant's data; a cross-tenant id returns `404`, not `403`,
  so existence isn't leaked.
- Completions, webhook events, and analytics upserts are all **idempotent**, so retries/replays are safe.

## AWS variant (honest note)
On AWS the event chain needs a broker. In the current Terraform the services run with
`KAFKA_ENABLED=false` and **no MSK**, so on a raw cloud deploy Steps 5–7 would not auto-populate. To make
this exact walkthrough work in the cloud, enable one of: an **MSK** cluster, **EventBridge** for the
internal events (preferred — cheaper, already used for partner events), or **synchronous** service calls.
This is tracked in [infra/README.md](../infra/README.md) under known gaps. Locally (Compose,
`KAFKA_ENABLED=true`) the full loop works today — see [RUNNING.md](../RUNNING.md) and
`scripts/smoke-test.sh`.
