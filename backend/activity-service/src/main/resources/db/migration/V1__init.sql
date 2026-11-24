-- HabitLoop activity-service schema (see docs/04-database-design.md)

CREATE TABLE habits (
    id                UUID         PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    name              VARCHAR(120) NOT NULL,
    description       VARCHAR(500),
    frequency         VARCHAR(20)  NOT NULL DEFAULT 'DAILY',
    schedule          JSONB        NOT NULL DEFAULT '{}',
    target_per_period INT          NOT NULL DEFAULT 1,
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX ix_habits_tenant_user ON habits(tenant_id, user_id) WHERE deleted_at IS NULL;

CREATE TABLE habit_completions (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    habit_id        UUID        NOT NULL REFERENCES habits(id),
    user_id         UUID        NOT NULL,
    completion_date DATE        NOT NULL,
    source          VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    note            VARCHAR(280),
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_completion_per_day UNIQUE (habit_id, completion_date)
);
CREATE INDEX ix_completions_tenant_user_date ON habit_completions(tenant_id, user_id, completion_date);

CREATE TABLE habit_streaks (
    habit_id       UUID        PRIMARY KEY REFERENCES habits(id),
    tenant_id      UUID        NOT NULL,
    user_id        UUID        NOT NULL,
    current_streak INT         NOT NULL DEFAULT 0,
    longest_streak INT         NOT NULL DEFAULT 0,
    last_completed DATE,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE TABLE activity_log (
    id                UUID         PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    habit_id          UUID,
    activity_type     VARCHAR(40)  NOT NULL,
    value             NUMERIC(12,2),
    unit              VARCHAR(20),
    occurred_at       TIMESTAMPTZ  NOT NULL,
    source            VARCHAR(20)  NOT NULL,
    external_event_id VARCHAR(128),
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_activity_idempotency UNIQUE (tenant_id, user_id, source, external_event_id)
);
CREATE INDEX ix_activitylog_tenant_user_time ON activity_log(tenant_id, user_id, occurred_at);
