CREATE TABLE habit_schedule_versions (
    id UUID PRIMARY KEY,
    habit_id UUID NOT NULL REFERENCES habits(id),
    tenant_id UUID NOT NULL,
    effective_date DATE NOT NULL,
    frequency VARCHAR(20) NOT NULL,
    schedule JSONB NOT NULL,
    target_per_period INTEGER NOT NULL,
    CONSTRAINT uq_habit_schedule_effective UNIQUE (habit_id, effective_date)
);

INSERT INTO habit_schedule_versions (id, habit_id, tenant_id, effective_date, frequency, schedule, target_per_period)
SELECT id, id, tenant_id, DATE '0001-01-01', frequency, schedule, target_per_period FROM habits;

CREATE INDEX ix_schedule_versions_tenant_habit ON habit_schedule_versions (tenant_id, habit_id, effective_date);

ALTER TABLE habit_streaks ADD COLUMN snapshot_date DATE;
ALTER TABLE habit_streaks ADD COLUMN snapshot_day_longest INTEGER;
ALTER TABLE habit_streaks ADD COLUMN snapshot_week_longest INTEGER;
