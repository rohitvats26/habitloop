-- Creates the per-service databases (database-per-service). The default DB (habitloop_user)
-- is created by the POSTGRES_DB env; the rest are created here on first container start.
CREATE DATABASE habitloop_activity;
CREATE DATABASE habitloop_challenge;
CREATE DATABASE habitloop_integration;
