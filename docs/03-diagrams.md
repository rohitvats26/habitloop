# 03 — Architecture Diagrams (Mermaid)

All ten required diagrams, each followed by a plain-language explanation. These render on GitHub and in
any Mermaid-aware viewer.

> **Revision note.** The frontend is a **static SPA on Amazon S3** (client-side JWT, CORS to the backend).
> There is no standalone API Gateway: local clients call service ports directly, and the AWS ALB routes
> requests to the backend services. The network topology is in
> [diagrams/network-architecture.drawio](diagrams/network-architecture.drawio); see
> [06-aws-infrastructure.md](06-aws-infrastructure.md) and `frontend/README.md`.

---

## 1. Overall system architecture

```mermaid
flowchart TB
    subgraph Client["Client"]
        Browser["SvelteKit SPA (browser)"]
    end

    subgraph Edge["Edge / Entry"]
        CF["CloudFront + S3<br/>(static frontend)"]
        ALB["Application Load Balancer<br/>(AWS path routing)"]
    end

    subgraph Backend["Backend microservices (ECS)"]
        US["user-service"]
        AS["activity-service"]
        CS["challenge-service"]
        IS["integration-service"]
        ANS["analytics-service"]
        RS["recommendation-service"]
    end

    subgraph Async["Event backbone"]
        KAFKA[("Kafka")]
        EB["EventBridge"]
        LAMBDA["Lambda<br/>normalizer"]
    end

    subgraph Data["Data tier"]
        RDS[("RDS PostgreSQL<br/>db-per-service")]
        DDB[("DynamoDB<br/>analytics aggregates")]
    end

    Partner["Wearable / Partner app"]

    Browser --> CF
    Browser -->|REST + JWT| ALB
    ALB --> US & AS & CS & IS & ANS & RS
    US & AS & CS & IS --> RDS
    ANS --> DDB
    RS -->|read| ANS

    AS -->|activity.logged| KAFKA
    KAFKA --> CS
    KAFKA --> ANS
    ANS -->|analytics.updated| KAFKA
    KAFKA --> RS

    Partner -->|webhook + HMAC| IS
    IS -->|raw event| RDS
    IS -->|partner.event.received| EB
    EB --> LAMBDA
    LAMBDA -->|partner.activity.normalized| KAFKA
    KAFKA --> AS
```

**Explanation.** The browser loads the static SvelteKit app from CloudFront/S3 and talks to the backend
through the ALB in AWS; the ALB routes requests directly to the six services. Locally, clients call each
service on its own port. Transactional data lives in
RDS (one logical database per service); aggregated analytics live in DynamoDB. The event backbone is the
spine: Activity Service publishes `activity.logged`, which both Challenge and Analytics react to; a
wearable's webhook enters Integration Service, is stored raw in RDS, then flows EventBridge → Lambda →
normalized event → Activity Service, which creates the real activity log.

---

## 2. Microservices architecture (internal layering & ownership)

```mermaid
flowchart LR
    Client["Frontend / API caller"]

    subgraph US["user-service"]
        USc["controller"] --> USs["service"] --> USr["repository"] --> USdb[("habitloop_user")]
    end
    subgraph AS["activity-service"]
        ASc["controller"] --> ASs["service"] --> ASr["repository"] --> ASdb[("habitloop_activity")]
    end
    subgraph CS["challenge-service"]
        CSc["controller"] --> CSs["service"] --> CSr["repository"] --> CSdb[("habitloop_challenge")]
    end
    subgraph IS["integration-service"]
        ISc["controller"] --> ISs["service"] --> ISr["repository"] --> ISdb[("habitloop_integration")]
    end
    subgraph ANS["analytics-service"]
        ANSc["controller"] --> ANSs["service"] --> ANSr["repository"] --> ANSdb[("DynamoDB")]
    end
    subgraph RS["recommendation-service"]
        RSc["controller"] --> RSs["service (heuristics)"]
    end

    Client --> USc & ASc & CSc & ISc & ANSc & RSc
    RSs -->|read aggregates| ANSc
    CSs -. consumes activity.logged .-> AS
```

**Explanation.** Every service repeats the same clean layering — controller (HTTP/DTO), service (domain
logic + tenant checks), repository (persistence) — and owns its own database. Clients reach service
controllers directly locally; the AWS ALB routes to them in deployment. Recommendation Service is special:
no database of its own, it reads analytics aggregates and
derives recommendations. The dashed line shows Challenge Service learning about progress by consuming
events rather than calling Activity Service synchronously.

---

## 3. Frontend-to-backend communication

```mermaid
flowchart TB
    subgraph FE["SvelteKit frontend"]
        R["Routes (pages)"]
        Runes["Svelte 5 Runes stores<br/>(auth, habits, challenges)"]
        Stencil["Stencil web components<br/>(chart, progress, tiles)"]
        APIc["Typed API client"]
    end

    Services["Backend service APIs"]
    ALB["AWS ALB<br/>(path routing)"]

    R --> Runes
    R --> Stencil
    APIc -->|"HTTPS + Bearer JWT (AWS)"| ALB
    ALB --> Services
    APIc -.->|"Bearer JWT (local direct)"| Services
    Services -->|JSON| APIc
    APIc --> Runes
    Runes --> R
```

**Explanation.** The static SvelteKit app reads reactive state from Runes stores and renders Stencil
components for charts and progress. Its browser API client calls the backend services with a Bearer JWT:
directly on local service ports, or through ALB path routing in AWS. Each target service validates the JWT.
Responses update the Runes stores, which re-render the pages.

---

## 4. Database architecture

```mermaid
flowchart TB
    subgraph RDS["Amazon RDS — PostgreSQL (database-per-service)"]
        U[("habitloop_user<br/>tenants, users, roles, profiles")]
        A[("habitloop_activity<br/>habits, schedules, completions, streaks, activity_log")]
        C[("habitloop_challenge<br/>challenges, invites, participants, progress")]
        I[("habitloop_integration<br/>connections, raw_partner_events")]
    end

    subgraph DDB["Amazon DynamoDB (single-table)"]
        T[("habitloop-analytics<br/>PK=TENANT#id#USER#id<br/>SK=STAT#daily#date / STAT#weekly#wk / CHALLENGE#id")]
    end

    US["user-service"] --> U
    AS["activity-service"] --> A
    CS["challenge-service"] --> C
    IS["integration-service"] --> I
    ANS["analytics-service"] --> T
    RS["recommendation-service"] -.reads.-> T
```

**Explanation.** Each service has an isolated logical database in RDS — no shared tables, no cross-service
foreign keys (references across services are by id only, validated via events/APIs). Every tenant-owned
table carries a `tenant_id` column. DynamoDB holds **only** aggregated analytics using a single-table
design: the partition key scopes to tenant+user and the sort key distinguishes daily stats, weekly stats,
and per-challenge rollups, giving O(1) reads for the dashboard.

---

## 5. AWS infrastructure architecture

```mermaid
flowchart TB
    User(("User"))
    User --> CF["CloudFront"]
    CF --> S3["S3 (static SvelteKit)"]
    User --> R53["Route 53"]

    subgraph VPC["VPC 10.0.0.0/16"]
        subgraph PUB["Public subnets (2 AZs)"]
            IGW["Internet Gateway"]
            ALB["Application Load Balancer"]
            NAT["NAT Gateway"]
        end
        subgraph APP["Private app subnets (2 AZs)"]
            ECS["ECS Fargate services<br/>(6 microservices)"]
        end
        subgraph DATA["Private data subnets (2 AZs)"]
            RDS[("RDS PostgreSQL<br/>Multi-AZ")]
            DDBE["DynamoDB (VPC endpoint)"]
        end
    end

    subgraph Serverless["Serverless / events"]
        EB["EventBridge"]
        LAM["Lambda normalizer"]
        DLQ["SQS DLQ"]
    end

    SM["Secrets Manager"]
    ECR["ECR"]
    CW["CloudWatch<br/>logs/metrics/alarms"]

    R53 --> ALB
    ALB --> ECS
    ECS --> RDS
    ECS --> DDBE
    ECS -->|pull images| ECR
    ECS -->|secrets| SM
    ECS --> NAT --> IGW
    IS2["integration-service (in ECS)"] -. emits .-> EB
    EB --> LAM
    LAM -->|failures| DLQ
    LAM --> SM
    ECS --> CW
    LAM --> CW
```

**Explanation.** A VPC with three subnet tiers across two AZs: public (ALB, NAT, IGW), private-app
(ECS Fargate running the services — no public IPs), and private-data (RDS Multi-AZ; DynamoDB reached via a
VPC endpoint so traffic never leaves AWS). Images come from ECR, secrets from Secrets Manager. The static
frontend is served globally by CloudFront in front of S3. Partner events emitted by Integration Service go
to EventBridge → Lambda, with an SQS dead-letter queue for failures. Everything logs and alarms to
CloudWatch.

---

## 6. External integration event flow

```mermaid
flowchart LR
    P["Partner / wearable"] -->|"POST /webhook<br/>API key + HMAC"| IS["integration-service"]
    IS -->|verify sig + tenant| V{"valid?"}
    V -->|no| R4["400/401<br/>log + metric"]
    V -->|yes| DUP{"duplicate?<br/>(connection_id,<br/>external_event_id)"}
    DUP -->|yes| ACK["200 OK (no-op)"]
    DUP -->|no| STORE[("store raw event<br/>in RDS")]
    STORE --> EMIT["emit partner.event.received"]
    EMIT --> EB["EventBridge"]
    EB --> LAM["Lambda:<br/>validate → enrich → normalize"]
    LAM -->|bad| DLQ["SQS DLQ + alarm"]
    LAM -->|ok| NORM["publish partner.activity.normalized"]
    NORM --> AS["activity-service"]
    AS --> IDEM{"idempotency key<br/>already seen?"}
    IDEM -->|yes| SKIP["skip (no dup log)"]
    IDEM -->|no| LOG[("create activity_log")]
    LOG --> KAFKA["activity.logged → Kafka"]
```

**Explanation.** A partner POSTs an event with an API key and an HMAC signature. Integration Service
verifies the signature and tenant, drops invalid payloads, and short-circuits duplicates by a unique
`(connection_id, external_event_id)`. Valid new events are stored raw in RDS and emitted to EventBridge.
Lambda validates/enriches/normalizes; unrecoverable events go to a DLQ with an alarm. Good events become
`partner.activity.normalized`, consumed by Activity Service, which applies a **second** idempotency check
before writing the activity log — so even a replayed event never creates a duplicate.

---

## 7. User registration & authentication (sequence)

```mermaid
sequenceDiagram
    actor U as User
    participant FE as SvelteKit
    participant US as user-service
    participant DB as RDS (user)
    participant K as Kafka

    U->>FE: fill registration form
    FE->>US: POST /api/auth/register
    US->>US: validate, hash password (BCrypt)
    US->>DB: create tenant (if new) + user + role
    US->>K: publish user.registered
    US-->>FE: 201 Created
    U->>FE: login (email, password)
    FE->>US: POST /api/auth/login
    US->>DB: load user, verify BCrypt hash
    US-->>FE: 200 + JWT (tenantId, userId, roles)
    FE->>FE: store JWT for browser API calls
    Note over FE,US: subsequent calls send Bearer JWT;<br/>each service validates it
```

**Explanation.** Registration validates input, hashes the password with BCrypt, creates the tenant (if the
user is the first) plus the user and role, and announces `user.registered`. Login verifies the hash and
returns a JWT carrying `tenantId`, `userId`, and roles. The frontend sends it as a Bearer token on
subsequent requests, and each target service validates it.

---

## 8. Habit creation & activity tracking (sequence)

```mermaid
sequenceDiagram
    actor U as User
    participant FE as SvelteKit
    participant AS as activity-service
    participant DB as RDS (activity)
    participant K as Kafka
    participant CS as challenge-service
    participant ANS as analytics-service

    U->>FE: create habit (name, schedule, frequency)
    FE->>AS: POST /api/habits (JWT)
    AS->>AS: validate + set tenant from JWT
    AS->>DB: insert habit + schedule
    AS-->>FE: 201 habit

    U->>FE: mark habit done today
    FE->>AS: POST /api/habits/{id}/completions (JWT)
    AS->>DB: insert completion, recompute streak
    AS->>K: publish activity.logged
    AS-->>FE: 200 + updated streak
    K-->>CS: update challenge progress
    K-->>ANS: update daily/weekly aggregates
    Note over FE: dashboard store invalidated → re-renders
```

**Explanation.** Creating a habit validates the payload, stamps the tenant from the JWT, and persists the
habit plus its schedule. Recording a completion inserts the row, recomputes the streak in India Standard
Time (`Asia/Kolkata`), and publishes `activity.logged`. Challenge Service and Analytics Service both consume that event
to update progress and aggregates asynchronously, so the write path stays fast. The frontend invalidates
the dashboard store so it re-renders with the new streak.

---

## 9. Challenge participation (sequence)

```mermaid
sequenceDiagram
    actor A as User A (creator)
    actor B as User B (invitee)
    participant FE as SvelteKit
    participant CS as challenge-service
    participant US as user-service
    participant DB as RDS (challenge)
    participant K as Kafka

    A->>CS: POST /api/challenges (public/private, JWT)
    CS->>DB: create challenge (tenant-scoped)
    CS-->>A: 201 challenge

    A->>CS: POST /api/challenges/{id}/invites {userId:B}
    CS->>US: GET /internal/users/B (verify, same tenant)
    US-->>CS: user ok
    CS->>DB: create invitation(PENDING)
    CS-->>A: 200

    B->>CS: POST /api/invitations/{id}/accept
    CS->>DB: invitation ACCEPTED + add participant
    CS-->>B: 200 joined
    K-->>CS: activity.logged events update B's progress
    CS->>DB: recompute participant progress + completion
```

**Explanation.** The creator makes a challenge (public or private, scoped to the tenant). Inviting a user
triggers a read-only, same-tenant verification call to User Service (timeout + circuit breaker). The
invitee accepts, becoming a participant. From then on, Challenge Service updates each participant's progress
by **consuming** `activity.logged` events — no synchronous polling — and recomputes completion against the
challenge's target.

---

## 10. Wearable integration event-processing (sequence)

```mermaid
sequenceDiagram
    participant P as Wearable/Partner
    participant IS as integration-service
    participant RDS as RDS (integration)
    participant EB as EventBridge
    participant L as Lambda
    participant DLQ as SQS DLQ
    participant K as Kafka
    participant AS as activity-service
    participant ANS as analytics-service

    P->>IS: POST /api/webhooks/{connId} (HMAC, external_event_id)
    IS->>IS: verify HMAC + connection active
    alt invalid
        IS-->>P: 401/400 (+ metric)
    else duplicate external_event_id
        IS-->>P: 200 (no-op)
    else new & valid
        IS->>RDS: store raw event
        IS->>EB: partner.event.received
        IS-->>P: 202 Accepted
    end
    EB->>L: invoke
    L->>L: validate schema, enrich (user/habit map), normalize units
    alt unrecoverable
        L->>DLQ: send + CloudWatch alarm
    else ok
        L->>K: partner.activity.normalized (idempotency key)
    end
    K->>AS: consume
    AS->>AS: idempotency check → create activity_log (once)
    AS->>K: activity.logged
    K->>ANS: update aggregates
```

**Explanation.** This is the full event pipeline and the heart of the brief's end-to-end workflow. The
partner's webhook is authenticated by HMAC and de-duplicated at the edge. Valid events are persisted raw
and handed to EventBridge → Lambda, which does the real work of validation, enrichment (mapping the partner
event to a HabitLoop user/habit), and unit normalization. Failures land in a DLQ with an alarm; successes
become a normalized event that Activity Service turns into exactly one activity log (idempotency key),
which then feeds analytics — closing the loop from device to dashboard.
