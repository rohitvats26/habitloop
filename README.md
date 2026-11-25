# HabitLoop — Multi-Tenant Activity Platform

HabitLoop is a community-oriented, **multi-tenant SaaS** where users register, track personal habits,
join public/private challenges, connect wearables & partner apps, and receive analytics-driven
recommendations. It is built as a **Spring Boot 3 microservices** backend, a **SvelteKit + Svelte 5**
frontend with reusable **Stencil** web components, and an **AWS (ECS/RDS/DynamoDB/EventBridge/Lambda)**
deployment described as Terraform.

This repository answers the three assignment sessions end to end:

| Session | Scope | Where | Verified |
|---|---|---|---|
| **1 — Microservices Design** | 6 Spring Boot services + shared `common`, DB-per-service, event-driven, multi-tenancy, JWT/RBAC | [`backend/`](backend/) | ✅ `mvn test` → **54 unit/integration tests pass** |
| **2 — Frontend Design** | SvelteKit (Svelte 5 Runes) **static SPA** for S3, 14 pages, client-side JWT auth + 7 Stencil web components | [`frontend/`](frontend/), [`components-stencil/`](components-stencil/) | ⚠️ Code complete; `npm`-build locally (Node not available in the build env) |
| **3 — AWS Deployment & Security** | Terraform (9 modules), ECS Fargate, RDS, DynamoDB, EventBridge + Lambda, Secrets Manager, CloudWatch | [`infra/`](infra/), [`backend/lambda-normalizer/`](backend/lambda-normalizer/) | ✅ `terraform validate` → **valid**; **not deployed** (no AWS account) |

> **Honest status.** Backend compiles and its tests pass. The frontend and Stencil library are complete
> source but were not `npm`-built here (no Node in the build environment). The AWS infrastructure is
> **written and validated, not deployed** — no `terraform apply` was run and nothing here is claimed as a
> live deployment. See [infra/README.md](infra/README.md) for the deploy steps and known gaps.

---

## Repository layout

```
habitloop/
├── README.md                     ← you are here (project index)
├── RUNNING.md                    local run guide (Docker Compose)
├── backend/docker-compose.yml    full local stack: Postgres, Kafka (KRaft), 6 services + lambda
├── docker/ , scripts/            Postgres init SQL, e2e smoke-test.sh
├── docs/                         8 design documents (Session 1–3 design)
├── backend/                      Maven multi-module reactor (Session 1 + Lambda for Session 3)
│   ├── common/                   shared security (JWT), tenant context, exceptions, DTOs, events
│   ├── user-service/             auth, tenants, roles, profiles          (RDS)
│   ├── activity-service/         habits, completions, streaks, ingest    (RDS)
│   ├── challenge-service/        challenges, invitations, participation  (RDS)
│   ├── integration-service/      partner connections, HMAC webhooks      (RDS)
│   ├── analytics-service/        aggregated stats/trends                 (DynamoDB)
│   ├── recommendation-service/   heuristic recommendations               (reads analytics)
│   └── lambda-normalizer/        partner-event normalizer (local consumer + AWS Lambda handler)
├── frontend/                     SvelteKit static SPA (adapter-static → S3) — 14 pages, client JWT auth
├── components-stencil/           @habitloop/components — 7 Stencil web components
└── infra/                        Terraform (Session 3)
    ├── modules/                  network, alb, ecs, rds, dynamodb, ecr, secrets, eventbridge, observability
    └── envs/dev/                 composition (providers, variables, main, outputs, tfvars.example)
```

---

## Design documentation (read in this order)

| # | Document | What it covers |
|---|----------|----------------|
| 1 | [docs/01-requirements-analysis.md](docs/01-requirements-analysis.md) | Requirements from the brief, 15 assumptions, resolved ambiguities, open questions |
| 2 | [docs/02-architecture.md](docs/02-architecture.md) | System & microservices architecture, service boundaries, tech-stack decisions |
| 3 | [docs/03-diagrams.md](docs/03-diagrams.md) | 10 Mermaid diagrams (architecture + sequences) with explanations |
| 4 | [docs/04-database-design.md](docs/04-database-design.md) | Per-service RDS schemas (DB-per-service) + DynamoDB single-table design |
| 5 | [docs/05-api-specification.md](docs/05-api-specification.md) | REST endpoints per service with request/response examples |
| 6 | [docs/06-aws-infrastructure.md](docs/06-aws-infrastructure.md) | VPC, ALB, ECS, ECR, RDS, DynamoDB, EventBridge, Lambda, Secrets Manager, CloudWatch |
| 7 | [docs/07-event-driven-design.md](docs/07-event-driven-design.md) | Kafka topics, EventBridge+Lambda flow, idempotency, retries, DLQ, reliability |
| 8 | [docs/08-security-multitenancy.md](docs/08-security-multitenancy.md) | JWT, RBAC, tenant isolation, secret management, input validation |

---

## Technology stack

**Backend:** Java 17 · Spring Boot 3.3.5 · Spring Security (JWT) · Spring Data JPA/Hibernate · Flyway · Apache Kafka · Resilience4j · springdoc-openapi · JUnit 5 + Mockito + Testcontainers · Maven
**Frontend:** SvelteKit 2 · Svelte 5 Runes · adapter-node (SSR) · TypeScript · Stencil 4 web components
**Data:** Amazon RDS (PostgreSQL) · Amazon DynamoDB (single-table)
**AWS:** VPC · ALB · ECS Fargate · ECR · EventBridge · Lambda · SQS (DLQ) · SNS · Secrets Manager · IAM · CloudWatch
**DevOps:** Docker · Docker Compose · Terraform · Git

## Microservices

| Service | Port | Data store | Responsibility |
|---------|------|-----------|----------------|
| `user-service`           | 8081 | RDS `habitloop_user`        | Registration, JWT auth, tenants, roles, profiles |
| `activity-service`       | 8082 | RDS `habitloop_activity`    | Habits, schedules, daily completions, streaks |
| `challenge-service`      | 8083 | RDS `habitloop_challenge`   | Public/private challenges, invites, participation |
| `integration-service`    | 8084 | RDS `habitloop_integration` | Wearable/partner connections, HMAC webhook ingest |
| `analytics-service`      | 8085 | DynamoDB `habitloop-analytics` | Aggregated stats, trends, cohort insights |
| `recommendation-service` | 8086 | (reads analytics)           | Pattern analysis, timing/frequency recommendations |

> **No standalone API gateway.** Routing is handled by **ALB path-based listener rules** on AWS and by the
> SvelteKit server + Docker Compose networking locally — each service owns its `/api/...` path prefix.

## How the services interact

Two interaction styles — **synchronous** (REST, circuit-breaker protected) for request/response, and
**asynchronous** (events) for propagation and decoupling:

**Synchronous (REST):**
- The **SvelteKit static SPA** (on S3) calls the backend services from the browser via the ALB, sending the
  JWT as a Bearer token; services enable CORS for the S3 origin.
- **challenge-service → user-service** `/internal/users/*` — verify a user exists in the same tenant before
  inviting (Resilience4j `@CircuitBreaker` with a fallback).
- **recommendation-service → analytics-service** — pull the user's aggregated patterns on demand (also
  circuit-breaker protected, with a short TTL cache).

**Asynchronous (events) — the core loop, incl. the Recommendation Microservice:**
```
activity-service  --activity.logged-->        analytics-service
integration-svc   --partner.event.received--> normalizer (Lambda/local) --partner.activity.normalized--> activity-service
analytics-service --analytics.updated-->       recommendation-service
```
- A habit completion in **activity-service** emits `activity.logged` (after-commit).
- **analytics-service** consumes it, upserts aggregates (DynamoDB, idempotent), then emits
  `analytics.updated`.
- **recommendation-service** consumes `analytics.updated` and recomputes timing/frequency/consistency
  suggestions — it is purely analytics-driven and holds no DB of its own.
- Partner webhooks are HMAC-verified by **integration-service**, normalized by the **Lambda/normalizer**,
  and fed back as activity. Failures go to an **SQS DLQ**.

A full click-by-click story is in [docs/09-user-walkthrough.md](docs/09-user-walkthrough.md); all 10 flow
diagrams are in [docs/03-diagrams.md](docs/03-diagrams.md), and the network topology is in
[docs/diagrams/network-architecture.drawio](docs/diagrams/network-architecture.drawio).

---

## How to run / verify

### Backend tests (works today)
```bash
cd backend
mvn test        # 54 tests across all modules
```

### Full local stack (Docker Compose)
Postgres + Kafka (KRaft) + 6 services + the normalizer. See [RUNNING.md](RUNNING.md).
```bash
docker compose -f backend/docker-compose.yml up --build
./scripts/smoke-test.sh      # end-to-end: register → habit → log → webhook (HMAC-signed) → analytics
```
Demo tenant seeded: `ACME2026` / `ria@acme.com` / `Password123!`.

### Frontend
```bash
cd components-stencil && npm install && npm run build
cd ../frontend && npm install && npm run dev      # SSR app on :3000
```

### AWS (Session 3 — design validated, not deployed)
```bash
cd infra/envs/dev
terraform init -backend=false && terraform validate   # → valid
```
Deploy steps and intentional gaps (MSK/Kafka, HTTPS/ACM, internal routing): [infra/README.md](infra/README.md).

---

## Design decisions at a glance

- **Database:** PostgreSQL (over MySQL) — stronger JSON/JSONB, partial indexes, standard in Spring shops.
- **Web components:** Stencil (over Lit) — compiler emitting standards-based, framework-agnostic custom
  elements with typed props, lazy-loading and a generated loader; a good fit for a design-system library
  consumed by SvelteKit.
- **Frontend hosting:** SvelteKit **static SPA (adapter-static) on Amazon S3**, per the case-study brief.
  The browser calls the backend cross-origin via the ALB, so the JWT is a client-side Bearer token and the
  services enable **CORS**. (A Node/SSR + httpOnly-cookie variant is possible but the brief specifies S3.)
  Documented in `frontend/README.md`.
- **IaC:** Terraform (over CloudFormation) — modular, provider-agnostic, easier to read/review.
- **Multi-tenancy:** shared-schema with a `tenant_id` discriminator + Hibernate `@Filter`, enabled per
  transaction via an AOP aspect, driven by a `TenantContext` populated from the JWT. (Rationale in doc 08.)
- **Idempotency:** 3 layers — unique DB constraints, DynamoDB PROCESSED markers, and event replay safety.
- **Secrets:** never stored in RDS or source — only references; JWT secret and DB password come from
  Secrets Manager on AWS and env vars locally.

Per the brief, **no AWS deployment is claimed as successful** — the infrastructure is written and validated
only. Implemented vs. simulated functionality is called out in each component's own README and in the status
table above.
