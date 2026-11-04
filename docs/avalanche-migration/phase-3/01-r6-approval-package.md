# R6 approval package: Phase 3 technical design, backlog and decisions

> **Status: PREPARED FOR OWNER DECISION. R6 is PENDING; Phase 3 is NOT APPROVED.** Prepared 2026-11-04 by the engineering
> agent. Nothing here is an approved requirement, and no Phase 3 source code exists. Scope proposal:
> [00-phase-3-proposal.md](00-phase-3-proposal.md) (objectives O1 to O5, milestones P3-M0 to P3-M5); this file adds the
> engineering detail needed to decide. Status source of truth: [PROJECT_STATUS](../PROJECT_STATUS.md).
> **Binding inputs (owner decisions, not reopened here):** O-2 shared immutable escrow is the baseline, factory/clone prototype
> LOCAL_SIMULATION only; O-6 stuck-and-reported, no privileged withdrawal, bypass or automatic rerouting; R8 fee, tax/MISA,
> payout and notification work (A4) belongs to Phase 3; M2 no backend signer or signing on Fuji; SIMULATION_SCOPE (no real
> funds, no Mainnet, no real fiat settlement, banking integration or tax remittance).

## Implementation record under delegated authority (2026-11-04, second update)
R6 is **still not owner approved** (since 2026-11-04: ENGINEERING EXECUTION AUTHORIZED UNDER DELEGATED AUTHORITY — OWNER APPROVAL PENDING). Under the Lead's delegation of engineering decisions, the following reversible,
simulation-only items were built (each defaults to current behaviour):
| Capability | State | Evidence |
|---|---|---|
| P3-C1 (A4) notifications | IMPLEMENTED, flag `avalanche-gateway.settlement-notifications` default off | marketplace tests (3) |
| P3-C1 (A4) fee, tax/MISA, payout | **payout: non-custodial adapter IMPLEMENTED 2026-11-04** (default off, LOCAL mock partner only, [03](03-avalanche-payout-adapter.md)); fee and tax NOT DEFINED (owner); real partner external; rail-aware tax label done (A7) | marketplace payout tests (19), V8 IT, gateway transfer verification, A2 run |
| R12 / F14 two-party settlement of a Disputed escrow | **isolated LOCAL variant IMPLEMENTED, NOT ADOPTED** (conflicts with O-5 question 8; baseline unchanged) | [lab](prototypes/escrow-mutual-settlement/README.md), 72 Foundry tests |
| P3-C2 primary rail | IMPLEMENTED, `marketplace.primary-rail` default SOLANA, fail closed | `PrimaryRailPolicyTest`, `JobAccessAndDiscoveryServiceImplTest` |
| P3-C3 CI | workflow added, not executed | `.github/workflows/avalanche-ci.yml` |
| P3-C4 stuck-escrow reporting | IMPLEMENTED (read-only, O-6) | gateway, marketplace, frontend tests; A2 live run |
| P3-C5 (F13) wallet mutual refund | IMPLEMENTED | gateway Anvil regression, marketplace tests, V7 IT |
| P3-C6 clone measurement | DONE (LOCAL measurement only) | `prototypes/escrow-clone-lab` |
| P3-C7 Fuji demonstration | EXTERNAL (C1, B1, E1) | - |
Decisions D-A, D-C, D-D, D-E, D-F, D-H: ENGINEERING DECIDED (PROJECT_STATUS table). D-G: EXTERNAL. D-B: engineering chose the
option; only the notification part is built.

## 0. One-page summary for the owner
**What approving R6 starts:** P3-M0 (entry checks) then, in dependency order, the business completion of the Avalanche rail
(O2), CI and simulation drills (O3), Avalanche as the default development rail for new contracts (O1), an optional
LOCAL-only clone measurement (O4), and a human-signed Fuji demonstration (O5).
**What it does not start:** anything Mainnet; any contract change in `avalanche-contracts/src`; any backend Fuji key; any
real-money, banking or tax-authority integration; removal of Solana code or data.
**Decisions the owner must make with R6** (section 9): D-A authorization to change Java 17 modules for Phase 3 work; D-B the
C3 treatment option; D-C C4 and A5 placement; D-D F13 design; D-E R9; D-F R10; D-G C1; D-H the meaning of "primary chain".
**Estimate:** about 31 to 47 engineer-days of LOCAL_SIMULATION work (section 8), plus the Fuji demonstration, whose timing
depends on the human operator (C1).
**Recommendation:** approve the scope with D-A and D-B answered, and keep O4 optional. Without D-A, O2 and O1 cannot be built,
because the fee, tax, payout, notification and rail-selection code lives in Java 17 modules.

## 1. Architecture overview (target, LOCAL_SIMULATION and AVALANCHE_FUJI)
```
 frontend (React)            marketplace-backend (Java 17)                     avalanche-integration (Java 21)      chain
 ───────────────             ─────────────────────────────                     ───────────────────────────────      ─────
 AvalancheEscrowPanel ─HTTP─▶ AvalancheEscrowController                        /api/v1/avalanche/... (internal key)
 avalancheActions.ts          AvalancheEscrowService ──AvalancheGatewayClient─▶ EscrowActionService ──web3j──▶ FreelaXEscrow
                              │  applyBusinessEffect (FINALIZED only)            SubmissionService / TxTracker      (shared,
                              │      └─▶ [P3] AvalancheSettlementBridge          LogScanner, EscrowReconciler        immutable)
                              │              └─▶ C3 treatment: existing stages   [P3] IssuerEventReporter ──reads──▶ USDC
                              │                  (SettlementTaxService, Payout,   (blacklist/pause events)
                              │                   TaxCertificate, Notification)   [P3] metrics endpoint
                              └─ [P3] PrimaryRailPolicy (new contracts only)
 misa-backend (Java 17): only if D-B wires tax records ("blockchain" label made rail-aware, A7 MISA part)
```
Rules unchanged by Phase 3: business state moves only on FINALIZED plus an independent chain read; the gateway is the only
chain writer; the backend authorization signer cannot move funds; environment and chain id on every record; every new flag
defaults to current behaviour.

## 2. Capability designs
Each capability lists business purpose, scope and exclusions, the existing implementation, what is missing, ownership,
contracts, state transitions, failure handling, security controls, LOCAL test cases, dependencies and acceptance criteria.

### 2.1 P3-C1: Avalanche settlement business records (O2, register A4; needs D-B and D-A)
- **Purpose:** an Avalanche release or resolve produces the same kind of *simulated* business records as the legacy rails, so
  the demonstration is complete. No real money moves.
- **Scope:** fee record, MISA tax record and certificate, payout record and stages, notifications for an Avalanche
  settlement, according to the C3 treatment option chosen by the owner. **Excluded:** new fee or tax rules (O-4/O-5: existing
  rules only, for example the existing 10 % withholding constant in `TaxCertificateServiceImpl` and `UnifiedExitService`), real
  fiat legs, tax remittance, changes to Solana-rail behaviour.
- **Existing:** `AvalancheEscrowService.applyBusinessEffect` sets milestone RELEASED / contract COMPLETED (or REFUNDED /
  CANCELLED) on FINALIZED with an independent `escrowSnapshot` check, once (`businessApplied`). Legacy settlement chain:
  `SettlementService`, `SettlementDownstreamService.process(settlementId)`, `SettlementTaxService`, `PayoutService`,
  `VndPayoutService`, `TaxCertificateService`, `NotificationService`. These are Solana-rail specific today, and
  `SettlementTaxService` and `TaxCertificateServiceImpl` hard-code `"solana"` (register A7).
- **Missing:** a bridge from an applied Avalanche settlement to the chosen treatment; a rail-aware blockchain label; the
  idempotency key linking a settlement record to the `avalanche_chain_tx` row.
- **Ownership:** `marketplace-backend` (new `AvalancheSettlementBridge`, called from `applyBusinessEffect`); `misa-backend`
  only if records are wired. The gateway is unchanged.
- **Contract (proposed, internal):** `AvalancheSettlementBridge.onFinalized(contractId, milestoneId, action, txHash, chainId,
  environment)`, idempotent on `(networkId, txHash)`; produces, per D-B: (a) **labelled mocks**: one settlement record marked
  `simulated=true`, `rail=AVALANCHE`, no downstream stages; (b) **wired**: the existing downstream entry point
  (`SettlementDownstreamService.process`) with the rail label `avalanche`; (c) **off the rail**: no records and a visible
  "no business records on this rail" notice.
- **Transitions:** `avalanche_chain_tx` FINALIZED and `businessApplied=false` → chain check passes → milestone/contract updated
  **and** the bridge called in the same database transaction → `businessApplied=true`. A CHAIN_MISMATCH applies nothing and
  calls nothing.
- **Failures:** the bridge throws → the whole apply rolls back and stays retryable on the next refresh (no partial records);
  a duplicate refresh → idempotent no-op; REVERTED/FAILED/REPLACED/UNKNOWN → never applied; MISA unavailable (option b) → the
  existing downstream retry behaviour, unchanged.
- **Security:** records derive from recorded terms and the chain snapshot only; the UI cannot trigger the bridge; all
  records are labelled simulated (scope section 7).
- **LOCAL tests:** release FINALIZED → exactly one record set; refresh twice → still one; CHAIN_MISMATCH → none; REVERTED →
  none; resolve refund → refund-type records per D-B; Solana rail regression unchanged (marketplace suite 381+).
- **Depends on:** D-B, D-A; A5/C4 only for status mirroring (not required).
- **Accept:** proposal section 10 O2 criteria, plus "zero records for any non-FINALIZED state" and "Solana suites unchanged".

### 2.2 P3-C2: Avalanche as the default development rail for new contracts (O1; needs A8/R9, A2, D-H, D-A)
- **Purpose:** new contracts in a demonstration environment use Avalanche by configuration, and Solana stays selectable for
  rollback.
- **Scope:** configuration property `marketplace.primary-rail` (`SOLANA` default, `AVALANCHE` opt-in), applied at contract
  creation only. **Excluded:** migrating existing contracts; removing Solana; changing persisted enums.
- **Existing:** client opt-in per contract (`AvalancheEscrowService.enable`, refused after funding or with a Solana escrow);
  `payment_network_id` (V6); gateway flag `avalanche-gateway.enabled`.
- **Missing:** `PrimaryRailPolicy` at contract creation; EVM wallet linking (A8), without which a real user cannot hold an
  Avalanche address; a single-stack run (A2); per-record rail labels in existing finance screens (A7 UI part; needs
  `paymentNetworkId` exposed in the contract DTO).
- **Contract:** contract summary DTO gains `paymentNetworkId` (additive, nullable). No other API change.
- **Transitions:** new contract → policy → `payment_network_id = AVALANCHE` (or null) → existing flows. Rollback: property
  back to `SOLANA` affects only contracts created afterwards.
- **Failures:** gateway disabled while the policy says AVALANCHE → creation fails closed with a clear error (no silent
  fallback to Solana: scope section 8 rule 2); user without a linked EVM wallet → funding refused with guidance.
- **LOCAL tests:** policy on/off; rollback; existing contracts of both rails readable; fail-closed when the gateway is off.
- **Depends on:** R9 → A8 → A2; D-H; D-A.
- **Accept:** proposal section 10 O1.

### 2.3 P3-C3: Simulation quality gates (O3; needs R10, C7)
- **Purpose:** catch regressions automatically and demonstrate operational behaviour.
- **Scope:** CI per [ci-proposal.md](../ci-proposal.md) (legacy JDK 17, Avalanche JDK 21, contracts, frontend; pinned Node and
  forge-std, F12); scanner metrics (A6/F6); drills on LOCAL_SIMULATION (pause blocks funding only; signer rotation;
  backend-offline permissionless release; arbiter loss); issuer-event reporting (below).
- **Existing:** suites listed in PROJECT_STATUS; `RetryAfterInterceptor`, scanner cursor, `CombinedFaultScenarioTest`
  (A3 gateway half, 2026-11-04).
- **Missing:** CI files; metrics endpoint; drill scripts and evidence; the marketplace half of A3.
- **Accept:** proposal section 10 O3.

### 2.4 P3-C4: Stuck-escrow reporting (O3 and O-6 "reported" part)
- **Purpose:** under the accepted O-6 behaviour a blacklisted party's escrow can be stuck; it must be **visible**, never
  bypassed.
- **Scope:** the gateway reads USDC issuer events (`Blacklisted`, `UnBlacklisted`, `Pause`, `Unpause`) for addresses that are
  parties of open escrows, and the escrow contract's own revert reasons on settlement attempts; it exposes
  `GET /api/v1/avalanche/escrows/{id}/alerts` (proposed) with `{kind, address, blockNumber, txHash, observedAt}`; the
  marketplace shows "Ký quỹ đang bị kẹt do nhà phát hành token chặn địa chỉ (mô phỏng/testnet)" and an admin list.
  **Excluded (O-6):** any withdrawal, bypass, rerouting, retry with another recipient, or admin override.
- **Transitions:** none on the escrow; alerts are append-only observations. A settlement transaction that reverts stays
  REVERTED.
- **LOCAL tests:** `MockUSDC.setBlacklisted` (already used by the Foundry lifecycle tests; M6 also checked real Fuji USDC on a
  fork) → release reverts → alert recorded once → no code path submits another transaction; un-blacklist → release succeeds
  normally. **Note:** MockUSDC reverts with an error but emits **no** `Blacklisted` event (Circle's FiatToken does), so LOCAL
  event reporting needs either an additive event in `src/mocks/MockUSDC.sol` (a mock, not the escrow baseline) or detection
  from settlement reverts only; this choice is part of P3-M0.
- **Accept:** proposal section 10 O4 (second half).

### 2.5 P3-C5: Mutual refund with users' own wallets (register F13; needs D-D and D-A)
- **Purpose:** make the A1 mutual-refund screen work with wallets, not only LOCAL simulated accounts.
- **Design (proposed):** (1) gateway `ActionRequest` gains optional `authorizationDeadline`; for `mutual-refund` with both
  signatures the gateway uses the supplied deadline, and requires it to be `> latest block timestamp` and
  `<= latest + AUTHORIZATION_TTL_SECONDS` (otherwise 400 `DEADLINE_OUT_OF_RANGE`); (2) marketplace table
  `avalanche_pending_authorization (contract_id, action, deadline, typed_data_digest, client_signature, freelancer_signature,
  created_at, expires_at)` (additive Flyway V7), filled by each party's signed typed data and cleared on use or expiry;
  (3) the UI shows "waiting for the other party's signature" and the expiry.
- **Security:** signatures are public authorization data, not keys; the digest is recomputed by the gateway and must match;
  the escrow contract verifies both signers and the nonce; expiry bounds replay; no backend key is involved (M2 holds).
- **LOCAL tests:** both sign the same typed data → refund FINALIZED; second signature after expiry → refused; tampered digest
  → refused; a block mined between the signatures → still succeeds (the regression test for F13).

### 2.6 P3-C6: Factory/clone measurement (O4, optional; O-2)
- **Scope:** an isolated folder `prototypes/escrow-clone-lab/` (not `avalanche-contracts/src`); Foundry gas and
  blast-radius measurement on LOCAL_SIMULATION; **no** deployment script for 43113, **no** manifest, **no** gateway support.
- **Accept:** baseline bytecode unchanged (hash of `avalanche-contracts/out` artefacts equal before and after); a
  measurement report labelled LOCAL-SIM.

### 2.7 P3-C7: Public Fuji demonstration (O5; needs C1, B1, E1)
- **Scope:** human-signed deployment, role grants and one escrow lifecycle, using the keyless pattern of
  `phase-1/prototypes/human-signed` (sign offline, broadcast signed bytes, `check_evidence.mjs` before commit). The gateway
  runs on Fuji **without signers** (M2): prepare and read only. Users sign with their own wallets.
- **Accept:** proposal section 10 O5; evidence labelled FUJI-OBS.

## 3. Transaction lifecycle (unchanged; applies to every capability)
Ten states (O-10/O-17): CREATED → SIGNED → SUBMITTED → PENDING → CONFIRMED → FINALIZED, with REVERTED (mined, status 0),
FAILED (definitive pre-chain failure), REPLACED (same nonce replaced, after repeated observations and grace) and UNKNOWN
(needs reconciliation; never resent automatically). Only FINALIZED plus an agreeing independent chain read changes business
state; business effects are applied once (`businessApplied`). Phase 3 adds no state.

## 4. Failure-mode matrix (Phase 3 additions)
| Failure | Handling | Test |
|---|---|---|
| Gateway down during refresh | rows keep their state; no business effect; UI shows the last known state | marketplace unit (existing pattern) |
| Bridge (P3-C1) exception | whole apply rolls back, retried on the next refresh | new unit |
| Duplicate FINALIZED observation | idempotent on `(networkId, txHash)` | new unit |
| Chain snapshot disagrees with terms | CHAIN_MISMATCH, nothing applied | existing plus new |
| USDC blacklist on a party | release reverts; alert recorded; no bypass (O-6) | new LOCAL scenario |
| USDC pause | settlement reverts; alert; funding blocked by the contract pause only for new funding | new LOCAL scenario |
| Primary rail AVALANCHE with gateway disabled | contract creation fails closed | new unit |
| Mutual-refund signature expiry | refused; pending row cleared | new unit and LOCAL |
| Fuji RPC 429 or outage | existing pacing and Retry-After; no fallback host (O-8) | existing |

## 5. Security controls (checklist for every Phase 3 change)
No backend key or signing on Fuji; development signers ephemeral and LOCAL-only; compiled chain and host allow-lists
unchanged; no role reaches escrowed funds; no privileged withdrawal, bypass or rerouting; the clone prototype is never
deployed beyond LOCAL; secrets never in Git; every new flag defaults to current behaviour; all money-like records labelled
simulated; Mainnet references only in deny-lists.

## 6. Test plans
- **LOCAL_SIMULATION (all capabilities):** Foundry (baseline unchanged; new tests only), JUnit with Testcontainers (Anvil,
  MySQL) for the gateway, marketplace unit and integration tests, Vitest, and a scripted single-stack run (marketplace,
  gateway, Anvil, MySQL) as the O1 acceptance test. The combined fault scenario is extended to the marketplace (A3 second half).
- **AVALANCHE_FUJI (O5 only):** human-signed run per the E1 procedure; read-only gateway; evidence files checked by
  `check_evidence.mjs`; never mixed with LOCAL results.

## 7. Mapping of remaining Phase 2 items (no relabelling without an owner decision)
| Item | Where it stays | Note |
|---|---|---|
| A1 | Phase 2 | PARTIAL 2026-11-04; the wallet mutual refund depends on F13 (P3-C5 if the owner moves it) |
| A2 | Phase 2 | needs A8 |
| A3 | Phase 2 | gateway half done 2026-11-04; the marketplace half needs D-A (Java 17 module) |
| A4 | **Phase 3** (R8) | P3-C1 |
| A5 | Phase 2 until C4 | would be P3-C1 extension if moved |
| A6 | Phase 2 optional | natural home P3-C3 if moved |
| A7 | Phase 2 | MISA part is inert until A4 and sits in Java 17 code; UI part needs a DTO field. Recommend moving it with A4 (owner decision) |
| A8 | Phase 2 | needs R9 |

## 8. Dependency-ordered backlog with estimates (engineer-days, LOCAL work; estimates, not commitments)
| # | Item | Depends on | Estimate |
|---|---|---|---|
| 1 | P3-M0 entry check, decision record | R6, D-A to D-H | 1 |
| 2 | CI pipeline (D1, F12 pins) | R10 | 3 to 4 |
| 3 | A8 EVM wallet linking (if still Phase 2) | R9 | 4 to 6 |
| 4 | A2 single-stack run script | 3, F3 | 3 to 4 |
| 5 | P3-C1 settlement bridge and records | D-B, D-A | 5 to 8 |
| 6 | A7 rail-aware labels (MISA and UI) | 5, D-A | 2 to 3 |
| 7 | P3-C5 mutual-refund wallet path (F13) | D-D, D-A | 3 to 5 |
| 8 | P3-C4 issuer-event reporting and alerts | 1 | 3 to 5 |
| 9 | A6 scanner metrics | 1 | 1 to 2 |
| 10 | P3-C2 primary-rail policy and rollback rehearsal | 3, 4, D-H | 3 to 4 |
| 11 | Drills (pause, rotation, backend-offline release, arbiter loss) | 2, C7 | 3 to 4 |
| 12 | P3-C6 clone measurement (optional) | 1 | 2 to 3 |
| 13 | P3-C7 Fuji demonstration | C1, B1, E1 | human-dependent |
Total for items 1 to 12: about 31 to 47 engineer-days.

## 9. Decisions requested together with R6
| ID | Question | Options (recommendation first) |
|---|---|---|
| D-A | May Phase 3 change Java 17 modules (`marketplace-backend`, `misa-backend`) additively, keeping Java 17 and the Solana behaviour unchanged? | (a) yes, additive only, Solana suites must stay green; (b) no: then P3-C1, P3-C2, P3-C5 and A7 cannot be built |
| D-B | C3 treatment of an Avalanche settlement | (a) labelled mocks only; (b) wired to the existing downstream stages; (c) off the rail |
| D-C | C4 status mirror and A5 placement | (a) marketplace-driven status kept, A5 dropped as not needed; (b) scanner-driven mirror in Phase 3 |
| D-D | F13 mutual-refund design (section 2.5) | (a) approve as designed; (b) mutual refund stays LOCAL simulated-account only |
| D-E | R9 wallet linking | as in decision-and-evidence-requests R9 |
| D-F | R10 CI proposal | approve / amend |
| D-G | C1 Fuji signer and funding owner | as in R1 |
| D-H | "Primary chain" means the default rail for new contracts in development and demo environments only | confirm / amend |
