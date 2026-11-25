#!/usr/bin/env bash
# HabitLoop end-to-end smoke test — drives the full workflow against the local Docker Compose stack.
# Prereq: docker compose -f backend/docker-compose.yml up (all services healthy). Needs: curl, openssl.
# Usage: bash scripts/smoke-test.sh
set -u

U=http://localhost:8081   # user
A=http://localhost:8082   # activity
C=http://localhost:8083   # challenge
I=http://localhost:8084   # integration
AN=http://localhost:8085  # analytics
R=http://localhost:8086   # recommendation

email="demo+$RANDOM@acme.com"
json() { grep -oP "\"$1\":\"?\K[^\",}]+"; }
hr() { echo "------------------------------------------------------------"; }

hr; echo "1) Register (creates tenant, user = TENANT_ADMIN)"
curl -s -X POST "$U/api/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"Password123!\",\"displayName\":\"Demo\",\"timezone\":\"Asia/Kolkata\",\"tenantName\":\"Acme Demo\"}"
echo

hr; echo "2) Login -> JWT"
TOKEN=$(curl -s -X POST "$U/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$email\",\"password\":\"Password123!\"}" | json accessToken)
echo "token: ${TOKEN:0:24}..."
AUTH="Authorization: Bearer $TOKEN"

hr; echo "3) Create a habit"
HABIT=$(curl -s -X POST "$A/api/habits" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Morning run","description":"5k","frequency":"DAILY","schedule":{"targetTime":"07:00"},"targetPerPeriod":1}')
echo "$HABIT"
HABIT_ID=$(echo "$HABIT" | json id)

hr; echo "4) Complete the habit today (streak + activity.logged -> analytics)"
curl -s -X POST "$A/api/habits/$HABIT_ID/completions" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"date\":\"$(date +%F)\",\"note\":\"done\"}"
echo

hr; echo "5) Create a public challenge"
curl -s -X POST "$C/api/challenges" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"title\":\"30-day Move\",\"description\":\"move daily\",\"visibility\":\"PUBLIC\",\"metric\":\"DAYS_COMPLETED\",\"target\":30,\"startsOn\":\"$(date +%F)\",\"endsOn\":\"2026-12-31\"}"
echo

hr; echo "6) Connect a wearable (returns apiKey + hmacSecret once)"
CONN=$(curl -s -X POST "$I/api/integrations/connections" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"partner":"FITBIT"}')
echo "$CONN"
CONN_ID=$(echo "$CONN" | json connectionId)
SECRET=$(echo "$CONN" | json hmacSecret)

hr; echo "7) Partner posts an HMAC-signed wearable event -> EventBridge/Lambda(local) -> activity log"
EVT="evt_$RANDOM"
BODY="{\"externalEventId\":\"$EVT\",\"type\":\"STEPS\",\"value\":8450,\"unit\":\"steps\",\"occurredAt\":\"$(date -u +%FT%TZ)\"}"
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.*= //')
curl -s -X POST "$I/api/webhooks/$CONN_ID" -H 'Content-Type: application/json' -H "X-HL-Signature: $SIG" -d "$BODY"
echo
echo "   (replay the same event — should be de-duplicated, HTTP 200 no-op)"
curl -s -o /dev/null -w "   replay status: %{http_code}\n" -X POST "$I/api/webhooks/$CONN_ID" \
  -H 'Content-Type: application/json' -H "X-HL-Signature: $SIG" -d "$BODY"

hr; echo "8) Wait for event propagation (Kafka -> activity -> analytics)..."
sleep 6

hr; echo "9) Analytics summary"
curl -s "$AN/api/analytics/summary" -H "$AUTH"; echo
echo "   Activity log:"
curl -s "$A/api/activity" -H "$AUTH"; echo

hr; echo "10) Recommendations"
curl -s "$R/api/recommendations" -H "$AUTH"; echo

hr; echo "Smoke test complete."
