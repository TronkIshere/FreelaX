# 06. Implementation backlog (historical plan; see the Phase 2 status mapping at the end)

> **Scope amendment (2026-11-01):** FreelaX is a development simulation with no real funds ([SIMULATION_SCOPE](../SIMULATION_SCOPE.md)). Supported environments: LOCAL_SIMULATION and AVALANCHE_FUJI only. **Mainnet is out of scope.** Wherever this document mentions Mainnet, production, audits for launch, key ceremonies or a Mainnet cutover, read it as historical planning; see the amendment table at the end of [04](04-migration-roadmap.md) and the guards in [phase-1/simulation-environment-guards.md](phase-1/simulation-environment-guards.md). Decisions D1 to D4 are unchanged.

Ordered by dependency; none may begin before Phase 0 approval. Priority P0 critical path, P1 required, P2 optional. "Expected files" are proposals. Phases: [04](04-migration-roadmap.md). Revision 2 adds the contract and signer security tasks (rows 12-18, 27-29, 35).

## Phase 1 execution status (updated after Phase 1; evidence in [phase-1/](phase-1/README.md))
Only Phase 1 rows are updated. Phase 2+ rows are unchanged and **not started**. "Done" requires evidence; see [phase-1-acceptance-report.md](phase-1/phase-1-acceptance-report.md).
| # | Phase 1 status | Notes |
|---|---|---|
| 1 | **PARTIAL** | Verified live: chain ids, official Fuji RPC, USDC addresses/code (Circle page is the only official address source; behaviour verified on a fork), EVM fork (Cancun, Fuji only), `eth_getLogs` ranges, `newHeads` timing, EIP-2612/EIP-3009 behaviour. Still open: public rate limits, official USDC.e address, Mainnet EVM fork, ACP-283 status, FiatToken version, explorer/verification flow, faucet limits. Delivered as `phase-1/*.md`, not a single `environment.md`. |
| 2 | **NOT DONE** | Needs DB access and an operator; out of this phase's reach. |
| 3 | **BLOCKED** | Avalanche/Circle faucets are interactive web flows; no funded Fuji wallet exists. Read-only `balanceOf` works on any address. |
| 4 | **NOT DONE** | Business/vendor decision; two free public RPCs measured, no KMS tested. |
| 5 | **DONE (prototype location)** | `forge build`/`forge test` pass with explicit `evm_version = "cancun"` in `phase-1/prototypes/solidity-foundry/`. `avalanche-contracts/` was deliberately **not** created (Phase 3 decision). |
| 6 | **DONE with a mandatory workaround** | web3j 6.1.0 + Boot 3.5.16 fails at runtime without `jackson-annotations:2.22`. See [java-web3j-evaluation.md](phase-1/java-web3j-evaluation.md). |
| 7 | **PARTIAL** | Measured read-only on Fuji: acceptance to receipt, acceptance to `finalized` (5.1-8.7 s), `newHeads` vs acceptance, `block.timestamp` replay. **Not done** (no funds): own-transaction timings, accepted-but-unexecuted observation, deadline race on Fuji. |

| # | Task | Phase | Depends on | Priority | Modules | Expected files | Acceptance |
|---|---|---|---|---|---|---|---|
| 1 | Re-verify all S/U facts (chain ids, USDC addresses and code, EVM version, RPC limits, `newHeads`) | 1 | approval | P0 | docs | `docs/avalanche-migration/environment.md` | every fact has two sources or "unverifiable" |
| 2 | Run ADR SQL evidence for rails (read-only, operator-run) | 1 | DB access | P0 | docs | evidence note | rail data mix known |
| 3 | Fuji wallets, AVAX, Circle test USDC | 1 | 1 | P0 | none | none | `balanceOf` works |
| 4 | Choose RPC providers and KMS vendor | 1 | none | P0 | docs | decision note | accounts exist |
| 5 | Foundry project skeleton with explicit `evm_version` | 1 | 1 | P0 | contracts | `avalanche-contracts/foundry.toml` | `forge build` passes |
| 6 | web3j 6.x dependency-tree check on Java 21 / Boot 3.5.16 | 1 | none | P0 | build | check note | no conflicts |
| 7 | Fuji experiments: submission to receipt to `finalized` timing; logs before execution; accepted-but-unexecuted visibility; `block.timestamp` semantics | 1 | 3 | P0 | docs | experiment log | results recorded in [07](07-open-questions.md) |
| 8 | `avalanche-integration` module skeleton + config validation | 2 | 1, 6 | P0 | new module, root pom | module sources | module builds alone |
| 9 | Chain-id and asset validation (`(chainId, address)`, decimals, USDC.e rejected) | 2 | 8 | P0 | module | classes + tests | wrong chain or token rejected |
| 10 | Signer interface with per-role key references (authorization signer, arbiter, relayer); dev signer; KMS signer; Mainnet guard | 2 | 4, 8 | P0 | module | classes + tests | KMS signature recovers the expected address; no role can use another role's key |
| 11 | DB-backed nonce allocator and transaction tracking table (fields in [03](03-target-architecture.md) section 8) | 2 | 8 | P0 | module | Flyway V1 | concurrent allocation and replacement tests |
| 12 | Gas strategy, submit, receipt mapping, event decoding, error taxonomy | 2 | 8-11 | P0 | module | classes + tests | Fuji transfer e2e; accepted or pending never reported as success |
| 13 | Design doc: shared vs factory gate (blacklist blast radius, verifiability, audit surface, operations) | 3 | 1, 7 | P0 | docs | `docs/avalanche-migration/escrow-isolation-gate.md` | decision recorded |
| 14 | Design doc: EIP-712 authorization spec (domain, typed data for fund/submit/revision/mutual refund, nonce and deadline rules) | 3 | 13 | P0 | docs | spec doc | reviewed |
| 15 | Design doc: role model and key management (roles, rotation, emergency, recovery, arbiter snapshot vs role) | 3 | 4 | P0 | docs | spec doc | reviewed |
| 16 | Escrow contract implementing the Anchor mapping ([03](03-target-architecture.md) section 7), roles, pause on funding only | 3 | 5, 13-15 | P0 | contracts | `src/` | all mapping rows implemented |
| 17 | Unit tests: one per mapping row, exact timing boundaries, bounds, negative role tests, EIP-712 replay tests | 3 | 16 | P0 | contracts | `test/` | all pass |
| 18 | Fuzz and invariant tests ([03](03-target-architecture.md) section 10 invariants), Slither | 3 | 16 | P0 | contracts | tests | invariants hold |
| 19 | Deploy scripts, Fuji deployment, explorer verification | 3 | 16 | P1 | contracts | `script/` | verified on explorer |
| 20 | Neutral port extension: neutral escrow status enum, `TransactionReference`, `PreparedTransaction` | 4 | 14, 16 | P0 | marketplace | `blockchain/*` | Solana tests unchanged |
| 21 | Move `UnifiedUsdFundingService` fully behind the port (including on-ramp strategy) | 4 | 20 | P0 | marketplace | service changes | existing tests pass |
| 22 | `AvalancheGatewayAdapter` + `AvalancheClient` | 4 | 12, 20 | P0 | marketplace | adapter + tests | adapter tests |
| 23 | Backend authorization service: produce EIP-712 authorizations only from MySQL-recorded terms (72h, maxRevisions, hashes) | 4 | 14, 22 | P0 | marketplace/module | service + tests | authorizations equal ledger values; none for unrecorded evidence |
| 24 | Additive Flyway migration (chain id, tx hash, nonce, block) | 4 | 20 | P0 | marketplace | migration V6+ | verified on a DB copy; legacy rows unaffected |
| 25 | Log scanner and reconciliation on existing schedulers | 4 | 22, 24 | P0 | marketplace/module | scanner | recovery test |
| 26 | Relayer job for permissionless release (convenience only) | 4 | 22 | P1 | marketplace | scheduler | release works with the relayer off |
| 27 | Move `SolanaEscrow*`, `Contract*`, `PaymentFlowService` literals behind the port | 4 | 20 | P1 | marketplace | service changes | regression |
| 28 | Parametrise MISA chain literal and explorer URL per record | 4 | 20 | P1 | marketplace | small edits | tests |
| 29 | Frontend neutral wallet interface + viem/wagmi adapter | 5 | 22 | P0 | frontend | wallet files | unit tests |
| 30 | Chain detection and switch, approve + fund UI with authorization, status | 5 | 29 | P0 | frontend | components | Fuji browser e2e |
| 31 | Fuji E2E matrix (fund, release, refund, dispute, claim, extension, revision, mutual refund) | 6 | 25, 30 | P0 | tests | e2e scripts | all pass |
| 32 | Fault-injection suite (RPC outage, delayed execution, accepted-not-executed, missing receipt, duplicates, nonce, revert, deadline race) | 6 | 25 | P0 | tests | tests | all pass |
| 33 | Drills: signer rotation, arbiter loss recovery, emergency pause, backend and relayer offline release | 6 | 31 | P0 | ops | runbooks | drills passed |
| 34 | Monitoring, alerts, runbooks, rollback drill | 6 | 31 | P0 | ops | runbooks | drill passed |
| 35 | External contract audit (scope includes roles and EIP-712) | 6 | 16 | P0 | external | report | findings resolved |
| 36 | Mainnet deploy, config, primary switch, rollback test | Final | 31-35 | P0 | all | runbooks | gates passed |

Parallelizable: 5 and 6 with 3 and 4; 8-12 with 13-19 once 13-15 are approved; 29 with 25-28 once 20 is frozen. Critical path: 1 -> 7 -> 13 -> 16 -> 17 -> 22 -> 25 -> 31 -> 35 -> 36.

## Scope amendment (2026-11-01, simulation-only constraint)
Authority: [SIMULATION_SCOPE](../SIMULATION_SCOPE.md). Rows above are historical; these statuses override. No row is started.
| # | Amendment |
|---|---|
| 10 | Replace "Mainnet guard" by an **environment allow-list guard** (reject 43114 and all unlisted chain ids); KMS signer becomes **optional** (owner to confirm); dev signer stays the default for LOCAL_SIMULATION and AVALANCHE_FUJI |
| 19 | Fuji deployment remains; explorer verification optional |
| 31 | Fuji and Anvil e2e remain; "real amounts" never apply |
| 33, 34 | Drills reinterpreted as simulation quality drills; scope to confirm |
| 35 | External audit: **owner decision required** (a simulation may not need one) |
| 36 | **WITHDRAWN** (Mainnet deploy, primary switch with Mainnet). Replaced, if the owner wants it, by a development-only configuration switch task |
| new | Environment/record labelling: `environment`, `isSimulated` on API and persisted chain records; UI terminology per the scope doc (Phase 4/5) |
| new | Simulated USDC token for LOCAL_SIMULATION and a deployment manifest (Phase 3) |

## Phase 1 Part B status (2026-11-01)
| # | Status after Part B |
|---|---|
| 1 | **PARTIAL** (remaining: official USDC.e address, public rate-limit threshold, faucet limits; Mainnet items are out of scope) |
| 2 | **BLOCKED**: no safe read-only DB access; operator request prepared ([db-rail-evidence-request.md](phase-1/db-rail-evidence-request.md)) |
| 3 | **BLOCKED**: interactive faucets; runbook and guarded script ready ([fuji-funding-runbook.md](phase-1/fuji-funding-runbook.md)) |
| 4 | **NOT DONE**: owner decision (RPC provider(s); KMS deferred by recommendation) |
| 5 | **DONE** (prototype location) |
| 6 | **DONE**: web-context matrix; Java 21 + 6.1.0 + annotations 2.22, Java 17 + 4.13.0 |
| 7 | **PARTIAL**: Fuji read-only measurements done; own-transaction experiments **BLOCKED** (task 3) |
| new (Phase 2 inputs) | environment guard specification refined; lifecycle state model (8 scenarios) validated by prototype; DER/low-s conversion spec validated locally; per-host pacing and 429 handling required in the RPC client |

## Owner decisions after Phase 1 Part B (2026-11-01)
| # | Update |
|---|---|
| 4 | KMS vendor: **OWNER-DECIDED: deferred**; RPC provider choice remains **OPEN** |
| 6 | **DONE**; Java 21 + web3j 6.1.0 **OWNER-DECIDED** (override to be re-validated in the actual FreelaX context) |
| 10 | Dev signer is the implementation target (ephemeral, per role, per environment); KMS signer **deferred** |
| 3, 7 | still **BLOCKED / PARTIAL** (funded Fuji wallet) |
| 2 | still **BLOCKED** (DB operator) |

## Phase 2 execution mapping (2026-11-02)
The rows above are the original plan and were never individually re-marked. Their outcome is recorded here; evidence is in `phase-2/M*-validation-report.md` and the open items are in the Outstanding Items Register of [PROJECT_STATUS](PROJECT_STATUS.md). Owner amendments (Mainnet withdrawn, KMS deferred, audit owner-gated) still apply.
| Rows | Outcome |
|---|---|
| 1 to 7 (Phase 1) | unchanged: see the Part B tables above; rows 2, 3, 7 BLOCKED or PARTIAL (register section E) |
| 8, 9 | done (M1) |
| 10 | done for LOCAL_SIMULATION development signers (M2); KMS signer deferred; no Fuji signer by owner rule |
| 11, 12 | done (M3); Fuji transfer end to end BLOCKED |
| 13 | O-2 gate: **DEFERRED**, no decision (M5) |
| 14, 15 | done within the contract and gateway work (M5, M6); arbiter snapshot per O-3 |
| 16 to 18 | done (M5, M6): Foundry tests, invariants, Slither triaged |
| 19 | Anvil deployment done (M7); **Fuji deployment BLOCKED** |
| 20 to 25 | done behind a disabled flag (M8, M9); fee, tax, payout and notification wiring NOT implemented (row 27, 28 partially) |
| 26 | relayer job: not built; release is permissionless and available through the gateway |
| 27, 28 | **NOT IMPLEMENTED** for the Avalanche rail (register A4, A7) |
| 29 | done without viem (dependency-free EIP-1193 adapter, M10) |
| 30 | **PARTIAL** (fund, extension-approve, release only; no browser or Fuji e2e) (M11) |
| 31, 32 | **PARTIAL**: layer-level coverage on Anvil; no cross-service run; Fuji part BLOCKED (M12) |
| 33, 34 | owner-gated simulation quality drills: not performed |
| 35 | owner decision O-13: DEFERRED |
| 36 | WITHDRAWN (Mainnet) |
