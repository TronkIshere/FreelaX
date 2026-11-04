# Avalanche migration: project status

Last updated: 2026-11-04, second update (delegated engineering decisions applied: A1, A3, A7, A8, F12, F13 implemented; Phase 3 notifications and primary-rail policy; CI workflow; see change note). First update the same day: A1 partial, A3 gateway half, Phase 1 E1/E2 tooling, R6 approval package, request R11. Previous: 2026-11-03 (owner decision update recorded: R2 development database, O-2, O-6, R8; earlier the same day: owner response recorded; Phase 1 closure work; Phase 3 proposal; local re-validation). Previous: 2026-11-02 (after M12, documentation reconciled). Scope: [SIMULATION_SCOPE](../SIMULATION_SCOPE.md) (LOCAL_SIMULATION and AVALANCHE_FUJI only; Mainnet out of scope; no real funds).

This file is the single source of truth for status, owner decisions and outstanding work. Other documents keep their historical text and point here. Final acceptance classification: [phase-2/M12-validation-report.md](phase-2/M12-validation-report.md).

## Current phase and milestone
- **Phase 2 (M1 to M12): IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING.** This is **not** "fully validated" and **not** "Phase 2 accepted by the owner". Several acceptance criteria are BLOCKED, PARTIAL, NOT IMPLEMENTED or NOT VALIDATED; each is in the Outstanding Items Register below.
- **Phase 1: PARTIAL** (funded Fuji wallet and read-only database evidence still blocked; not waived). Item-by-item closure tracking (2026-11-03): [phase-1-acceptance-report.md, "Phase 1 closure tracking"](phase-1/phase-1-acceptance-report.md). See register section E.
- **Phase 3:** the owner defined Phase 3 (2026-11-03) as **a new phase after Phase 2**. A proposal exists: [phase-3/00-phase-3-proposal.md](phase-3/00-phase-3-proposal.md), **PROPOSED, NOT APPROVED** (C9 stays OPEN until approval). The label "Phase 3" in older documents (README D2, `04`, `07`, Phase 1 reports) still means the Phase 0 roadmap's Solidity phase, executed inside P2-M5 to M7; its residuals are listed in register section G. No Phase 3 implementation or contract change starts before approval. The owner decision update of 2026-11-03 (O-2, O-6, R8) is reflected in the proposal; the proposal itself (R6) is still **NOT APPROVED**.
- **Owner authorization (2026-11-01): M4 to M12 run sequentially without a stop after each milestone**, within the unchanged security and simulation scope. Earlier reviews: M1 accepted by the owner (2026-11-01, decisions below); M2 and M3 commits authorized; M4 to M12 committed locally.
- **Roadmap note:** the autonomous-execution prompt names milestones differently from this repository (for example its M4 "transaction lifecycle", M5 "contract system", M7 "business integration", M10 "Fuji validation", M11 "coexistence") and lists phase-2 files that do not exist. The repository roadmap in [phase-2/README.md](phase-2/README.md) was followed. Theme mapping: prompt M4 = repo M3 and M4; prompt M5 and M6 = repo M5 to M7; prompt M7 and M9 = repo M8 and M9 (plus end-to-end items in M12); prompt M8 and M10 frontend items = repo M10 and M11; prompt M10 (Fuji validation) = repo M12 Fuji part, **BLOCKED**; prompt M11 (coexistence) = [phase-2/10](phase-2/10-legacy-solana-coexistence.md), M9 and M12.

## Milestones
| Milestone | Status |
|---|---|
| Phase 0 (research, architecture) | complete |
| Phase 1 (environment, experiments) | **PARTIAL** (funded Fuji wallet and read-only DB evidence blocked; not waived) |
| P2-M0 | complete (owner decisions recorded below) |
| P2-M1 | **accepted by owner** (24 module tests, 22 pass, 2 optional skipped) |
| P2-M2 | validated and committed (development signers, LOCAL only) |
| P2-M3 | validated and committed (chain client, nonce allocator, transaction tracker; Flyway V1 of the gateway) |
| P2-M4 | validated and committed (log scanner, cursor, provider profiles, hints, second-provider cross-check; 85 module tests, 83 pass, 2 optional skipped) |
| P2-M5 | validated and committed (Foundry project, MockUSDC, escrow core: 29 Foundry tests; O-2 still deferred, no gas comparison claimed) |
| P2-M6 | validated and committed (contract completion, Slither triage, O-6 evidence on a Fuji fork, Java/Solidity digest parity; Foundry 49, gateway 89 tests) |
| P2-M7 | validated and committed (deploy script with chain guard, manifest, verifier, submission allow-list; real forge deployment to Anvil; **Fuji deployment BLOCKED**) |
| P2-M8 | validated and committed (gateway REST API, neutral port types, marketplace adapter and client behind a flag; gateway 104 tests, 101 pass, 3 optional skipped; marketplace 364 pass at that point) |
| P2-M9 | validated and committed (additive V6 migration verified on MySQL, authorization-before-submission service behind a flag; marketplace 381 pass). **Fee, tax, payout and notification wiring NOT implemented; chain-event status mirror NOT implemented** |
| P2-M10 | validated and committed (EIP-1193 adapter, chain allow-list 31337/43113, 24 tests). 13 existing frontend suites cannot load on Node 22.10 (pre-existing) |
| P2-M11 | **partially implemented** and committed (panel, banners, ten-state display; fund, extension-approve and release only; other action screens NOT implemented; no browser wallet check; 10 tests) |
| P2-M12 | final regression and acceptance report written and committed; **final classification: IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING** (re-validated 2026-11-03 on the current machine; see Validation results) |
| Phase 3 (new phase after Phase 2) | **PROPOSED, NOT APPROVED** ([proposal](phase-3/00-phase-3-proposal.md)); entry criteria not yet met (approved Phase 2 residuals A1, A2, A3, A7, A8 open; A4 moved to Phase 3 by R8, 2026-11-03) |

## Commits
On 2026-11-02 the owner asked to commit and push: all local commits below and the documentation reconciliation commit were pushed to `origin/master` (check `git log origin/master` for the final hash). Pushed earlier on the owner's request: `3b12e7a` Phase 0, `1662a80` Phase 0 decisions, `e5127ae` Phase 1 checkpoint, `73b5d14` simulation scope, `cf61c0f` Phase 1 Part B, `0224c71` Phase 2 plan, `c8d18cd` M1+M2, `761686d` M3.
Pushed on the owner request of 2026-11-02 (previously local): `1ea5033` M4, `68bf13b` M5, `499ca3c` M6, `72a203b` M7, `c2d9068` M8, `e75828a` M9, `7dac031` M10, `9ad2b23` M11, `dccee12` M12.
The documentation reconciliation of 2026-11-02 (this file and the files listed in its change note at the end) was committed and pushed together with them.

## Approved decisions (owner, 2026-11-01)
Earlier: D1 to D4; Java 21 baseline for the Avalanche integration; web3j 6.1.0; KMS deferred with ephemeral development signers; simulation-only scope.
M0 decisions:
| ID | Decision |
|---|---|
| O-1 | separate Avalanche integration service |
| O-2 | DEFERRED to the M5 exit (measured gas and security evidence); keep factory/clones possible. **Still deferred after M12.** **DECIDED 2026-11-03** (decision update below): the shared immutable escrow remains the baseline; a factory/clone prototype is permitted exclusively in LOCAL_SIMULATION |
| O-3 | per-escrow arbiter snapshot only; no reassignment, no implicit global rotation |
| O-4, O-5 | preserve verified existing behaviour; invent no rules |
| O-6 | evaluate USDC blacklist behaviour on a Fuji fork at M6; no rerouting or privileged withdrawal; report stuck-escrow behaviour before final acceptance. **Evidence delivered (M6 report section 4).** **ACCEPTED 2026-11-03** (decision update below): stuck-and-reported escrow behaviour is accepted; no privileged withdrawal, bypass or automatic rerouting |
| O-7 | users pay testnet gas; relayer limited to release |
| O-8 | DEFERRED (Fuji RPC provider) |
| O-9 | chain identifier added to the existing rail model, backward-compatible migration (implemented as `payment_network_id`, V6) |
| O-10, O-17 | union state set: CREATED, SIGNED, SUBMITTED, PENDING, CONFIRMED, FINALIZED, REVERTED, FAILED, REPLACED, UNKNOWN (FAILED = definitive application-level failure without on-chain execution; REVERTED = mined and failed; REPLACED = same nonce replaced; UNKNOWN = needs reconciliation); the neutral port may expose a documented mapped subset |
| O-11 | backend development wallets only in LOCAL_SIMULATION, never in AVALANCHE_FUJI |
| O-12 | no WalletConnect in this phase |
| O-13 | DEFERRED: no external audit or key ceremony in this phase (not a production-readiness claim) |
| O-14 | Avalanche for new work; in-flight Solana escrows finish on Solana |
| O-15 | fiat operations remain explicitly labelled mocks (does not by itself decide fee, tax, payout or notification treatment on the Avalanche rail) |
| O-16 | additive-only root changes (module in reactor, compose service, `.env.example` placeholders, `avalanche_gateway` in `init-db.sql`, dedicated JDK 21 Dockerfile); no change to the Java 17 baseline, Solana targets or `Dockerfile.maven` |
| O-18 | Testcontainers for integration tests; runtime uses a separately provisioned local MySQL database `avalanche_gateway`; no `.env` reads, no unidentified database |
Fuji authorization: paced read-only `eth_chainId` and `web3_clientVersion` only; Fuji tests optional and never required by the default build. (M6 additionally ran one optional read-only fork test against the public Fuji RPC.)

### M1 review decisions (owner, 2026-11-01)
1. The JDK-activated Maven profile is accepted. JDK 21 validation is mandatory for the Avalanche module; **JDK 17 builds intentionally exclude Avalanche.**
2. Fuji RPC host allow-list accepted: `api.avax-test.network` and `avalanche-fuji-c-chain-rpc.publicnode.com`. Additional providers require explicit review.
3. Single-label hostnames such as `anvil` are accepted only in LOCAL_SIMULATION and never bypass Fuji hostname validation (verified in code and by tests).
4. `CREATE DATABASE avalanche_gateway` in `init-db.sql` was a deferred O-16 item; **completed in M3.**

### M2 owner instructions (2026-11-01)
Development signers are permitted **only in LOCAL_SIMULATION**; no backend development signer, private key or signing operation in AVALANCHE_FUJI; no Mainnet; no KMS; ephemeral keys, no silent persistence, never logged or exposed. This **supersedes** the Phase 2 plan text that allowed ephemeral Fuji signers with a funds cap (plan files carry a supersession note; their original text is kept as history).

### Owner response (project lead, 2026-11-03)
1. **Phase 3** is a new phase after Phase 2. Before it starts: complete the remaining approved Phase 2 implementation tasks;
   close or explicitly document external validation blockers; preserve unresolved decisions here; prepare a Phase 3 proposal
   (objectives, scope, environments, acceptance criteria). No Phase 3 contract change before the relevant decisions.
2. **M2 stays authoritative:** no backend development private key and no backend signing operation in AVALANCHE_FUJI. The old
   Phase 1 `LiveExperimentsTest` Fuji procedure (and its 10 AVAX cap) does **not** override M2. E1 must come from a
   human-operated signing procedure; the backend never accesses the human wallet's key; the exact procedure is an owner
   decision (C1). No backend Fuji signer as a workaround.
3. **Funding:** no funded Fuji wallet is assumed; the funding owner, address and procedure must be provided by the owner
   first; no private key is requested, generated, stored or committed. AVAX is needed for gas; test USDC only for experiments
   that move tokens. Until confirmed, those experiments are BLOCKED.
4. **No blanket waiver.** E2, E4, E5, E6, E7 are tracked individually; nothing is WAIVED without explicit approval.
5. **O-8 stays DEFERRED**; the approved host allow-list is unchanged; any new provider needs a documented proposal and approval.
6. **CI** stays an engineering quality item (D1), not a Phase 1 closure requirement; a JDK 21 + Docker CI proposal is requested
   (legacy modules stay on Java 17; no global baseline change).
7. **DB evidence** only by the DB operator against explicitly authorized environments; read-only; local results are never
   production evidence.
8. *(Superseded for O-2 and O-6 by the decision update below.)* **O-2 and O-6 stay unresolved**; no change to the deployment architecture or the blacklist behaviour without an approved
   decision backed by consequences, affected users, alternatives and security implications.
9. **Local tests** may be run with compatible toolchains; no global JDK or Node upgrade, no system-wide installs, no toolchain
   requirement changes; environment incompatibilities are reported separately from code failures.
10. **Authorized:** inspect, fix inconsistencies, implement approved Phase 2 tasks, run compatible local tests, update docs,
    commit locally. **Not authorized:** push, Fuji deployment or signing, Mainnet, changing unresolved decisions, new contract
    architecture, unauthorized databases.
Open requests derived from this response: [decision-and-evidence-requests.md](decision-and-evidence-requests.md).

### Owner decision update (2026-11-03, authoritative)
Recorded as stated; not expanded or reinterpreted. Where a decision leaves a question unanswered, the question stays open
(listed after the table).

| Request | Decision | Register effect |
|---|---|---|
| R2 | **Development database only** is authorized for the **six read-only queries** of [db-rail-evidence-request.md](phase-1/db-rail-evidence-request.md). The DB operator and the evidence are still pending | E2 / B6 stay BLOCKED (DB operator); no other environment is authorized |
| O-2 (R7, C2) | The **shared immutable escrow remains the baseline**. A factory/clone prototype is permitted **exclusively in LOCAL_SIMULATION** | C2 DECIDED; no deployment-architecture change; no prototype has been built |
| O-6 (R7, C5) | **Stuck-and-reported escrow behaviour is accepted.** No privileged withdrawal, no bypass, no automatic rerouting | C5 DECIDED; contract behaviour unchanged |
| R8 (C3) | The remaining Avalanche **fee, tax/MISA, payout and notification** work **moves to Phase 3** | A4 leaves the Phase 2 residuals and becomes Phase 3 scope (inside the still unapproved proposal) |
Not answered by this update and therefore still open: the **treatment** option of C3 (labelled mocks, wired to the existing
stages, or left off the rail); **C4** (scanner-driven versus marketplace-driven milestone status) and therefore the placement
of **A5**; the identity of the DB operator; R1, R3 to R6, R9, R10 and X1 to X3.

### Delegated decision classification (2026-11-04, Lead instruction "autonomous engineering decisions")
The project Lead delegated to engineering every decision that can reasonably be made within technical authority. Each open
decision is classified below. **OWNER APPROVED** = decided by the owner (recorded above); **ENGINEERING DECIDED UNDER
DELEGATED AUTHORITY** = chosen by the engineering agent under that delegation (the Lead did **not** personally approve the
option; every such decision is reversible and recorded with its rationale in the execution log); **EXTERNAL ACTION REQUIRED**
= needs a person, a credential, funds, a database, or an owner/business authority that was not delegated.

| ID | Decision | Classification | Selected option (alternatives in the log) |
|---|---|---|---|
| R11 / D-A | Java 17 module changes | ENGINEERING DECIDED | additive changes and tests in `marketplace-backend`; Java level, Solana behaviour and legacy defaults unchanged; full legacy reactor must stay green (supersedes the 2026-11-04 "no Java 17 changes" rule, which came from the same Lead) |
| D-D / F13 | Mutual refund with users' own wallets | ENGINEERING DECIDED | gateway reuses a caller-supplied deadline bounded by the TTL; marketplace holds the first signature until the second (design in the R6 package section 2.5) |
| R9 / D-E | EVM wallet linking design | ENGINEERING DECIDED | EIP-191 personal_sign challenge, verified in the Java 21 gateway, as already planned in `phase-2/04` section 3; Solana and EVM wallets coexist, told apart by address format |
| D-H | Meaning of "primary chain" | ENGINEERING DECIDED | default rail for **new** contracts in development and demonstration environments only (the conservative reading of O-14 and the scope amendment) |
| C11 | Gateway-driven local deployment | ENGINEERING DECIDED | not wanted; deployment stays with the forge script (status quo, F2 recorded limitation) |
| D-C / C4, A5 | Status mirror from chain events | ENGINEERING DECIDED | keep marketplace-driven milestone status (status quo); scanned events are used only for reconciliation and reporting; A5 closed as not needed. Reversible |
| R10 / D-F | CI proposal | ENGINEERING DECIDED | accepted as written; CI files may be added locally (nothing is pushed, so no runner executes them) |
| R3 | E4 USDC.e address source | ENGINEERING DECIDED | option (a) documented limitation (not a waiver) |
| R5 | E7 explorer verification | ENGINEERING DECIDED | option (a) optional: NOT APPLICABLE until a Fuji deployment exists (B2); not a waiver of anything that can run |
| R4 | E5/E6 after the E1 run | EXTERNAL ACTION REQUIRED | depends on the human-signed E1 run (X1) |
| R6 / C9 | Phase 3 scope | **ENGINEERING EXECUTION AUTHORIZED UNDER DELEGATED AUTHORITY — OWNER APPROVAL PENDING** (Lead instruction 2026-11-04, "R6, R12 and Avalanche payout"). The owner has **not** approved R6. Engineering may implement independently implementable Phase 3 work inside the agreed migration scope (architecture, interfaces, additive migrations, simulations and mocks, tests, tooling); not external deployments, real financial operations, or anything needing external authorization; CLAUDE.md section 6 hard stops unchanged | see the execution log for each item (L-019 onwards) |
| D-B / C3 | Treatment of fees, tax/MISA, payout, notifications for an Avalanche settlement | ENGINEERING DECIDED | option (b): reuse the existing downstream stages and their existing rules (no new formula), records labelled simulated, behind a flag that defaults off. Built 2026-11-04: notifications and the rail-aware tax label (A7) only; the fee/tax/payout wiring is **NOT STARTED** (needs the Solana coupling of `SettlementDownstreamService` analysed first; next task) |
| R1 / C1 | Fuji signer, funding owner, device | EXTERNAL ACTION REQUIRED | |
| R2 remainder | DB operator identity | EXTERNAL ACTION REQUIRED | |
| C6 / O-8, C7 / O-13 | RPC provider; audit, key ceremony | OWNER DEFERRED (owner decisions, kept) | |
| C8 | Legacy rail retirement | EXTERNAL (irreversible business decision; needs E2) | |
| C10 | Vietnamese copy review | EXTERNAL ACTION REQUIRED (native editor) | |

## Outstanding Items Register
Status words: OPEN, BLOCKED (needs a human or external action), DEFERRED (owner deferred), NOT IMPLEMENTED, PARTIAL, NOT VALIDATED. "Evidence" points to the existing document and section that records the item. Nothing here is decided by this register.

### A. Implementation work remaining (no owner decision needed to start unless noted)
| ID | Item | Status | Evidence |
|---|---|---|---|
| A1 | Frontend action screens: submit, revise, dispute, resolve (admin), extension-request, mutual-refund; legacy Solana labelling | **IMPLEMENTED 2026-11-04** (all six actions, arbiter resolve on the admin case page, chain-state display; **wallet mutual refund** two-party flow after F13; per-record chain labels incl. "Solana (tham chiếu cũ)" in finance rows). Tested in Vitest only; **no real-browser wallet check (B7)** | execution log L-007, L-012 |
| A2 | Cross-service run: marketplace plus gateway plus Anvil plus MySQL in one environment | **IMPLEMENTED and PASSING 2026-11-04** (`phase-2/a2-local-stack`): wallet links, fund-submit-release, F13 mutual refund, P3-C4 stuck escrow reported live. Found and fixed: Avalanche-funded contracts could not accept submissions (job status) | execution log L-016 |
| A3 | Combined fault-injection scenario (timeout, 429, WebSocket drop, duplicate, missed event, revert, nonce conflict, restart, invalid signer/env/contract) at marketplace level | **IMPLEMENTED 2026-11-04** in two halves: gateway `CombinedFaultScenarioTest` (real Anvil and MySQL: timeout after send, duplicate, revert, 429, null lookups, restart, duplicate after restart) and marketplace `AvalancheEscrowServiceTest.combinedGatewayFaultsNeverApplyEarlyNeverFailAndApplyExactlyOnce` (timeout at execute, duplicate, 429 and outage at refresh, UNKNOWN/CONFIRMED, failed chain read after FINALIZED, CHAIN_MISMATCH, restart, duplicate FINALIZED). WebSocket drop and invalid signer/env/contract stay covered per layer (M3, M4, M7 tests). The two halves are joined by mocks, not by a live single stack (that is A2) | execution log L-009, L-011 |
| A4 | Fees, MISA tax records, payouts and notifications for an Avalanche release | **PARTIAL**: notifications IMPLEMENTED (flag, default off); rail-aware tax label done. **Fee/tax/payout: EXTERNAL DECISION** (2026-11-04 analysis): the existing pipeline starts with an on-chain off-ramp from the freelancer's Solana wallet, and payout and tax run only after it; an Avalanche equivalent is a new money-movement/custody path (freelancer sends USDC to a partner deposit address), which needs separate owner approval | execution log L-017 |
| A5 | Mirror of on-chain Submitted/Revision/Disputed transitions into milestone status | **CLOSED: NOT NEEDED** (D-C engineering decision 2026-11-04: milestone status stays marketplace-driven; scanned events serve reconciliation and reporting). Reversible | delegated decision table |
| A6 | Scanner metrics and alerting | **IMPLEMENTED 2026-11-04**: `GET /api/v1/avalanche/scan/status` (OK/STARTING/DEGRADED after 3 failures, lag); `ScanHealthTest` | execution log L-017 |
| A7 | Remaining Solana-literal clean-up in MISA/tax flows and explorer labelling in existing screens | **IMPLEMENTED 2026-11-04**: `TaxRecordBlockchain` sends `avalanche` for Avalanche-rail contracts and keeps `solana` for every legacy rail (existing assertions unchanged); contract summary DTO gains the additive, nullable `paymentNetworkId`; finance rows show a chain label. `misa-backend` unchanged (it stores the label it receives; default `solana` kept for old callers) | execution log L-011 |
| A8 | **EVM wallet linking in the marketplace** | **IMPLEMENTED 2026-11-04**: `evm_wallets` (V7, separate from the Solana `wallets` table); single-use EIP-191 challenge (`EvmWalletLinkService`, `/api/v1/avalanche/wallet-link`), verified by the gateway (`/wallet-signatures/verify`, low-s only); one address per user and per address; challenge consumed even on rejection; `AvalancheEscrowService` uses the proven EVM wallet first. Frontend link flow; `personalSign` signs only FreelaX binding messages. Tests: gateway 5 + API, marketplace 9 + 1, V7 MySQL IT 2, frontend 3 | execution log L-012 |

### B. External validation and human actions
| ID | Item | Status | Evidence |
|---|---|---|---|
| B1 | Funded Fuji wallet held by a human (testnet AVAX; Circle test USDC only for token-moving experiments) | BLOCKED (owner must name the funding owner, address and signing device, C1; interactive faucets). The old key-file runbook is superseded for Fuji by the M2 rule; keyless tooling: [phase-1/prototypes/human-signed](phase-1/prototypes/human-signed/README.md) | [phase-1/fuji-funding-runbook.md](phase-1/fuji-funding-runbook.md) (historical for Fuji); owner response 2026-11-03 |
| B2 | Fuji deployment, role grant, smoke transactions, authorized-action prepare/execute on Fuji | BLOCKED (needs B1 and decision C1) | M7 section 3 item 1; M8 section 4 item 1; M2 section 5 item 1 |
| B3 | Fuji calibration: fee-bump threshold, own-transaction timings, near-deadline inclusion, finalized-tag path, real provider limits and WebSocket stability | BLOCKED (needs B1, B2) | M3 section 7 item 1; M4 section 4 item 1; `phase-2/08` R8 |
| B4 | Fuji deadline-boundary test with real transactions (submit with deadline = latest + N seconds, record included block timestamp) | BLOCKED. The **lab-contract** part (Phase 1 #18) needs only B1 and C1 (plan entries `E7-*` of the human-signed tooling); the **escrow-level** part needs B2 | [phase-1/timestamp-deadline-experiments.md](phase-1/timestamp-deadline-experiments.md) line 44 ("Phase 3 gate" label) |
| B5 | Optional Fuji fork test (`UsdcFork.t.sol`) re-run with `FUJI_RPC` | OPEN (optional; ran once in M6) | M6 section 6 item 2; M12 section 1 |
| B6 | Read-only database evidence for legacy rails (DB operator) | BLOCKED (DB operator). Environment authorized 2026-11-03 (R2): **development database only**, six read-only queries; results describe that database only | [phase-1/db-rail-evidence-request.md](phase-1/db-rail-evidence-request.md); needed only before any legacy-rail change |
| B7 | Browser wallet test with a real wallet extension | NOT IMPLEMENTED (no extension available) | M10 section 4; M11 section 2 |
| B8 | Node upgrade to 22.12 or later (or a dependency decision), then re-run the 13 frontend suites that cannot load on Node 22.10 (ESM-only `uuid` via `@solana/web3.js`) | **VALIDATED on the current developer machine (2026-11-03): Node 24.19.0, `npm ci` from the lockfile, `tsc -b` clean, 28 of 28 Vitest files and 841 tests pass**, including the 13 suites (Solana wallet and page suites) that could not load on 22.10. No project toolchain requirement was changed; CI must pin a Node version (D1) | Validation results 2026-11-03 |
| B9 | `docker compose config` validation (needs the owner's ignored `.env`, never read here) | NOT VALIDATED | M12 section 1 |
| B10 | JDK on developer machines | OPEN, **changed**: the current machine has only JDK 25.0.4.1 (no JDK 17 or 21), Maven 3.10.0 in the wrapper cache (`mvn` not on `PATH`). JDK 17 and 21 runtimes were validated through the repository's pinned images (`maven:3.9-eclipse-temurin-17/21`, as in `Dockerfile.maven`/`Dockerfile.avalanche`). On JDK 23+ `payment-backend` compiles only with `-Dmaven.compiler.proc=full` (it is the only module without an explicit Lombok `annotationProcessorPaths`): an environment incompatibility, not a code failure | Validation results 2026-11-03 |

### C. Architectural and business decisions (owner)
| ID | Decision | Status | Evidence |
|---|---|---|---|
| C1 | **Fuji human-operated signing procedure** (who holds Fuji AVAX, how deployment and smoke transactions are signed when the backend may hold no Fuji signer) | OPEN; no policy invented; gates B2 to B4 **and Phase 1 E1** (owner response 2026-11-03); options in request R1 | M2 section 5; M7 section 3 |
| C2 | O-2 shared immutable contract versus factory/clones | **DECIDED 2026-11-03**: shared immutable escrow remains the baseline; factory/clone prototype permitted exclusively in LOCAL_SIMULATION | decision update 2026-11-03; `phase-2/09` section 4; M5 section 4 |
| C3 | Fee, tax, payout and notification treatment for an Avalanche release (labelled mocks, wired to existing stages, or left off the rail) | **PLACEMENT DECIDED 2026-11-03** (R8: the work moves to Phase 3); the treatment option itself is **OPEN** | M9 section 5; decision update 2026-11-03 |
| C4 | Scanner-driven milestone status mirror versus marketplace-driven status | **ENGINEERING DECIDED 2026-11-04: marketplace-driven (status quo)**; A5 closed | delegated decision table |
| C5 | O-6 final acceptance of the stuck-escrow behaviour under a USDC blacklist | **DECIDED 2026-11-03**: stuck-and-reported accepted; no privileged withdrawal, bypass or automatic rerouting | decision update 2026-11-03; M6 section 4 |
| C6 | O-8 Fuji RPC provider(s) | DEFERRED | `phase-2/09` O-8 |
| C7 | O-13 external audit or key ceremony | DEFERRED | `phase-2/09` O-13 |
| C8 | Whether and how legacy rails retire (needs B6) | OPEN | `07` open questions; ADR 0001 |
| C9 | Scope of the phase after Phase 2 ("Phase 3") | OPEN: owner defined Phase 3 as a new phase (2026-11-03); proposal prepared, **NOT APPROVED**; **approval package prepared 2026-11-04** with decisions D-A to D-H | [phase-3/00-phase-3-proposal.md](phase-3/00-phase-3-proposal.md); [phase-3/01-r6-approval-package.md](phase-3/01-r6-approval-package.md); request R6 |
| C10 | Review of the Vietnamese UI copy | OPEN | M11 section 2 |
| C11 | Whether a gateway-driven local deployment is wanted | **ENGINEERING DECIDED 2026-11-04: not wanted**; deployment stays with the forge script (F2 recorded limitation) | delegated decision table |

### D. Quality gates (simulation; not production-readiness claims)
| ID | Item | Status | Evidence |
|---|---|---|---|
| D1 | CI with a JDK 21 step and the Solana baseline on a Docker-capable runner | **WORKFLOW ADDED 2026-11-04** (`.github/workflows/avalanche-ci.yml`, R10 engineering decided): legacy JDK 17, Avalanche JDK 21, Foundry, frontend (Node 24.19.0), Phase 1 tooling, hygiene. **NOT EXECUTED by any runner** (nothing pushed); hygiene steps and the forge-std pin fetch were run locally and pass | execution log L-014 |
| D2 | Testcontainers skip-without-Docker behaviour | NOT VALIDATED | M3 section 7 item 6 |
| D3 | External audit, key ceremony, KMS integration, signer-rotation and pause drills, monitoring, rollback drill (old Phase 6 and Final-phase items) | DEFERRED / owner-gated by C7; Mainnet items withdrawn | `04` and `06` amendment tables |
| D4 | Slither triage is the only security review so far; no external review | recorded; the Slither run was reproduced on 2026-11-03 with the same 12 results and its output is now committed ([evidence](phase-2/evidence/slither-escrow-2026-11-03.txt)) | M6 report; O-13 |

### E. Phase 1 remaining validation requirements (Phase 1 stays PARTIAL)
| ID | Item | Status | Evidence |
|---|---|---|---|
Full per-item tracking (evidence, missing work, who, next action, recommended closure): [phase-1-acceptance-report.md, "Phase 1 closure tracking (2026-11-03)"](phase-1/phase-1-acceptance-report.md). Nothing below is WAIVED (owner response section 4).
| E1 | Own-transaction Fuji experiments (#14, #18, #25) | BLOCKED (C1 and B1). Keyless human-signed tooling **IMPLEMENTED**, validated on LOCAL_SIMULATION only; **pre-commit evidence checker `check_evidence.mjs` added 2026-11-04** (11 network-free tests pass) | `06` rows 3 and 7; [phase-1/prototypes/human-signed](phase-1/prototypes/human-signed/README.md) |
| E2 | Read-only DB evidence | BLOCKED (= B6; DB operator not yet named). R2 answered 2026-11-03 for the environment: **development database only**, the six read-only queries. Query list and required output format prepared; **2026-11-04: the six queries executed unchanged against an empty throwaway schema built from the current migrations under a SELECT-only, READ ONLY session (all ran, writes refused); not evidence** | [phase-1/db-rail-evidence-request.md](phase-1/db-rail-evidence-request.md) |
| E3 | RPC provider choice | DEFERRED (= C6; owner keeps O-8 deferred; allow-list unchanged) | `06` row 4 |
| E4 | Official USDC.e address source | **DOCUMENTED LIMITATION** (R3 option (a), engineering decided 2026-11-04; **not a waiver**): no official Circle or Ava Labs page names the address (re-checked 2026-11-03 and 2026-11-04); safety does not depend on it (allow-list of Circle USDC plus symbol check plus deny-list entry) | closure tracking E4; execution log L-009 |
| E5 | Public RPC rate-limit threshold; faucet limits | INCONCLUSIVE (threshold); faucet limits BLOCKED on E1. Handling implemented in code | closure tracking E5 |
| E6 | Accepted-but-unexecuted observation; `eth_getLogs` before execution; cause of one by-hash miss; server-initiated WebSocket drops | INCONCLUSIVE; partly addressable by the E1 run; handled by conservative defaults | closure tracking E6 |
| E7 | ACP-283 status; FiatToken version; explorer/verification flow | PARTIAL: ACP-283 and FiatToken evidence obtained (2026-11-03). Explorer verification: **NOT APPLICABLE until a Fuji deployment exists** (R5 option (a), engineering decided 2026-11-04; optional per backlog row 19; procedure written) | closure tracking E7 |

### F. Carried-forward technical issues (each recorded in one milestone report)
| ID | Issue | Status | Evidence |
|---|---|---|---|
| F1 | No operator procedure to clear a nonce gap (ephemeral LOCAL signers only; decide if signers ever persist) | OPEN | M3 section 7 item 3 |
| F2 | Contract creation (`to` empty) and non-zero value unsupported in the submission path; deployment goes through forge | OPEN (= C11) | M3 section 7 item 5; M7 section 3 item 2 |
| F3 | LOCAL signer addresses change per process: a local deployment must grant roles to the current signer addresses | OPEN | M7 section 3 item 2; M12 section 3 |
| F4 | Mutual refund supports EOA signatures only (no EIP-1271 contract wallets) | recorded limitation | M6 section 6 item 4 |
| F5 | User-wallet transactions are tracked only by a live by-hash read; the marketplace persists the hash and polls | recorded limitation | M8 section 4 item 3 |
| F6 | No scanner metrics or alerting | OPEN (= A6) | M4 section 4 item 3 |
| F7 | The gateway trusts the marketplace for terms (internal API key is the only caller check); the marketplace derives terms from records | recorded limitation | M8 section 4 item 2; M9 section 2 |
| F8 | `TxReconcileScheduler` (`avalanche.tx.reconcile-interval-ms`) and the scan timer (`avalanche.scan.interval-ms`) default to 0 (off) and were not passed by compose; `TxTracker` has no other runtime caller, so gateway-submitted transactions never left SUBMITTED in a compose run, and the marketplace has no Avalanche `@Scheduled` job (refresh runs only on an HTTP call) | **Configuration exposed 2026-11-03** (`docker-compose.yml`, `.env.example`; defaults unchanged at 0); enabling them for A2 is an operator setting | audit 2026-11-03 |
| F9 | `statusByHash` reported CONFIRMED/FINALIZED for any successful receipt, without the plan 06 section 4 target check, and used a hard-coded confirmation depth | **FIXED 2026-11-03**: a receipt whose target is not a verified manifest contract is UNKNOWN; LOCAL finality uses `avalanche.tx.confirmations`; covered in `AvalancheApiTest` | `EscrowActionService.statusByHash` |
| F10 | `EscrowReconciler` (events versus `eth_call` state) is used only in tests; scanned events (`avalanche_event`) have no runtime consumer | OPEN (tied to C4 / A5) | audit 2026-11-03 |
| F11 | `avalanche_tx.replaced_by_tx_hash` is never written; earlier attempts of a sped-up transaction are kept in `previous_tx_hashes` instead (plan 06 section 3 wording differs) | recorded (documentation difference, no defect found) | `TxTracker.missing`, `SubmissionService.speedUp` |
| F13 | Mutual refund with users' own wallets could not complete (fresh deadline per prepare; nowhere to hold the first signature) | **FIXED 2026-11-04**: the gateway reuses a supplied deadline bounded by the TTL (`DEADLINE_REQUIRED`, `DEADLINE_OUT_OF_RANGE`); the marketplace holds the first signature (`avalanche_pending_authorizations`, V7), re-derives the digest from the gateway, lets each party write only its own column, resets the pair on a new deadline, sends the pair only when complete and unexpired, and deletes it when the refund is applied. Regression on real Anvil mines blocks between the signatures. Limitation: the marketplace does not itself verify the typed-data signature (the escrow contract does; a bad signature can only make the refund revert) | execution log L-012 |
| F14 | **Arbiter loss on a Disputed escrow** (drill 2026-11-04): only the snapshotted arbiter can resolve (O-3, no reassignment) and `mutualRefund` refuses Disputed, so a lost arbiter key leaves the escrow unsettleable on chain | **OPEN on the baseline; VARIANT IMPLEMENTED AND TESTED 2026-11-04 (LOCAL only, not adopted)**: two-party EIP-712 `mutualSettleDispute` (Disputed only, binary outcome, bound to escrow, amount, settlement hash, `disputedAt`, nonce, deadline, chain, contract; no reassignment, no privileged withdrawal) in `phase-3/prototypes/escrow-mutual-settlement`; 72/72 Foundry tests incl. the baseline suites re-run against it. **Business-rule conflict:** it contradicts owner decision O-5 question 8 ("no mutual refund once Disputed", preserved) and needs a new contract version under O-2; the baseline `avalanche-contracts/src` is unchanged. Adoption: **owner decision R12** | `phase-3/02-simulation-drills-2026-11-04.md`; lab README; execution log L-019 |
| F12 | forge-std clone without a pin in `install-deps.sh` | **FIXED 2026-11-04**: pinned to `dc647cc030d9d65910d8f1172a0cde48d7106e32` (fetch by hash verified in the Foundry image) | `avalanche-contracts/scripts/install-deps.sh` |

### G. Residuals of the roadmap's Solidity phase ("old Phase 3", executed in P2-M5 to M7)
| ID | Item | Status | Evidence |
|---|---|---|---|
| G1 | `03` section 10 invariant "a dispute changes only its own escrow" had no dedicated test | **IMPLEMENTED 2026-11-03** (test only, no contract change): `test_dispute_changesOnlyItsOwnEscrow` | `avalanche-contracts/test/FreelaXEscrowLifecycle.t.sol` |
| G2 | Slither, gas and test outputs were cited in reports but not committed | **IMPLEMENTED 2026-11-03**: reproduced and committed | `phase-2/evidence/` (Slither, gas report, forge test log) |
| G3 | Fork test against real Fuji USDC re-run | OPEN (= B5; needs a read-only Fuji fork, not run on 2026-11-03) | M6 section 6 |
| G4 | Design documents of backlog rows 13 to 15 (isolation gate, EIP-712 spec, role model) never written as separate files | recorded; O-2 analysis now in the Phase 3 proposal section 6.2; EIP-712 and roles are embodied in the contract and the M5/M6 reports | `06` Phase 2 execution mapping |
| G5 | Fuji deployment and explorer verification | BLOCKED (B2, C1, E7) | M7 section 3 |

## Implementation completion versus full validation completion
- **Implementation completion (reached, with the NOT IMPLEMENTED items A1, A5 and A8, recorded 2026-11-03; A4 moved to Phase 3 by R8):** contracts, gateway, marketplace rail behind a flag, wallet adapter and a partial panel exist, pass local tests and keep Solana unchanged.
- **Full validation completion (not reached):** needs B1 to B4 and C1 (Fuji), B7 to B9, A2 (cross-service run), D1, owner decisions C6 and C7 (C2 and C5 decided 2026-11-03), and the Phase 1 items E1 and E2. Phase 1 stays PARTIAL until then.

## Validation results
- M1: Solana baseline unchanged (16/16 before and after); `avalanche-integration` 24 tests (22 pass, 2 optional skipped); real Anvil and Fuji read-only runs pass; Phase 1 Jackson failure reproduced and fixed by `jackson-annotations:2.22` (module only).
- M2: 43 module tests (41 pass, 2 optional skipped); Fuji with signers enabled aborts with `SIGNER_FORBIDDEN_ENVIRONMENT` without any RPC call.
- M3: 71 module tests (69 pass, 2 optional skipped); Phase 1 scenarios S1 to S6 and S8 on real Anvil and MySQL; 16 threads, 50 submissions never reused a nonce.
- M4: 85 module tests (83 pass, 2 optional skipped). M5: 29 Foundry tests. M6: 49 Foundry tests, 89 gateway tests. M7: 98 gateway tests. M8: 104 gateway tests (101 pass, 3 optional skipped), marketplace 364 pass. M9: marketplace 381 pass. M10: 24 Vitest tests. M11: 10 Vitest tests.
- **M12 (2026-11-02):** full reactor on JDK 21 with Docker BUILD SUCCESS (payment 72, marketplace 381, misa 20, Solana gateway 10, avalanche-integration 104 with 3 optional skipped); JDK 17 reactor of the existing modules BUILD SUCCESS; Foundry 54 pass, 1 optional skipped; frontend `tsc` and `vite build` clean, 345 Vitest tests pass in 15 files, 13 files cannot load on Node 22.10 (B8); secret scan clean; Mainnet references only in deny-lists. Details: [phase-2/M12-validation-report.md](phase-2/M12-validation-report.md).
- **Re-validation (2026-11-03, current developer machine; no Fuji, Mainnet or database access):**
  - Environment: JDK 25.0.4.1 only, Maven 3.10.0 (wrapper cache), Node 24.19.0, Docker 29.8.2 (Docker Desktop had to be started).
  - Maven, JDK 25 runtime, Docker: full reactor BUILD SUCCESS, payment 72, marketplace 381, misa 20, solana-integration 10,
    avalanche-integration 104 (3 optional skipped). Needs `-Dmaven.compiler.proc=full` on JDK 23+ (B10): environment issue,
    no source change.
  - **JDK 17 runtime** (`maven:3.9-eclipse-temurin-17`, OpenJDK 17.0.20.1): legacy reactor BUILD SUCCESS, 72 / 381 / 20 / 10,
    Avalanche excluded by design.
  - **JDK 21 runtime** (`maven:3.9-eclipse-temurin-21`, OpenJDK 21.0.12.1, Testcontainers through the Docker socket):
    `-pl avalanche-integration -am` BUILD SUCCESS, 104 run, 0 failures, 3 optional skipped (includes the F9 fix and its test).
  - Foundry 1.8.7 (pinned image, OpenZeppelin v5.7.0, forge-std `dc647cc`): **55 passed, 1 skipped** (fork suite) after
    adding G1; before it 54 passed, 1 skipped, as in M12 (Foundry counts the invariant contract as one test with three
    invariants, all passing, 64 runs x 64 calls).
  - Slither 0.11.6: 12 results, 3 detectors, identical to the M6 triage. Gas report committed.
  - Frontend (Node 24.19.0): `npm ci`, `tsc -b` clean, **28/28 files, 841 tests pass** (B8).
  - Phase 1 keyless tooling: 5 network-free guard tests pass; LOCAL_SIMULATION run of all 19 plan entries recorded.
- Phase 1 evidence otherwise as in [phase-1](phase-1/README.md).

- **2026-11-04 (current developer machine; no Fuji, Mainnet or database access):**
  - Frontend (Node 24.19.0): `npx tsc -b` clean; `npx vitest run` **29 files, 859 tests pass** (+18 for A1); `npx vite build` built.
  - Gateway on JDK 21 (`maven:3.9-eclipse-temurin-21`, Testcontainers via the Docker socket): full module run **105 tests,
    101 pass, 3 optional skipped, 1 failure in the new `CombinedFaultScenarioTest`**: a wrong intermediate-state assertion in
    the new test (the record was already FINALIZED because a later transaction had mined a block); the assertion was corrected
    to "CONFIRMED or FINALIZED, FINALIZED only with a later block", and the class re-run passes (1/1). All 104 earlier tests
    unchanged and green. **Full module re-run after the fix: 105 run, 0 failures, 3 optional skipped, BUILD SUCCESS.**
  - Phase 1 tooling: `node --test` human-signed 11/11; E2 query validation script run (output committed).
  - Not re-run on 2026-11-04: legacy JDK 17 reactor and Foundry (no change in those modules or contracts).

- **2026-11-04, second update (delegated decisions):**
  - Legacy JDK 17 reactor after A7/A3 (`355e019`): payment 72, marketplace 385, misa 20, solana-integration 10, all pass.
  - After the primary-rail change: the first full reactor run showed marketplace **408 run, 49 errors** (48 in
    `ContractCancellationServiceTest`: its `@DataJpaTest` slice imports `JobServiceImpl` but not the new `PrimaryRailPolicy`;
    1 in the new primary-rail test, which lacked the deliverable/criterion stubs `assignFreelancer` requires). Both were test
    setup gaps, fixed in test code only; the three affected classes then passed **66/66**. A second full reactor run was
    **stopped by the host for low memory** (not a test failure) after payment 72/72. **A single uninterrupted full JDK 17
    reactor pass after the last fix has NOT been obtained yet**; misa and solana-integration are unchanged since `355e019`.
  - **Resolved 2026-11-04 (third run, memory-capped, detached):** full legacy JDK 17 reactor **BUILD SUCCESS, exit 0**: payment
    **72**, marketplace **408**, misa **20**, solana-integration **10**; 0 failures, 0 errors, 0 skipped; not OOM-killed.
  - Foundry: not re-run on 2026-11-04 (no contract change; only `install-deps.sh` pinning, verified separately).
  - Marketplace JDK 17 module runs: 400 pass after A8/F13; 403 pass after the notification slice.
  - V7 on MySQL 8.4 (throwaway local container): Flyway V1 to V7 applied, Hibernate `validate` passed, `AvalancheV7MySqlIT` 2/2.
  - Gateway JDK 21: **110 run, 0 failures, 3 optional skipped** (adds the F13 Anvil regression and the A8 verifier).
  - Frontend (Node 24.19.0): `tsc -b` clean, **29 files, 865 tests pass**, `vite build` built.
  - CI hygiene checks and the forge-std pin fetch: pass locally.

## Known blockers
External only (2026-11-04): B1 and C1 / R1 (human-held funded Fuji wallet, signing device, funding owner: Phase 1 E1, X1, X3,
all Fuji validation and the Phase 3 Fuji demonstration); B6 / E2 (DB operator identity; environment authorized: development
database only); B7 (a real browser wallet extension); B9 (owner `.env` for `docker compose config`); C10 (native Vietnamese copy
review); R6 owner approval of Phase 3 (engineering proceeds only on delegated, reversible, simulation-only items); owner-deferred
O-8 and O-13; C8 legacy rail retirement (irreversible). Engineering items still open: **none independently implementable** (2026-11-04: A2, A6, P3-C4,
drills and the LOCAL clone measurement are done). The fee/tax/payout part of A4 needs an Avalanche off-ramp (new custody path,
owner decision) and F14 needs R12.

## Next recommended action
1. Owner: R6 (Phase 3 approval), R12 (F14 arbiter loss), the Avalanche off-ramp decision for fees/tax/payout (custody), R1
   (Fuji signer and funding), the DB operator (R2).
2. Operators: X1 after R1; X2 once the DB operator is named; B7 browser wallet check.
3. Engineering: re-run the baselines after any owner decision; nothing else is open without a decision.

## Change note (2026-11-04, second update)
Applied the Lead's delegation of engineering decisions (classification table above). Implemented and tested: A1 (complete
apart from B7), A3 (both halves), A7, A8, F12, F13; Phase 3 under delegated authority: settlement notifications (default off)
and the primary-rail policy for new contracts (default SOLANA); CI workflow (not executed). Closed by engineering decision:
A5, C4, C11; E4 documented limitation; E7 explorer verification NOT APPLICABLE until B2. Not done: A2, A6, the fee/tax/payout
part of A4, stuck-escrow reporting (P3-C4), drills, the clone measurement. R6 is still **not owner approved**. No Fuji
transaction, no key, no database other than throwaway local containers, nothing pushed.

## Change note (2026-11-04)
A1 partially implemented (frontend; F13 recorded); A3 gateway half implemented (test only); A7 and the marketplace half of A3
blocked by the 2026-11-04 instruction not to modify Java 17 legacy modules (owner request R11); Phase 1 E1 evidence checker,
E2 query validation, E4 re-check (no verifiable official source; unchanged OPEN); R6 approval package. No contract, Java 17
module, configuration or database change; no Fuji transaction; no database queried; R6 not approved; nothing pushed.
Execution history: [AUTONOMOUS_EXECUTION_LOG.md](AUTONOMOUS_EXECUTION_LOG.md) L-005 to L-010.

## Change note (2026-11-03, owner decision update)
Recorded the owner decision update (R2 development database only; O-2 shared baseline with a LOCAL_SIMULATION-only clone
prototype permitted; O-6 stuck-and-reported accepted; R8 fee, tax/MISA, payout and notification work moved to Phase 3).
Register: A4, A5, B6, C2, C3, C4, C5, E2; Approved decisions O-2, O-6. Synchronized:
[decision-and-evidence-requests.md](decision-and-evidence-requests.md), [phase-3 proposal](phase-3/00-phase-3-proposal.md)
(still NOT APPROVED), [phase-1 acceptance report](phase-1/phase-1-acceptance-report.md),
[db-rail-evidence-request.md](phase-1/db-rail-evidence-request.md), [phase-1/README.md](phase-1/README.md),
[phase-2/09](phase-2/09-unresolved-decisions-and-prerequisites.md), [phase-2/08](phase-2/08-risk-and-rollback-plan.md) R4,
[04](04-migration-roadmap.md), [05](05-risk-register.md), [06](06-implementation-backlog.md),
[README](README.md). Documentation only: no code, contract, configuration or database change; no prototype built; no Phase 3
work started; nothing pushed.

## Change note (2026-11-03)
Recorded the owner response; Phase 1 closure tracking; keyless human-signed E1 tooling (`phase-1/prototypes/human-signed/`)
and its LOCAL evidence; DB operator output format; explorer verification procedure; Phase 3 proposal; CI proposal; decision and
evidence requests; register updates (A2, A8, B1, B4, B8, B10, C1, C9, D1, D4, E1 to E7, F8 to F12, section G).
Code: `EscrowActionService.statusByHash` target check (F9) with a test; compose and `.env.example` expose the reconcile and scan
intervals (F8, defaults unchanged); new Foundry test G1 (no contract change). Corrections: M12 says "Nothing was pushed"
but the 2026-11-02 commits were pushed (`origin/master` equals `1ef0af7`); an audit claim of a Foundry count mismatch was a
counting artifact (Foundry reports the invariant contract as one test). No owner decision was changed; nothing was pushed.

## Change note (2026-11-02 documentation reconciliation)
Updated: this file; `README.md`; `04-migration-roadmap.md`; `05-risk-register.md`; `06-implementation-backlog.md`; `phase-1/README.md`; `phase-2/README.md`, `01`, `02`, `03`, `05`, `06`, `08`, `09`. No source, configuration or migration file was modified; no owner decision was changed.
