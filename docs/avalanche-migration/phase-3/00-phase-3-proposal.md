# Phase 3 proposal: Avalanche as the primary development chain, with simulation quality gates

> **Status: PROPOSED, NOT APPROVED.** Prepared 2026-11-03 at the owner's request (owner response section 1: "Phase 3 means a
> new phase after Phase 2 ... prepare a Phase 3 proposal with objectives, scope, environments and acceptance criteria").
> Nothing in this document is an approved requirement. No Phase 3 implementation, and no contract change, starts before the
> owner approves this scope **and** the decisions listed in section 14. Register item C9 stays OPEN until then.
> Scope constraint: [SIMULATION_SCOPE](../../SIMULATION_SCOPE.md) (LOCAL_SIMULATION and AVALANCHE_FUJI only, Mainnet out of
> scope, no real funds). Status source of truth: [PROJECT_STATUS](../PROJECT_STATUS.md).

## 1. Objectives and expected outcomes
Every objective below is traced to an existing documented item; none is a new feature idea.
| # | Objective | Source in existing documents | Expected outcome |
|---|---|---|---|
| O1 | Make Avalanche the **primary development and demonstration chain** of FreelaX, with Solana kept as the legacy reference | Phase 0 objective ([README](../README.md)); Final phase reinterpreted by the scope amendment ([04](../04-migration-roadmap.md) amendment table: "config switch ... with Solana kept as the legacy reference"); O-14 (Avalanche for new work) | New contracts in a demo environment use the Avalanche rail by configuration; Solana stays readable and selectable for rollback |
| O2 | Complete the **business treatment of an Avalanche settlement** (fees, tax records, payouts, notifications) and the **chain-driven status** of milestones, if the owner places them in Phase 3 | Register A4 (needs C3), A5 (needs C4); [M9](../phase-2/M9-validation-report.md) section 5 | An Avalanche release produces the same simulated business records as the Solana rails, labelled per the scope document |
| O3 | Establish **simulation quality gates**: CI, operational drills, scanner metrics and token-issuer event reporting | Register D1 (CI), D3 (drills, reinterpreted as simulation gates), A6/F6 (metrics); [04](../04-migration-roadmap.md) Phase 6 reinterpreted; O-6 "report stuck-escrow behaviour" | Regressions are caught automatically; pause, rotation and backend-offline release are demonstrated; a stuck escrow is reported, not silent |
| O4 | Take an **evidence-based decision on the escrow isolation model and the blacklist behaviour** and, only if the owner approves a change, deliver it as a new contract version | O-2 (DEFERRED), O-6 (evidence delivered, acceptance OPEN), register C2 and C5 | A recorded O-2 and O-6 decision backed by measurements; the current contract stays untouched unless a change is approved |
| O5 | A **public Fuji demonstration** of the escrow lifecycle with human-operated signing | Scope amendment ("Fuji ... is the public demonstration environment"); register B1 to B4, C1 | A reproducible, human-signed Fuji demo with evidence; no backend Fuji signer (M2 rule) |

## 2. Business rationale
FreelaX's documented purpose is a development simulation of an Avalanche-powered escrow platform
([SIMULATION_SCOPE](../../SIMULATION_SCOPE.md) section 1). After Phase 2 the Avalanche rail exists but sits behind
`avalanche-gateway.enabled=false`, settles without fees, tax or notifications, and has never run on Fuji. Phase 3 would turn
the implemented components into the default demonstration path (O1), make an Avalanche settlement as complete as the legacy
rails (O2), and make the result trustworthy to demonstrate (O3, O5). O4 closes the two contract-design questions that
Phase 2 deliberately left open, so any later contract work rests on a decision instead of an assumption.

## 3. Technical scope (proposed)
- `marketplace-backend`: primary-chain selection for **new** contracts (configuration, default per environment); per-record
  chain labelling; rollback by configuration; O2 work only as decided by C3 and C4.
- `avalanche-integration`: metrics and reporting for the scanner and for token issuer events (blacklist, pause) touching the
  escrow; drill support only where a drill needs it.
- `frontend`: default rail per configuration; remaining legacy labelling; no new wallet type.
- `avalanche-contracts`: **no change** unless O-2 or O-6 approves one (O4); otherwise only tests and evidence.
- Repository-level: CI pipeline per [ci-proposal.md](../ci-proposal.md).

## 4. Explicit exclusions
Mainnet in any form; real funds or real financial settlement; KMS/HSM and key ceremonies (O-13 DEFERRED) unless the owner
decides otherwise; WalletConnect (O-12); any backend signer or backend signing on Fuji (M2); removal of any Solana module,
rail, column or historical record; retirement of a legacy payout rail without the gates G1 to G5 of
[phase-2/10](../phase-2/10-legacy-solana-coexistence.md) (needs E2 database evidence); an external indexer; additional chains
or L1s; a change of the global Java baseline (legacy modules stay on Java 17); any Phase 2 residual relabelled as Phase 3 work
without an owner decision.

## 5. Relevant repositories and modules
`marketplace-backend` (Java 17), `avalanche-integration` (Java 21, web3j 6.1.0), `avalanche-contracts` (Foundry 1.8.7,
solc 0.8.38, OpenZeppelin 5.7.0), `frontend` (React/Vite), `misa-backend` (only if C3 wires tax records), root build and
compose files (additive only, O-16). Solana modules are read-only references.

## 6. Proposed architecture and design alternatives
### 6.1 Primary-chain selection (O1)
Alternative A (proposed): a marketplace property selects the rail for **newly created** contracts per environment
(`payment_network_id` already exists, V6); existing contracts keep their recorded rail, so rollback is a configuration change
that affects new contracts only (matches [05](../05-risk-register.md) "Rollback"). Alternative B: a per-job user choice in
the UI (more UI and test surface; not documented anywhere; not recommended). Alternative C: a global switch that also moves
existing contracts (rejected: contradicts O-14 and the coexistence rules).

### 6.2 O-2: shared immutable escrow (baseline, kept) versus factory/clones
The baseline stays: one shared immutable `FreelaXEscrow` with logically isolated per-escrow records (D2). Trade-offs, using
only measured or code-verified facts:
| Aspect | Shared contract (current) | Factory + EIP-1167 clones |
|---|---|---|
| Blast radius of a USDC blacklist of the escrow address | **Every open escrow freezes** (fork-verified on real Fuji USDC, M6 section 4, `test_realUsdc_blacklistingTheSharedContractFreezesEveryOpenEscrow`); unblacklisting restores all | One clone per escrow, so one frozen escrow per blacklisted address; each clone can still be blacklisted individually |
| Custody model | Logical isolation; invariants fuzzed (`invariant_balanceCoversEveryOpenEscrow`, `invariant_valueIsConserved`, `invariant_terminalStatesAreFinal`) plus `test_dispute_changesOnlyItsOwnEscrow` | Physical per-escrow custody, like the Solana per-escrow vault |
| Gas | Measured 2026-11-03 (test-suite medians, LOCAL, not an Avalanche cost claim): `fund` 224,950; `submit` 91,436; `release` 85,368; deployment size 12,075 bytes ([evidence](../phase-2/evidence/forge-gas-report-escrow.txt)) | **Not measured**; a clone deployment per escrow adds cost to every funding. No figure is claimed until measured (P3-M4) |
| Gateway impact | Submission allow-list = manifest escrow and token (`VerifiedDeployment.allowedTargets`); scanner filters one address | **Architectural change**: dynamic allow-list derived from the factory, per-escrow EIP-712 `verifyingContract`, multi-address scanning, a per-escrow `approve` target for the client, manifest schema change |
| Audit surface | One contract; Slither 12 results, all triaged ([evidence](../phase-2/evidence/slither-escrow-2026-11-03.txt)) | Factory plus implementation; initialisation risks (uninitialised implementation, re-initialisation) added |
| Existing escrows | n/a | A new architecture cannot move open escrows; the old contract must keep serving them until they settle |
Recommendation for the owner: keep the shared baseline for the simulation unless the blacklist blast radius is judged material
for demonstrations; if a decision needs numbers, approve P3-M4 step 1 (a measured prototype in an isolated folder that does
not touch `avalanche-contracts/src`).

### 6.3 O-6: blacklist and stuck-escrow behaviour
Current behaviour (push payout, no privileged path; verified on MockUSDC and on real Fuji USDC through a fork, M6 section 4):
| Case | Who is affected | Funds | Exit today |
|---|---|---|---|
| Freelancer blacklisted at `release` | that freelancer; that client | that escrow's amount stays held; state stays Submitted | unblacklisting, or a dispute resolved as refund to the client, or a mutual refund |
| Freelancer blacklisted at `resolveDispute` (release) | same | held; state stays Disputed | the arbiter can still refund the client |
| Client blacklisted at mutual refund or refund resolution | that client | held; escrow stays open | unblacklisting, or release to the freelancer when eligible |
| **Escrow contract address blacklisted** | **every client and freelancer with an open escrow**; new funding also fails | all open amounts held | **none until unblacklisted** |
| Issuer pause | everyone | all payouts revert, state preserved | wait for unpause |
Alternatives and their security implications:
| Alternative | Effect | Security and compliance implication |
|---|---|---|
| A. Keep push payout, add **detection and reporting** (gateway reports reverts caused by issuer controls and issuer `Blacklisted`/`Pause` events for escrow-related addresses) | Stuck escrows become visible; no contract change | No new privilege; consistent with O-6 "no rerouting or privileged withdrawal". **Recommended** |
| B. Pull payment (settlement credits a claimable balance; the payee withdraws later) | The escrow reaches its final state even if the payee is blacklisted; funds wait in a claimable balance | New withdrawal function and accounting to audit; does **not** help when the contract itself is blacklisted; contract change (needs approval) |
| C. Payee-chosen alternative payout address (signed by the payee) | A blacklisted payee could direct funds elsewhere | Would let a sanctioned address move value around the issuer's control: a compliance concern; not recommended |
| D. Admin or arbiter reroute or rescue | Funds can always leave | **Rejected by the existing role rules** (D3, `02` section 2: no role may reach escrowed funds) |
| E. Clones (O-2) | Limits a contract-address blacklist to one escrow | See 6.2 |
Recommendation: accept the stuck-and-reported behaviour (alternative A) as the O-6 outcome for the simulation; consider B only
if the owner wants terminal states independent of token transfers.

## 7. Dependencies on Phase 1 and Phase 2
Entry criteria (owner response section 1): all remaining **approved** Phase 2 implementation tasks complete; external
validation blockers closed or explicitly documented; unresolved owner decisions preserved in the register. As of 2026-11-03
the approved Phase 2 implementation residuals are A1 (frontend action screens), A2 (cross-service run), A3 (combined fault
injection), A7 (Solana-literal clean-up) and the newly recorded A8 (EVM wallet linking, see PROJECT_STATUS), with A6 optional.
A4 and A5 need C3 and C4. O5 additionally depends on Phase 1 E1 (human-signed Fuji run) and on B1, B2 and C1.

## 8. Environments
| Environment | Use in Phase 3 | Notes |
|---|---|---|
| LOCAL_SIMULATION | all development, CI, drills, measurements | Anvil 1.8.7, MockUSDC, ephemeral development signers (M2 permits them here only) |
| AVALANCHE_FUJI | O5 only, after C1 and B1 | human-operated signing only; Circle test USDC needed only for token-moving steps; host allow-list unchanged (O-8 DEFERRED) |
| any other | none | Mainnet is out of scope; a local Fuji fork is LOCAL_SIMULATION |

## 9. Security and operational considerations
No backend key or signing on Fuji (M2); development signers stay ephemeral and LOCAL-only; no change to the compiled chain and
host allow-lists without review; environment and chain id on every record; the backend authorization signer still cannot
move funds; pause still blocks funding only; no role gains access to escrowed funds under any alternative; secrets never in
Git; every new configuration flag defaults to the current behaviour; rollback by configuration is rehearsed (P3-M2).

## 10. Acceptance criteria (proposed, per objective)
- O1: with the primary-chain property set to Avalanche in LOCAL_SIMULATION, a newly created contract is on the Avalanche rail
  and completes fund to release on a single running stack; with the property reverted, new contracts use the previous rail;
  existing contracts of both chains remain readable; all existing Solana and Avalanche suites pass unchanged.
- O2 (if placed in Phase 3): an Avalanche release creates exactly the records decided by C3, labelled "simulated", once per
  settlement; status transitions follow the C4 decision; FINALIZED-only and CHAIN_MISMATCH rules still hold.
- O3: CI runs on every change (section 11); the pause, signer-rotation, backend-offline-release and arbiter-loss drills are
  executed on LOCAL_SIMULATION with recorded evidence; scanner progress and issuer events are observable.
- O4: O-2 and O-6 decisions recorded with the evidence used; if a contract change is approved, it ships as a new contract
  version with its own manifest version, all existing tests plus new ones pass, Slither is triaged, and the old version keeps
  serving open escrows.
- O5: a human-signed Fuji deployment, role grants and one complete escrow lifecycle with evidence files; no backend key used.

## 11. Test strategy
Keep the Phase 2 layers ([phase-2/07](../phase-2/07-test-strategy.md)): Foundry unit, fuzz, invariants and fork tests; JUnit
with Testcontainers (Anvil, MySQL); Vitest. Add: a scripted single-stack run (marketplace, gateway, Anvil, MySQL) as the O1
acceptance test; drill scripts with evidence files; CI on JDK 17 (legacy) and JDK 21 (Avalanche) with Docker. Fuji evidence is
labelled FUJI-OBS and never mixed with LOCAL-SIM results.

## 12. Implementation milestones (proposed)
| ID | Scope | Depends on | Exit evidence |
|---|---|---|---|
| P3-M0 | Owner approval of this scope; decisions of section 14; verification of the entry criteria (section 7) | owner | decision record in PROJECT_STATUS |
| P3-M1 | Business completion of the rail (O2), only if the owner moves A4 and A5 here | C3, C4 | M1 report |
| P3-M2 | Avalanche as the primary development chain (O1), including the rollback rehearsal | P3-M0, A2 and A8 done | single-stack run evidence |
| P3-M3 | Simulation quality gates (O3): CI, drills, metrics, issuer-event reporting (O-6 alternative A) | P3-M0; C7 for drill scope | CI run links, drill evidence |
| P3-M4 | O-2/O-6 design gate (O4): step 1 measured clone prototype in an isolated folder (only if approved); step 2 contract change only if a decision requires it | C2, C5 | measured comparison; decision record |
| P3-M5 | Public Fuji demonstration (O5) | C1, B1, E1 | FUJI-OBS evidence files |

## 13. Risks and mitigations
| Risk | Mitigation |
|---|---|
| Phase 2 residuals leak into Phase 3 under a new name | entry gate P3-M0 checks the register; residuals stay A-items unless the owner moves them |
| Primary switch breaks Solana flows | new-contracts-only selection; full Solana regression in CI; rollback rehearsal |
| O-2 change multiplies gateway complexity | measured prototype first; no change without decision; old contract keeps serving open escrows |
| Fuji work stalls on human availability | O5 is isolated in its own milestone; everything else runs on LOCAL_SIMULATION |
| Wording implies real money | scope document terminology; review of the Vietnamese copy (C10) |
| Toolchain drift (JDK 25, Node 24 on developer machines) | CI pins JDK 17/21 and Node; known JDK 23+ Lombok note recorded (PROJECT_STATUS) |

## 14. Outstanding owner decisions (needed before P3-M0 can close)
C9 approve or change this scope; C1 Fuji human-operated signing procedure (for O5 and Phase 1 E1); C2 (O-2) shared versus
clones; C5 (O-6) accept stuck-and-reported or choose an alternative; C3 and C4 (and whether A4 and A5 move to Phase 3);
C7 (O-13) drill scope; C10 Vietnamese copy review; O-8 provider choice (only if O5 needs a decision); confirmation that "primary
chain" means the default rail for **new** contracts in development and demo environments (O-14 and the scope amendment).
