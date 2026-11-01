# P2-M3 validation report: chain client, nonce allocator, transaction tracker

Date: 2026-11-01. Scope: [SIMULATION_SCOPE](../../SIMULATION_SCOPE.md). Scope source: [03 backend plan, P2-M3](03-backend-implementation-plan.md), [06 lifecycle plan](06-transaction-lifecycle-and-reconciliation-plan.md), [01 persistence](01-target-module-architecture.md), owner decisions O-16, O-17, O-18 and the M2 rule "development signers only in LOCAL_SIMULATION".
Commands run with `JAVA_HOME` set per process to JDK 21; Maven 3.9.16 from the local wrapper distribution. All chain activity was on a local Anvil (Docker). **No Fuji request, signature or transaction, no Mainnet contact, no real funds, no KMS.**
Note on the task's file list: several named files (`08-implementation-milestones.md`, `04-backend-integration.md`, ...) do not exist; the real documents listed above were used.

## 1. What M3 delivers
| Plan item | Implementation |
|---|---|
| Chain client | `ChainClient` extended: pending/latest nonce, block number, finalized block, base fee, `eth_sendRawTransaction` (RPC error returned as a message, transport errors thrown), receipt and by-hash lookups (null becomes empty), all paced, web3j types kept inside `chain` |
| `NonceAllocator` | row lock per `(environment, chain_id, signer_address)`; the allocation and the CREATED record are one database transaction, so a nonce is consumed if and only if its record exists; the node's pending nonce is only a lower bound |
| `TxRepository` | plain JDBC, every query scoped by environment and chain id, optimistic `version` updates |
| `ErrorClassifier` | RPC messages: already known, nonce too low, underpriced, insufficient funds, permanent rejection, unclassified; transport: HTTP 429/rate limit versus other |
| `SubmissionService` | `submit(operationKey, role, to, data, gas)`: validation, idempotency by operation key, fees capped, CREATED, SIGN, **persist SIGNED (raw tx and hash) before any send**, send; `speedUp` (same nonce, higher fees, earlier hashes kept) |
| `TxTracker` + optional `TxReconcileScheduler` | the transition table of plan 06 with the O-17 states; finality policy (confirmations on LOCAL_SIMULATION, `finalized` tag code path for Fuji) |
| Flyway `V1__avalanche_gateway.sql` | `avalanche_nonce`, `avalanche_tx` (scan cursor, event and deployment tables belong to M4/M7) |
| Spring wiring | `PersistenceConfig` and `TxConfig`, **opt-in** (`avalanche.tx.enabled`, default false); Spring Boot's DataSource/Flyway auto-configuration is excluded, so M1/M2 startup needs no database |
| Database guard | `DbEndpointGuard`: only `jdbc:mysql://` to a local host and only the database named `avalanche_gateway` |

## 2. Transaction flow
1. `submit` validates the request before touching the database (key `[A-Za-z0-9:_.-]{1,190}`, 20-byte address, whole-byte hex data, gas between 21000 and `maxGasLimit`; value is always zero).
2. Same key again: returns the existing record (a different role, target, data or gas is `IDEMPOTENCY_CONFLICT`); a new nonce is never allocated.
3. New key: node pending nonce (floor) and fees are read, then one database transaction allocates the nonce and inserts the record as **CREATED**. A lock conflict rolls back everything and retries (no nonce burned).
4. The EIP-1559 transaction is signed with the role's development signer, bound to the signer's verified chain id; the raw bytes and hash are stored as **SIGNED**.
5. Only then is the raw transaction sent. Accepted becomes **SUBMITTED**; "already known" is also SUBMITTED; nonce too low becomes **UNKNOWN**; underpriced or insufficient funds keep SIGNED with the error recorded (retry with the same nonce); a transport error, timeout or HTTP 429 becomes **UNKNOWN** and is never retried with a new nonce; a permanent rejection becomes **FAILED** and flags a nonce gap.
6. The tracker looks up every known hash of the nonce: receipt status 1 and the expected `to` is **CONFIRMED**; status 0 is **REVERTED**; by-hash found without receipt is **PENDING**; CONFIRMED becomes **FINALIZED** when `latest >= block + confirmations` (LOCAL) or the `finalized` block reaches it (Fuji code path).
7. Null receipt and null by-hash prove nothing. They change the state only as follows: nonce not consumed and grace elapsed means rebroadcast the same raw transaction (same hash); nonce consumed, grace elapsed and repeated observations means **REPLACED**; otherwise unchanged. A lookup error or HTTP 429 leaves the state unchanged and records the error class.
8. A record of another environment or chain is never read, changed or reconciled.

## 3. Security boundaries
- No submission path exists in Fuji: `SubmissionService` is built only with the M2 signer registry and its constructor refuses any environment other than LOCAL_SIMULATION (`SIGNER_FORBIDDEN_ENVIRONMENT`). In Fuji only the read-only tracker could exist, and no signer does.
- A transaction is signed only for the signer's verified chain id and address; a mismatch throws. Gas limit, max fee and tip are capped; a replacement above the fee cap is refused.
- The persisted raw transaction is public data; no key is stored or logged. A permanent rejection or a refused signature flags a nonce gap and **blocks further allocation for that signer** (fail closed).
- The database URL must be local and named `avalanche_gateway`: the gateway cannot be pointed at the marketplace, payment, MISA or Solana databases or at a remote server. No `.env` was read; credentials come from `AVALANCHE_DB_*` placeholders that default to blank.
- `SubmissionService` has no REST endpoint (M8) and performs **no business authorization**: it signs what its caller asks. M8/M9 must authorize operations, and M7's deployment manifest should supply a target allow-list.

## 4. Phase 1 scenarios S1-S8 on a real Anvil and a real MySQL (Testcontainers)
| Scenario | Test | Result |
|---|---|---|
| S1 happy path, persist-before-send, idempotency, FINALIZED only after the policy | `s1_happyPath_...` (the row existed in state SIGNED with the identical raw bytes when the node received it; exactly one `eth_sendRawTransaction`) | pass |
| S2 reverted | `s2_receiptStatusZeroIsRevertedAndNeverConfirmed` | pass |
| S3 timeout after submit (response held until the client times out) | `s3_timeoutAfterSubmitIsUnknown_...` (UNKNOWN, retry sends nothing, one send seen by the node, reconcile by hash gives CONFIRMED, nonce advanced once) | pass |
| S4 restart | `s4_...` (new objects over the same database: UNKNOWN reconciled; SIGNED row resent with the same hash; CREATED row signed and sent with the same nonce; CREATED row of a vanished ephemeral signer becomes FAILED without a gap) | pass |
| S5 null lookups and HTTP 429 | `s5_nullLookupsAndHttp429...` (state unchanged, error class RATE_LIMITED recorded, recovery to CONFIRMED) | pass |
| S6 replaced nonce | `s6_replacedOnlyAfter...` (rebroadcast refused as underpriced, first observation not enough, REPLACED on the second) | pass |
| S7 duplicate or missed hints | the WebSocket hint part belongs to M4 (`WsHintListener`); M3 proves a periodic pass reconciles without any hint: `TxSpringWiringTest` (scheduler confirms on its own) | M3 part pass; hint part deferred to M4 |
| S8 environment isolation | `s8_recordsOfAnotherEnvironment...` (a Fuji-labelled row is invisible and untouched) | pass |
Additional: concurrency (16 threads, 40 distinct keys plus 10 duplicates of one key: no nonce reused, nonces contiguous from zero, duplicates resolve to one record, every transaction mined), speed-up (same nonce, earlier hash kept, whichever mines is confirmed), an underpriced replacement is not a failure and the original still confirms, insufficient funds keeps the nonce and is resent after funding, permanent rejection is FAILED with a nonce gap that blocks that signer only, request validation and caps consume no nonce, idempotency conflict sends nothing, no signer means no submission.

## 5. Test results
| Suite | Result |
|---|---|
| `avalanche-integration` default build (JDK 21, Docker available) | **71 run, 69 passed, 0 failed, 2 skipped** (the optional Anvil and Fuji tests). M2 had 43; M3 added 28: `TxUnitTest` 8, `TxDbGuardContextTest` 3, `TxLifecycleTest` 16, `TxSpringWiringTest` 1 |
| Repeats of the Testcontainers classes | 2 further runs, 17/17 each (no flakiness seen) |
| M1 and M2 acceptance tests | unchanged and passing inside the 71 |
| Solana baseline (same commands as M1/M2) | marketplace 6/6 plus gateway 10/10 = 16/16 combined, unchanged; reactor compile success; JDK 17 reactor still has the original 4 modules and the existing modules compile on JDK 17 |
| JDK 21 image (`Dockerfile.avalanche`) with signers, tx tracking and a throwaway MySQL | started; Flyway migrated; tables `avalanche_nonce`, `avalanche_tx`; signers created; wrong database name and missing database settings both refused at startup; no key or password string in the logs |
Bugs found by the tests and fixed in code: (a) two first-time nonce allocators deadlocked (gap lock on `SELECT ... FOR UPDATE` of a missing row): the row is now upserted first and a lock conflict retries a whole transaction; (b) Anvil's own wording for an over-limit gas limit (`intrinsic gas too high -- tx.gas_limit > env.block.gas_limit`) was unclassified, so it became UNKNOWN (the safe default); it is now a permanent rejection.
Facts learned about Anvil (LOCAL_SIMULATION only, not Fuji): automine is asynchronous by a few milliseconds; any strictly higher fee replaces a pending transaction (the equal-fee case is "replacement transaction underpriced"); intrinsic gas is not enforced at admission.

## 6. Deferred items status
- **O-16 `CREATE DATABASE avalanche_gateway` in `init-db.sql`: completed in M3** (one additive line; it only runs on a fresh MySQL volume and creates an empty database).
- O-18: Testcontainers for tests, runtime database separately provisioned and guarded: implemented.
- Compose: opt-in variables for signers, tx and the three database settings (blank defaults, no root credentials) on the Avalanche service only; `.env.example` placeholders.

## 7. Limitations and unresolved items
1. **Fuji-dependent parts remain unmeasured (blocked: no funded wallet and, by owner rule, no backend Fuji signer):** the fee-bump threshold, own-transaction timings, near-deadline inclusion and the `finalized`-tag finality path were not exercised against Fuji (only identity calls are authorized there). Fee, bump, tip, grace and observation defaults (30 %, 1 gwei, 200 gwei cap, 30 s, 2) are conservative placeholders marked "to calibrate". **Decision still needed before M7 and the Fuji parts of M12:** how Fuji deployment and smoke transactions are signed.
2. **No second-provider cross-check before REPLACED** (plan 06, last row of the transition table): M3 relies on grace plus repeated observations. A provider that misses both lookups while the nonce is consumed could still mislead it. The same-environment second provider and provider profiles belong with M4.
3. **No operator procedure to clear a nonce gap.** After a permanent rejection the affected signer is blocked until the process restarts (ephemeral signers get new addresses). A resolution step is not designed; it is not needed for ephemeral LOCAL_SIMULATION keys but must be decided if signers ever persist.
4. **Business authorization and target allow-list are not in M3** (see section 3).
5. Contract creation (`to` empty) and non-zero value are not supported; deployment (M7) must extend the submission path under its own review.
6. The Testcontainers tests need Docker. They are annotated to skip when Docker is unavailable, but I could not simulate an unavailable Docker on this machine (Testcontainers kept using its cached Docker Desktop strategy), so that skip behaviour is **unverified**. A Docker-capable runner is part of the CI follow-up (there is still no CI configuration in the repository).
7. The tracker's `latest nonce > record nonce` signal cannot tell "our transaction mined" from "another transaction took the nonce"; both lookups returning null is what distinguishes them, hence the observation count.
8. Phase 1 stays PARTIAL; its evidence and the Solana baseline are untouched.
