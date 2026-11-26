# 02 — Architecture & Technology Decisions

**Phase 2** of the execution strategy. This document defines the system architecture, service
boundaries, the frontend architecture, and records every technology decision with its rationale.
All diagrams referenced here are rendered in [03-diagrams.md](03-diagrams.md).

---

## 1. Architectural style

HabitLoop is a **microservices** system with an **event-driven backbone**. Three principles drive it:

1. **Database-per-service** — each service owns its data; no service reads another service's tables.
   Cross-service data needs are met by API calls (synchronous, read-only) or domain events (async).
2. **Event-driven decoupling** — state changes that other services care about are published as events.
   External partner events use the AWS EventBridge + Lambda pipeline; internal events use Kafka.
3. **Clean Architecture inside each service** — a clear `controller → service → repository → entity`
   layering, DTOs at the boundary, domain logic in the service layer, framework concerns at the edges.

### Why microservices here (and not a modular monolith)

The brief mandates microservices, but it is also justified: the six domains have **different scaling and
data profiles** — Integration Service is write-heavy and spiky (wearable bursts), Analytics is
read-heavy, Recommendation is compute-light but latency-sensitive. Separating them lets ECS scale each
independently and lets us use the right datastore per service (RDS vs DynamoDB).

---

## 2. Service boundaries & responsibilities

| Service | Owns (data) | Publishes (events) | Consumes (events) | Sync calls out |
|---------|-------------|--------------------|--------------------|----------------|
| **user-service** | users, tenants, roles, profiles | `user.registered`, `tenant.created` | — | — |
| **activity-service** | habits, schedules, completions, streaks | `activity.logged`, `habit.created` | `partner.activity.normalized` (from Lambda) | — |
| **challenge-service** | challenges, invites, participants, progress | `challenge.joined`, `challenge.completed` | `activity.logged` (to update progress) | user-service (validate invitee) |
| **integration-service** | connections, raw partner events | `partner.event.received` (to EventBridge) | — | — |
| **analytics-service** | aggregates (DynamoDB) | `analytics.updated` | `activity.logged`, `challenge.*` | — |
| **recommendation-service** | — (stateless/cache) | — | `analytics.updated` (cache-bust) | analytics-service (read aggregates) |

**Boundary rules enforced:**
- No service connects to another's database.
- Challenge progress is updated by **consuming** `activity.logged`, not by polling Activity Service —
  this avoids synchronous coupling (NFR-9).
- Recommendation Service never writes; it derives from analytics + recent activity and caches results.

---

## 3. Component responsibilities within a service

Standard layering (illustrated with User Service):

```
user-service/
└── src/main/java/com/habitloop/user/
    ├── controller/      REST controllers — HTTP, DTO in/out, no business logic
    ├── service/         business logic, transactions, tenant checks, orchestration
    ├── repository/      Spring Data JPA interfaces
    ├── entity/          JPA entities (own the DB schema)
    ├── dto/             request/response records (never expose entities)
    ├── mapper/          entity ↔ DTO mapping (MapStruct or manual)
    ├── security/        JWT filter, SecurityConfig, TenantContext, @CurrentUser
    ├── config/          beans, OpenAPI, Kafka config
    └── exception/       domain exceptions + @RestControllerAdvice handler
```

Shared concerns (JWT parsing, `TenantContext`, common DTOs/errors, event envelope) live in a
`backend/common` library consumed by every service — no duplication (implementation standard).

---

## 4. Communication patterns

### Synchronous (REST, JSON)
- Locally, clients call each service on its own port. In AWS, the ALB routes requests to the matching
  service. Each service validates the JWT itself; there is no standalone API Gateway service.
- A few **read-only** service-to-service calls (e.g. challenge-service verifying an invitee exists in
  user-service) go through an internal client with a **timeout + circuit breaker** (Resilience4j).

### Asynchronous (events)
- **Internal:** Kafka topics. Producers publish a domain event; consumers react. Used for
  `activity.logged → challenge progress` and `activity.logged → analytics`.
- **External:** partner webhooks hit Integration Service → raw event stored in RDS → event emitted to
  **EventBridge** → **Lambda** validates/enriches/normalizes → normalized activity published back
  (Kafka topic `partner.activity.normalized`) → Activity Service creates the activity log idempotently.

Locally, EventBridge+Lambda is emulated by a Kafka topic + a consumer that runs the identical
normalization code (AS-13), so the same end-to-end flow is demonstrable without AWS.

---

## 5. Frontend architecture (Session 2 overview)

**SvelteKit** application, static-adapter build hosted on S3/CloudFront.

- **Routing:** SvelteKit file-based routes under `src/routes` — one route per required page.
- **State:** **Svelte 5 Runes** (`$state`, `$derived`, `$effect`) for page-local view state, with shared
  auth/session and toast state in `src/lib/auth.svelte.ts` and `src/lib/stores/toast.svelte.ts`.
- **Reusable UI:** cross-cutting, framework-agnostic widgets are built as a **Stencil** component library
  (`components-stencil/`, its own compiler build) that emits standards-based custom elements
  (progress ring, chart widget, partner tile, etc.) — consumable by SvelteKit or any framework.
  Svelte-specific layout pieces that compose those elements stay as Svelte components in
  `src/lib/components/`.
- **Server interaction:** the app is a **static SPA** (adapter-static → private S3 via CloudFront, per the
  PDF). Data loads in `onMount` and actions use the browser API client (`src/lib/api.ts`); CloudFront routes
  `/api/*` to the ALB, so production requests stay same-origin.
- **Auth:** JWT held client-side (`sessionStorage`, `src/lib/auth.svelte.ts`) and sent as a Bearer header;
  expired tokens are rejected by the client route guard. The `(app)` route group is guarded by
  `(app)/+layout.ts`.
- **States:** every data view handles loading / empty / error / success explicitly.
- **Refresh:** pages reload their own data after mutations. External device events appear on the next
  page load; live SSE/polling is not currently implemented.

### Page → service mapping

| Page | Primary service(s) |
|------|--------------------|
| Register / Login | user-service |
| Onboarding | user-service |
| Dashboard | activity, analytics, recommendation |
| Habit Management | activity-service |
| Activity History | activity-service |
| Challenge Feed / Details / Create | challenge-service |
| Invitations | challenge-service |
| Integrations / Partner Connection | integration-service |
| Analytics Dashboard | analytics-service |
| Recommendations | recommendation-service |
| Profile & Settings | user-service |

### Reusable component inventory

Activity Summary Card · Habit Card · Challenge Card · Partner Connection Tile · Progress Indicator
(Stencil `<hl-progress-ring>`) · Chart Widget (Stencil `<hl-chart>`) · Recommendation Card · Nav + Sidebar ·
Toast. Stencil components expose typed props and emit DOM `CustomEvent`s, so SvelteKit
binds to them with `on:` handlers after importing the generated loader.

---

## 6. Technology decisions (with rationale)

| Decision | Chosen | Alternatives | Why |
|----------|--------|--------------|-----|
| RDS engine | **PostgreSQL 16** | MySQL | JSONB for habit schedule/metadata, partial & GIN indexes, robust constraints. |
| Web components | **Stencil** | Lit | Compiler emits typed, standards-based custom elements + loader; ideal for a reusable design-system library consumed by SvelteKit. |
| IaC | **Terraform** | CloudFormation | Modular, reviewable, reusable modules, provider-agnostic HCL. |
| Internal messaging | **Kafka** | SQS/SNS only | Brief asks for Kafka for internal async; durable log enables replay. |
| External event pipeline | **EventBridge + Lambda** | Kafka only | Brief mandates it; serverless normalization scales to spiky partner traffic. |
| Multi-tenancy | **Shared schema + `tenant_id` + Hibernate filter** | schema-/db-per-tenant | Operationally simplest, adequate isolation with enforced filters. (doc 08) |
| Resilience | **Resilience4j** | Hystrix (EOL) | Modern, Spring Boot 3 compatible, timeouts + circuit breakers + retries. |
| API docs | **springdoc-openapi** | manual | Generates OpenAPI/Swagger UI from controllers automatically. |
| Mapping | **MapStruct** | manual mappers | Compile-time, fast, no reflection. |
| Integration tests | **Testcontainers** | H2/embedded | Real Postgres/Kafka/DynamoDB-Local in tests = realistic behaviour. |
| API entry/routing | **Direct service ports locally; ALB path routing in AWS** | Spring Cloud Gateway | Matches the implemented topology without adding a separate gateway service. |
| Auth token | **JWT (HS256 dev / RS256 prod)** | opaque + session store | Stateless, service-friendly; secret/key in Secrets Manager. |

---

## 7. Cross-cutting concerns

- **Validation:** Bean Validation (`jakarta.validation`) on all request DTOs; fail fast with 400.
- **Exceptions:** per-service `@RestControllerAdvice` returning a consistent `ErrorResponse`
  (`timestamp, path, status, code, message, details[]`). Common base in `backend/common`.
- **Logging:** structured JSON logs (Logback + logstash encoder) with `tenantId`, `userId`, `traceId`
  in MDC; shipped to CloudWatch Logs.
- **Config & secrets:** no secrets in code or images. Local uses `.env` + Docker Compose; AWS uses
  Secrets Manager injected as ECS task secrets.
- **Tracing/correlation:** a `X-Correlation-Id` is propagated through REST headers and the event envelope;
  this does not depend on a standalone gateway.

---

## 8. Build & run topology

- **Local:** `docker-compose up` brings up Postgres, Kafka (+Zookeeper/KRaft), DynamoDB Local, all six
  services, and the SvelteKit dev/preview server. Clients reach services on their published ports. The
  partner-event normalizer runs as a local consumer standing in for Lambda.
- **AWS:** images in ECR → ECS services behind an ALB in a VPC (private subnets); RDS + DynamoDB in the
  data tier; EventBridge rule → Lambda; SvelteKit build in S3 behind CloudFront. Full detail in doc 06.
