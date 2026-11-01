# Phase 1 - Transaction lifecycle experiments (critical)

Evidence: [java-local-experiments.json](evidence/java-local-experiments.json) (J2-J10), [java-fuji-fork-experiments.json](evidence/java-fuji-fork-experiments.json) (J13), [ws-observe-official.json](evidence/ws-observe-official.json), [finality-probe-official.json](evidence/finality-probe-official.json). Code: `prototypes/java-web3j/.../LocalEvmExperimentsTest.java`, `prototypes/ws-observer/*.mjs`.

## Honest scope statement (read first)
1. **No transaction of ours was ever broadcast successfully to Fuji** (no funded wallet, see [environment-setup.md](environment-setup.md)). Own-transaction timings (submit -> receipt -> finalized) were **not measured**.
2. Write-path experiments (revert, nonce, duplicate, replacement, timeouts) ran on **local anvil (Foundry 1.8.7, chain 31337)**. **Anvil is not Helicon**: it has an ordinary mempool and instant execution, so it cannot show the *accepted-but-unexecuted* state. Its results prove client/web3j behaviour and EVM-level semantics, not Avalanche acceptance semantics.
3. Helicon behaviour was studied **read-only on real Fuji**, observing other people's transactions through WebSocket `newAcceptedTransactions`, `newHeads`, HTTP lookups and the `finalized` tag, plus one rejected submission from an unfunded key.

## L1. The nine stages and how each was observed
| # | Stage | How observed | Where |
|---|---|---|---|
| 1 | constructed | `RawTransaction.createTransaction(...)` | Java |
| 2 | signed | `TransactionEncoder.signMessage`; **`keccak256(signed bytes)` known before sending and equal to the node's hash** (J2) | Java, anvil |
| 3 | submitted | `eth_sendRawTransaction` returned hash or JSON-RPC error | anvil; Fuji (rejection only) |
| 4 | accepted by network | Fuji WS `newAcceptedTransactions` fires with tx hash | Fuji (others' tx) |
| 5 | included in block | `eth_getTransactionByHash` has `blockNumber` | both |
| 6 | executed | receipt exists with `status` | both |
| 7 | receipt available | `eth_getTransactionReceipt` non-null | both |
| 8 | logs available | receipt logs; `eth_getLogs` | both |
| 9 | business finalized | `finalized` tag >= tx block, receipt status 1, logs decoded, contract state read matches | Fuji (timing only) |

## L2. Experiments (each: objective, procedure, result, status)
| ID | Objective / procedure | Actual result | Status |
|---|---|---|---|
| J3 | Successful tx + event decode: `setValue(7)`, decode `ValueSet` | receipt `0x1`, indexed + non-indexed values decoded, state read back = 7 | PASS (anvil) |
| J4 | Reverted tx: `boom(42)` forced in with explicit gas limit | **`eth_sendRawTransaction` accepted it** (no error); receipt **`status 0x0`**; `getTransactionByHash` found; logs on revert = 0; `eth_call` returns custom-error data (selector decoded manually); `eth_estimateGas` errors `execution reverted: custom error 0x...` | PASS (anvil). **Acceptance != success: proven.** |
| J5 | Nonce too low / duplicate after mined / insufficient funds | `nonce too low` (reuse), duplicate of mined tx -> `nonce too low`, zero-balance -> `Insufficient funds for gas * price + value` | PASS (anvil) |
| J5b | Duplicate while pending (automine off) | `transaction already imported`; tx **found by hash with `blockNumber` null, receipt null**; `latest` nonce 1 vs `pending` nonce 2 while pending; receipt appears after mining | PASS (anvil mempool only; **not** the Helicon accepted state) |
| J6 | Nonce gap + replacement | nonce-gap tx **accepted and visible by hash** (queued); same-fee replacement -> `replacement transaction underpriced`; 3x fee bump accepted; **original disappears from by-hash**, never gets a receipt; gap filler mined, bumped replacement mined (`0x1`), final nonce 2 | PASS (anvil) |
| J7 | RPC timeout **after** submission (proxy forwards the send, then holds the response; client timeout 2 s) | client got `InterruptedIOException: timeout`; **tx was mined anyway**; found by the locally precomputed hash; rebroadcast of the same raw tx -> `nonce too low`; nonce advanced exactly 1 (no double execution) | PASS (simulated fault) |
| J7b | Temporary RPC failure/recovery (proxy 503 x2) | sequence: timeout, `ClientConnectionException`, then OK, OK | PASS (simulated fault) |
| J10 | Naive nonce read race (two readers of `pending`) | same nonce read twice; second send `nonce too low` | PASS (shows need for a DB-backed allocator) |
| J13 | Fuji: unfunded signed tx; wrong chain id; unknown hash | `-32000 insufficient funds ... balance 0, tx cost 1.8e15`; `-32000 invalid sender`; unknown hash/receipt -> `null` | PASS (real Fuji, nothing executed) |
| F-a | Fuji, 53 txs by others (90 s): by-hash + receipt right after the acceptance event | by-hash found 52/53; **receipt present on the first query 53/53**; 2 had receipt `status 0x0` (natural reverts, with by-hash found) | OBS |
| F-b | Fuji, 14 txs: acceptance -> receipt -> finalized | receipt present on first query 14/14; **acceptance to `finalized` >= tx block: min 5.1 s, median 5.5 s, max 7.8 s** (earlier run: 5.4 / 6.2 / 8.7 s); `latest` was 4-5 blocks ahead of `finalized` | OBS (one vantage, one host, off-peak/unknown load) |
| F-c | `newHeads` vs `newAcceptedTransactions` (53 txs, same block number) | newHeads event arrived **0-18 ms (median 3 ms) after** the acceptance event for the same block, i.e. **`newHeads` appears to fire at acceptance, not later at execution** | OBS; closes the Phase 0 question for this endpoint |
| F-d | `eth_getLogs` right after acceptance vs after receipt (same filter, address = tx.to) | identical counts 14/14 | OBS (see limits) |

## L3. Accepted-but-unexecuted: can it be observed?
**Not reproduced; not fabricated.** Reasons and what is known:
- We cannot send our own Fuji transactions (no funds), and the window must be observed per transaction.
- Official docs: *receipt and state reads wait for an accepted block to execute*; `eth_getLogs` and filters *do not wait* (unexecuted block gives no logs, or an error if the filter has no address/topic) ([C-Chain RPC page](https://build.avax.network/docs/rpcs/c-chain)). If that is accurate, a public RPC **would not return a null receipt for an accepted transaction**: the call would block until execution. Our observation is consistent (receipts on first query for 67/67 observed txs; acceptance-to-receipt at most 1.2 s including our own earlier calls) but **cannot distinguish "blocked briefly" from "never lagging"**.
- Same acceptance-vs-execution gap in logs: with one filter, results before and after the receipt were identical (14/14). However the early query ran 0.2-0.65 s after the acceptance event, after `by-hash` had answered, so it may already have been post-execution. **INCONCLUSIVE**, and the documented rule (no inference from empty `getLogs` near head) stays.
- **Available RPC evidence** of acceptance: WS `newAcceptedTransactions`; `eth_getTransactionByHash` with `blockNumber`; block `latest` (last executed) vs `pending` (executed by default) vs `finalized`/`safe` (settled). **Not inferable:** whether an accepted tx will succeed; whether a null by-hash means "unknown" or "lagging provider" (one real by-hash miss observed in 53, [fuji-network-validation.md](fuji-network-validation.md) F9); whether `getLogs` is complete near head.
- Status: **INCONCLUSIVE** for observing the state itself. The model below is built to be correct whether or not it is observable.

## L4. Conservative backend state model (for the future Java module)
Keep the existing persisted statuses (`PENDING`, `PROCESSING`, `UNKNOWN`, `CONFIRMED`, `FAILED`); the observations below are derived inside the Avalanche module (no new enum). Rules marked * are directly evidenced above; others are recommendations.

| Observation (from RPC) | Meaning | Handling |
|---|---|---|
| Before sending: persist `chainId, signer, nonce, rawTx, txHash`* | stage 1-2 | `PENDING` |
| Send returns hash | submitted; **not proof** | `PENDING` |
| Send error `already known/imported`* | idempotent resubmission | stay `PENDING` |
| Send error `nonce too low`* | nonce consumed: by this tx (e.g. after a timeout) or by another | look up *our* hash; found -> follow it; not found and our nonce consumed -> replaced, this attempt is definitively failed |
| Send error `insufficient funds`*, `invalid sender`* (wrong chain), `underpriced`* | rejected at admission; not on chain | attempt failed; fix and retry with the same nonce policy; verify by-hash is null first |
| Send timeout / 5xx / connection error* | **unknown**: tx may be accepted* | `UNKNOWN`; never re-sign with a new nonce; poll by hash; same raw tx may be re-broadcast safely* |
| by-hash null **and** receipt null | unknown to this endpoint (not accepted yet, dropped, or provider lag) | `UNKNOWN`; ask a second endpoint; do not treat as failure; keep polling for a configured window |
| by-hash found, `blockNumber` null (pool) / found, receipt not yet there | pending or accepted-not-executed | `PROCESSING`; re-query only |
| receipt `status 0x1` | executed OK | `PROCESSING` until `finalized` >= block (measured ~5-9 s after acceptance) |
| receipt `status 0x0`* | executed and reverted; **definitive** (gas spent, nonce consumed) | `FAILED`, decode custom error where possible |
| receipt OK + `finalized` >= block + logs filtered by `address == contract` decoded by `(txHash, logIndex)` + `eth_call` state matches | stage 9 | `CONFIRMED` then business step |
| nonce check (`latest`/`finalized`) > our nonce, hash != ours* | replaced | definitive failure of this tx; compensate/rebuild |
| empty `eth_getLogs` near head | **no information** | never used as absence evidence |
| by-hash null while receipt exists* | provider inconsistency | trust the receipt |
Rules carried from Solana, adapted: keep **UNKNOWN + re-query + idempotency + reconciliation + duplicate protection**. **Do not** copy blockhash-expiry reasoning ("not found after expiry is safe"): on EVM the safety argument is **nonce + tx hash** (a nonce can only be consumed once; an unexecuted tx stays valid indefinitely unless replaced). A persisted raw tx plus nonce reservation is the EVM analogue of a deterministic signature.
Restart safety: all inputs above are persisted before send; the reconciliation job (existing scheduler pattern) resumes from `PENDING/UNKNOWN/PROCESSING` rows after a restart.

## L5. Limits
Anvil semantics differ from Avalanche on admission, replacement price rules and mempool; the bump factor needed for replacement on Avalanche is **not** measured (3x worked on anvil). Fuji: single host vantage, unknown load, 67 observed txs, no own txs, no reorg/finality-failure observation (none expected after acceptance per Snowman, not tested). Acceptance->receipt latencies include our own sequential calls.

## Status
Lifecycle mapping and client behaviour: **PASS**. Fuji-native submit -> receipt -> finalized for our own tx and accepted-but-unexecuted observability: **INCONCLUSIVE / BLOCKED (no funds)**.

---
# Part B update (2026-11-01)

Separation used throughout: **FUJI-OBS** = directly observed on real Fuji (read-only); **LOCAL-SIM** = local anvil / fault proxy / local Fuji fork; **BLOCKED** = needs a funded Fuji wallet. Nothing in LOCAL-SIM is evidence of Fuji behaviour.

## B1. Lifecycle prototype (LOCAL-SIM, `prototypes/java-web3j/.../lifecycle/`)
`Lifecycle.java`, `FaultProxy.java`, `LifecycleScenariosTest.java`; evidence [lifecycle-prototype.json](evidence/lifecycle-prototype.json). States: CREATED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, UNKNOWN (+ REPLACED, added because "nonce consumed by a different transaction" is a definitive outcome for that attempt and none of the seven fits). Isolated; nothing replaces an existing FreelaX service.
| Scenario | Principle shown | Result |
|---|---|---|
| S1 happy path | accepted ack = SUBMITTED only; CONFIRMED at receipt status 1; FINALIZED after the finality policy; same operation key returns the same record (1 send) | PASS |
| S2 reverted | receipt status 0 -> **REVERTED** (never CONFIRMED), terminal | PASS |
| S3 timeout after submit | client timeout -> **UNKNOWN**; impatient retry of the same business operation does **not** send again (proxy saw exactly 1 `eth_sendRawTransaction`); reconcile by hash -> CONFIRMED; nonce advanced once | PASS |
| S4 restart | state persisted before send; a new process (new objects, file only) reconciles UNKNOWN -> CONFIRMED | PASS |
| S5 null / throttled provider | both lookups `null` -> stays UNKNOWN (not failure, not replaced); an HTML **HTTP 429** body -> state unchanged, `lastError` recorded; recovery -> CONFIRMED | PASS |
| S6 replaced nonce | same nonce reused by a higher-fee tx (automine off, then mined) -> **REPLACED** only after receipt null + by-hash null + `latest` nonce > ours | PASS |
| S7 duplicate / missed hints | hints delivered 3x and a hint for an unknown hash change nothing; a transaction whose hint never arrives is still reconciled by the periodic pass | PASS |
| S8 environment isolation | a reconciler configured for another environment/chain id leaves the record untouched | PASS |
Design points that the tests enforce: operation-key idempotency (business level, not just nonce); raw tx + hash persisted before send; a failed lookup is never evidence; event hints only trigger reconciliation; no blockhash-expiry reasoning (only hash + nonce). Finality policy is injected: on anvil the `finalized` tag **did not reach the receipt block within 40 s** (LIVE script), so local runs use confirmation depth while Fuji uses the `finalized` tag (FUJI-OBS: about 5 to 9 s).
Limits: single-process nonce allocator (the Phase 2 design needs a DB-backed allocator with locking); JSON-file ledger stands in for the database; the finalized/`safe` handling of a reorg was not simulated.

## B2. Fuji transaction experiments: BLOCKED, runbook and validated script
No funded wallet is obtainable without interactive faucet use ([fuji-funding-runbook.md](fuji-funding-runbook.md)). A reusable script exists (`LiveExperimentsTest`) with built-in environment guards, and it was **validated on LOCAL-SIM** (plain anvil and a local Fuji fork): [evidence/live/](evidence/live/). Observations from those local runs (anvil only): the reverted transaction was accepted and executed with status 0; an immediate resend returned `transaction already imported`, a resend after mining `nonce too low`; replacement at equal fee was `underpriced`, **5 %** accepted; a held send response (client timeout) still mined exactly once; with the next block's timestamp following wall clock, `actUntil(head+0)` reverted (included 4 s later) while `k >= 1` succeeded. **All of the following remain BLOCKED for real Fuji:** own-transaction timings, revert, nonce gap and replacement threshold on Avalanche, timeout after submission, duplicate submission, near-deadline inclusion.

## B3. Lookup inconsistency and receipts (FUJI-OBS)
Cumulative: 53 txs (Part A) saw **1** by-hash miss with a receipt present; a later 14-tx run saw 0 misses; Part B ran 8 txs against **two** providers (official and publicnode) at three nominal offsets: **0 disagreements, by-hash found and receipt present in all 48 samples** ([consistency-probe-fuji.json](evidence/consistency-probe-fuji.json); the paced client may have delayed the first query by up to a few seconds, so offsets are nominal). Conclusion unchanged and still INCONCLUSIVE about cause: **a by-hash miss is not proof of absence**; consult the receipt and a second provider before deciding; keep UNKNOWN.

## B4. Finalized-block scanning and restart-safe reconciliation (FUJI-OBS, read-only)
`cursor_scan.mjs`: 3,001 blocks of USDC `Transfer` logs scanned on the official RPC in 100-block chunks with a persisted cursor and unique `(txHash, logIndex)` keys, **a simulated crash after 12 chunks and a restart**, one chunk deliberately replayed: 31 chunks, 1 restart, **1 duplicate suppressed**, 41 events recorded; an independent single call to a different provider (publicnode) returned the same **41** events: **0 missing, 0 extra** ([cursor-scan-fuji.json](evidence/cursor-scan-fuji.json)). During this the official endpoint answered **HTTP 429** (HTML body, `Retry-After`) and the helper honoured it and finished. Limits: one window, one small contract, off-peak.

## B5. Status of the lifecycle criteria
| Criterion | Status |
|---|---|
| Accepted != success, receipt status 0 = REVERTED, timeout != failure, hash+nonce reconciliation, operation-level idempotency, hints are not truth, recovery from missed/duplicated events | **PASS** (LOCAL-SIM prototype; restart/duplicate scan also FUJI-OBS read-only) |
| Own Fuji transactions (timings, revert, nonce gap/replacement, timeout, duplicate) | **BLOCKED** (funding) |
| Accepted-but-unexecuted state on Fuji | **INCONCLUSIVE** (unchanged) |
| Provider lookup inconsistency cause | **INCONCLUSIVE** |
