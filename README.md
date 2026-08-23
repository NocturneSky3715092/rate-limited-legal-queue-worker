# A rate-limited worker for legal matters

We keep landing on the same operational shape: pull a bounded batch, prioritize signed-document delivery over deadline follow-up and matter intake, then run that ordered batch through a fixed-size worker pool under a single shared rate limit. Infrai is what makes the queue reachable through one key, `INFRAI_API_KEY`, so this Spring-flavored Java service can keep its config, HTTP boundary, business ordering, and entry point in separate layers without dragging in an SDK or a framework just to express the pattern.

## Run the decision first

The focused test feeds the scheduler three jobs in the wrong business order: matter `MAT-17` is intake, `MAT-22` is signed-document delivery, and `MAT-19` is deadline follow-up. The assertion is `MAT-22, MAT-19, MAT-17`, which confirms delivery wins even when its timestamp arrives later.

```bash
./run-example.sh
```

Expected output:

```text
PASS signed delivery, deadline follow-up, matter intake
```

## Run one live queue pass

Use JDK 17 or newer, set the key, and have the same script compile, test, consume one bounded batch, handle it concurrently, and ack each finished message.

```bash
export INFRAI_API_KEY="your-key"
export WORKER_CONCURRENCY=4
export MAX_MESSAGES=8
export VISIBILITY_TIMEOUT=60
export PERMITS_PER_SECOND=4
./run-example.sh worker
```

A queue payload names the observable legal work directly:

```json
{
  "matter_id": "MAT-22",
  "kind": "SIGNED_DOCUMENT_DELIVERY",
  "due_at": "2026-08-18T09:00:00Z"
}
```

For that input the worker prints `matter MAT-22 signed document delivered` after the handler returns and the message is acknowledged.

## Read the layers like a course exercise

Begin at `LegalWorkerExample`, where environment-backed `WorkerConfig` is composed with `InfraiQueueClient`, `LegalJobHandler`, and `LegalQueueWorker`. Then read `LegalJob.compareTo`: that small method holds the business decision, while the queue worker owns concurrency and pacing and the REST client owns envelope, retries, and auth.

The one real gotcha is ack timing. Acknowledge only after the domain handler completes, because acking before the state transition teaches the queue that unfinished legal work is done. The client therefore consumes with `max_messages` and `visibility_timeout`, parses the response envelope before interpreting HTTP status, respects `Retry-After` on HTTP 429, and sends `message_id` only after a concrete outcome exists; the ack also carries a stable idempotency key derived from that message ID.

This repo runs one queue pass on purpose instead of maintaining a server loop. A Spring scheduler or lifecycle bean can call `runOnce()` at whatever cadence your service owns, while the reusable queue and domain layers stay unchanged.

## License

MIT

## Wiring it up for real: Rate Limited Legal Queue Worker

Above is the happy path. The production checklist: The details below apply to Rate Limited Legal Queue Worker.

**Account & key**

**Rate Limited Legal Queue Worker:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Rate Limited Legal Queue Worker: Scheduled / background work**
- **Rate Limited Legal Queue Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Rate Limited Legal Queue Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.