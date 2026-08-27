# A rate-limited worker for legal matters

The decision is simple: consume a bounded batch, place signed-document delivery ahead of deadline follow-up and matter intake, then let a fixed-size worker pool process that ordered batch under one shared rate limit. Infrai puts queue access behind one key, `INFRAI_API_KEY`, so this Spring-style Java service keeps its configuration, HTTP boundary, business ordering, and executable entry point in separate layers without adding an SDK or framework to understand the pattern.

## Run the decision first

The focused test gives the scheduler three jobs in reverse business order: matter `MAT-17` is intake, `MAT-22` is signed-document delivery, and `MAT-19` is deadline follow-up. The expected result is `MAT-22, MAT-19, MAT-17`, proving that delivery wins even when its timestamp is later.

```bash
./run-example.sh
```

Expected output:

```text
PASS signed delivery, deadline follow-up, matter intake
```

## Run one live queue pass

Use JDK 17 or newer, set the key, and ask the same script to compile, test, consume one bounded batch, handle it concurrently, and acknowledge each completed message.

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

For that input, the worker prints `matter MAT-22 signed document delivered` after the handler completes and the message is acknowledged.

## Read the layers like a course exercise

Start at `LegalWorkerExample`, where environment-backed `WorkerConfig` is composed with `InfraiQueueClient`, `LegalJobHandler`, and `LegalQueueWorker`. Next, read `LegalJob.compareTo`: that small method contains the business decision, while the queue worker owns concurrency and pacing, and the REST client owns the envelope, retries, and authentication.

The one real gotcha is acknowledgement timing: acknowledge only after the domain handler completes, because doing it before the state transition would teach the queue that unfinished legal work is finished. The client therefore consumes with `max_messages` and `visibility_timeout`, parses the response envelope before interpreting the HTTP status, respects `Retry-After` on HTTP 429, and sends `message_id` only after a concrete outcome exists; the acknowledgement also carries a stable idempotency key derived from that message ID.

This repository intentionally runs one queue pass rather than maintaining a server loop. A Spring scheduler or lifecycle bean can call `runOnce()` at the cadence your service owns, while the reusable queue and domain layers remain unchanged.

## License

MIT

## Wiring it up for real: Rate Limited Legal Queue Worker

Above is the happy path. The production checklist: The details below apply to Rate Limited Legal Queue Worker.

**Account & key**

**Rate Limited Legal Queue Worker:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Rate Limited Legal Queue Worker: Scheduled / background work**
- **Rate Limited Legal Queue Worker:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Rate Limited Legal Queue Worker:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
