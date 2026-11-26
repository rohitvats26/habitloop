ALTER TABLE habits
    ADD COLUMN paused_until DATE;

CREATE TABLE habit_pause_periods (
    id          UUID        PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    habit_id    UUID        NOT NULL REFERENCES habits(id),
    start_date  DATE        NOT NULL,
    resume_date DATE        NOT NULL,
    resumed_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_habit_pause_periods_tenant_habit
    ON habit_pause_periods(tenant_id, habit_id, start_date, resume_date);
