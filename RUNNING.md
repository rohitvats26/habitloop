# Running HabitLoop locally (Docker Compose)

The full stack — PostgreSQL, Kafka, all six microservices and the partner-event normalizer (local
stand-in for the AWS Lambda) — runs with Docker Compose. Analytics uses an in-memory store locally for a
frictionless run; DynamoDB is used in AWS (see docs/06).

## Prerequisites
- Docker Desktop (running) + Docker Compose v2
- `curl` and `openssl` (for the smoke test)

## 1. Start the stack
```bash
cp .env.example .env            # from the repo root
docker compose -f backend/docker-compose.yml up --build       # add -d to run detached
```
The images build their own jars using the backend Maven reactor; no local Maven or JDK is required.
Wait until `postgres` and `kafka` are healthy and the services have started. Ports:

| Service | URL | Swagger UI |
|---------|-----|-----------|
| user-service           | http://localhost:8081 | /swagger-ui.html |
| activity-service       | http://localhost:8082 | /swagger-ui.html |
| challenge-service      | http://localhost:8083 | /swagger-ui.html |
| integration-service    | http://localhost:8084 | /swagger-ui.html |
| analytics-service      | http://localhost:8085 | /swagger-ui.html |
| recommendation-service | http://localhost:8086 | /swagger-ui.html |
| pgAdmin (database UI)  | http://localhost:5050 | — |

A demo tenant is seeded in user-service (`SEED_DEMO=true`): join code **ACME2026**,
users **ria@acme.com** / **sam@acme.com**, password **Password123!**.

### Open the database in your browser
1. Open [http://localhost:5050](http://localhost:5050) and sign in with
   `admin@habitloop.local` / `local-dev-pgadmin-password` (the pgAdmin web login).
2. In pgAdmin, choose **Add New Server**. Under **General**, enter `HabitLoop`.
3. Under **Connection**, enter host `postgres`, port `5432`, maintenance database `habitloop_user`,
   username `habitloop`, and password `habitloop`. Save the password and click **Save**.
4. Expand **HabitLoop → Databases** to browse the service databases:
   `habitloop_user`, `habitloop_activity`, `habitloop_challenge`, and `habitloop_integration`.

The pgAdmin login is separate from the PostgreSQL credentials. The pgAdmin port is bound to
localhost only; override its login with `PGADMIN_DEFAULT_EMAIL` and `PGADMIN_DEFAULT_PASSWORD` in
the root `.env` if needed.

## 2. Run the end-to-end smoke test
```bash
bash scripts/smoke-test.sh
```
It exercises the whole workflow: register → login → create habit → complete it → create a challenge →
connect a wearable → post an HMAC-signed webhook event → (normalizer) → activity log → analytics →
recommendations, and verifies that a replayed webhook event is de-duplicated.

### Explore the API by hand
- **Postman:** import [`postman/HabitLoop.postman_collection.json`](postman/HabitLoop.postman_collection.json),
  run **Auth → Login** first (it stores the token automatically), then any request. The webhook request
  signs itself via a pre-request HMAC script.
- **Swagger UI:** each service at `http://localhost:808x/swagger-ui.html` (see the port table above).
- **Walkthrough:** [docs/09-user-walkthrough.md](docs/09-user-walkthrough.md) describes the same flow
  click-by-click (register → activity → done/missed → analytics → accept a recommendation).

## 3. Stop
```bash
docker compose -f backend/docker-compose.yml down         # add -v to also drop the Postgres volume
```

## Notes
- `KAFKA_ENABLED=true` turns on event publishing/consuming in the services; the normalizer bridges
  `partner.event.received` → `partner.activity.normalized`.
- No secrets are baked into images; `JWT_SECRET` comes from `.env`, DB creds from compose env.
- PostgreSQL uses one database per service, not a database named `habitloop`. To connect manually, specify
  the target database, for example
  `docker compose -f backend/docker-compose.yml exec postgres psql -U habitloop -d habitloop_user`.
  The other service databases are `habitloop_activity`, `habitloop_challenge`, and `habitloop_integration`.
- AWS is **not** deployed by this; see docs/06 for the Terraform-based deployment design.
