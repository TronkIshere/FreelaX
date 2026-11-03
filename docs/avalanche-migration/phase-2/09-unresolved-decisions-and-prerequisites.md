# 09. Unresolved decisions and prerequisites (plan only)

Nothing below is decided by this document. "Recommendation" is advice for the owner. Decisions D1-D4 and the owner decisions after Part B (Java 21, web3j 6.1.0, KMS deferred) are fixed inputs.

## 1. Owner decisions
| ID | Decision | Gates | Recommendation | Evidence/basis |
|---|---|---|---|---|
| O-1 | Avalanche integration as a **separate Spring Boot service** (mirrors Solana) vs a library inside the marketplace | start of M1 | separate service | Jackson 3 isolation, Java 21 vs 17, signer isolation ([01](01-target-module-architecture.md)) |
| O-2 | Shared immutable escrow vs factory/clones (design gate) | final M5/M6 contract shape | decide after a measured comparison at the M5 exit; start shared behind an interface that keeps clones possible | 03 section 10; USDC freeze blast radius |
| O-3 | Allow explicit per-escrow arbiter reassignment (Option C) or keep the pure snapshot (Option A) | M6 | A first; C only if lost-arbiter recovery matters for demos | [02](02-contract-implementation-plan.md) section 4; implicit global rotation is excluded |
| O-4 | Answers to escrow questions 1-3: arbiter model, revision co-sign, 72 h as bounded parameter vs constant | M5 authorization shape, M6 | preserve current behaviour (snapshot arbiter, backend-bound parameters, keep the revision co-sign for parity) | Phase 0 clarifications |
| O-5 | Confirm "preserve as is" for questions 4-8, 10, 11 (no Revision deadline, no unilateral refund, binary dispute outcome, extension never expires, no mutual refund once Disputed, mutual-refund mechanism, high-value grace) | M6 | preserve; choose two EIP-712 signatures for mutual refund only if approved | 07 |
| O-6 | Payout under a USDC blacklist (question 9) | M6 | evaluate options on a Fuji fork first; the simulation tolerates "escrow stays stuck and is reported" if the owner accepts it | 02 section 5 |
| O-7 | Gas payer (users pay testnet AVAX vs a relayer/sponsor) | M8-M11 | users pay on Fuji; relayer only for permissionless `release` | 07 question 13 |
| O-8 | Fuji RPC provider(s) | M4 defaults | keep per-provider configuration; choose primary and a same-environment second after more evidence | Phase 1: throttling, 100-block cap, pruning |
| O-9 | Rail literal: new `AVALANCHE_ESCROW` vs `UNIFIED_USDC_PAYOUT` + chain column | M9 | chain column on the existing rail (no new persisted literal) | 07 question 15 |
| O-10 | Neutral `TransactionStatus` expanded (originally eight states; superseded by O-17: ten states) | M8 | accept | Phase 1 prototype |
| O-11 | LOCAL_SIMULATION user wallets: backend-held dev wallets (like `LocalAutoWalletService`) vs injected wallet on Anvil | M10-M11 | support both only in LOCAL_SIMULATION, never in Fuji | [04](04-frontend-integration-plan.md) |
| O-12 | WalletConnect/Reown in scope | M10 | no; injected wallets only | |
| O-13 | Scope of any external audit or key ceremony under the simulation | M12 | none required; Slither + fuzz/invariants instead | scope document |
| O-14 | Meaning of "primary chain" and handling of in-flight Solana escrows (questions 17) | M9, coexistence | Avalanche for **new** work; Solana read-only history and in-flight escrows finish on Solana | [10](10-legacy-solana-coexistence.md) |
| O-15 | Legacy invoice/off-ramp path on Avalanche (question 16) and the on-ramp replacement (question 18) | M9 | keep fiat legs as labelled mocks; Fuji USDC comes from the Circle faucet, no mock mint on a real testnet token | |
| O-16 | Edits to root `pom.xml`, `docker-compose.yml`, `.env.example` (one module line, a service, placeholders) | M1 | approve as additive edits at M1 | |
| O-17 | Union transaction state set of ten states (CREATED, SIGNED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, FAILED, REPLACED, UNKNOWN); the neutral port may expose a documented mapped subset | M3, M8 | accept (owner-approved) | Phase 1 prototype, M3 |
| O-18 | Integration tests use Testcontainers; runtime uses a separately provisioned local MySQL database `avalanche_gateway`; no `.env` reads, no unidentified database | M1, M3 | accept (owner-approved) | owner decision at M0 |

## 2. Prerequisites and Phase 1 blockers
| Item | Type | Status | Needed for |
|---|---|---|---|
| Owner approval to start Phase 2 | decision | **SATISFIED** (owner approved M1 onward; M4 to M12 authorized to run sequentially) | everything |
| Funded ephemeral Fuji wallet (testnet AVAX; optional Circle test USDC) | human, interactive faucets | **BLOCKED**; runbook [fuji-funding-runbook.md](../phase-1/fuji-funding-runbook.md) | M7-Fuji, M12-Fuji, Avalanche-native calibration (fee bump, own-transaction timings, near-deadline inclusion) |
| Read-only database evidence for legacy rails | DB operator | **BLOCKED**; request [db-rail-evidence-request.md](../phase-1/db-rail-evidence-request.md) | any legacy rail change or retirement (not needed for additive Avalanche work) |
| JDK 21 on `JAVA_HOME` for developers and CI | environment | **OPEN**: the dev machine still defaults to JDK 20; a per-process `JAVA_HOME` override is used; no CI exists (see PROJECT_STATUS, Outstanding Items Register) | M1 and every Avalanche build |
| Docker for Anvil, Foundry (pinned images) | environment | available | M3-M7 |
| Official USDC.e address source | information | still without an official source (deny-list only) | M1 asset deny-list completeness |
| Remaining INCONCLUSIVE observations (public rate-limit threshold, server-initiated WebSocket drops, cause of one by-hash miss, `getLogs` before execution, accepted-but-unexecuted state) | evidence | open | none: handled by conservative defaults; refine after Fuji access |

## 3. What can proceed in parallel
After approval and O-1: backend infrastructure M1-M4 and contracts M5-M6 are independent; the two human actions (funded wallet, DB evidence) and owner decisions O-2 to O-9 run alongside. M8 needs the ABI from M6 and the tracker from M3. The frontend M10 can start once the M8 API shape is frozen, with a mocked provider.

## 4. Decision status register (reconciled 2026-11-02 after M12; O-2, O-6 and O-15 note updated 2026-11-03)
This register records status only. It does not change any decision, and it approves nothing that the owner deferred. The recommendation column above is advice; this table is what the owner actually decided. Source of the decisions: `PROJECT_STATUS` section "Approved decisions".

| ID | Status | Note |
|---|---|---|
| O-1 | DECIDED: separate Avalanche service | implemented as `avalanche-integration` |
| O-2 | **DECIDED 2026-11-03**: shared immutable escrow remains the baseline; factory/clone prototype permitted exclusively in LOCAL_SIMULATION | was DEFERRED through M12; decision recorded in PROJECT_STATUS "Owner decision update" |
| O-3 | DECIDED: per-escrow arbiter snapshot only | no reassignment, no global rotation |
| O-4, O-5 | DECIDED: preserve verified existing behaviour, invent no rules | mutual refund uses two EIP-712 signatures |
| O-6 | **DECIDED 2026-11-03**: stuck-and-reported escrow behaviour accepted; no privileged withdrawal, bypass or automatic rerouting | evidence in the M6 report section 4; decision recorded in PROJECT_STATUS "Owner decision update" |
| O-7 | DECIDED: users pay testnet gas, relayer only for release | |
| O-8 | **DEFERRED** (Fuji RPC provider) | host allow-list accepted for two hosts only |
| O-9 | DECIDED: chain identifier on the existing rail model | implemented as `payment_network_id` (V6) |
| O-10, O-17 | DECIDED: ten transaction states | "eight states" wording in older plan text is superseded |
| O-11 | DECIDED: backend development wallets only in LOCAL_SIMULATION, never in AVALANCHE_FUJI | |
| O-12 | DECIDED: no WalletConnect in this phase | |
| O-13 | **DEFERRED**: no external audit or key ceremony in this phase | the "none required" recommendation above is advice only; this is not a production-readiness claim |
| O-14 | DECIDED: Avalanche for new work; in-flight Solana escrows finish on Solana | |
| O-15 | DECIDED: fiat operations stay labelled mocks | fee, tax, payout and notification treatment for an Avalanche release is **not** covered by this decision; the work was moved to Phase 3 by R8 (2026-11-03) and its treatment option (C3) is still OPEN (see PROJECT_STATUS) |
| O-16 | DECIDED: additive-only root changes | `avalanche_gateway` line added to `init-db.sql` in M3 |
| O-18 | DECIDED: Testcontainers for tests; separately provisioned local runtime database | |
| (new, OPEN) | Fuji human-operated signing procedure | not decided; no policy invented |
| (new, OPEN) | Fee, tax, payout and notification treatment on the Avalanche rail | |
| (new, OPEN) | Whether chain events (scanner) drive milestone status or the marketplace flows do | |
