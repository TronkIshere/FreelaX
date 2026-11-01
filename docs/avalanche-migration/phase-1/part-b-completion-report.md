# Phase 1 Part B: completion report

Date: 2026-11-01. Base commits: `e5127ae` (Phase 1 checkpoint), `73b5d14` (simulation scope). **Not committed.** Scope: [SIMULATION_SCOPE](../../SIMULATION_SCOPE.md): LOCAL_SIMULATION and AVALANCHE_FUJI only; Mainnet untouched (no Mainnet request was made in Part B; the endpoint guard rejects it before any network call).

## 1. Task status
| Task | Status | Summary |
|---|---|---|
| A. web3j + Spring Boot web context, Jackson 2/3 | **COMPLETED** | original failure reproduced in a real web context under Boot's and FreelaX's own Jackson pins (HTTP 500, `NoClassDefFoundError JsonApplyView`); `jackson-annotations:2.22` fixes it and works with FreelaX's `jackson-bom.version` 2.21.7; aligning the BOM to 2.22.4 also works; Jackson 2 and 3 coexist. [java-web3j-evaluation.md](java-web3j-evaluation.md) |
| B. Java 17 | **COMPLETED** | web3j 6.1.0 (and 6.0.0, 5.0.2/5.0.3, 4.14.0) are Java 21 bytecode, so **not usable on Java 17**; web3j 4.13.0 works on Java 17 (and 21), full suite green |
| C. Funded Fuji wallet experiments | **BLOCKED** (human action) | faucets are interactive (reCAPTCHA / console gate); no key or funds touched. Guarded reusable script built and validated on LOCAL_SIMULATION; exact steps in [fuji-funding-runbook.md](fuji-funding-runbook.md) |
| D. RPC and WebSocket behaviour | **PARTIAL** | done: provider ranges/history (3 free providers), a 429 throttling episode, WebSocket reconnect/duplicates/gap recovery, cross-provider lookup consistency, finalized-cursor scan. Open: rate-limit threshold, server-initiated WS drops, cause of the by-hash miss |
| E. Lifecycle prototypes | **COMPLETED** (LOCAL-SIM) | 8 scenarios, states CREATED..UNKNOWN (+REPLACED); restart/duplicate scan also verified on Fuji read-only |
| F. KMS and signature conversion | **COMPLETED (local); provider compatibility NOT APPLICABLE** | KMS recommended deferred; DER to `r,s,v`, low-s, recovery parity, EIP-712 and EIP-1559 digests validated with a fake KMS; role separation and rotation tests |
| G. Consolidation | **COMPLETED** | acceptance report (final section), README, roadmap, backlog, open questions, guards, scope links updated additively |
| Database / legacy rails (Part B item 8) | **BLOCKED** (operator) | no safe read-only access; schema checked statically; operator SQL in [db-rail-evidence-request.md](db-rail-evidence-request.md) |
| USDC recheck (item 6) | **COMPLETED** (Fuji only) | address unchanged; Mainnet not contacted by owner direction |

## 2. Test results (this session)
| Suite | Result |
|---|---|
| Java prototype, Java 21 + web3j 6.1.0 | 31 run, 0 failures, 2 skipped (live generate/run, enabled only by flags) |
| Java prototype, **Java 17 + web3j 4.13.0** | 31 run, 0 failures, 2 skipped |
| Java prototype, Java 21 + web3j 4.13.0 | 31 run, 0 failures, 2 skipped |
| Spring Boot web-context matrix | Java 21: none FAIL (expected), annotations22 PASS, bom22 PASS; Java 21 with FreelaX pin 2.21.7: none FAIL, annotations22 PASS; Java 17 + 6.1.0: compile FAIL (class version 65); Java 17/21 + 4.13.0: PASS |
| Foundry | 9 unit tests PASS (incl. role separation and rotation); 7 Fuji-fork tests PASS (Part A, unchanged) |
| Live script on LOCAL_SIMULATION | plain anvil (31337) and Fuji fork (43113 on loopback): all experiments recorded, no failures; guard rejections verified (fork-as-Fuji, Mainnet endpoint without contact, missing key) |
| Node probes | provider, WebSocket reconnect, consistency, finalized-cursor scan: results in `evidence/` |
Tool versions: JDK 21.0.2 (Oracle) and Corretto 17.0.10; Maven 3.9.16; Spring Boot 3.5.16; web3j 6.1.0 and 4.13.0; Foundry 1.8.7; solc 0.8.38; OpenZeppelin 5.7.0; Slither 0.11.6 (Part A); Node 22.10.0; anvil 1.8.7 (Docker).

## 3. Recommended technology baseline for the simulation
| Area | Baseline | Basis / open point |
|---|---|---|
| Java | **Java 21** for the new Avalanche module (owner to confirm); Java 17 only via web3j 4.13.0 | 6.1.0 is Java 21 bytecode; existing project is on 17 (**OPEN: owner**) |
| Spring Boot / web3j | Spring Boot 3.5.16 + web3j 6.1.0 + explicit `jackson-annotations:2.22` (re-test on every Boot or web3j bump; CI smoke test with a real call); keep FreelaX's `jackson-bom.version` pin | web-context verified |
| Solidity / Foundry | Foundry 1.8.7, solc 0.8.38, OpenZeppelin 5.7.0, `evm_version = "cancun"`, Slither in CI | Part A + B |
| Fuji RPC | public endpoints for development with: a per-provider config (`maxLogRange`, `requireAddress`), startup chain-id and client-version checks, per-host pacing of about 1 request/s for background work, `Retry-After` honoured, HTTP status checked before JSON parsing, a **second provider** of the **same environment** only for cross-checks (no silent fallback). Provider choice is **OPEN** | measured: official 100 blocks + 429 throttling; publicnode 50,000 + address required; drpc free 100 |
| Local EVM | anvil (Docker, pinned 1.8.7) as LOCAL_SIMULATION, optionally a local Fuji fork (accepted only on loopback; reports 43113); confirmation-depth finality locally | the `finalized` tag is not usable on anvil |
| Signing | development signers only (ephemeral, outside the repo, testnet/local), per-role keys, signer interface so KMS can be added later; **KMS deferred** (owner to confirm); DER/low-s conversion spec validated | local validation only |
| Lifecycle / reconciliation | operation-key idempotency; persist raw tx and hash before send; states CREATED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, UNKNOWN, REPLACED; reconcile by hash and nonce; hints never decide; scan to the `finalized` block with a persisted cursor and `(txHash, logIndex)` keys; DB-backed nonce allocator in Phase 2 | prototype + Fuji read-only scan |
| Environment safety | explicit environment, compiled allow-list, pre-network endpoint check, chain id then client-version check, fail closed, record isolation | specification; **not implemented** |

## 4. Unresolved items
**Owner decisions (OPEN):** Java 17 vs 21; RPC provider(s); KMS deferred or not; whether any external audit or key ceremony is wanted; meaning of "primary chain"; Phase 0 escrow questions 1-18 (unchanged); who funds Fuji demo wallets.
**Technical, blocked:** own-transaction Fuji experiments (funded wallet); near-deadline inclusion and original-execution timestamp; replacement bump rule on Avalanche; accepted-but-unexecuted observability.
**Technical, inconclusive:** public rate-limit threshold; server-initiated WebSocket drops; cause of the by-hash miss; USDC.e official address; `getLogs` before execution.
**Operator:** database rail evidence.

## 5. Limitations
Single host and short runs; the rate-limit episode may have been triggered by my own probing and its threshold is unknown; the fake KMS says nothing about any real provider; local-simulation results must not be quoted as Fuji behaviour; the lifecycle prototype uses a single-process nonce allocator and a JSON-file ledger; no production guard, signer or KMS code exists.

## 6. Scope confirmation
Only files under `docs/avalanche-migration/phase-1/` were created or changed, plus additive status sections in `04-migration-roadmap.md`, `06-implementation-backlog.md` and `07-open-questions.md`. No production Java, frontend, Solidity, database, dependency, configuration, Docker or Solana file was modified; the database was not accessed; no private key, seed phrase or `.env` value was read or written; no Mainnet request was made; nothing was committed or pushed.

---
# Owner decisions recorded after Part B (OWNER-DECIDED)

| Decision | Status | Meaning and limits |
|---|---|---|
| **Java baseline: Java 21** for the Avalanche integration | **OWNER-DECIDED** | Closes the Java 17 vs 21 question. The Java 17 / web3j 4.13.0 fallback stays documented as research only. The existing project's `java.version` (17) and Maven files are **not** changed by Phase 1 or by this record; moving the build to 21 is an implementation-phase change. |
| **web3j 6.1.0** | **OWNER-DECIDED** | Selected library version. The experimentally verified Jackson solutions (explicit `jackson-annotations:2.22`, or aligning `jackson-bom.version` to 2.22.4) are **research findings, not authorization to change production dependencies**. The final dependency arrangement must be validated in the **actual FreelaX application context** during implementation, with a CI smoke test that performs a real web3j call. |
| **KMS: DEFERRED** | **OWNER-DECIDED** | LOCAL_SIMULATION and AVALANCHE_FUJI use **ephemeral development signers**, isolated by role and environment. No production KMS integration, no production signing infrastructure. Mainnet signing is impossible under the simulation scope. Limitations and future requirements: [security-key-management-evaluation.md](security-key-management-evaluation.md) (section "Ephemeral development signers"). |

**Still OPEN / UNRESOLVED (not touched by these decisions):** funded Fuji wallet experiments (BLOCKED, human); read-only database evidence for legacy payment rails (BLOCKED, DB operator); final Fuji RPC provider selection; remaining inconclusive RPC/WebSocket observations (rate-limit threshold, server-initiated disconnects, cause of the by-hash miss); scope of any external security audit and key ceremony; the meaning of "primary chain"; Phase 0 escrow questions 1-18; Phase 0 decisions D1-D4 unchanged. **Phase 1 remains PARTIAL** until the blockers are resolved or explicitly waived by the owner.

Sanitization note: the local user-profile path in the web-matrix Maven logs was replaced by `%USERPROFILE%` before publication; no other evidence content was altered.
