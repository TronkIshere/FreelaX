# Phase 2: implementation plan and execution record

> **Status update (2026-11-02):** this plan was written as planning only. Phase 2 was then executed (M1 to M12) and is **IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING**. The authoritative status, decision register and outstanding items are in [PROJECT_STATUS](../PROJECT_STATUS.md) (see its Outstanding Items Register); per-milestone evidence is in `M1` to `M12-validation-report.md` in this folder; the final acceptance classification is [M12-validation-report.md](M12-validation-report.md). The plan text below is preserved as the original plan; where it differs from an owner decision or an implemented behaviour, the notes in [09](09-unresolved-decisions-and-prerequisites.md) and `PROJECT_STATUS` win.

Original plan status: plan for owner review. Scope of the plan: no Fuji transaction and no Mainnet contact. The owner later authorized sequential execution of M4 to M12 without a stop after each milestone (recorded in `PROJECT_STATUS`).

Scope: [SIMULATION_SCOPE](../../SIMULATION_SCOPE.md). FreelaX is a development simulation of an Avalanche-powered freelance payment and escrow platform. Environments: **LOCAL_SIMULATION** (Anvil, simulated USDC) and **AVALANCHE_FUJI** (chain id 43113, testnet AVAX and testnet tokens). **Mainnet is out of scope and must be impossible to configure.**

## 1. Inputs this plan is built on
| Input | Source |
|---|---|
| Architecture, Anchor-to-Solidity mapping, roles, isolation gate | [03](../03-target-architecture.md), [00](../00-current-architecture-audit.md) |
| Decisions D1 to D4 (unchanged) | [README](../README.md) |
| Phase 1 evidence and reports | [phase-1](../phase-1/README.md), [Part B report](../phase-1/part-b-completion-report.md), [guards](../phase-1/simulation-environment-guards.md) |
| **Owner decisions after Part B (OWNER-DECIDED)** | **Java 21** baseline for the Avalanche integration; **web3j 6.1.0**; **KMS deferred**, ephemeral development signers per role and environment |
Dependency note: the Jackson arrangement for web3j 6.1.0 (`jackson-annotations:2.22`, or BOM 2.22.4) is a **research finding**, not authorization to change production dependencies; it is re-validated in the real application context in milestone P2-M1.

## 2. Mapping to the Phase 0 roadmap
The owner asked for one Phase 2 plan covering contracts, backend, signing, environments and frontend. The Phase 0 roadmap ([04](../04-migration-roadmap.md)) split this work into its Phases 2 to 6. This plan keeps the content and groups it into **milestones P2-M0 to P2-M12** that can each be reviewed on their own. Mapping: M1-M4 and M8 = old Phase 2; M5-M7 = old Phase 3; M8-M9 = old Phase 4; M10-M11 = old Phase 5; M12 = old Phase 6. The old "Final phase (Mainnet switch)" is withdrawn by the simulation scope.

## 3. Milestones and sequence
| ID | Milestone | Depends on | Can run in parallel with | Detail |
|---|---|---|---|---|
| P2-M0 | Preconditions: owner approval, open decisions that gate code, blockers triaged | none | funded-wallet and DB-evidence requests | [09](09-unresolved-decisions-and-prerequisites.md) |
| P2-M1 | `avalanche-integration` module skeleton, configuration model, **environment guards**, Jackson/web3j validation in the real context | M0 | M5 | [01](01-target-module-architecture.md), [03](03-backend-implementation-plan.md), [05](05-environment-and-signer-safety-plan.md) |
| P2-M2 | Development signer infrastructure (ephemeral, per role, per environment) | M1 | M3, M5 | [05](05-environment-and-signer-safety-plan.md) |
| P2-M3 | Chain client, DB-backed nonce allocator, transaction tracker (state machine), error taxonomy | M1, M2 | M5, M6 | [06](06-transaction-lifecycle-and-reconciliation-plan.md) |
| P2-M4 | Log scanner, cursor, reconciliation, provider profiles, WebSocket hints | M3 | M6, M7 | [06](06-transaction-lifecycle-and-reconciliation-plan.md) |
| P2-M5 | `avalanche-contracts/` Foundry project, simulated USDC, escrow core (fund, release, refund paths, pause) | M0 (+ decision O-4; O-2 at the M5 exit) | M1-M4 | [02](02-contract-implementation-plan.md) |
| P2-M6 | Escrow completion: extension, submit, revision, dispute, resolve, mutual refund, EIP-712 authorizations, invariants | M5, escrow decisions | M3, M4 | [02](02-contract-implementation-plan.md) |
| P2-M7 | Deployment and environment manifests for Anvil and Fuji (**Fuji part gated by a funded wallet**) | M5 (M6 for the final contract) | M4 | [02](02-contract-implementation-plan.md), [05](05-environment-and-signer-safety-plan.md) |
| P2-M8 | Gateway REST API, neutral port extension, `AvalancheGatewayAdapter` | M3, M4, M6 (ABI) | M10 after API freeze | [03](03-backend-implementation-plan.md) |
| P2-M9 | Marketplace integration behind a disabled/secondary configuration, additive migration, labelling fields | M8 | M10 | [03](03-backend-implementation-plan.md), [10](10-legacy-solana-coexistence.md) |
| P2-M10 | Frontend neutral wallet interface and EVM adapter | M8 API shape frozen | M9 | [04](04-frontend-integration-plan.md) |
| P2-M11 | Frontend flows and simulation labelling | M9, M10 | none | [04](04-frontend-integration-plan.md) |
| P2-M12 | End-to-end on Anvil, Fuji smoke, fault injection, quality drills | M9, M11 (Fuji needs M7 Fuji) | none | [07](07-test-strategy.md) |
Critical path: M0 -> M1 -> M2 -> M3 -> M4 -> M8 -> M9 -> M11 -> M12, with M5 -> M6 -> M7 joining at M8.

## 4. Blockers and what can proceed in parallel
| Phase 1 blocker | Blocks | Does **not** block |
|---|---|---|
| Funded Fuji wallet (human, faucet) | Fuji deployment (M7-Fuji), Fuji smoke and Fuji calibration in M12; replacement-bump and own-transaction timing calibration used by M3 defaults | everything on Anvil: M1-M6, M7-Anvil, M8-M11 |
| Read-only database evidence for legacy rails (DB operator) | **any** change to, retirement of, or migration of a legacy Solana payment path | all additive Avalanche work (new columns, new adapter) |
| Final Fuji RPC provider choice (owner) | default values in M4 provider profiles | M4 itself (profiles are per-provider configuration) |
| Escrow business questions ([07](../07-open-questions.md) 1-11) | contract semantics in M6 (and the authorization shape in M5/M6) | M1-M4, M5 funding/release/pause core if questions 1-3 are answered or "preserve as is" |
| Owner confirmation of module form (service vs library) | start of M1 | M5, M6 |
| Remaining inconclusive RPC/WebSocket observations | nothing (handled by conservative defaults) | |
Parallel tracks that can start at once after approval: A) backend infrastructure M1-M4; B) contracts M5-M6; C) human actions (funded wallet, DB evidence, owner decisions).

## 5. Document index
| File | Content |
|---|---|
| [01-target-module-architecture.md](01-target-module-architecture.md) | the ten conceptual boundaries, modules, packages, dependency rules |
| [02-contract-implementation-plan.md](02-contract-implementation-plan.md) | Solidity plan, roles, arbiter semantics, isolation gate, authorizations, deployment |
| [03-backend-implementation-plan.md](03-backend-implementation-plan.md) | backend milestones with acceptance, security, tests, rollback |
| [04-frontend-integration-plan.md](04-frontend-integration-plan.md) | wallet interface, EVM adapter, labelling |
| [05-environment-and-signer-safety-plan.md](05-environment-and-signer-safety-plan.md) | environment isolation, guards, signers, record metadata |
| [06-transaction-lifecycle-and-reconciliation-plan.md](06-transaction-lifecycle-and-reconciliation-plan.md) | state machine, idempotency, scanning, provider handling |
| [07-test-strategy.md](07-test-strategy.md) | test layers and gates |
| [08-risk-and-rollback-plan.md](08-risk-and-rollback-plan.md) | risks and rollback per milestone |
| [09-unresolved-decisions-and-prerequisites.md](09-unresolved-decisions-and-prerequisites.md) | open decisions, prerequisites, recommendations |
| [10-legacy-solana-coexistence.md](10-legacy-solana-coexistence.md) | coexistence and migration gates |

## 6. Rules that apply to every milestone
1. No Mainnet configuration, endpoint, address or fallback. No real funds, credentials or settlement.
2. Solana code and persisted enums stay unchanged; schema changes are additive and forward-only.
3. Each milestone ends with an explicit owner review; no milestone starts early. (Amended by the owner on 2026-11-01: M4 to M12 ran sequentially without a stop between milestones; the reviews are recorded in `PROJECT_STATUS` and the milestone reports.)
4. Unresolved business semantics are **not invented**: they are listed in [09](09-unresolved-decisions-and-prerequisites.md) and the code follows "preserve as is" only when the owner says so.
5. Evidence labelling stays: observed Fuji behaviour is never confused with local-simulation behaviour.
