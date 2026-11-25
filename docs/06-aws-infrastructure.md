# 06 — AWS Infrastructure & Deployment Design (Session 3)

> **Not deployed.** This is the infrastructure **design** and the Terraform plan. No AWS resources have
> been created; nothing here is claimed as a working deployment (brief rule #9).
>
> **Revision (PDF-aligned, free-tier).** The Terraform under `infra/` was trimmed to the case-study PDF's
> resource list and made free-tier-friendly:
> - **Frontend → private Amazon S3 behind CloudFront** (not an ECS container). ECS runs the 6 backend
>   services only; CloudFront routes `/api/*` to the ALB.
> - **No NAT Gateway** (not in the PDF): private app subnets reach AWS via **VPC endpoints** (S3 + DynamoDB
>   gateway, ECR + Secrets Manager + CloudWatch Logs interface).
> - **Network ACLs** added per subnet tier.
> - **RDS** single-AZ `db.t3.micro`; **ECS** 1 task × 0.25 vCPU/0.5 GB each.
> - The normalizer **Lambda publishes to EventBridge** (not SNS). SNS remains only for CloudWatch alarm
>   notifications.
> - **Internal events run over EventBridge + SQS** (`EVENTS_TRANSPORT=eventbridge`): services PutEvents to the
>   bus, and each consumer long-polls its own SQS queue (EventBridge rule → SQS). No MSK/Kafka on AWS; Kafka is
>   the local-Compose transport only.
> - Production browser API traffic is same-origin through CloudFront; CORS is only needed for local
>   development's direct browser-to-service calls.

Rendered topology: see diagram 5 in [03-diagrams.md](03-diagrams.md).

---

## 1. Networking (VPC)

| Resource | Design |
|----------|--------|
| **VPC** | `10.0.0.0/16`, DNS hostnames + resolution enabled. |
| **AZs** | 2 (e.g. `ap-south-1a`, `ap-south-1b`) for HA. |
| **Public subnets** | `10.0.0.0/24`, `10.0.1.0/24` — ALB, IGW. |
| **Private app subnets** | `10.0.10.0/24`, `10.0.11.0/24` — ECS Fargate tasks (no public IP). |
| **Private data subnets** | `10.0.20.0/24`, `10.0.21.0/24` — RDS; isolated, no internet route. |
| **Internet Gateway** | attached to VPC; default route for public subnets. |
| **VPC endpoints (no NAT)** | S3 + DynamoDB (gateway), ECR api/dkr + Secrets Manager + CloudWatch Logs (interface) — private egress without a NAT Gateway. |
| **Network ACLs** | per tier (public / app / data) in addition to security groups. |
| **Route tables** | public → IGW; app → local + endpoints; data → local only. |

---

## 2. Compute (ECS + ECR)

- **ECR:** one repository per service (`habitloop/user-service`, …). Images tagged with git SHA.
- **ECS cluster** on **Fargate** (serverless containers; no EC2 to manage).
- **One ECS service per microservice**, each a task definition referencing its ECR image,
  CPU/memory sized per profile (Integration/Analytics larger), env + secrets injected.
- **Auto Scaling:** target-tracking on CPU (e.g. 60%) and ALB request-count-per-target; min 2 / max N
  tasks per service across both AZs.
- **Secrets:** task definitions reference Secrets Manager ARNs (DB creds, JWT key, HMAC secrets) — values
  never baked into images or task env plaintext.
- **Health checks:** ECS + ALB target-group health check on `/actuator/health` (Spring Boot Actuator).

---

## 3. Load balancing (ALB)

- **Application Load Balancer** in public subnets, HTTPS listener (ACM cert), HTTP→HTTPS redirect.
- **Path-based routing** to the backend service target groups (the ALB is the AWS entry point; locally,
  clients call service ports directly):
  `/api/auth/*`,`/api/users/*` → user-service; `/api/habits/*`,`/api/activity/*` → activity-service;
  `/api/challenges/*`,`/api/invitations/*` → challenge-service; `/api/integrations/*`,`/api/webhooks/*`
  → integration-service; `/api/analytics/*` → analytics-service; `/api/recommendations/*` →
  recommendation-service.
- Deregistration delay + slow-start for zero-downtime deploys.

---

## 4. Data tier

- **RDS PostgreSQL**, Multi-AZ, in private data subnets, not publicly accessible. One instance hosting the
  per-service logical databases (or one instance per service in a higher-budget variant — design supports
  both; default is one Multi-AZ instance with separate databases to keep cost reasonable while preserving
  the database-per-service boundary at the schema/DB level).
- Automated backups + snapshots; storage encrypted with KMS; credentials in Secrets Manager with rotation.
- **DynamoDB** `habitloop-analytics` table + `GSI1`, on-demand capacity, encryption at rest, PITR enabled;
  accessed from ECS via the DynamoDB VPC gateway endpoint.

---

## 5. Frontend hosting

- **S3** bucket (private) holds the SvelteKit static build (`adapter-static`).
- **CloudFront** distribution in front of S3 (OAC — origin access control), HTTPS, caching, SPA fallback
  to `index.html`. Gives the "secure global distribution and low-latency access" the brief asks for.
- **Route 53** record for the app domain → CloudFront; API subdomain → ALB.

---

## 6. Event processing (EventBridge + Lambda)

- **EventBridge** custom bus `habitloop-bus` with a rule matching `source = habitloop.integration`,
  `detail-type = partner.event.received`.
- **Lambda** `partner-event-normalizer` (Java 17 or Node — Java to share DTOs; design allows either):
  validates schema, enriches (maps partner `userRef` → HabitLoop user/habit via a lookup), normalizes
  units, then publishes `partner.activity.normalized` (to the app's Kafka/MSK or an SNS topic the Activity
  Service subscribes to).
- **Retries:** EventBridge retries + Lambda async retry (2); on exhaustion → **SQS dead-letter queue**
  `partner-events-dlq` with a CloudWatch alarm. A redrive path reprocesses after a fix.
- Lambda reads the HMAC/partner secrets from Secrets Manager; least-privilege execution role.

(Full event semantics — idempotency, dedup, ordering — in [07-event-driven-design.md](07-event-driven-design.md).)

---

## 7. Security services

- **IAM:** one least-privilege task role per service (e.g. analytics-service role can only
  `dynamodb:Query/PutItem` on its table + read its secret). Separate execution role for image pull/log push.
  Lambda role scoped to its queue, bus, secret.
- **Secrets Manager:** DB creds, JWT signing key, per-connection HMAC secrets; rotation enabled for DB.
- **Security Groups:** ALB SG allows 443 from internet; ECS SG allows traffic only from ALB SG; RDS SG
  allows 5432 only from ECS SG. No broad `0.0.0.0/0` on data tier.
- **Network ACLs:** data subnets deny inbound from anything but app subnets.
- **TLS everywhere:** CloudFront & ALB terminate TLS (ACM); internal service calls over the private network.
- Tenant isolation is enforced in the application layer (doc 08), independent of infra.

(Full security model in [08-security-multitenancy.md](08-security-multitenancy.md).)

---

## 8. Monitoring (CloudWatch)

- **Logs:** every ECS task + Lambda ships structured JSON logs to CloudWatch Logs (one log group each).
- **Metrics:** ALB (latency, 5xx, request count), ECS (CPU/mem, running task count, restarts), RDS
  (connections, CPU, free storage), DynamoDB (throttles), Lambda (errors, duration, throttles), custom app
  metrics (auth failures, webhook rejections, events normalized, DLQ depth).
- **Dashboards:** a "HabitLoop Ops" dashboard with API latency/error, ECS health, event pipeline, and DLQ.
- **Alarms** (→ SNS): API p99 latency high, ALB 5xx rate, auth-failure spike, ECS unhealthy/restart loop,
  Lambda error rate, DLQ depth > 0, RDS storage low. Mapped 1:1 to the brief's "Monitor" list.

---

## 9. Terraform layout (planned)

```
infra/
├── modules/
│   ├── network/        # VPC, subnets, IGW, NAT, routes, endpoints
│   ├── ecs/            # cluster, services, task defs, autoscaling
│   ├── alb/            # ALB, listeners, target groups, rules
│   ├── rds/            # Postgres Multi-AZ, subnet group, SG
│   ├── dynamodb/       # analytics table + GSI
│   ├── eventbridge/    # bus, rule, Lambda, DLQ
│   ├── s3-cloudfront/  # frontend hosting
│   ├── ecr/            # repositories
│   ├── secrets/        # Secrets Manager entries
│   └── observability/  # log groups, dashboards, alarms
└── envs/dev/           # composition: main.tf, variables.tf, outputs.tf, backend.tf (S3 state)
```

Remote state in S3 + DynamoDB lock. `terraform plan` output will be reviewed before any `apply`.

## 10. Deployment pipeline (design)

Build & test (Maven) → build images → push to ECR → `terraform apply` (infra) → ECS rolling deploy (new
task def revision) → health checks gate the rollout → frontend build synced to S3 + CloudFront
invalidation. Documented in the later deployment-docs phase; CI config (GitHub Actions) to follow.
