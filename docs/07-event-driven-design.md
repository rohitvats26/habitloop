# 07 — Event-Driven Design, Idempotency & Reliability

This document specifies the event backbone, how duplicates are prevented end-to-end, and the reliability
mechanisms (timeouts, circuit breakers, retries, DLQ). It directly addresses NFR-2, NFR-3, NFR-9.

---

## 1. Two event planes

| Plane | Transport | Used for |
|-------|-----------|----------|
| **Internal** | Kafka (MSK in AWS) | domain events between HabitLoop services |
| **External** | EventBridge → Lambda | partner/wearable events entering the system |

They meet at Activity Service: the Lambda's normalized output becomes the internal `activity.logged` flow.

---

## 2. Event envelope (common)

Every event (internal and the normalized external one) uses one envelope, defined once in
`backend/common`:

```json
{
  "eventId": "e-7f3c...",          // unique per event (UUID)
  "eventType": "activity.logged",
  "tenantId": "t1",
  "occurredAt": "2026-11-23T06:40:00Z",
  "correlationId": "c-91ab...",    // propagated from the originating request
  "idempotencyKey": "t1:u9:WEARABLE:evt_123",
  "payload": { }
}
```

- `eventId` identifies the event; `idempotencyKey` identifies the **business fact** so replays are safe.
- `correlationId` ties events back to the HTTP request that caused them (tracing across services).

---

## 3. Internal topics (Kafka)

| Topic | Producer | Consumers | Key |
|-------|----------|-----------|-----|
| `user.registered` | user-service | (future: notifications) | tenantId |
| `activity.logged` | activity-service | challenge-service, analytics-service | tenantId:userId |
| `challenge.joined` / `challenge.completed` | challenge-service | analytics-service | tenantId:challengeId |
| `analytics.updated` | analytics-service | recommendation-service (cache-bust) | tenantId:userId |
| `partner.activity.normalized` | Lambda (via SNS/MSK) | activity-service | tenantId:userId |

Partitioning by `tenantId:userId` preserves per-user ordering where it matters (streak math) while
allowing parallelism across users.

---

## 4. Idempotency — three layers (so no duplicate activity logs)

This is the brief's most-tested property. Duplicates are blocked at three independent points:

1. **Edge (Integration Service):** `raw_partner_events` has `UNIQUE(connection_id, external_event_id)`.
   A re-sent webhook with the same partner event id is a no-op (`200`), never stored twice.
2. **Normalization (Lambda):** the normalized event carries a deterministic `idempotencyKey`
   = `tenantId:userId:source:externalEventId`. Lambda is side-effect-light; its only output is the event.
3. **Write (Activity Service):** `activity_log` has `UNIQUE(tenant_id, user_id, source, external_event_id)`.
   The consumer does an **insert-if-absent**; a duplicate key is caught and the message is acknowledged
   without creating a second row.

Manual completions get their own guard: `UNIQUE(habit_id, completion_date)` (one tick per habit per day).

**Consumer pattern:** at-least-once delivery + idempotent write = effectively-once. Consumers are written
to tolerate re-delivery (catch unique-violation → ack).

---

## 5. Reliability mechanisms (NFR-3)

| Concern | Mechanism |
|---------|-----------|
| **Timeouts** | all synchronous service-to-service calls have connect + read timeouts (Resilience4j / WebClient). No unbounded waits. |
| **Circuit breakers** | Resilience4j breaker on the challenge→user verify call; open circuit → fail fast + fallback (defer invite, surface a friendly error). |
| **Retries** | Kafka consumer retry with backoff (e.g. 3 attempts) before routing to a retry/DLQ topic; EventBridge+Lambda async retries; webhook producers (partners) can safely retry thanks to idempotency. |
| **Dead-letter** | Kafka: `*.DLT` topics via Spring Kafka `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`. External: SQS `partner-events-dlq`. Both alarmed. |
| **Graceful degradation** | if analytics is down, activity writes still succeed (event buffered in Kafka); dashboard shows last-known aggregates. Recommendation falls back to generic tips if analytics read fails. |
| **Reliable publishing** | events are published **after** the DB commit using the transactional-outbox pattern where exactly-once matters (outbox table polled → Kafka), avoiding "committed but not published" gaps. |
| **Backpressure / backlog** | consumer lag + DLQ depth are CloudWatch metrics with alarms; Integration webhook is rate-limited per connection. |

---

## 6. Transactional outbox (activity.logged)

To guarantee an event is published iff the state change committed:

1. In the same DB transaction that inserts the `activity_log`/`habit_completion`, insert a row into an
   `outbox` table (event envelope as JSON).
2. A relay (scheduled poller / Debezium-style) reads unpublished outbox rows, publishes to Kafka, marks
   them sent.
3. Crash-safe: an un-sent row is retried; a double-send is absorbed by consumer idempotency.

This removes the classic dual-write race between the database and Kafka.

---

## 7. End-to-end trace of one wearable event

1. Partner POSTs `evt_123` (HMAC). Integration Service verifies → stores raw (unique) → emits
   `partner.event.received` to EventBridge. Returns `202`.
2. EventBridge rule invokes Lambda. Lambda validates, maps `userRef`→user/habit, normalizes
   `8450 steps`, emits `partner.activity.normalized` with key `t1:u9:WEARABLE:evt_123`.
3. Activity Service consumes, attempts insert into `activity_log`; unique key ensures a single row; emits
   `activity.logged` via outbox.
4. Challenge Service consumes `activity.logged` → increments participant progress (its own idempotency by
   `(participant, activityLogId)`). Analytics Service consumes → updates DynamoDB daily/weekly/by-hour.
5. Analytics emits `analytics.updated` → Recommendation Service invalidates its cache for that user.
6. Replay of `evt_123` at **any** step is absorbed: step 1 (edge), step 3 (write), step 4 (consumer) each
   reject the duplicate. No duplicate activity log, no double-counted challenge progress.

This is exactly the brief's 14-step workflow, with idempotency proven at each hop.
