-- HabitLoop user-service schema (see docs/04-database-design.md)

CREATE TABLE tenants (
    id         UUID         PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    join_code  VARCHAR(16)  NOT NULL UNIQUE,
    status     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE roles (
    id   SERIAL      PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE
);

CREATE TABLE users (
    id            UUID         PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants(id),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);
CREATE INDEX ix_users_tenant ON users(tenant_id);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INT  NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_profiles (
    user_id      UUID        PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    tenant_id    UUID        NOT NULL,
    display_name VARCHAR(80),
    timezone     VARCHAR(64) NOT NULL DEFAULT 'UTC',
    avatar_url   VARCHAR(512),
    updated_at   TIMESTAMPTZ NOT NULL
);
