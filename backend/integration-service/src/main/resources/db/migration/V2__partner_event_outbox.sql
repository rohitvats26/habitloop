CREATE TABLE partner_event_outbox (
    id UUID PRIMARY KEY,
    partition_key VARCHAR(100) NOT NULL,
    envelope TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(500)
);

CREATE INDEX ix_partner_outbox_pending ON partner_event_outbox (next_attempt_at, created_at)
    WHERE published_at IS NULL;
CREATE INDEX ix_partner_outbox_partition ON partner_event_outbox (partition_key, created_at, id)
    WHERE published_at IS NULL;
