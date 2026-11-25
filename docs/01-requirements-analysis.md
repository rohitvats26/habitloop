# 01 — Requirements Analysis, Assumptions & Decisions

This document is **Phase 1** of the execution strategy: analyse the brief, extract requirements,
surface assumptions, resolve ambiguities, and list anything that is genuinely open.

---

## 1. Functional requirements (what the system must do)

Extracted and grouped from the assignment brief.

### FR-A — Identity & tenancy
- FR-A1 Users register with email + password.
- FR-A2 Users authenticate; the system issues a **JWT**.
- FR-A3 Every user belongs to exactly one **tenant** (organization/community).
- FR-A4 Roles exist and gate actions (**RBAC**): at minimum `USER`, `TENANT_ADMIN`, and a platform `SYSTEM` role.
- FR-A5 A user can only ever read/write data inside their own tenant (**tenant isolation**).
- FR-A6 Tenant-aware user profiles (display name, fixed India Standard Time timezone, avatar).

### FR-B — Habits & activity
- FR-B1 Create / update / delete habits.
- FR-B2 Configure a habit **schedule & frequency** (e.g. daily, 3×/week, specific weekdays, target time).
- FR-B3 Record a **daily completion** for a habit.
- FR-B4 Maintain full **activity history**.
- FR-B5 Track **streaks** (current & longest) and completion status.

### FR-C — Challenges
- FR-C1 Create **public** or **private** challenges.
- FR-C2 **Invite** users to a challenge.
- FR-C3 **Accept / reject** invitations.
- FR-C4 Join a public challenge directly; private requires an invite.
- FR-C5 Track each participant's progress.
- FR-C6 Calculate challenge completion / leaderboard.

### FR-D — Integrations (wearables / partner apps)
- FR-D1 Connect a wearable device or partner application (establish a connection with credentials).
- FR-D2 Receive **external activity events** over a webhook endpoint.
- FR-D3 Validate the incoming request and the partner's authentication (signature / API key).
- FR-D4 Store incoming integration events in **RDS** (raw, auditable).
- FR-D5 Handle **duplicate** events and **invalid payloads** without corrupting data.
- FR-D6 Maintain integration **connection status** (connected / error / revoked).

### FR-E — Analytics
- FR-E1 Compute **daily & weekly** activity statistics.
- FR-E2 Track completion **trends**.
- FR-E3 Generate **user** and **cohort** insights.
- FR-E4 Compute challenge progress & streaks.
- FR-E5 Store **aggregated** analytics in **DynamoDB**.

### FR-F — Recommendations
- FR-F1 Analyse historical activity.
- FR-F2 Identify activity **patterns** (best weekday, best time-of-day).
- FR-F3 Recommend optimal habit **frequency**.
- FR-F4 Suggest suitable activity **timings**.
- FR-F5 Provide **actionable** recommendations visible in the UI.

### FR-G — End-to-end workflow (must be demonstrable)
Register → create habit → join challenge → connect wearable → wearable event received by Integration
Service → stored in RDS → EventBridge triggers Lambda → Lambda validates/enriches/normalizes →
valid event becomes an activity log → Analytics computes consistency/trends/progress → aggregates to
DynamoDB → Recommendation Service analyses → user sees recommendations → CloudWatch captures
logs/metrics/events throughout.

---

## 2. Non-functional requirements

| ID | Requirement |
|----|-------------|
| NFR-1  | **Multi-tenancy** with strict cross-tenant isolation at API **and** service layers. |
| NFR-2  | **Idempotent** event processing — duplicate wearable events must never create duplicate activity logs. |
| NFR-3  | **Reliability** — timeouts, circuit breakers, graceful degradation, retries, dead-letter handling. |
| NFR-4  | **Security** — JWT auth, RBAC, input validation, secure integration endpoints, no hardcoded secrets. |
| NFR-5  | **Observability** — structured logs, metrics, dashboards, alarms (CloudWatch). |
| NFR-6  | **Portability** — full stack runs locally via Docker Compose; AWS via Terraform. |
| NFR-7  | **Testability** — JUnit + Mockito unit tests, integration tests (Testcontainers). |
| NFR-8  | **Responsive**, modern, production-grade SaaS UI. |
| NFR-9  | **Data consistency** across services without unnecessary synchronous coupling (event-driven). |

---

## 3. Assumptions (made where the brief is silent — all documented per instruction #12)

| # | Assumption | Rationale |
|---|-----------|-----------|
| AS-1  | **PostgreSQL** is the RDS engine. | Brief allows MySQL *or* PostgreSQL; PG has better JSONB & indexing for our schedule/metadata columns. |
| AS-2  | **Stencil** is used for web components (not Lit). | Brief says "Lit or Stencil (choose one)". Stencil's compiler emits typed, standards-based custom elements ideal for a reusable design-system library consumed by SvelteKit. |
| AS-3  | **Terraform** for IaC (not CloudFormation). | Brief allows either; Terraform modules are easier to review and reuse. |
| AS-4  | **Shared-schema multi-tenancy** with a `tenant_id` column + Hibernate filter. | Simplest to operate at this scale; schema-/DB-per-tenant is over-engineering for the assignment. (See doc 08.) |
| AS-5  | One **user → one tenant** (no multi-tenant membership). | Brief implies a single tenant per user; keeps JWT claims and isolation simple. |
| AS-6  | A tenant is created implicitly at **first registration** (the first user becomes `TENANT_ADMIN`), or a user joins an existing tenant via a tenant code. | Brief doesn't define tenant onboarding; this is a common SaaS pattern. |
| AS-7  | **Kafka** is used for *internal* async events (service-to-service); **EventBridge + Lambda** handle *external* partner-event normalization — matching the brief's wording ("Kafka where appropriate for internal", EventBridge/Lambda for partner events). | The brief explicitly separates these two concerns. |
| AS-8  | Partner events are authenticated with a **per-connection API key + HMAC signature** over the body. | Brief says "validate partner authentication" without specifying a scheme; HMAC is the wearable-industry norm. |
| AS-9  | Analytics aggregates are **recomputed on each relevant activity event** and also recomputable via a scheduled job; DynamoDB stores the latest aggregates. | Brief wants "aggregated analytics in DynamoDB" but not the trigger cadence. |
| AS-10 | Recommendation Service is **read-mostly**: it reads analytics aggregates + recent activity and derives recommendations on demand (cached). No ML model training in scope — rule/heuristic based. | Brief says "analyze patterns / recommend"; a heuristic engine is sufficient and demonstrable, ML pipeline is out of scope. |
| AS-11 | No standalone API Gateway service: services are reached on their own ports locally; in AWS the **ALB** performs path routing to ECS services. | Matches the implemented Docker Compose topology while retaining a single routed entry point in AWS. |
| AS-12 | S3 hosts the **static SvelteKit build** (adapter-static); "secure global distribution and low-latency" ⇒ **CloudFront** in front of S3. | Brief asks for global distribution; CloudFront is the standard way. |
| AS-13 | Local dev substitutes AWS services: **Postgres container** for RDS, **DynamoDB Local** for DynamoDB, **Kafka container** for Kafka; EventBridge/Lambda flow is emulated by a Kafka topic + a local consumer that runs the same normalization logic as the Lambda. | Keeps the end-to-end workflow runnable locally without real AWS. |
| AS-14 | JWT is **stateless HS256** for local/dev with the secret in Secrets Manager; production can move to RS256 with a rotating key. | Keeps local setup simple; documented upgrade path. |
| AS-15 | Timezone handling: all profiles use the IANA timezone `Asia/Kolkata`; "daily" boundaries and streaks are computed in India Standard Time (IST). | The product uses one consistent timezone and does not ask users to configure it. |

---

## 4. Resolved ambiguities

- **"Store events in RDS" vs "store activity logs"** — these are two different things. Raw partner events
  are stored by Integration Service in RDS (audit trail). *Normalized* activity logs are created only
  after Lambda validation and are written to the Activity Service's store. This separation is what makes
  idempotency and replay possible.
- **Where does de-duplication happen?** At two layers: (1) Integration Service rejects a duplicate raw
  event by `(connection_id, external_event_id)` uniqueness; (2) Activity Service rejects a duplicate
  normalized log by an idempotency key `(tenant_id, user_id, source, external_event_id)`. Belt and braces.
- **Analytics store choice** — transactional data (habits, completions, challenges, raw events) lives in
  RDS; only **aggregated, high-read** analytics live in DynamoDB, matching the brief.

---

## 5. Genuinely open questions (non-blocking — sensible defaults chosen, revisit on request)

1. **Tenant onboarding UX** — self-serve (first user = admin) vs invite-code join. Default: both supported,
   self-serve shown in the demo. *(Not blocking; AS-6.)*
2. **Challenge scoring rule** — % of days completed vs points vs streak-based leaderboard. Default:
   percentage of target days completed within the challenge window. *(Not blocking.)*
3. **Recommendation richness** — heuristic now; could later be an ML service. Default: heuristic. *(AS-10.)*

None of these block implementation. They are noted so the reviewer can redirect if desired.

---

## 6. Mapping to the three assignment sessions

| Session | Covered by |
|---------|-----------|
| **Session 1 — Microservices Design** | docs 02 (architecture), 04 (DB), 05 (API), 07 (events), 08 (security) + backend implementation (later phase) |
| **Session 2 — Frontend Design** | doc 02 (frontend architecture) + `frontend/` implementation (later phase) |
| **Session 3 — AWS Deployment & Security** | docs 06 (AWS infra), 08 (security) + `infra/` Terraform (later phase) |

---

## 7. Acceptance-criteria traceability

Every item in the brief's "Final Acceptance Criteria" maps to a requirement above:

| Acceptance criterion | Requirement(s) |
|----------------------|----------------|
| Register & authenticate | FR-A1, FR-A2 |
| Create & track habits | FR-B1–B5 |
| Join & participate in challenges | FR-C1–C6 |
| Receive & process integration events | FR-D1–D6, FR-G |
| No duplicate activity logs | NFR-2, FR-D5 |
| Analytics & streaks correct | FR-E1–E5, FR-B5 |
| Recommendations displayed | FR-F1–F5 |
| Tenant isolation enforced | FR-A5, NFR-1 |
| Frontend ↔ backend communicate | Session 2 |
| Runs locally via Docker Compose | NFR-6 |
| AWS config provided | Session 3 |
| Security & monitoring addressed | NFR-4, NFR-5 |
| Tests pass | NFR-7 |
| Docs sufficient to run | this `docs/` set + README |
