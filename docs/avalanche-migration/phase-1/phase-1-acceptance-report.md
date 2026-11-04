# Phase 1 - Acceptance report

> **Scope amendment (2026-11-01):** FreelaX is a development simulation with no real funds ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)); Mainnet is out of scope. Mainnet probes in this phase were read-only and historical; no further Mainnet contact (including a Mainnet fork test) is planned. Guard evaluation: [simulation-environment-guards.md](simulation-environment-guards.md).

> **Part B update:** the classification below is the first-pass result. The **final, authoritative classification after Part B is in the last section** ([Part B final acceptance](#part-b-final-acceptance-2026-11-01)); where they differ, the last section wins.

Date: 2026-11-01. Base: `master` @ `1662a80`. Status keys: **PASS** (evidence recorded), **PARTIAL**, **INCONCLUSIVE** (could not be established), **FAIL**, **BLOCKED** (needs something unavailable).
**Overall: PARTIAL. Recommendation: resolve a short list of blockers before Phase 2** (section 6).

## 1. Acceptance criteria
### 1a. Roadmap Phase 1 criterion ([04](../04-migration-roadmap.md), Phase 1 item 13) - the stricter, original criteria
| Criterion | Status | Evidence / reason |
|---|---|---|
| All facts verified from two sources or marked unverifiable | **PARTIAL** | Chain ids, RPC, USDC identity/behaviour, EVM fork, log limits, `newHeads`: verified by observation (+ docs where they exist). USDC address has **one** official source (Circle). Unverified and marked: rate limits, USDC.e official address, Mainnet EVM/permit behaviour, ACP-283 status, FiatToken version, explorer verification flow, faucet limits |
| Faucet-funded wallet can call USDC `balanceOf` | **FAIL / BLOCKED** | No funded wallet (interactive faucets). `balanceOf` works on any address (read-only, real Fuji, via web3j and Foundry). |
| Fuji: time from submission to receipt and to `finalized` | **INCONCLUSIVE (own tx)** | Measured for others' txs only: acceptance -> receipt <= 1.2 s incl. our calls; acceptance -> finalized 5.1-8.7 s ([lifecycle](transaction-lifecycle-experiments.md)) |
| Fuji: `eth_getLogs` before execution | **INCONCLUSIVE** | Same-filter results before/after receipt identical 14/14, but the early query may already have been post-execution |
| Fuji: accepted-but-unexecuted visible via `eth_getTransactionByHash` | **INCONCLUSIVE** | Not observed; docs say receipt reads wait for execution; one by-hash miss in 53 |
| Fuji: `block.timestamp` semantics | **PARTIAL** | Header/ms/opcode-replay measured; near-deadline inclusion and original execution not testable without funds |
| No signer or key-management code written in this phase | **PASS with documented discrepancy** | No production signer, KMS client, role or key store exists. The **directive required** local signing with ephemeral keys, so test-only signing code exists in the isolated prototypes (web3j `Credentials`; two trivially-guessable Foundry test keys `0x5151`, `0xA11CE5` used only with `vm.sign`). The stricter reading of the roadmap is met in spirit (no signer module) and the discrepancy is recorded here. |

### 1b. Directive objectives
| # | Objective | Status |
|---|---|---|
| 1 | Isolated Avalanche dev environment | **PASS** (Docker-pinned Foundry; all under `phase-1/`) |
| 2 | Fuji connectivity and network config | **PASS** |
| 3 | Native USDC contract info | **PASS** (Fuji; Mainnet read-only; USDC.e official address unverified) |
| 4 | Java 21 / Boot 3.5.16 / web3j | **PASS with mandatory workaround**; Java 17 and Boot-web coexistence INCONCLUSIVE |
| 5 | Solidity, Foundry, OpenZeppelin tooling | **PASS** (Hardhat not run; Fuji deployment and explorer verification not done) |
| 6 | Minimal standalone prototypes | **PASS** (14 Java tests, 13 Foundry tests, 3 Node probes) |
| 7 | EVM transaction lifecycle incl. accepted-but-unexecuted | **PARTIAL**: client/EVM behaviour PASS on anvil; Helicon state INCONCLUSIVE |
| 8 | EIP-712 and domain separation | **PASS** (wallet-side signing and KMS UNVERIFIED) |
| 9 | `block.timestamp` and deadlines | **PARTIAL** |
| 10 | RPC reliability, limits, logs, reconciliation | **PASS** for limits/strategy; rate limits, WS reconnect, duplicate delivery INCONCLUSIVE |
| 11 | Reproducible evidence | **PASS** (`scripts/run-all.sh`, `evidence/`) |
| 12 | Documentation and honest backlog statuses | **PASS** ([06](../06-implementation-backlog.md) updated: 5 and 6 done, 1 and 7 partial, 2, 3, 4 not done) |

## 2. Failed and inconclusive experiments (complete list)
| Item | Type | Reason |
|---|---|---|
| Phase 0 hypothesis: 2,000-block log chunks | **FAILED (hypothesis)** | official Fuji RPC allows 100 |
| Phase 0 claim: USDC `name()` is "USDC Coin" | **FAILED (fact)** | actual "USD Coin" |
| web3j 6.1.0 out-of-the-box on Boot 3.5.16 | **FAILED, fixed** | Jackson 3 vs annotations 2.21 |
| Own Fuji transactions (success, revert, nonce gap, replacement, near-deadline, timings) | BLOCKED | no funded wallet |
| Accepted-but-unexecuted observation | INCONCLUSIVE | see lifecycle L3 |
| `eth_getLogs` before execution | INCONCLUSIVE | observation window |
| Fuji deployment of the lab contract, explorer verification | NOT PERFORMED | no funds; not required for any question |
| Replacement fee-bump factor on Avalanche | INCONCLUSIVE | measured on anvil only (3x worked) |
| Public RPC rate limits, WS reconnect/duplicates, historical depth | INCONCLUSIVE | not stress-tested |
| Mainnet permit/EIP-3009/EVM-fork behaviour, USDC blacklist/pause writes | UNVERIFIED | Mainnet probes were read-only; privileged roles unavailable |
| KMS signing, Vault, wallet-side `eth_signTypedData_v4` | UNVERIFIED | no accounts / no browser wallet |
| `cast hash-typed-data` digest compare | script error | covered indirectly by identical signature |
| Slither on the real escrow, fuzz/invariants | NOT APPLICABLE | Phase 3 |
| Read-only SQL rail evidence (backlog 2) | NOT DONE | needs DB operator |
| Java 17 with web3j 6.1.0; Boot web-context Jackson coexistence | INCONCLUSIVE | not run |

## 3. Remaining technical unknowns
Public-endpoint rate limits; behaviour of a Helicon-accepted transaction before execution; what an *original* asynchronous execution sees as `block.timestamp`; replacement bump rule on Avalanche; Mainnet-specific behaviour (EVM fork, USDC implementation); KMS signature conversion; Jackson 2.22 + databind 2.21 in a web context; WebSocket reconnect/duplicate semantics; web3j wrapper generation.

## 4. Remaining business/architecture decisions (not closed by this phase)
All items in [07](../07-open-questions.md) questions 1-18 remain as recorded (Phase 0 decision register D1-D4 untouched): arbiter model, revision co-sign, 72h parameter, escrow ambiguities, USDC blacklist payout, mutual refund mechanism, gas payer, multisig membership, rail literal, legacy payout path, in-flight Solana escrows, on-ramp. New items surfaced: module Java level (17 vs 21), RPC provider(s), KMS vendor, whether to accept the 100-block scanning default or buy a provider with larger ranges.

## 5. Safety and scope verification
- Tracked files modified: only [04](../04-migration-roadmap.md), [06](../06-implementation-backlog.md), [07](../07-open-questions.md) (additive status text; decision register untouched). All other changes are new files under `docs/avalanche-migration/phase-1/`.
- No production source, configuration, Flyway migration, dependency, Solana, frontend or auth file was modified. `.idea/` and `.serena/` untouched. No commit, no push.
- No private key or secret in files or logs (scan: only bytecode, public constants, digests, signatures; the EIP-712 interop key lived in the OS temp dir and was deleted). One-time nested `.git` accidentally created by `git init` in the Foundry prototype was removed.
- No mainnet writes; no Fuji writes except one signed transaction from an unfunded ephemeral key that the node rejected (nothing executed, nothing spent).
- Build artefacts (`lib/`, `out/`, `cache/`, `target/`) are git-ignored by local `.gitignore` files.
- Documentation links checked (0 broken). Disposable Docker containers removed.

## 6. Recommendation
**Resolve blockers first, then proceed to Phase 2.** Phase 2 module work (RPC client, nonce allocator, receipt mapping) is not technically blocked, but its acceptance depends on facts Phase 1 could not measure. Minimum list, estimated as a short session:
1. Obtain Fuji AVAX and Circle test USDC for one ephemeral wallet (interactive faucets; needs the operator) and run the blocked Fuji experiments: submit -> receipt -> finalized timings, revert, nonce gap/replacement bump, deadline-boundary calls with included block timestamps, accepted-state observation with own hashes. A guarded live test class can reuse the existing prototype helpers.
2. Decide the module Java level and confirm web3j under a Boot web context with the Jackson override.
3. Choose RPC provider(s) and KMS vendor (decisions, not experiments).
4. Optionally repeat the USDC fork test against a Mainnet fork.
Phase 2 must also keep a CI smoke test that performs a real web3j call.
**Stop here: Phase 2 is not started; explicit approval is required.**

---
# Part B final acceptance (2026-11-01)

Classification keys: **PASS** reproducible evidence recorded; **FAIL** tested and did not hold; **BLOCKED** needs a human, a credential or an unavailable resource (not a negative result); **INCONCLUSIVE** tested but the evidence cannot decide. Scope: LOCAL_SIMULATION and AVALANCHE_FUJI only; Mainnet is out of scope ([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md)). "FUJI-OBS" = observed on real Fuji (read-only); "LOCAL-SIM" = local anvil / fault proxy / local fork.

| # | Criterion | Final status | Basis |
|---|---|---|---|
| 1 | Isolated environment, reproducible scripts | **PASS** | `scripts/run-all.sh`, Docker-pinned Foundry, all under `phase-1/` |
| 2 | Fuji connectivity, chain id 43113, required RPC methods | **PASS** | FUJI-OBS, two providers (Part A) |
| 3 | Native USDC identity and behaviour on Fuji; USDC vs USDC.e distinction | **PASS** | rechecked Part B; permit and EIP-3009 executed on a **local fork** of Fuji; USDC.e has no official source (UNVERIFIED address) |
| 4 | Mainnet USDC behaviour | **NOT IN SCOPE** | Mainnet fork test dropped by owner direction |
| 5 | Java 21 + Spring Boot 3.5.16 + web3j 6.1.0 in a real web context | **PASS with mandatory override** | `jackson-annotations:2.22`; original failure reproduced (HTTP 500) under Boot's and FreelaX's own Jackson pins |
| 6 | Java 17 compatibility | **PASS only with web3j 4.13.0; FAIL for web3j 6.1.0** | 6.1.0 bytecode is Java 21 (class version 65); 31-test suite passes on Java 17 + 4.13.0 |
| 7 | Solidity/Foundry/OpenZeppelin toolchain | **PASS** | 9 unit + 7 fork tests; Slither 0.11.6 |
| 8 | EIP-712 (digest equality, interop, replay, expiry, wrong chain/contract) | **PASS** | web3j = contract = Foundry `cast` |
| 9 | DER to `r,s,v` conversion, low-s, recovery parity, EIP-712/EIP-1559 digests | **PASS (local cryptographic validation only)** | fake KMS, 300 signatures, on-chain acceptance on anvil |
| 10 | Real KMS provider compatibility | **NOT APPLICABLE (deferred by scope)** | KMS recommended to be deferred; owner confirmation OPEN |
| 11 | Role separation and rotation (lab contract) | **PASS** | Foundry negative tests |
| 12 | Lifecycle principles (accepted != success, REVERTED on status 0, timeout != failure, hash+nonce reconciliation, operation-level idempotency, hints are not truth, recovery from missed/duplicate events) | **PASS (LOCAL-SIM prototype, 8 scenarios)** | restart/duplicate scan also FUJI-OBS |
| 13 | Restart-safe finalized-cursor scanning and duplicate suppression | **PASS** | FUJI-OBS read-only; equal to an independent provider (41 of 41) |
| 14 | Own Fuji transactions: success timing, revert, nonce gap/replacement threshold, timeout, duplicate, near-deadline inclusion | **BLOCKED** | no funded wallet; faucets are interactive; see [runbook](fuji-funding-runbook.md); script validated locally |
| 15 | Accepted-but-unexecuted state on Fuji | **INCONCLUSIVE** | not observable with available means |
| 16 | `eth_getLogs` before execution on Fuji | **INCONCLUSIVE** | observation window too coarse |
| 17 | `block.timestamp`: header/opcode behaviour | **PASS** | Part A, 240 blocks, 8 replayed samples |
| 18 | `block.timestamp`: original-execution timestamp, Fuji near-deadline inclusion | **BLOCKED / INCONCLUSIVE** | needs own Fuji transactions |
| 19 | Provider log ranges and history (three free providers) | **PASS** | official 100, publicnode 50,000 (pruned between 10M and 30M blocks back), drpc free 100 |
| 20 | Rate limiting | **PASS (throttling observed: HTTP 429, HTML, Retry-After); threshold INCONCLUSIVE** | one host, one episode |
| 21 | WebSocket reconnect, missed-event recovery, duplicate delivery | **PASS (client-initiated, one provider, about 90 s)**; server-initiated drops **INCONCLUSIVE** | |
| 22 | Provider inconsistency (by-hash miss) | **INCONCLUSIVE** | 1 miss in 53, then 0 in 14 and 0 in 8 (two providers); cause unknown |
| 23 | Environment guards: specification and research prototype | **PASS (specification refined; prototype partially exercised)** | production guards NOT implemented (separate phase) |
| 24 | Database and legacy payment-rail evidence | **BLOCKED** | no safe read-only access; operator request in [db-rail-evidence-request.md](db-rail-evidence-request.md) |
| 25 | Roadmap criterion: faucet-funded wallet can call USDC `balanceOf` | **BLOCKED** | `balanceOf` works for any address (read-only PASS) |
| 26 | Roadmap criterion: "every fact from two sources or marked unverifiable" | **PARTIAL (PASS where possible; remainder marked UNVERIFIED)** | |

## Remaining blockers and exact human actions
1. **Funded Fuji wallet** (criteria 14, 18, 25): run `generate`, fund the printed address with testnet AVAX in a browser faucet, run `run` ([runbook](fuji-funding-runbook.md)). Needs a person (captcha / console eligibility).
2. **Database evidence** (24): a DB operator runs the read-only queries in [db-rail-evidence-request.md](db-rail-evidence-request.md) and returns counts labelled by environment.
3. **Owner decisions** (see Part B completion report): Java level (17 or 21), RPC provider(s), KMS deferral, external audit and key ceremony scope, primary-chain meaning.

## Verdict
Phase 1 is **PARTIAL but technically substantive**: every item that could be established without a funded Fuji wallet or database access is established with evidence; the blocked items are listed with exact actions; nothing was fabricated.

---
# Owner decisions after Part B (2026-11-01)

These decisions change the status of two criteria in the Part B table above; **the overall verdict remains PARTIAL**.
| Criterion | Status before | Status now |
|---|---|---|
| 6. Java 17 compatibility | PASS only with web3j 4.13.0; FAIL for 6.1.0 | **OWNER-DECIDED: Java 21 + web3j 6.1.0.** The Java 17 result is retained as a research finding; the question is closed, not "passed" |
| 5. Java 21 + Spring Boot + web3j 6.1.0 in a real web context | PASS with mandatory override | unchanged: PASS (research); the arrangement must still be **validated in the actual FreelaX application context** during implementation |
| 10. Real KMS provider compatibility | NOT APPLICABLE (deferred by scope) | **OWNER-DECIDED: KMS deferred.** Ephemeral development signers for LOCAL_SIMULATION and AVALANCHE_FUJI |
Still **BLOCKED**: funded Fuji wallet experiments (criteria 14, 18, 25), database rail evidence (24). Still **INCONCLUSIVE**: 15, 16, 20 (threshold), 21 (server-initiated drops), 22. Final Fuji RPC provider selection and external audit / key ceremony scope remain **OPEN** owner decisions.

---
# Phase 1 closure tracking (2026-11-03, after the owner response)

Authority: owner response of 2026-11-03 (recorded in [PROJECT_STATUS](../PROJECT_STATUS.md)): the M2 rule stands (no backend
private key or backend signing operation in AVALANCHE_FUJI; the old `LiveExperimentsTest` Fuji procedure and its 10 AVAX cap
do **not** override it); no blanket waiver; every item below is tracked individually; nothing is WAIVED without explicit owner
approval; O-8 stays DEFERRED. **Overall Phase 1 status: PARTIAL** (unchanged). Criteria #6 and #10 stay closed by decision.
Criterion #23 is **superseded by implementation** (production guards exist since P2-M1: `EnvironmentGuard`,
`StartupGuardContextTest`).

| Item (criteria) | Requirement | Evidence available | Missing | Needs | Next action | Recommended closure |
|---|---|---|---|---|---|---|
| **E1** (#14, #18, #25; backlog 3, 7) | Own Fuji transactions: timings to receipt and `finalized`, revert, duplicate, nonce gap and replacement threshold, near-deadline inclusion; a funded wallet calls `balanceOf` | Keyless human-signed tooling **IMPLEMENTED** and validated on LOCAL_SIMULATION ([prototypes/human-signed](prototypes/human-signed/README.md), [LOCAL evidence](evidence/live/LOCAL_SIMULATION-human-signed-chain31337-20261103T150927.json)); no AVALANCHE_FUJI evidence; no key exists | A Fuji run and its evidence file | Owner decision (signing device and wallet owner, C1), a **human operator** with a funded Fuji address (testnet AVAX only; test USDC not needed for this plan) | Owner names funding owner, address and signing device; operator follows the README procedure | **Obtain evidence** (no waiver needed if the run happens). Status: **BLOCKED** |
| **E2** (#24; backlog 2) | Read-only database evidence on legacy rails | Exact queries, read-only session rules and the **required output format** in [db-rail-evidence-request.md](db-rail-evidence-request.md) | Query results for the authorized environment | **Human DB operator** (identity still pending). Environment **authorized 2026-11-03 (R2): development database only**, six read-only queries | Owner names the DB operator; operator returns counts for the development database in the stated format | **Obtain evidence**; needed only before any legacy-rail change (C8), so a waiver for Phase 1 closure is a legitimate alternative **if the owner grants it**. Status: **BLOCKED** |
| **E3** (backlog 4; O-8) | Choose the Fuji RPC provider(s) | Code supports both allow-listed hosts (`EnvironmentGuard.FUJI_HOSTS`, `ProviderProfile`: official 100 blocks, publicnode 50,000 with address); same-environment secondary supported; measured limits in [rpc-reconciliation-evaluation.md](rpc-reconciliation-evaluation.md) | A recorded choice | **Owner decision** (O-8 DEFERRED by the owner) | None until the owner decides; no host is added, the allow-list is unchanged | **Owner decision**; not technically necessary to change anything. Status: **OWNER DECISION REQUIRED (DEFERRED)** |
| **E4** (#3, #26) | Official source for the USDC.e address | Code safety does not depend on it: `ManifestVerifier` deny-lists `0xa7d7...c664`, requires symbol `USDC`, and Fuji accepts only Circle's Fuji USDC. 2026-11-03 search: Circle's address page lists native USDC only; Avalanche support articles (Oct 2025) describe USDC.e as the Avalanche Bridge asset with new onboarding disabled but **publish no address**; only third-party listings (CoinGecko, explorers, chain registries) give `0xa7d7...c664` | An official (Circle or Ava Labs) page naming the address | Desk research only (no operator, no code) | Re-check if Circle or Ava Labs publish a token list | **Document as a limitation** (the deny-list entry is defence in depth; the allow-list and symbol check already reject USDC.e); request an **explicit waiver** only if the owner wants the item closed. Status: **OPEN (no official source exists as of 2026-11-03)** |
| **E5** (#20; backlog 1) | Public RPC rate-limit threshold; faucet limits | Throttling shape observed (HTTP 429, HTML body, `Retry-After` 16 s then 60 s); handled in code (`RetryAfterInterceptor`, `RpcPacer` 1 req/s default, status-before-JSON) | The numeric threshold; faucet limits (sources conflict) | Threshold: an **external provider** statement or a stress test of a public endpoint (not recommended); faucet limits: observable by the **human operator** during E1 | Ask the operator to note the faucet's stated limits during E1 funding | Threshold: **document as a limitation** (the design does not depend on it) and request an explicit waiver; faucet limits: **obtain evidence** during E1. Status: **INCONCLUSIVE / BLOCKED (faucet part)** |
| **E6** (#15, #16, #21, #22) | Accepted-but-unexecuted visibility; `eth_getLogs` before execution; server-initiated WebSocket drops; cause of the by-hash miss | Read-only Fuji observations (67 transactions) recorded; the implementation is conservative whatever the answer (`TxTracker` never treats a null as evidence, second-provider check, scans only to `finalized`, WebSocket is a hint) | Direct observations; the E1 run will add own-transaction by-hash and receipt timings | E1 (human) for own-transaction data; some observations may stay INCONCLUSIVE even then | Include the by-hash and receipt timings from the E1 run in this section | **Obtain evidence** from E1 where possible; for what stays inconclusive, **document as a limitation** and request an explicit waiver. Status: **INCONCLUSIVE** |
| **E7** (backlog 1) | ACP-283 status; FiatToken version; explorer verification flow | **ACP-283:** the official Helicon page (docs.avax.network, checked 2026-11-03) lists ACP-283 among the six Helicon ACPs activated on Fuji 2026-07-28 15:00 UTC (the ACP README's "Proposed" wording is outdated). **FiatToken:** the Fuji USDC implementation `0x6F3c...D9f4` (read in Phase 1) is source-verified on testnet Snowtrace as `FiatTokenV2_2` (compiler v0.6.12, partial match), `version()` "2". **Explorer flow:** official docs point Fuji verification to `api-testnet.snowtrace.io` via `forge verify-contract`; procedure written in `avalanche-contracts/deployments/README.md` | Whether the Fuji proxy still points at that implementation (needs a read-only Fuji call beyond the authorized identity calls); an actual verification of the FreelaX escrow (needs Fuji deployment B2 and a Snowtrace API key) | Owner authorization for one read-only Fuji `eth_getStorageAt`; B2 (owner/human); an external API-key account | Owner decides whether explorer verification is wanted (backlog row 19 made it optional) | ACP-283 and FiatToken: **evidence obtained** (single official/explorer source each; recorded here). Explorer verification: **obtain evidence after B2**, or the owner marks it NOT APPLICABLE as optional. Status: **PARTIAL** |

Owner decision update (2026-11-03): R2 authorizes the **development database only** for the six read-only queries (E2 stays
BLOCKED until the DB operator is named and returns evidence; a development-database result says nothing about any other
environment). E1, E3 to E7 are unchanged: E1 waits for R1 and X3, E4 for the owner decision on the limitation (R3), E5 and E6
for the E1 run and evidence review (R4), E7 stays partial and its explorer-verification part optional/pending (R5). The same
update decided O-2 and O-6 and moved A4 to Phase 3; neither changes a Phase 1 criterion.

Additional Phase 1 items carried with the above: the replacement fee-bump rule (part of #14) and the original-execution
`block.timestamp` (#18) are produced by the same E1 run (`E5-*` and `E7-*` plan entries); the E6 "timeout after submission"
fault is covered only on LOCAL_SIMULATION (gateway test S3). CI remains an engineering quality item (register D1), **not** a
Phase 1 closure requirement (owner response section 6).

## Phase 1 closure tracking update (2026-11-04)
Authority: owner response 2026-11-03 section 10 and the project lead's execution instruction of 2026-11-04. **Overall Phase 1
status: PARTIAL (unchanged).** Nothing was waived, no Fuji transaction was signed or sent, no database was queried.

| Item | Work done 2026-11-04 | Status after |
|---|---|---|
| E1 | Added `prototypes/human-signed/check_evidence.mjs` (pre-commit check of the evidence file: no key-like field names, no 32-byte hex outside `*hash` fields, no PEM or mnemonic, environment / chain id / client family / file name consistent, Mainnet refused, run finished) with 6 network-free tests; README step 4 now requires it. The committed LOCAL evidence passes. Guard tests re-run: 5 + 6 pass. No key generated or accessed | **BLOCKED** (R1/C1, B1, X1): tooling ready end to end on LOCAL_SIMULATION |
| E2 | The six queries were executed unchanged against an empty throwaway MySQL 8.4.11 built from the current migrations (marketplace V1 to V6, payment V1) under a SELECT-only account in a READ ONLY session: all ran; writes refused. Script `scripts/db_query_validate.sh`; output `evidence/db-validation/LOCAL_SIMULATION-e2-query-validation-20261104.txt`. **Not evidence** about any real database | **BLOCKED** (DB operator; development database only, R2) |
| E3 | No change; allow-list unchanged; O-8 DEFERRED | **DEFERRED** |
| E4 | Official sources re-searched (circle.com, developers.circle.com, avax.network domains). Circle's address page lists native USDC only; Ava Labs support states that new USDC.e onboarding is disabled. A search-engine summary claimed a Circle Help Center article lists `0xA7D7...c664` as bridged USDC, but the article body did not render for verification, so **it is not cited as a source**; a human may check it in a browser. Code behaviour re-confirmed from the M7 tests: `ManifestVerifier` deny-lists `0xa7d7...c664` and requires the configured Circle USDC | **OPEN** (R3: documented limitation or waiver) |
| E5 | Local handling re-run with the gateway suite (`RetryAfterInterceptorTest`, S5 429 handling, combined scenario of register A3). No threshold measured or inferred | **INCONCLUSIVE** (threshold); faucet part BLOCKED on E1 |
| E6 | Local conservative handling re-run (null lookups never become FAILED or REPLACED, timeout is UNKNOWN, restart reconciliation) in the combined scenario; Fuji observations unchanged | **INCONCLUSIVE** (needs E1 data, R4) |
| E7 | No change; no verification is claimed without a deployment (B2) | **PARTIAL** |
