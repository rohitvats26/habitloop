# 04 — Database Design

**Database-per-service** on Amazon RDS (PostgreSQL) for transactional data; **DynamoDB** single-table for
aggregated analytics. Every tenant-owned RDS table carries a non-null `tenant_id` and is indexed on it.
All ids are UUIDs. Timestamps are `timestamptz` (UTC); day/week math uses India Standard Time (`Asia/Kolkata`).

Conventions: `created_at`/`updated_at` on every table; soft-delete via `deleted_at` where deletion must be
reversible (habits, challenges); enums stored as `varchar` with a check constraint.

---

## 1. `habitloop_user` (user-service)

```sql
CREATE TABLE tenants (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(120) NOT NULL,
    join_code     VARCHAR(16)  NOT NULL UNIQUE,     -- for invite-based join (AS-6)
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','SUSPENDED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES tenants(id),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,            -- BCrypt
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE','DISABLED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)  -- email unique per tenant
);
CREATE INDEX ix_users_tenant ON users(tenant_id);

CREATE TABLE roles (
    id    SERIAL PRIMARY KEY,
    name  VARCHAR(30) NOT NULL UNIQUE               -- USER, TENANT_ADMIN, SYSTEM
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INT  NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_profiles (
    user_id      UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    tenant_id    UUID NOT NULL,
    display_name VARCHAR(80),
    timezone     VARCHAR(64) NOT NULL DEFAULT 'Asia/Kolkata', -- fixed product timezone (AS-15)
    avatar_url   VARCHAR(512),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Notes.** Email is unique *within* a tenant, not globally — two tenants may have the same user email.
`join_code` supports tenant onboarding. BCrypt hash only; raw passwords never stored.

---

## 2. `habitloop_activity` (activity-service)

```sql
CREATE TABLE habits (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    frequency   VARCHAR(20) NOT NULL                 -- DAILY, WEEKLY, CUSTOM
                CHECK (frequency IN ('DAILY','WEEKLY','CUSTOM')),
    schedule    JSONB NOT NULL DEFAULT '{}',         -- {daysOfWeek:[1,3,5], targetTime:"07:00", perWeek:3}
    target_per_period INT NOT NULL DEFAULT 1,
    active      BOOLEAN NOT NULL DEFAULT true,
    paused_until DATE,                                -- temporary pause end date (Asia/Kolkata)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX ix_habits_tenant_user ON habits(tenant_id, user_id) WHERE deleted_at IS NULL;

CREATE TABLE habit_schedule_versions (
    id UUID PRIMARY KEY,
    habit_id UUID NOT NULL REFERENCES habits(id),
    tenant_id UUID NOT NULL,
    effective_date DATE NOT NULL,                    -- IST date, inclusive
    frequency VARCHAR(20) NOT NULL,
    schedule JSONB NOT NULL,
    target_per_period INTEGER NOT NULL,
    UNIQUE (habit_id, effective_date)
);
CREATE INDEX ix_schedule_versions_tenant_habit
    ON habit_schedule_versions(tenant_id, habit_id, effective_date);

CREATE TABLE habit_pause_periods (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    habit_id    UUID NOT NULL REFERENCES habits(id),
    start_date  DATE NOT NULL,
    resume_date DATE NOT NULL,                       -- exclusive: the first active day
    resumed_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_habit_pause_periods_tenant_habit
    ON habit_pause_periods(tenant_id, habit_id, start_date, resume_date);

CREATE TABLE habit_completions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    habit_id      UUID NOT NULL REFERENCES habits(id),
    user_id       UUID NOT NULL,
    completion_date DATE NOT NULL,                   -- user-local day
    source        VARCHAR(20) NOT NULL DEFAULT 'MANUAL'  -- MANUAL, WEARABLE, PARTNER
                  CHECK (source IN ('MANUAL','WEARABLE','PARTNER')),
    note          VARCHAR(280),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_completion_per_day UNIQUE (habit_id, completion_date)  -- one completion per habit per day
);
CREATE INDEX ix_completions_tenant_user_date ON habit_completions(tenant_id, user_id, completion_date);

CREATE TABLE habit_streaks (
    habit_id       UUID PRIMARY KEY REFERENCES habits(id),
    tenant_id      UUID NOT NULL,
    user_id        UUID NOT NULL,
    current_streak INT NOT NULL DEFAULT 0,
    longest_streak INT NOT NULL DEFAULT 0,
    last_completed DATE,
    snapshot_date DATE,                              -- IST Monday baseline; null means rebuild needed
    snapshot_day_longest INT,
    snapshot_week_longest INT,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Normalized activity log: the single source of truth for "an activity happened",
-- whether manual or from a partner. Idempotent on the external key.
CREATE TABLE activity_log (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    user_id           UUID NOT NULL,
    habit_id          UUID,                           -- nullable: generic activity
    activity_type     VARCHAR(40) NOT NULL,           -- STEPS, WORKOUT, HABIT_DONE, ...
    value             NUMERIC(12,2),                  -- normalized metric (e.g. steps, minutes)
    unit              VARCHAR(20),
    occurred_at       TIMESTAMPTZ NOT NULL,
    source            VARCHAR(20) NOT NULL,           -- MANUAL, WEARABLE, PARTNER
    external_event_id VARCHAR(128),                   -- partner's id, for idempotency
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_activity_idempotency
        UNIQUE (tenant_id, user_id, source, external_event_id)   -- NFR-2: no duplicate logs
);
CREATE INDEX ix_activitylog_tenant_user_time ON activity_log(tenant_id, user_id, occurred_at);

CREATE TABLE activity_outbox (
    id              UUID PRIMARY KEY,
    partition_key   VARCHAR(100) NOT NULL,
    envelope        TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error      VARCHAR(500)
);
CREATE INDEX ix_activity_outbox_pending ON activity_outbox(next_attempt_at, created_at)
    WHERE published_at IS NULL;
CREATE INDEX ix_activity_outbox_partition ON activity_outbox(partition_key, created_at, id)
    WHERE published_at IS NULL;
```

The historical `V3__in_app_notifications.sql` migration is retained for Flyway compatibility.
It creates the following unused tables; existing data is not deleted when notification functionality is removed.
```sql
CREATE TABLE notification_preferences (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                  UUID NOT NULL,
    user_id                    UUID NOT NULL,
    reminders_enabled          BOOLEAN NOT NULL DEFAULT true,
    completion_updates_enabled BOOLEAN NOT NULL DEFAULT true,
    reminder_time              TIME NOT NULL DEFAULT '09:00', -- interpreted as Asia/Kolkata
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_preference_user UNIQUE (tenant_id, user_id)
);

CREATE TABLE in_app_notifications (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    user_id           UUID NOT NULL,
    habit_id          UUID,
    idempotency_key   VARCHAR(200) NOT NULL,
    notification_type VARCHAR(32) NOT NULL,
    title             VARCHAR(120) NOT NULL,
    message           VARCHAR(500) NOT NULL,
    delivery_status   VARCHAR(20) NOT NULL,
    delivery_attempts INT NOT NULL DEFAULT 1,
    reminder_date     DATE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at      TIMESTAMPTZ NOT NULL,
    read_at           TIMESTAMPTZ,
    CONSTRAINT uq_notification_idempotency UNIQUE (tenant_id, user_id, idempotency_key)
);
CREATE INDEX ix_notifications_tenant_user_created
    ON in_app_notifications(tenant_id, user_id, created_at DESC);
```

**Notes.** Two uniqueness guards create idempotency: `uq_completion_per_day` (one manual tick per habit
per day) and `uq_activity_idempotency` (one log per external event). The `schedule` JSONB keeps frequency
flexible without extra tables. Streaks are stored denormalized for fast reads and recomputed on write.

---

## 3. `habitloop_challenge` (challenge-service)

```sql
CREATE TABLE challenges (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    creator_id  UUID NOT NULL,
    title       VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    visibility  VARCHAR(10) NOT NULL CHECK (visibility IN ('PUBLIC','PRIVATE')),
    metric      VARCHAR(40) NOT NULL,                 -- e.g. DAYS_COMPLETED, STEPS
    target      NUMERIC(12,2) NOT NULL,               -- goal within the window
    starts_on   DATE NOT NULL,
    ends_on     DATE NOT NULL,
    status      VARCHAR(12) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('DRAFT','ACTIVE','ENDED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX ix_challenges_tenant_vis ON challenges(tenant_id, visibility) WHERE deleted_at IS NULL;

CREATE TABLE challenge_invitations (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL,
    challenge_id UUID NOT NULL REFERENCES challenges(id),
    inviter_id   UUID NOT NULL,
    invitee_id   UUID NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                 CHECK (status IN ('PENDING','ACCEPTED','REJECTED')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,
    CONSTRAINT uq_invite UNIQUE (challenge_id, invitee_id)
);
CREATE INDEX ix_invites_tenant_invitee ON challenge_invitations(tenant_id, invitee_id, status);

CREATE TABLE challenge_participants (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL,
    challenge_id UUID NOT NULL REFERENCES challenges(id),
    user_id      UUID NOT NULL,
    joined_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    progress     NUMERIC(12,2) NOT NULL DEFAULT 0,    -- accumulated metric
    completed    BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_participant UNIQUE (challenge_id, user_id)
);
CREATE INDEX ix_participants_tenant_user ON challenge_participants(tenant_id, user_id);

CREATE TABLE achievement_awards (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    code        VARCHAR(40) NOT NULL,
    source_key  VARCHAR(128) NOT NULL,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL,
    awarded_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_achievement_award UNIQUE (tenant_id, user_id, code, source_key)
);
CREATE INDEX ix_achievement_awards_tenant_user_time
    ON achievement_awards(tenant_id, user_id, awarded_at DESC);

CREATE TABLE achievement_activity_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL,
    user_id         UUID NOT NULL,
    activity_log_id UUID NOT NULL,
    habit_id        UUID,
    completion_date DATE NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_achievement_activity_event UNIQUE (tenant_id, user_id, activity_log_id)
);
CREATE INDEX ix_achievement_activity_dates
    ON achievement_activity_events(tenant_id, user_id, completion_date DESC);
CREATE INDEX ix_achievement_activity_habit_dates
    ON achievement_activity_events(tenant_id, user_id, habit_id, completion_date DESC);

CREATE TABLE achievement_activity_users (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (tenant_id, user_id)
);
```

**Notes.** Progress is accumulated from consumed `activity.logged` events; `completed` flips when
`progress >= target`. Invitations are unique per `(challenge, invitee)`.

The challenge-service also stores the user's earned achievements and activity-event receipts. Unique
keys make event processing and milestone awards idempotent. Per-user lock rows serialize milestone
calculation; participant row locks serialize progress updates. Old activity receipts have a null habit ID
until the original event is replayed or a reliable historical backfill is performed.

---

## 4. `habitloop_integration` (integration-service)

```sql
CREATE TABLE partner_connections (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL,
    user_id       UUID NOT NULL,
    partner       VARCHAR(40) NOT NULL,               -- FITBIT, GARMIN, APPLE_HEALTH, GENERIC
    api_key_hash  VARCHAR(100) NOT NULL,              -- hash of the per-connection key
    hmac_secret_ref VARCHAR(200) NOT NULL,            -- Secrets Manager reference, not the secret
    status        VARCHAR(12) NOT NULL DEFAULT 'CONNECTED'
                  CHECK (status IN ('CONNECTED','ERROR','REVOKED')),
    connected_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_event_at TIMESTAMPTZ,
    CONSTRAINT uq_connection UNIQUE (tenant_id, user_id, partner)
);
CREATE INDEX ix_connections_tenant_user ON partner_connections(tenant_id, user_id);

CREATE TABLE raw_partner_events (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    connection_id     UUID NOT NULL REFERENCES partner_connections(id),
    external_event_id VARCHAR(128) NOT NULL,          -- partner-supplied id
    payload           JSONB NOT NULL,                 -- raw body, audit trail
    received_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    processing_status VARCHAR(12) NOT NULL DEFAULT 'RECEIVED'
                      CHECK (processing_status IN ('RECEIVED','NORMALIZED','FAILED')),
    CONSTRAINT uq_raw_event UNIQUE (connection_id, external_event_id)  -- edge-level dedup
);
CREATE INDEX ix_raw_events_tenant_status ON raw_partner_events(tenant_id, processing_status);

CREATE TABLE partner_event_outbox (
    id              UUID PRIMARY KEY,
    partition_key   VARCHAR(100) NOT NULL,
    envelope        TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at    TIMESTAMPTZ,
    attempts        INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error      VARCHAR(500)
);
CREATE INDEX ix_partner_outbox_pending ON partner_event_outbox(next_attempt_at, created_at)
    WHERE published_at IS NULL;
CREATE INDEX ix_partner_outbox_partition ON partner_event_outbox(partition_key, created_at, id)
    WHERE published_at IS NULL;
```

**Notes.** The HMAC secret is **referenced**, not stored — the actual secret lives in Secrets Manager
(NFR-4, no hardcoded secrets). `uq_raw_event` is the first de-duplication layer; the second is in
`activity_log`. Raw payloads are retained for audit and replay. New webhook receipts and their outbox
rows commit atomically; the migration does not backfill events accepted before the outbox existed.

---

## 5. DynamoDB — `habitloop-analytics` (analytics-service)

Single-table design optimized for the dashboard's read patterns.

| Attribute | Example | Meaning |
|-----------|---------|---------|
| `PK` (partition) | `TENANT#t1#USER#u9` | scopes every item to tenant + user (isolation + locality) |
| `SK` (sort)      | `STAT#DAILY#2026-11-23` / `STAT#WEEKLY#2026-W47` / `CHALLENGE#c3` / `PATTERN#SUMMARY` | item type |
| `completions`    | `3` | count for that period |
| `targetMet`      | `true` | whether target met |
| `currentStreak`  | `12` | streak snapshot |
| `byHour`         | `{ "07": 5, "18": 9 }` | completions by hour (feeds recommendations) |
| `byWeekday`      | `{ "MON": 4, "SAT": 1 }` | completions by weekday |
| `updatedAt`      | ISO timestamp | last recompute |

**Access patterns:**
- *Daily stats for a user, last N days* → `PK = TENANT#t#USER#u AND begins_with(SK, "STAT#DAILY#")`.
- *Weekly trend* → `begins_with(SK, "STAT#WEEKLY#")`.
- *All challenge rollups for a user* → `begins_with(SK, "CHALLENGE#")`.
- *Pattern summary (for recommendations)* → `SK = PATTERN#SUMMARY` (single item, O(1)).

**GSI-1** (`GSI1PK = TENANT#t#DATE#2026-11-23`, `GSI1SK = USER#u`) supports **cohort** queries: "all users'
daily stats for a date" → tenant-wide insights (FR-E3) without scanning.

**Why DynamoDB here.** Aggregates are read on every dashboard load and are naturally key-value by
tenant+user+period. DynamoDB gives single-digit-ms reads, autoscaling for spiky traffic, and the
single-table pattern keeps all of a user's analytics in one partition.

---

## 6. Initialization & seeding

- Each service ships `db/<service>/schema.sql` (DDL above) + `db/<service>/seed.sql` (demo tenant, users,
  habits) — Spring Boot runs them via Flyway migrations.
- The user-service migration `V3__set_india_timezone.sql` updates existing profiles to the fixed product timezone.
- DynamoDB table + GSI are created by Terraform (AWS) and by an init script against DynamoDB Local (dev).
- A demo dataset (one tenant, 3 users, sample habits, one challenge, one wearable connection) is seeded so
  the end-to-end workflow is runnable immediately after `docker-compose up`.
