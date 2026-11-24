# 08 — Security & Multi-Tenancy Design

Covers authentication, authorization, the tenant-isolation strategy (the brief's headline architecture
requirement), input validation, secure integration endpoints, and secret management. Addresses NFR-1 and
NFR-4.

---

## 1. Authentication (JWT)

- **Registration:** password hashed with **BCrypt** (strength 10+). Raw passwords never stored or logged.
- **Login:** issues a signed **JWT access token** (~1h) + a **refresh token** (longer-lived, rotating).
- **Signing:** HS256 for local/dev (shared secret in Secrets Manager); RS256 for production (private key in
  Secrets Manager, public key distributed to services for verification). Upgrade path documented (AS-14).
- **Claims:** `sub` (userId), `tenantId`, `roles[]`, `iss`, `iat`, `exp`, `jti`.
- **Verification:** the API gateway verifies the token once; each service **also** verifies (defense in
  depth) via a shared `JwtAuthenticationFilter` in `backend/common`. No service trusts an unauthenticated
  caller even internally (internal calls carry a service token or the propagated user JWT).

---

## 2. Authorization (RBAC)

- Roles: `USER` (own data), `TENANT_ADMIN` (manage the tenant's users, see cohort analytics), `SYSTEM`
  (platform ops; not exposed to end users).
- Enforced with Spring Security method security: `@PreAuthorize("hasRole('TENANT_ADMIN')")` on admin
  endpoints; ownership checks in the service layer (`resource.userId == currentUser.id` unless admin).
- Example matrix:

| Action | USER | TENANT_ADMIN |
|--------|------|--------------|
| CRUD own habits | ✅ | ✅ |
| View another user's habits | ❌ | ✅ (same tenant) |
| Create challenge | ✅ | ✅ |
| View tenant cohort analytics | ❌ | ✅ |
| Manage tenant users | ❌ | ✅ |

---

## 3. Multi-tenancy — the isolation strategy

**Chosen model: shared schema with a `tenant_id` discriminator column + a mandatory Hibernate filter,**
driven by a request-scoped `TenantContext`. (AS-4)

### Why this model (vs alternatives)

| Model | Isolation | Ops cost | Verdict |
|-------|-----------|----------|---------|
| Database-per-tenant | strongest | high (N databases, migrations×N) | over-engineered for this scope |
| Schema-per-tenant | strong | medium (N schemas) | unnecessary complexity here |
| **Shared schema + tenant_id** | strong **if enforced** | low | **chosen** — simple, scalable, adequate when filters are mandatory and tested |

### How isolation is enforced (layered — the key safeguard)

1. **Token is the only source of tenant.** `tenantId` comes from the verified JWT and is put into a
   request-scoped `TenantContext`. It is **never** read from a path, query param, or request body — so a
   client cannot ask for another tenant's data.
2. **API layer check.** A filter/interceptor rejects any request whose body/path references a `tenantId`
   different from the token's (`403`).
3. **Persistence layer check (the backstop).** A Hibernate `@Filter("tenantFilter")` with
   `tenant_id = :tenantId` is enabled on every session from `TenantContext`, and every tenant-owned entity
   declares `@FilterDef`. This means even a developer who forgets a `WHERE tenant_id = ?` still cannot leak
   rows — the filter is applied globally. Writes stamp `tenant_id` from context automatically (a JPA
   `@PrePersist` / entity listener), so it can't be spoofed or omitted.
4. **Query/index design.** Every tenant-owned table is indexed on `(tenant_id, …)` so tenant-scoped queries
   are efficient and the composite key is the natural access path.
5. **Events carry `tenantId`.** Consumers set `TenantContext` from the envelope before touching the DB, so
   async processing is tenant-scoped too.
6. **Cross-tenant references are impossible by construction.** A challenge invite verifies the invitee is in
   the **same** tenant (via a same-tenant internal lookup); a not-found/other-tenant user returns `404`
   without revealing existence.

### Testing isolation

A dedicated test suite logs in as tenant A and attempts to read/modify tenant B's habits, challenges,
analytics, and raw events — every attempt must return `403/404` and never data. This is part of the
integration-test gate (NFR-7).

---

## 4. Input validation & safe endpoints

- **Bean Validation** on every request DTO (`@NotBlank`, `@Email`, `@Size`, `@Positive`, custom
  validators for schedule JSON). Violations → `400` with field details.
- **No entity exposure:** controllers accept/return DTOs only; mass-assignment is impossible.
- **SQL injection:** JPA/parameterized queries only; no string-concatenated SQL.
- **Webhook endpoints** (unauthenticated by JWT) are protected by:
  - per-connection **API key** (hashed at rest) +
  - **HMAC-SHA256** signature over the raw body with the connection's secret (from Secrets Manager),
    compared in constant time; mismatch → `401`.
  - **replay/idempotency** via `external_event_id` uniqueness.
  - **rate limiting** per connection at the gateway (`429`).
- **CORS** restricted to the known frontend origin(s); **security headers** (HSTS, X-Content-Type-Options,
  frame options) set at the edge/gateway.

---

## 5. Secret management (no hardcoded secrets — NFR-4)

- **Local:** secrets come from a git-ignored `.env` consumed by Docker Compose; an `.env.example` documents
  the keys without values.
- **AWS:** all secrets (DB creds, JWT key, per-connection HMAC secrets) live in **Secrets Manager**,
  injected into ECS tasks and Lambda via IAM-scoped references. Rotation enabled for DB credentials.
- Code references secret **names/ARNs**, never literal values. CI scans (e.g. gitleaks) guard against
  accidental commits. Partner HMAC secrets are stored only as Secrets Manager references in RDS
  (`hmac_secret_ref`), never the secret itself.

---

## 6. Transport & data protection

- **TLS** on all external traffic (CloudFront, ALB via ACM); internal traffic stays on the private VPC
  network.
- **Encryption at rest:** RDS (KMS), DynamoDB (default encryption), S3 (SSE), Secrets Manager (KMS).
- **Least-privilege IAM** per service/Lambda (doc 06 §7).
- **PII hygiene:** email is the only PII; it is never placed in URLs, logs are structured with ids not
  emails, and tenant data is isolated as above.

---

## 7. Audit & logging

- Structured logs include `tenantId`, `userId`, `correlationId` (not secrets, not raw passwords, not full
  tokens).
- Security-relevant events (login success/failure, webhook rejection, cross-tenant attempt, role change)
  are logged and surfaced as CloudWatch metrics with alarms (auth-failure spike, cross-tenant attempt).

---

## 8. Summary — how each security requirement is met

| Brief requirement | Mechanism |
|-------------------|-----------|
| JWT authentication | HS256/RS256 tokens, gateway + per-service verification |
| Role-based authorization | Spring Security `@PreAuthorize` + ownership checks |
| Tenant-level access control | JWT-sourced `TenantContext` + mandatory Hibernate filter + API/event checks |
| Input validation | Bean Validation on all DTOs |
| Secure integration endpoints | API key + HMAC + idempotency + rate limit |
| Secret management | Secrets Manager / `.env`, references only, rotation |
