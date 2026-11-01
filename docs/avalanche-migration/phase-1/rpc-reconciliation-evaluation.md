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
