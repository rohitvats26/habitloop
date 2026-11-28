# 07 — Event-Driven Design, Idempotency & Reliability

This document specifies the event backbone, how duplicates are prevented end-to-end, and the reliability
mechanisms (timeouts, circuit breakers, retries, DLQ). It directly addresses NFR-2, NFR-3, NFR-9.

---

> **Implementation note — pluggable transport (`EVENTS_TRANSPORT`).** The event backbone is abstracted so
> the same envelope and handlers run over either transport: **`kafka`** locally (Docker Compose) and
> **`eventbridge`** on AWS. On AWS there is **no MSK** — services PutEvents to a single EventBridge bus, and
> each consumer long-polls its own **SQS** queue (one EventBridge rule per event type → SQS target). This
> unifies the two planes below onto EventBridge in production. See
> [06-aws-infrastructure.md](06-aws-infrastructure.md) and `common/event/eventbridge/`.

## 1. Two event planes

| Plane | Transport (local / AWS) | Used for |
|-------|-------------------------|----------|
| **Internal** | Kafka / **EventBridge + SQS** | domain events between HabitLoop services |
| **External** | Kafka / **EventBridge → Lambda** | partner/wearable events entering the system |

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
| `user.registered` | user-service | — | tenantId |
| `activity.logged` | activity-service | challenge-service, analytics-service | tenantId:userId |
| `challenge.joined` / `challenge.completed` | challenge-service | analytics-service | tenantId:challengeId |
| `analytics.updated` | analytics-service | recommendation-service (cache-bust) | tenantId:userId |
| `partner.event.received` | integration-service | normalizer | connectionId |
| `partner.activity.normalized` | normalizer (Kafka locally, Lambda on AWS) | activity-service | tenantId:userId |

Partitioning by `tenantId:userId` preserves Kafka's publication order per user while allowing parallelism
across users. This does not guarantee transaction or occurrence order for concurrent activity writes;
EventBridge/SQS delivery also does not guarantee ordering.

---

## 4. Idempotency — three layers (so no duplicate activity logs)

This is the brief's most-tested property. Duplicates are blocked at three independent points:

1. **Edge (Integration Service):** `raw_partner_events` has `UNIQUE(connection_id, external_event_id)`.
   A re-sent webhook with the same partner event id is a no-op (`200`), never stored twice.
2. **Normalization (Lambda):** the normalized event carries a deterministic `idempotencyKey`
   = `tenantId:userId:source:externalEventId`. Lambda is side-effect-light; its only output is the event.
3. **Write (Activity Service):** `activity_log` has `UNIQUE(tenant_id, user_id, source, external_event_id)`.
   The consumer checks for an existing row; if concurrent inserts race, a database uniqueness failure
   propagates for retry. A subsequent delivery observes the existing row and is acknowledged.

Manual completions get their own guard: `UNIQUE(habit_id, completion_date)` (one tick per habit per day).

**Consumer pattern:** at-least-once delivery + idempotent write = effectively-once. Consumers tolerate
re-delivery; a failed database transaction is not acknowledged as a success.

---

## 5. Reliability mechanisms (NFR-3)

| Concern | Mechanism |
|---------|-----------|
| **Timeouts** | all synchronous service-to-service calls have connect + read timeouts (Resilience4j / WebClient). No unbounded waits. |
| **Circuit breakers** | Resilience4j breaker on the challenge→user verify call; open circuit → fail fast + fallback (defer invite, surface a friendly error). |
| **Retries** | Kafka activity, challenge, analytics and local normalizer consumers retry transient failures indefinitely; malformed messages go to their DLT. Lambda async invocations and SQS use their configured retry policies. Webhook producers can safely retry thanks to idempotency. |
| **Dead-letter** | Kafka: consumer-specific `*.DLT` topics via Spring Kafka. AWS: partner target delivery and Lambda asynchronous failures use `partner-events-dlq`; internal SQS queues use the internal DLQ. Monitor both AWS DLQs and Kafka DLTs. |
| **Graceful degradation** | if analytics is down, activity writes still succeed (event buffered in Kafka); dashboard shows last-known aggregates. Recommendation falls back to generic tips if analytics read fails. |
| **Reliable publishing** | activity-service writes `activity.logged` and integration-service writes `partner.event.received` to separate PostgreSQL outboxes in their respective business transactions. Relays publish to Kafka or EventBridge and retry failures. Other producers do not use these outboxes. |
| **Backpressure / backlog** | consumer lag + DLQ depth are CloudWatch metrics with alarms; Integration webhook is rate-limited per connection. |

---

## 6. Transactional outbox (activity.logged)

For `activity.logged`, a committed state change leaves a durable event to publish:

1. In the same DB transaction that inserts the `activity_log`/`habit_completion`, insert a row into an
   `activity_outbox` row (event envelope as JSON).
2. A scheduled relay locks pending rows, publishes to Kafka (waiting for acknowledgement) or EventBridge
   (checking entry failures), then marks them published. Failed rows record an error and retry after 30 seconds.
3. If publication succeeds but the database update fails, the event can be published again. Consumers must
   remain idempotent. Outbox rows remain pending when transport is `none`, and are delivered when a transport
   is enabled. Published rows are retained; operators should monitor backlog and manage retention.

Analytics commits the processed marker and daily, weekly, and pattern aggregates in one DynamoDB
transaction. Conditional revisions prevent concurrent updates from silently overwriting one another;
conflicts are retried by the event transport. The local memory store applies the same operation under a
lock, but is not durable. Kafka analytics and challenge processing retry transient failures indefinitely.
Malformed messages are published to `activity.logged.analytics.DLT` or `activity.logged.challenge.DLT`;
configure each topic with at least as many partitions as `activity.logged` and monitor them.
Failed dead-letter publication is not acknowledged.
SQS failures are retried according to the queue's redrive policy.

Challenge progress and achievement recording are separate transactions. A failed second transaction is
retried; participant locks and per-activity receipts prevent replay from incrementing progress twice.
The AWS observability module alarms on the internal DLQ, queue message age, and activity and partner
outbox backlogs older than five minutes. These outbox alarms depend on logs from the respective running
services; monitor service availability separately. Configure and confirm the alarm email subscription.

Existing incorrect aggregates or markers from earlier deployments are not automatically repaired. Avoid
running older analytics-service versions alongside this implementation because they do not enforce the
revision conditions.

This removes the classic dual-write race between the database and Kafka.

### Partner outbox and SQS operations

The webhook transaction stores both the raw receipt and a `partner_event_outbox` row; a per-connection
lock serializes duplicate checks. The integration relay publishes pending rows in order per connection,
waits for Kafka acknowledgement or EventBridge acceptance, and retries failures after 30 seconds.
Delivery is at least once: a publish followed by a database failure can produce duplicates.
With transport `none`, rows stay pending. Published rows are retained; monitor and manage both pending
backlog and retention. Raw events accepted before the outbox migration are not automatically republished:
recover them only with a deliberate, idempotent replay plan.

For a backlog, inspect pending row age, attempts and last error without exposing event payloads. Repair
the transport or invalid event before replay: the relay retries automatically, and an earlier failed row
blocks later rows for the same connection. Never mark a row published merely to clear the alarm. Retain
published rows until the agreed audit and replay window has passed; archive them securely before any
approved cleanup, and never delete pending rows. Reposting an old webhook does not recreate a missing
outbox row because the raw receipt is deduplicated. Recover pre-migration receipts only through a
reviewed backfill that preserves their original identity and validates idempotency downstream.

Local Kafka malformed partner input goes to `partner.event.received.normalizer.DLT`, and malformed
normalized activity to `partner.activity.normalized.activity.DLT`. Provision each DLT with at least as
many partitions as its source topic, monitor it, and ensure the producer can publish to it.
Failed DLT publication must not acknowledge the source message.

EventBridge consumers require a nonempty queue URL. They process one SQS message at a time with a
60-second visibility timeout, renewing it every 20 seconds during processing. Grant
`sqs:ChangeMessageVisibility` along with receive and delete permissions. A failed renewal or deletion can
cause redelivery, so handlers must remain idempotent. Apply the integration Flyway migration and deploy
the updated Terraform (including Lambda asynchronous failure destination and ECS permissions) before
relying on these guarantees. Lambda target-delivery failures and asynchronous handler failures are
different failure paths; the latter destination contains invocation records rather than raw events.

### Integration checks

With Docker available, run the PostgreSQL outbox and challenge concurrency tests from `backend` using
`mvn -pl "activity-service,challenge-service,integration-service" -am "-Dtest=ActivityOutboxIT,HabitScheduleHistoryIT,HabitScheduleMigrationIT,ChallengeConcurrencyIT,PartnerOutboxIT" "-Dsurefire.failIfNoSpecifiedTests=false" test`.
For DynamoDB Local, set `DYNAMODB_LOCAL_ENDPOINT` to a loopback URL such as `http://127.0.0.1:8000` and run
`mvn -pl analytics-service -am "-Dtest=DynamoDbAnalyticsStoreIT" "-Dsurefire.failIfNoSpecifiedTests=false" test`.
The latter test creates and removes a uniquely named local table; it is skipped when the variable is absent.

---

## 7. End-to-end trace of one wearable event

1. Partner POSTs `evt_123` (HMAC). Integration Service verifies → commits the raw receipt and
   `partner.event.received` outbox row together → returns `202`. The relay publishes asynchronously.
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
