CREATE TABLE notification_preferences (
    id                         UUID         PRIMARY KEY,
    tenant_id                  UUID         NOT NULL,
    user_id                    UUID         NOT NULL,
    reminders_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    completion_updates_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    reminder_time              TIME         NOT NULL DEFAULT '09:00',
    updated_at                 TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_notification_preference_user UNIQUE (tenant_id, user_id)
);

CREATE TABLE in_app_notifications (
    id                UUID         PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    user_id           UUID         NOT NULL,
    habit_id          UUID,
    idempotency_key   VARCHAR(200) NOT NULL,
    notification_type VARCHAR(32)  NOT NULL,
    title             VARCHAR(120) NOT NULL,
    message           VARCHAR(500) NOT NULL,
    delivery_status   VARCHAR(20)  NOT NULL,
    delivery_attempts INT          NOT NULL DEFAULT 1,
    reminder_date     DATE,
    created_at        TIMESTAMPTZ  NOT NULL,
    delivered_at      TIMESTAMPTZ  NOT NULL,
    read_at           TIMESTAMPTZ,
    CONSTRAINT uq_notification_idempotency UNIQUE (tenant_id, user_id, idempotency_key)
);
CREATE INDEX ix_notifications_tenant_user_created
    ON in_app_notifications(tenant_id, user_id, created_at DESC);
