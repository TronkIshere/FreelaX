# Phase 1 - RPC, event scanning and reconciliation

Evidence: [fuji-probe-official.json](evidence/fuji-probe-official.json), [fuji-probe-publicnode.json](evidence/fuji-probe-publicnode.json), [ws-observe-official.json](evidence/ws-observe-official.json), [finality-probe-official.json](evidence/finality-probe-official.json), [timestamp-probe-official.json](evidence/timestamp-probe-official.json), [java-fuji-fork-experiments.json](evidence/java-fuji-fork-experiments.json) (J12).

## R1. Measured RPC facts (Fuji, 2026-11-01, one host)
| Topic | Official public RPC | publicnode | Status |
|---|---|---|---|
| `eth_getLogs` max block range | **100** (101 fails `-32602`) | **50,000** (100,000 fails `-32701`) | PASS (measured) |
| `eth_getLogs` without address | allowed (3 logs / 99 blocks) | **rejected** (address required) | PASS |
| Historical depth | not measurable (range cap) | empty success 1,000,000 blocks back (not proof of depth, the window may simply have no USDC logs) | INCONCLUSIVE |
| Rate limiting | none seen at ~2.5 req/s sequential and ~100 calls/90 s | none seen | **INCONCLUSIVE** (limits unknown; not stress-tested on purpose) |
| Batch JSON-RPC | 40-request batches of `eth_getBlockByNumber` worked | not tested | PASS (official) |
| `eth_call` with state override | worked (stub-code probes) | not tested | PASS (official) |
| WebSocket | `wss://api.avax-test.network/ext/bc/C/ws`: `newHeads` and `newAcceptedTransactions` subscriptions worked for 90 s without disconnect | not tested | PASS (short) |
| Reconnect/duplicate delivery/missed events | not exercised (connection never dropped; client closed deliberately, close code 1005) | | **INCONCLUSIVE** |
| Reorg/finality | `finalized` trails `latest` by 4-5 blocks; acceptance->finalized 5.1-8.7 s (see [lifecycle](transaction-lifecycle-experiments.md)); no reorg expected after acceptance (DOC) and none observed | | OBS |
| Unknown tx/receipt | JSON `null`, not error | | PASS |
| Empty logs near head | cannot be trusted (DOC); same-filter results before/after receipt were equal 14/14 but see lifecycle caveat | | INCONCLUSIVE |
**The Phase 0 hypothesis "scan in 2,000-block chunks" is refuted for the official public endpoint (100 blocks) and merely safe for publicnode.** Nothing here generalises to paid providers.

## R2. Is an external indexer needed?
Arithmetic from evidence: observed mean block interval about 1.7 s means about **50,000 blocks/day**. At the official endpoint's 100-block limit, **one day of catch-up costs about 500 `eth_getLogs` calls per (contract, topic-set) filter**; a week is about 3,500. At the ~2.5 req/s that was observed to be accepted, a day of backlog takes about 3-4 minutes of calls. Steady state (head moves ~0.6 blocks/s) needs about 1 call per ~170 s per filter.
**Conclusion: direct bounded log scanning is sufficient for the initial system; no external indexer is justified by any experiment.** Caveat: this holds for a single escrow contract with a handful of event types and for catch-up measured in days; a long outage on a rate-limited provider, or historical backfill from genesis, would justify revisiting. No indexer dependency was added.

## R3. Recommended reconciliation strategy (idempotent, restart-safe)
1. **Config per provider:** `maxLogRange` (default **100**; raise only when measured), `requireAddress`, timeouts, retry/backoff. On `-32602`/`-32701`-style errors parse the stated maximum and halve the range; do not hard-code 2,000.
2. **Cursor:** table `chain_scan_cursor(chain_id, contract, last_scanned_block)`; scan `[cursor+1, min(cursor+range, safeHead)]` where `safeHead` is the number of the **`finalized`** block (measured lag about 5 s behind `latest`). Advance the cursor in the **same DB transaction** that records the decoded events.
3. **Idempotency:** unique key `(chain_id, tx_hash, log_index)`; process each event at most once; accept only logs where `address == configured contract` and `topic0` is known; reject by chain id check (`eth_chainId` verified at start and each client).
4. **Duplicate and missed events:** WebSocket/`newAcceptedTransactions` is **only a latency hint** (wakes the reconciler early); correctness comes from the cursor scan plus per-transaction receipt checks. Duplicates collapse on the unique key; gaps are closed because the cursor never skips.
5. **Pending vs failed:** transaction rows follow the state model in [transaction-lifecycle-experiments.md](transaction-lifecycle-experiments.md) L4. A transaction is failed only on `status 0x0` or on replaced-nonce proof, never on null results or empty logs.
6. **State cross-check:** after decoding an escrow event, confirm with `eth_call` on the escrow at the `finalized` block; mismatch -> alert, not auto-fix.
7. **Second provider:** compare head and the receipt of any transaction treated as failed/unknown before declaring it (one by-hash miss was seen in 53 observations).
8. **Restart safety:** nothing is held in memory; the job re-reads cursor, `PENDING/UNKNOWN/PROCESSING` rows and resumes (fits the existing `@Scheduled` fixed-delay pattern, see [java-web3j-evaluation.md](java-web3j-evaluation.md)).
9. **Reorg policy:** scan only up to `finalized`; use receipts (not logs) for faster provisional status.

## R4. Provider selection (still a business/ops decision)
Public endpoints are fine for development (100-block logs, no documented SLA). Production needs a dedicated provider as primary and a second as fallback (Phase 0 recommendation unchanged); the **100-block default makes a paid provider optional for log scanning but not for availability/SLA**. No account was created and no provider other than the two free endpoints was measured.

## Status
Range limits, endpoint behaviour, scanning arithmetic: **PASS**. Rate limits, WS reconnection, duplicate delivery, long historical depth: **INCONCLUSIVE**.

---
# Part B update (2026-11-01): providers, rate limiting, WebSocket, scanning

All AVALANCHE_FUJI, read-only, single host, small samples. Conservative pacing: the provider probe sent about 5 requests/s for 30 s per provider (150 each); every later probe used at most 1 request per 0.6 to 1.5 s per host. **No finding below generalises to other providers or to paid plans.**

## P1. Provider-specific `eth_getLogs` behaviour (evidence: [provider-probe-fuji.json](evidence/provider-probe-fuji.json))
| Provider | Max range observed | Address required | History (<=99-block window at depth) | Notes |
|---|---|---|---|---|
| Official public RPC | **100** blocks (101 rejected `-32602`) | no | answered at depths 1k, 100k, 1M, 10M, **30M** (found 8 logs), 55M | windows with 0 logs are not evidence of absence |
| publicnode | **50,000** (100,000 rejected `-32701`) | **yes** (error otherwise) | answered to 10M; **`not found`** at 30M and 55M, i.e. older history appears **pruned** somewhere between 10M and 30M blocks back (roughly 200 to 600 days at the observed 1.7 s mean block time; an estimate) | |
| drpc free (`avalanche-fuji.drpc.org`) | **100** observed (101 rejected, HTTP 400) | no | answered at 30M (7 logs) | the error text says "ranges over 10000 blocks are not supported on free plan" although 101 blocks was already rejected (message and behaviour disagree); address-less queries at 99/100 returned HTTP 500 "Temporary internal error" (transient or not unknown) |
The default **100-block** scan range from Phase 1 holds for two of three free endpoints; publicnode is the outlier. Chunk size stays a **per-provider setting discovered at startup/from errors**, never assumed.

## P2. Rate limiting (OBS)
- 150 sequential requests at ~5 req/s: **150 x HTTP 200 on all three providers** (official median 240 ms, publicnode 217 ms, drpc 46 ms from this host).
- **Then the official endpoint began throttling this host**: `HTTP 429`, `Server: cloudflare`, **HTML body** (not a JSON-RPC error), `Retry-After: 16` at 14:26 UTC ([official-rate-limit-event.json](evidence/official-rate-limit-event.json)); later `Retry-After: 60`. Throttling persisted at lower rates (three 429s at about 1.6 req/s and three at about 0.7 req/s in two scan runs) until each `Retry-After` was honoured. Preceding load was roughly 190 requests in 10 minutes. **The threshold, window and whether it is per-IP are not established.** Earlier Part A (2.5 req/s, 90 s) was not throttled.
- Client rules proven necessary: check the **HTTP status before parsing JSON** (a 429 HTML page makes a naive client throw a JSON parse error); honour `Retry-After`; treat 429 as non-definitive (UNKNOWN, never failure; prototype S5); pace background polling (the shared helper `rpc_util.mjs` spaces calls per host, default 600 ms).

## P3. WebSocket (official, `newHeads`; evidence [ws-reconnect-fuji.json](evidence/ws-reconnect-fuji.json))
Session A (20 s): 16 heads, no gaps. Client closes (code 1005, client-initiated), stays offline 15 s. Session C (reconnect, **two** overlapping subscriptions, 20 s): connection opened in 0.6 s; **0 missed heads replayed** on resubscribe (the 5 blocks produced while offline were not delivered); **each head was delivered twice** (28 events, 14 distinct heads) because two subscriptions overlapped; the 5-block gap was recovered by bounded HTTP block reads (5 of 5). Not observed: a **server-initiated** disconnect, idle-timeout behaviour, or long-lived stability beyond about 90 s. Conclusion: reconnect = resubscribe + cursor-based recovery; duplicates must be de-duplicated by key; WebSocket stays a latency hint.

## P4. Reconciliation
Restart-safe finalized-cursor scan validated (crash, restart, duplicate chunk, equality with an independent provider): see [transaction-lifecycle-experiments.md](transaction-lifecycle-experiments.md) B4. Practical settings from evidence: scan up to the `finalized` block (about 4 to 5 blocks behind `latest`), 100-block chunks by default, per-host pacing, `Retry-After` honoured, second provider for cross-checks, HTTP polling budget of about 1 request/s per host for background work.

## P5. Status
| Criterion | Status |
|---|---|
| Provider-specific log ranges and history (3 free providers) | **PASS** |
| Rate limits: existence of throttling and its shape (429 + Retry-After, HTML) | **PASS (observed)**; threshold | **INCONCLUSIVE** |
| WebSocket reconnect, missed-event recovery, duplicate delivery (client-initiated) | **PASS (observed, one provider)**; server-initiated drops | **INCONCLUSIVE** |
| Finalized-cursor scanning, restart safety, duplicate suppression | **PASS** |
| Provider inconsistency (null/miss) cause | **INCONCLUSIVE** |
