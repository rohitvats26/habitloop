-- HabitLoop integration-service schema (see docs/04-database-design.md)
-- Stores only the API-key hash and a reference to the HMAC secret (never the secret itself).

CREATE TABLE partner_connections (
    id              UUID         PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    partner         VARCHAR(40)  NOT NULL,
    api_key_hash    VARCHAR(100) NOT NULL,
    hmac_secret_ref VARCHAR(200) NOT NULL,
    status          VARCHAR(12)  NOT NULL DEFAULT 'CONNECTED',
    connected_at    TIMESTAMPTZ  NOT NULL,
    last_event_at   TIMESTAMPTZ,
    CONSTRAINT uq_connection UNIQUE (tenant_id, user_id, partner)
);
CREATE INDEX ix_connections_tenant_user ON partner_connections(tenant_id, user_id);

CREATE TABLE raw_partner_events (
    id                UUID         PRIMARY KEY,
    tenant_id         UUID         NOT NULL,
    connection_id     UUID         NOT NULL REFERENCES partner_connections(id),
    external_event_id VARCHAR(128) NOT NULL,
    payload           JSONB        NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL,
    processing_status VARCHAR(12)  NOT NULL DEFAULT 'RECEIVED',
    CONSTRAINT uq_raw_event UNIQUE (connection_id, external_event_id)
);
CREATE INDEX ix_raw_events_tenant_status ON raw_partner_events(tenant_id, processing_status);
