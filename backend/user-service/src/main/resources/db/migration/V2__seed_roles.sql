-- Base roles (idempotent). Demo tenant/users are seeded at runtime by DemoDataSeeder
-- (habitloop.seed-demo=true) so passwords are hashed with the real encoder.

INSERT INTO roles (name) VALUES ('USER')         ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('TENANT_ADMIN') ON CONFLICT (name) DO NOTHING;
INSERT INTO roles (name) VALUES ('SYSTEM')       ON CONFLICT (name) DO NOTHING;
