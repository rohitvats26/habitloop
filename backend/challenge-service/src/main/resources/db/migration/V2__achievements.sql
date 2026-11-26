CREATE TABLE achievement_awards (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    user_id     UUID         NOT NULL,
    code        VARCHAR(40)  NOT NULL,
    source_key  VARCHAR(128) NOT NULL,
    title       VARCHAR(100) NOT NULL,
    description VARCHAR(300) NOT NULL,
    awarded_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_achievement_award UNIQUE (tenant_id, user_id, code, source_key)
);
CREATE INDEX ix_achievement_awards_tenant_user_time
    ON achievement_awards(tenant_id, user_id, awarded_at DESC);

CREATE TABLE achievement_activity_events (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    user_id         UUID        NOT NULL,
    activity_log_id UUID        NOT NULL,
    completion_date DATE        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_achievement_activity_event UNIQUE (tenant_id, user_id, activity_log_id)
);
CREATE INDEX ix_achievement_activity_dates
    ON achievement_activity_events(tenant_id, user_id, completion_date DESC);
