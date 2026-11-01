# 06. Transaction lifecycle and reconciliation plan (plan only)

Derived from the Phase 1 prototype (8 scenarios on local Anvil) and Fuji read-only measurements. Separation kept: **FUJI-OBS** observed on Fuji, **LOCAL-SIM** local simulation, **BLOCKED** needs a funded Fuji wallet. Evidence: [lifecycle experiments](../phase-1/transaction-lifecycle-experiments.md), [RPC evaluation](../phase-1/rpc-reconciliation-evaluation.md).

## 1. States
`CREATED` (persisted with raw tx and hash, not sent) -> `SUBMITTED` (node returned the hash: **not success**) -> `PENDING` (known to the node, no receipt) -> `CONFIRMED` (receipt status 1) -> `FINALIZED` (finality policy met); `REVERTED` (receipt status 0, definitive, gas spent); `UNKNOWN` (outcome not determinable); `REPLACED` (nonce consumed by a different transaction; definitive for this attempt, added to the required seven).
Mapping to the existing ledger step statuses (no enum change): CREATED, SUBMITTED -> `PENDING`; PENDING and CONFIRMED -> `PROCESSING`; FINALIZED -> `CONFIRMED` (business step may proceed); REVERTED, REPLACED -> `FAILED`; UNKNOWN -> `UNKNOWN`. The neutral port exposes the eight states ([01](01-target-module-architecture.md) section 7).

## 2. Transition table
| From | Observation | To | Notes |
|---|---|---|---|
| (new) | operation key unseen | CREATED | allocate nonce from the DB, build and sign, persist raw tx + hash **before** any send |
| (existing) | same operation key again | unchanged | business-level idempotency: returns the existing record, **never a new nonce** |
| CREATED | send returns hash | SUBMITTED | acceptance != success |
| CREATED | `already known/imported` | SUBMITTED | idempotent resubmission |
| CREATED | `underpriced`, `insufficient funds`, `invalid sender` | CREATED (+error) | rejected at admission, not on chain; fix and resend with the **same nonce** |
| CREATED/SUBMITTED | `nonce too low` | UNKNOWN | nonce consumed by us or another: look up our hash |
| any in-flight | timeout, 5xx, connection error, HTTP 429 (HTML body) | UNKNOWN | **never a failure**; never re-sign with a new nonce |
| SUBMITTED/UNKNOWN | by-hash found, no receipt | PENDING | re-query only |
| any in-flight | receipt status 1 | CONFIRMED | record block |
| any in-flight | receipt status 0 | REVERTED | terminal |
| CONFIRMED | finality policy met | FINALIZED | Fuji: `finalized` tag >= block; Anvil: configured confirmations |
| UNKNOWN | receipt null **and** by-hash null (not proof of absence), grace elapsed, `latest` nonce > ours | REPLACED | otherwise rebroadcast the **same raw tx** (same hash) |
| any | lookup error or null | unchanged | a failed lookup is not evidence; ask a second provider of the **same environment** before escalating |

## 3. Idempotency and replacement
- Idempotency key = business operation (for example `FUND:{milestoneId}`, `RELEASE:{escrowId}:{submissionCount}`), unique in `avalanche_tx`; the contract additionally rejects a second `fund` or invalid transition, so a duplicate can never double-execute.
- Resubmitting the **same signed bytes** is safe (Phase 1: `already imported` / `nonce too low`, executed exactly once).
- Replacement (speed-up) keeps the nonce and raises both fee fields; the original is recorded as REPLACED with `replaced_by_tx_hash`. **The Avalanche bump threshold is not measured** (anvil accepted 5 %, rejected equal fee): default conservative bump until the funded-wallet calibration (BLOCKED).
- Timeout handling: a hold-the-response fault still mined exactly once (LOCAL-SIM); the client waits, then reconciles by hash.
- Business deadlines: a boundary revert triggers a re-read of escrow state; it is not a business failure by itself (the including block's timestamp decides).

## 4. Receipt and confirmation rules
CONFIRMED needs receipt status 1 **and** `to`/log emitter equal to the configured contract; FINALIZED additionally needs the finality policy; the business step runs only at FINALIZED, followed by an `eth_call` state read compared with MySQL (contract state wins for custody, status, deadlines, counters; MySQL wins for business state). Empty `eth_getLogs` near the head is **never** evidence of absence. A by-hash miss with a present receipt happened once in 53 Fuji observations: trust the receipt.

## 5. Bounded scanning and cursor
Per `(environment, chain_id, contract)`: scan `[cursor+1, min(cursor+range, safeHead)]` where `safeHead` is the `finalized` block (Fuji) or head minus confirmations (Anvil). `range` comes from the provider profile: default **100**; observed Fuji free endpoints: official 100, publicnode 50,000 with an **address filter required**, drpc free 100; discover from range errors and halve; never assume 2,000. Record events and advance the cursor in **one DB transaction**. Unique key `(environment, chain_id, tx_hash, log_index)` suppresses duplicates; only logs from the configured contract address and known topics are accepted. Validated: crash and restart equals a single pass, duplicate chunk suppressed, equal to an independent provider (41 of 41, FUJI-OBS read-only).

## 6. WebSocket
`newHeads`/`newAcceptedTransactions` are **latency hints**: a hint triggers an early reconcile of the matching record or an early scan; it never sets state. Reconnect = resubscribe + bounded recovery over `[lastSeen+1, firstAfter-1]` (resubscribe does not replay: FUJI-OBS); overlapping subscriptions duplicate events, so de-duplicate by key. Server-initiated disconnects and long-lived stability are not yet observed (INCONCLUSIVE); the design must not depend on WebSocket staying up.

## 7. HTTP and provider handling
Check the **HTTP status before parsing JSON** (the official Fuji endpoint answered 429 with an HTML body); honour `Retry-After` (observed 16 s then 60 s); per-host pacing about 1 request/s for background work (minimum gap configurable); bounded retries with backoff; 429 and null are non-definitive. Providers are configured per environment with a primary and an optional second provider **of the same environment**, for cross-checks only (no silent fallback). Public rate-limit thresholds are unknown; provider choice is an open owner decision.

## 8. Restart safety
All state is in the DB before any send; on startup the reconciler resumes records in CREATED (resend same raw tx), SUBMITTED, PENDING, UNKNOWN, CONFIRMED and continues scans from the stored cursor. A restart in the middle of any transition must converge to the same final state (Phase 1 S4).

## 9. Acceptance
The eight Phase 1 scenarios become integration tests on Anvil with the real database; fault injection covers hold-response, null lookups, HTTP 429 with HTML and `Retry-After`, duplicate subscriptions, process kill and restart; a concurrency test proves unique nonces. Fuji acceptance (own-transaction timings, bump threshold, near-deadline inclusion) is **BLOCKED** on the funded wallet and is tracked in [09](09-unresolved-decisions-and-prerequisites.md).
