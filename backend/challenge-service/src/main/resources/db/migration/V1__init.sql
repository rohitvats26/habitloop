-- HabitLoop challenge-service schema (see docs/04-database-design.md)

CREATE TABLE challenges (
    id          UUID          PRIMARY KEY,
    tenant_id   UUID          NOT NULL,
    creator_id  UUID          NOT NULL,
    title       VARCHAR(120)  NOT NULL,
    description VARCHAR(1000),
    visibility  VARCHAR(10)   NOT NULL,
    metric      VARCHAR(40)   NOT NULL,
    target      NUMERIC(12,2) NOT NULL,
    starts_on   DATE          NOT NULL,
    ends_on     DATE          NOT NULL,
    status      VARCHAR(12)   NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    deleted_at  TIMESTAMPTZ
);
CREATE INDEX ix_challenges_tenant_vis ON challenges(tenant_id, visibility) WHERE deleted_at IS NULL;

CREATE TABLE challenge_invitations (
    id           UUID        PRIMARY KEY,
    tenant_id    UUID        NOT NULL,
    challenge_id UUID        NOT NULL REFERENCES challenges(id),
    inviter_id   UUID        NOT NULL,
    invitee_id   UUID        NOT NULL,
    status       VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    CONSTRAINT uq_invite UNIQUE (challenge_id, invitee_id)
);
CREATE INDEX ix_invites_tenant_invitee ON challenge_invitations(tenant_id, invitee_id, status);

CREATE TABLE challenge_participants (
    id           UUID          PRIMARY KEY,
    tenant_id    UUID          NOT NULL,
    challenge_id UUID          NOT NULL REFERENCES challenges(id),
    user_id      UUID          NOT NULL,
    joined_at    TIMESTAMPTZ   NOT NULL,
    progress     NUMERIC(12,2) NOT NULL DEFAULT 0,
    completed    BOOLEAN       NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_participant UNIQUE (challenge_id, user_id)
);
CREATE INDEX ix_participants_tenant_user ON challenge_participants(tenant_id, user_id);

CREATE TABLE challenge_progress_events (
    id              UUID        PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    participant_id  UUID        NOT NULL REFERENCES challenge_participants(id),
    activity_log_id UUID        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_progress_event UNIQUE (participant_id, activity_log_id)
);
