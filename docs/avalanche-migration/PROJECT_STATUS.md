# Avalanche migration: project status

Last updated: 2026-11-02 (after M12, documentation reconciled). Scope: [SIMULATION_SCOPE](../SIMULATION_SCOPE.md) (LOCAL_SIMULATION and AVALANCHE_FUJI only; Mainnet out of scope; no real funds).

This file is the single source of truth for status, owner decisions and outstanding work. Other documents keep their historical text and point here. Final acceptance classification: [phase-2/M12-validation-report.md](phase-2/M12-validation-report.md).

## Current phase and milestone
- **Phase 2 (M1 to M12): IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING.** This is **not** "fully validated" and **not** "Phase 2 accepted by the owner". Several acceptance criteria are BLOCKED, PARTIAL, NOT IMPLEMENTED or NOT VALIDATED; each is in the Outstanding Items Register below.
- **Phase 1: PARTIAL** (funded Fuji wallet and read-only database evidence still blocked; not waived). See register section E.
- **Phase 3:** **no approved scope exists.** The label "Phase 3" in older documents (README D2, `04`, `07`, Phase 1 reports) means the Phase 0 roadmap's Solidity phase, which was executed inside P2-M5 to M7. No post-Phase-2 roadmap has been written or approved, and none is invented here. A next phase needs an explicit owner scope decision.
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
| P2-M12 | final regression and acceptance report written and committed; **final classification: IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING** |

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
| O-2 | DEFERRED to the M5 exit (measured gas and security evidence); keep factory/clones possible. **Still deferred after M12** |
| O-3 | per-escrow arbiter snapshot only; no reassignment, no implicit global rotation |
| O-4, O-5 | preserve verified existing behaviour; invent no rules |
| O-6 | evaluate USDC blacklist behaviour on a Fuji fork at M6; no rerouting or privileged withdrawal; report stuck-escrow behaviour before final acceptance. **Evidence delivered (M6 report section 4); the owner's final acceptance is still open** |
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

## Outstanding Items Register
Status words: OPEN, BLOCKED (needs a human or external action), DEFERRED (owner deferred), NOT IMPLEMENTED, PARTIAL, NOT VALIDATED. "Evidence" points to the existing document and section that records the item. Nothing here is decided by this register.

### A. Implementation work remaining (no owner decision needed to start unless noted)
| ID | Item | Status | Evidence |
|---|---|---|---|
| A1 | Frontend action screens: submit, revise, dispute, resolve (admin), extension-request, mutual-refund; legacy Solana labelling | NOT IMPLEMENTED | M11 section 2; M12 section 2 |
| A2 | Cross-service run: marketplace plus gateway plus Anvil plus MySQL in one environment | NOT IMPLEMENTED | M9 section 4 item 3; M12 section 2 |
| A3 | Combined fault-injection scenario (timeout, 429, WebSocket drop, duplicate, missed event, revert, nonce conflict, restart, invalid signer/env/contract) at marketplace level | PARTIAL (covered per layer only) | M12 section 2; M3, M4, M7, M8 reports |
| A4 | Fees, MISA tax records, payouts and notifications for an Avalanche release | NOT IMPLEMENTED (needs decision C3) | M9 section 4 item 1 and section 5 |
| A5 | Mirror of on-chain Submitted/Revision/Disputed transitions into milestone status | NOT IMPLEMENTED (needs decision C4) | M9 section 4 item 2 |
| A6 | Scanner metrics and alerting | NOT IMPLEMENTED (simulation: optional) | M4 section 4 item 3 |
| A7 | Remaining Solana-literal clean-up in MISA/tax flows and explorer labelling in existing screens | NOT IMPLEMENTED | M9 section 4 item 4; `06` rows 27, 28 |

### B. External validation and human actions
| ID | Item | Status | Evidence |
|---|---|---|---|
| B1 | Funded ephemeral Fuji wallet (testnet AVAX, optional Circle test USDC) | BLOCKED (interactive faucets) | [phase-1/fuji-funding-runbook.md](phase-1/fuji-funding-runbook.md); `phase-2/09` section 2 |
| B2 | Fuji deployment, role grant, smoke transactions, authorized-action prepare/execute on Fuji | BLOCKED (needs B1 and decision C1) | M7 section 3 item 1; M8 section 4 item 1; M2 section 5 item 1 |
| B3 | Fuji calibration: fee-bump threshold, own-transaction timings, near-deadline inclusion, finalized-tag path, real provider limits and WebSocket stability | BLOCKED (needs B1, B2) | M3 section 7 item 1; M4 section 4 item 1; `phase-2/08` R8 |
| B4 | Fuji deadline-boundary test with real transactions (submit with deadline = latest + N seconds, record included block timestamp) | BLOCKED (needs B1, B2) | [phase-1/timestamp-deadline-experiments.md](phase-1/timestamp-deadline-experiments.md) line 44 ("Phase 3 gate" label) |
| B5 | Optional Fuji fork test (`UsdcFork.t.sol`) re-run with `FUJI_RPC` | OPEN (optional; ran once in M6) | M6 section 6 item 2; M12 section 1 |
| B6 | Read-only database evidence for legacy rails (DB operator) | BLOCKED | [phase-1/db-rail-evidence-request.md](phase-1/db-rail-evidence-request.md); needed only before any legacy-rail change |
| B7 | Browser wallet test with a real wallet extension | NOT IMPLEMENTED (no extension available) | M10 section 4; M11 section 2 |
| B8 | Node upgrade to 22.12 or later (or a dependency decision), then re-run the 13 frontend suites that cannot load on Node 22.10 (ESM-only `uuid` via `@solana/web3.js`); Solana frontend regression is meanwhile **unverifiable** | PARTIAL / NOT VALIDATED | M10 section 3; M12 section 1; reproduced without Phase 2 changes |
| B9 | `docker compose config` validation (needs the owner's ignored `.env`, never read here) | NOT VALIDATED | M12 section 1 |
| B10 | JDK 21 default on developer machines (currently JDK 20 default; per-process override; `mvn` not on `PATH`) | OPEN | M1 section 7 |

### C. Architectural and business decisions (owner)
| ID | Decision | Status | Evidence |
|---|---|---|---|
| C1 | **Fuji human-operated signing procedure** (who holds Fuji AVAX, how deployment and smoke transactions are signed when the backend may hold no Fuji signer) | OPEN; no policy invented; gates B2 to B4 | M2 section 5; M7 section 3 |
| C2 | O-2 shared immutable contract versus factory/clones | DEFERRED | `phase-2/09` section 4; M5 section 4 |
| C3 | Fee, tax, payout and notification treatment for an Avalanche release (labelled mocks, wired to existing stages, or left off the rail) | OPEN | M9 section 5 |
| C4 | Scanner-driven milestone status mirror versus marketplace-driven status | OPEN | M9 section 5 |
| C5 | O-6 final acceptance of the stuck-escrow behaviour under a USDC blacklist | OPEN (evidence delivered) | M6 section 4 |
| C6 | O-8 Fuji RPC provider(s) | DEFERRED | `phase-2/09` O-8 |
| C7 | O-13 external audit or key ceremony | DEFERRED | `phase-2/09` O-13 |
| C8 | Whether and how legacy rails retire (needs B6) | OPEN | `07` open questions; ADR 0001 |
| C9 | Scope of any phase after Phase 2 ("Phase 3"): none approved | OPEN (no scope exists) | this file, "Current phase" |
| C10 | Review of the Vietnamese UI copy | OPEN | M11 section 2 |
| C11 | Whether a gateway-driven local deployment (contract creation through the submission pipeline) is wanted | OPEN | M3 section 7 item 5; M7 section 3 item 2 |

### D. Quality gates (simulation; not production-readiness claims)
| ID | Item | Status | Evidence |
|---|---|---|---|
| D1 | CI with a JDK 21 step (`mvn -B -pl avalanche-integration -am test` plus the Solana baseline) on a Docker-capable runner; no CI configuration exists | OPEN | M1 section 7; M3 section 7 item 6 |
| D2 | Testcontainers skip-without-Docker behaviour | NOT VALIDATED | M3 section 7 item 6 |
| D3 | External audit, key ceremony, KMS integration, signer-rotation and pause drills, monitoring, rollback drill (old Phase 6 and Final-phase items) | DEFERRED / owner-gated by C7; Mainnet items withdrawn | `04` and `06` amendment tables |
| D4 | Slither triage is the only security review so far; no external review | recorded | M6 report; O-13 |

### E. Phase 1 remaining validation requirements (Phase 1 stays PARTIAL)
| ID | Item | Status | Evidence |
|---|---|---|---|
| E1 | Funded Fuji wallet; own-transaction experiments | BLOCKED (= B1) | `06` rows 3 and 7; Phase 1 acceptance report |
| E2 | Read-only DB evidence | BLOCKED (= B6) | `06` row 2 |
| E3 | RPC provider choice | DEFERRED (= C6) | `06` row 4 |
| E4 | Official USDC.e address source (deny-list holds the two known Mainnet-network addresses only) | OPEN | `phase-2/09` section 2; M7 section 3 item 4 |
| E5 | Public RPC rate-limit threshold; faucet limits | OPEN (INCONCLUSIVE) | `06` row 1; `phase-2/09` section 2 |
| E6 | Accepted-but-unexecuted observation; `eth_getLogs` before execution; cause of one by-hash miss; server-initiated WebSocket drops | OPEN (INCONCLUSIVE; handled by conservative defaults) | `phase-2/09` section 2; Phase 1 acceptance report |
| E7 | ACP-283 status; FiatToken version; explorer/verification flow | OPEN | `06` row 1 |

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

## Implementation completion versus full validation completion
- **Implementation completion (reached, with the NOT IMPLEMENTED items A1, A4, A5):** contracts, gateway, marketplace rail behind a flag, wallet adapter and a partial panel exist, pass local tests and keep Solana unchanged.
- **Full validation completion (not reached):** needs B1 to B4 and C1 (Fuji), B7 to B9, A2 (cross-service run), D1, owner acceptance of C2, C5, C6, C7, and the Phase 1 items E1 and E2. Phase 1 stays PARTIAL until then.

## Validation results
- M1: Solana baseline unchanged (16/16 before and after); `avalanche-integration` 24 tests (22 pass, 2 optional skipped); real Anvil and Fuji read-only runs pass; Phase 1 Jackson failure reproduced and fixed by `jackson-annotations:2.22` (module only).
- M2: 43 module tests (41 pass, 2 optional skipped); Fuji with signers enabled aborts with `SIGNER_FORBIDDEN_ENVIRONMENT` without any RPC call.
- M3: 71 module tests (69 pass, 2 optional skipped); Phase 1 scenarios S1 to S6 and S8 on real Anvil and MySQL; 16 threads, 50 submissions never reused a nonce.
- M4: 85 module tests (83 pass, 2 optional skipped). M5: 29 Foundry tests. M6: 49 Foundry tests, 89 gateway tests. M7: 98 gateway tests. M8: 104 gateway tests (101 pass, 3 optional skipped), marketplace 364 pass. M9: marketplace 381 pass. M10: 24 Vitest tests. M11: 10 Vitest tests.
- **M12 (2026-11-02):** full reactor on JDK 21 with Docker BUILD SUCCESS (payment 72, marketplace 381, misa 20, Solana gateway 10, avalanche-integration 104 with 3 optional skipped); JDK 17 reactor of the existing modules BUILD SUCCESS; Foundry 54 pass, 1 optional skipped; frontend `tsc` and `vite build` clean, 345 Vitest tests pass in 15 files, 13 files cannot load on Node 22.10 (B8); secret scan clean; Mainnet references only in deny-lists. Details: [phase-2/M12-validation-report.md](phase-2/M12-validation-report.md).
- Phase 1 evidence otherwise as in [phase-1](phase-1/README.md).

## Known blockers
B1 (funded Fuji wallet, human), C1 (Fuji signing procedure, owner), B6 (DB operator), B8 (Node version), B9 (owner `.env`), B10 (JDK default). See the register for the full list.

## Next recommended action
Owner decisions C1, C3, C4 and C5 unblock the largest remaining items; B1 plus C1 unblock all Fuji validation; B8 restores the Solana frontend regression. No next-phase scope is proposed here (C9).

## Change note (2026-11-02 documentation reconciliation)
Updated: this file; `README.md`; `04-migration-roadmap.md`; `05-risk-register.md`; `06-implementation-backlog.md`; `phase-1/README.md`; `phase-2/README.md`, `01`, `02`, `03`, `05`, `06`, `08`, `09`. No source, configuration or migration file was modified; no owner decision was changed.
