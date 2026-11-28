ALTER TABLE achievement_activity_events ADD COLUMN habit_id UUID;

CREATE INDEX ix_achievement_activity_habit_dates
    ON achievement_activity_events (tenant_id, user_id, habit_id, completion_date DESC);

CREATE TABLE achievement_activity_users (
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (tenant_id, user_id)
);
