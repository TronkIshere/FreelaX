# Phase 1 - `block.timestamp` and deadline semantics

Evidence: [timestamp-probe-official.json](evidence/timestamp-probe-official.json), [java-local-experiments.json](evidence/java-local-experiments.json) (J8), [forge-unit-tests.txt](evidence/forge-unit-tests.txt). Code: `prototypes/ws-observer/timestamp_probe.mjs`, `Phase1Lab.t.sol`, `LocalEvmExperimentsTest.deadlineBoundariesOnRealBlocks`. Run 2026-11-01 ~13:34 UTC against the official Fuji RPC.

## T1. What the official sources say (DOC)
- ACP-194 "Continuous Execution" (status *Implementable*; fetched from the ACPs repo): a block is *accepted* by consensus when all its transactions can be paid for in the worst case; acceptance *enqueues* it for asynchronous *execution*; blocks are *settled* once a later block's timestamp is at least their execution time plus `tau` (= **5 s**). The executor keeps its own, more granular **execution timestamps** (sub-second) in addition to the **block header timestamp**; execution timestamps drive gas/price accounting.
- ACP-194 as read does **not state which timestamp the EVM `TIMESTAMP` opcode returns during execution**. (A text search for `block.timestamp` in the ACP found nothing.) This is therefore settled here only by observation (T3), and is **not guaranteed by the spec I read**.
- The C-Chain RPC page: `latest` = last *executed* block; `finalized`/`safe` = last *settled* block.
- ACP-226 (millisecond `timestampMilliseconds` and dynamic minimum block delay) is live on Fuji (T2). Coreth assumptions were **not** relied on: all conclusions below come from live Fuji headers/EVM calls, not from Coreth-era behaviour. That Mainnet runs the same VM is carried from Phase 0 (secondary source) and **not re-verified**.

## T2. Header timestamps (OBS, 240 consecutive blocks 60,352,955-60,353,194)
| Property | Result |
|---|---|
| `timestamp` (seconds) vs `timestampMilliseconds` | `floor(ms/1000) == timestamp` for **all 240** blocks |
| Monotonic | `timestamp` non-decreasing, `timestampMilliseconds` **strictly increasing** across all pairs |
| Granularity | Seconds in the header/EVM; 12 of 239 consecutive pairs **share the same second** (multiple blocks can have equal `block.timestamp`) |
| Inter-block time | min 900 ms, median 900 ms, p90 3.5 s, p99 8.3 s, **max 19.6 s**; mean about 1.7 s; seconds-histogram {0:12, 1:153, 2:43, 3:6, 4:9, 5:6, 6:2, 7:2, 8:3, 9:1, 11:1, 20:1}. Blocks are **not evenly spaced**; every sampled block contained 1-3 transactions (blocks appear to be produced when there is work). |
| Wall clock vs latest header | `local wall - head timestampMilliseconds` = **60 ms to 3.1 s** (12 samples). It includes request latency and the fact that `latest` is the last *executed* block. |

## T3. What the EVM sees (OBS)
`eth_call` with a state-override stub (`TIMESTAMP` opcode returned as a word; no deployment, no funds) at 8 historical blocks: **the opcode value equals the header `timestamp` in all 8**. At `latest` and `pending` it returned the current head's timestamp. So, replayed through the node, `block.timestamp` is the **header's second-resolution timestamp, not execution wall-clock time and not the transaction's submission time**.
**Limitation:** this is a replay by `eth_call` against a historical header, which is strong circumstantial evidence for what executed transactions saw, but I could not run our own transaction on Fuji (no funds) to confirm what an *original* asynchronous execution observed. Marked **observed for replay, UNVERIFIED for original execution**.

## T4. Deadline comparisons (contract-level, local EVM)
`Phase1Lab` implements `actUntil(d)` (allowed while `ts <= d`) and `actAfter(d)` (allowed once `ts > d`). On anvil with a chosen next-block timestamp (J8) and in forge with `vm.warp`:
| Case | Result |
|---|---|
| `actUntil` at `ts == d` | succeeds; event shows block timestamp equals `d` |
| `actAfter` at `ts == d` | **reverts** |
| `actUntil` at `ts == d+1` | reverts |
| `actAfter` at `ts == d+2` | succeeds |
Strict and non-strict comparisons are mutually exclusive at the boundary, as required to copy the Anchor rules (`now <= delivery_due_at` vs `now > delivery_due_at`, `now < review_due_at` vs `now >= review_due_at`) from [03](../03-target-architecture.md). PASS. Caveat: anvil lets the test choose the timestamp, so this proves **comparison semantics only**.

## T5. Transaction near a deadline (the Fuji question)
**INCONCLUSIVE** on Fuji (no own transactions). What follows from T2-T3 plus the documents, and is therefore an inference, not a measurement:
1. Whether a deadline-gated call succeeds depends on the **timestamp of the block in which the transaction is ordered**, which can be later than the moment the user clicked, because blocks can be up to ~20 s apart and the transaction waits for the next block.
2. Because several blocks can share a second, equality at the second boundary can hold for more than one block.
3. Execution is asynchronous, but the observed EVM time is the block's header time, so execution delay should not by itself change deadline outcomes (still to be confirmed on a real transaction in Phase 2/3 smoke tests).

## T6. Implications for FreelaX
- Deadlines in the contract are `block.timestamp` comparisons in **seconds**; never rely on milliseconds from the EVM, and never on `block.timestamp` being "now".
- Backend and UI must treat deadlines as *approximate by up to several seconds* near the boundary; the **release** path being permissionless after `review_due_at` is robust to this (any caller after the deadline works).
- Do not design flows where seconds matter (e.g. a revision window that closes exactly when a user submits). Where the Anchor rules have exclusive boundaries, keep them verbatim and test both sides (done at contract level here).
- Phase 3 gate: re-run a deadline-boundary test **on Fuji with real transactions** (submit calls with deadline = latest + N seconds) and record the included block's timestamp.

## Status
Header/timestamp behaviour measured: **PASS**. Contract comparison semantics: **PASS (local)**. Fuji inclusion-near-deadline and original-execution timestamp: **INCONCLUSIVE**.
