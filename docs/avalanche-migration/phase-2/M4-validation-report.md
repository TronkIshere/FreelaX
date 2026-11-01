# P2-M4 validation report: log scanner, cursor, provider profiles, WebSocket hints, second-provider cross-check

Date: 2026-11-01. Scope source: [03 backend plan, P2-M4](03-backend-implementation-plan.md) and [06 lifecycle plan sections 5-7](06-transaction-lifecycle-and-reconciliation-plan.md). The prompt's milestone names (M4 = "transaction lifecycle") differ from the repository roadmap; the repository roadmap was followed (M3 already delivered the lifecycle state machine; M4 is scanning and provider handling). All chain activity was a local Anvil; **no Fuji request, signature or transaction, no Mainnet contact, no real funds.**

## 1. Delivered
| Item | Implementation |
|---|---|
| `LogScanner` | one bounded pass from the stored cursor to the safe head (latest minus confirmations on LOCAL_SIMULATION, the `finalized` block on Fuji); chunk size from the provider profile; **halves the range on a provider refusal**; accepts only logs of the configured contract, with a registered topic, not removed and inside the requested range |
| `ScanRepository` | each chunk's events **and** the cursor are written in one database transaction; duplicates suppressed by the unique `(environment, chain_id, tx_hash, log_index)` key; the cursor can only move forward (`GREATEST`) |
| `ProviderProfile` | measured limits: official Fuji host 100 blocks, publicnode 50,000 (address required), unknown hosts 100, local 2,000; a configured override can only lower the limit |
| `EventDecoder` | topic0 to name registry; empty until the escrow ABI exists (M6) |
| `ScanCoordinator` + `WsHintListener` | WebSocket `newHeads` is a **latency hint only**: a hint requests a scan, carries no data and sets no state; hints are coalesced; every (re)connect also requests a pass so the cursor recovers missed blocks with bounded reads; a periodic timer guarantees progress without any hint |
| `RetryAfterInterceptor` + `RpcPacer.backoff` | HTTP 429/503 `Retry-After` (bounded to 120 s, numeric seconds, default 5 s) delays the following calls; the throttled call itself stays a non-definitive transport error. HTTP status is checked before any JSON parsing (web3j raises on non-2xx) |
| `TxTracker` second provider | a null receipt from the primary is cross-checked with an optional second provider of the **same environment** before a record can be treated as missing/REPLACED. Closes M3 limitation 2 |
| Flyway `V2__avalanche_scan.sql` | `avalanche_scan_cursor`, `avalanche_event` |
| Wiring | `ScanConfig`, opt-in (`avalanche.scan.enabled`, needs `avalanche.tx.enabled`); contract address must be a 20-byte address (`SCAN_CONTRACT_INVALID`); the WebSocket URL passes the same endpoint guard (local-class host on LOCAL_SIMULATION, allow-listed https host on Fuji) |

## 2. Acceptance criteria
| Criterion (plan) | Evidence | Result |
|---|---|---|
| cursor advances only with the recorded events, in one transaction | `saveChunk`; `aReplayedChunkIsSuppressedAndTheCursorNeverMovesBackwards` | PASS |
| scan stops at the safe head | `scanFindsEventsStopsAtTheSafeHead...` (newest block inside the confirmation window not scanned; found after one more block) | PASS |
| crash and restart equals a single pass | `aCrashInTheMiddleOfAScan...` (proxy fails `eth_getLogs` after 2 chunks, a new scanner finishes; the stored set equals an independent read of the chain; 12 of 12) | PASS |
| replayed chunk suppressed by (tx hash, log index) | same test plus the unique key | PASS |
| WebSocket gap recovered by bounded reads; duplicates harmless | `hintsTriggerScans...` (events emitted with no listener are recovered on connect; a live hint triggers a scan with no timer; two overlapping subscriptions both hint, 7 events and 7 unique keys) | PASS |
| chunk size obeys the profile and halves on a range error | `theRangeIsHalved...` (provider cap 10: 100 -> 50 -> 25 -> 12 -> 6, every accepted request within the cap, nothing missed) and profile tests | PASS |
| HTTP status before JSON; Retry-After honoured; pacing | `retryAfterMakesTheFollowingCallsWait...` (next call waited at least 800 ms after `Retry-After: 1`); the 429-with-HTML case is covered since M3 (S5) | PASS (pacing 1 req/s is the configurable default, not load-tested) |
| only logs of the configured contract and known topics are accepted | `onlyLogsOfTheConfiguredContract...` (foreign address, out-of-range block, unknown topic, removed, topic-less: all ignored) | PASS |
| rows of other environments ignored | `anotherEnvironmentsCursorAndEventsAreNeverTouched` | PASS |
| no second-provider cross-check before REPLACED (M3 limitation) | `aSecondProviderOfTheSameEnvironment...` (control: lying primary alone gives REPLACED; with the second provider the record is CONFIRMED) | PASS |
| Reconciler: event versus `eth_call` state | needs the escrow ABI | **deferred to M6** |
| Server-initiated WebSocket drops, long-lived stability on Fuji | not observable locally; the design never depends on the socket | not tested (Phase 1 INCONCLUSIVE remains) |

## 3. Test results
`avalanche-integration` default build (JDK 21, Docker available): **85 run, 83 passed, 0 failed, 2 skipped** (optional Anvil and Fuji tests), identical on two consecutive runs. M4 added 14 (`ScanTest` 10, `RetryAfterInterceptorTest` 2, `TxSpringWiringTest` +1, `TxLifecycleTest` +1). Solana baseline unchanged: marketplace 6/6 plus gateway 10/10; JDK 17 compile of the existing modules passes.
Test-design notes: the scanned contract is a hand-assembled runtime that emits one known log per call (no Solidity involved); each test uses its own emitter address because the Anvil chain is shared. A test-order dependence (Anvil's base fee decays as other tests mine blocks) was fixed in the cap test by making the tip equal to the cap.

## 4. Limitations
1. Fuji behaviour of the scanner (the `finalized` path, real provider limits, WebSocket stability) is untested: only identity calls were authorized in the earlier phases and the new prompt permits read-only calls, but no Fuji call was needed or made in M4.
2. `EventDecoder` is empty until M6 registers the escrow events; a scan enabled before then stores nothing.
3. The ScanCoordinator logs failures but exposes no metrics; alerting is out of scope for the simulation.
4. The deployment block (`start-block`) must come from the M7 deployment manifest; M4 takes it from configuration.
