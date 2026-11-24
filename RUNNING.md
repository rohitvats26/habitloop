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
docker compose up --build       # add -d to run detached
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

A demo tenant is seeded in user-service (`SEED_DEMO=true`): join code **ACME2026**,
users **ria@acme.com** / **sam@acme.com**, password **Password123!**.

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
docker compose down         # add -v to also drop the Postgres volume
```

## Notes
- `KAFKA_ENABLED=true` turns on event publishing/consuming in the services; the normalizer bridges
  `partner.event.received` → `partner.activity.normalized`.
- No secrets are baked into images; `JWT_SECRET` comes from `.env`, DB creds from compose env.
- AWS is **not** deployed by this; see docs/06 for the Terraform-based deployment design.
