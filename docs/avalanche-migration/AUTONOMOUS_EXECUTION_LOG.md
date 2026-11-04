# Avalanche migration: autonomous execution log

Rules for this file: [CLAUDE.md](CLAUDE.md) section 9. Status source of truth: [PROJECT_STATUS.md](PROJECT_STATUS.md); this log
records *what was done, why, and on what authority*, and the resume point. One entry per meaningful task or decision. When
new evidence changes an entry's status, edit its **Status** line and add a dated "Update" line under it; do not delete the
original reasoning. Do not add routine progress entries.

## Current state (updated 2026-11-04, after L-017)
- **Branch:** `avalanche/phase1-closure-phase3-proposal`; everything after `origin/master` (`1ef0af7`) is local and unpushed.
- **Decision regime:** Lead delegation of engineering decisions (2026-11-04); PROJECT_STATUS classification table. R6 is
  **not owner approved**.
- **Phase 1:** PARTIAL. E1 BLOCKED (R1, X1), E2 BLOCKED (DB operator, X2), E3 DEFERRED (owner), E4 DOCUMENTED LIMITATION,
  E5/E6 INCONCLUSIVE (need E1), E7 PARTIAL (explorer verification NOT APPLICABLE until B2). Tooling for E1/E2 is complete.
- **Phase 2:** all engineering residuals done (A1, A2, A3, A6, A7, A8, F12, F13); A5 closed; B7 external.
- **Phase 3 (delegated, reversible):** notifications (off by default), primary rail (default SOLANA), P3-C4 stuck-escrow
  reporting, A6 scanner health, CI workflow (not executed), drills. Owner/external: fee/tax/payout off-ramp (custody), F14/R12,
  Fuji demonstration (C1). P3-C6 LOCAL clone measurement DONE (L-018).
- **Last measured:** JDK 17 reactor 72/408/20/10 then marketplace 414; gateway 115/0/3; frontend 867; Foundry 55/1; A2 PASS.
- **How to run heavy suites here:** one at a time, detached and memory-capped (CLAUDE.md section 7).
- **Next executable task:** none left that is independently implementable; everything open needs an owner decision or an external person.

## Entries

### L-001: Phase 0 to Phase 2 execution (summary of prior record)
- **Date:** 2026-11-01 to 2026-11-02. **Phase / task:** Phase 0, Phase 1, P2-M0 to P2-M12.
- **Action:** research and architecture (D1 to D4); Phase 1 experiments; Phase 2 implementation M1 to M12 executed
  sequentially without per-milestone stops.
- **Reasoning / alternatives:** recorded in [README](README.md), [07](07-open-questions.md), [phase-2/README.md](phase-2/README.md)
  and each `phase-2/M*-validation-report.md`; not repeated here. The repository milestone numbering was followed where the
  execution prompt numbered milestones differently (PROJECT_STATUS "Roadmap note").
- **Authorization:** owner decisions of 2026-11-01 (O-1 to O-18, M1 review, M2 instructions); owner authorization to run M4 to
  M12 sequentially; owner request of 2026-11-02 to commit and push.
- **Files:** `avalanche-integration/`, `avalanche-contracts/`, Avalanche parts of `marketplace-backend/` and `frontend/`,
  root additive changes (O-16), `docs/avalanche-migration/`.
- **Tests / evidence:** [phase-2/M12-validation-report.md](phase-2/M12-validation-report.md).
- **Status:** DONE; pushed to `origin/master` (`1ef0af7`). Classification: IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION
  PENDING.
- **Remaining dependencies:** register sections A to G.

### L-002: Owner response, audit and Phase 2 fixes (2026-11-03)
- **Date:** 2026-11-03. **Phase / task:** Phase 2 residuals F8, F9, G1, G2; register A8, B8, B10; Phase 1 E1 tooling.
- **Action:** recorded the owner response; audited the runtime paths; fixed `EscrowActionService.statusByHash` to report
  UNKNOWN for non-manifest targets (F9); exposed reconcile and scan intervals in compose and `.env.example` with defaults
  unchanged at 0 (F8); added `test_dispute_changesOnlyItsOwnEscrow` (G1, test only); committed Slither, gas and forge
  evidence (G2); built keyless human-signed E1 tooling validated on LOCAL_SIMULATION only; recorded A8 (EVM wallet linking
  missing) and filed R9; re-validated all suites on this machine.
- **Reasoning / alternatives:** F9: the plan 06 section 4 target check was missing, so a foreign receipt could read as
  CONFIRMED; reporting UNKNOWN was chosen over rejecting outright because UNKNOWN already means "needs reconciliation" (O-10).
  F8: enabling the loops by default was rejected (changes current behaviour). E1: a backend Fuji signer was rejected (M2);
  keyless tooling where a human signs was the only compliant option.
- **Authorization:** owner response 2026-11-03 section 10 (fix inconsistencies, implement approved Phase 2 tasks, local
  tests, docs, local commits).
- **Files:** commits `e823a4a`, `bfb1421`, `14683cf`, `de4c6ac`.
- **Tests / evidence:** baselines in "Current state"; `phase-2/evidence/*-2026-11-03.txt`;
  `phase-1/evidence/live/LOCAL_SIMULATION-human-signed-chain31337-20261103T150927.json`.
- **Status:** DONE, committed locally, **not pushed**.
- **Remaining dependencies:** E1 execution needs R1 and X1; F12 (forge-std pin) waits for R10.

### L-003: Phase 1 closure tracking, Phase 3 proposal, CI proposal, owner requests (2026-11-03)
- **Date:** 2026-11-03. **Phase / task:** Phase 1 closure; C9; D1.
- **Action:** item-by-item Phase 1 closure tracking; Phase 3 proposal; CI proposal; DB operator output format; explorer
  verification procedure; [decision-and-evidence-requests.md](decision-and-evidence-requests.md) R1 to R10, X1 to X3.
- **Reasoning / alternatives:** the owner asked for a proposal rather than Phase 3 work, so all Phase 3 content is marked
  PROPOSED. Waiving E4 to E7 was not proposed as a blanket; each has its own request (owner response section 4).
- **Authorization:** owner response 2026-11-03 section 1 and section 10.
- **Files:** `phase-1/phase-1-acceptance-report.md`, `phase-3/00-phase-3-proposal.md`, `ci-proposal.md`,
  `decision-and-evidence-requests.md`, `phase-1/db-rail-evidence-request.md`, `PROJECT_STATUS.md` (commit `de4c6ac`).
- **Tests / evidence:** documentation only.
- **Status:** DONE. Proposal **NOT APPROVED** (R6); CI proposal **NOT APPROVED** (R10).
- **Remaining dependencies:** owner answers R1 to R10.

### L-004: Owner decision update recorded (2026-11-03)
- **Date:** 2026-11-03. **Phase / task:** R2, R7 (O-2/C2, O-6/C5), R8 (C3 placement).
- **Action:** recorded the decisions verbatim in PROJECT_STATUS "Owner decision update" and synchronized the register,
  requests page, Phase 3 proposal, Phase 1 report and roadmap documents. No code, contract, configuration or database change;
  no clone prototype built.
- **Reasoning / alternatives:** decisions recorded as stated, not expanded; questions they leave unanswered (C3 treatment,
  C4, A5 placement, DB operator) kept open rather than inferred.
- **Authorization:** owner decision update 2026-11-03 (authoritative).
- **Files:** commit `d98aec2`.
- **Status:** DONE, committed locally, not pushed.
- **Remaining dependencies:** R8 remainder; R2 remainder; R6 (the decisions fix proposal inputs but do not approve it).

### L-005: Persistent instructions and execution log (2026-11-04)
- **Date:** 2026-11-04. **Phase / task:** governance (no register ID).
- **Action:** created [CLAUDE.md](CLAUDE.md) (standing rules) and this log; created a minimal repository-root `CLAUDE.md`
  that imports this directory's `CLAUDE.md` (no root `CLAUDE.md` existed; the only other `CLAUDE.md` in the tree is vendored
  OpenZeppelin code under `avalanche-contracts/lib/` and is not a project instruction).
- **Reasoning / alternatives:** instructions point to PROJECT_STATUS instead of duplicating the register, so there is one
  status source; decisions are summarized with "PROJECT_STATUS wins" to avoid drift. A large root instruction file was
  rejected (task constraint; other root instructions may be added later by the team).
- **Authorization:** project lead request of 2026-11-04 (governance setup; no source or contract change; no push).
- **Files:** `CLAUDE.md` (root, new), `docs/avalanche-migration/CLAUDE.md` (new), this file (new).
- **Tests / evidence:** consistency check against PROJECT_STATUS, decision-and-evidence-requests and the Phase 3 proposal
  (no contradiction found; R6 left NOT APPROVED); root import verified by path.
- **Status:** DONE, committed locally, not pushed.
- **Remaining dependencies:** none.

### L-006: Next authorized Phase 2 residuals (resume point)
- **Date:** 2026-11-04. **Phase / task:** Phase 2 A7, A1, A3 (A6 optional).
- **Planned action, in order:** A7 (Solana-literal clean-up in MISA/tax flows and explorer labelling; smallest, no owner
  gate; must not change Solana behaviour or persisted values); A1 (frontend action screens: submit, revise, dispute, resolve
  (admin), extension-request, mutual-refund; legacy Solana labelling), reusing the M10 adapter and M11 panel; A3 (combined
  fault-injection scenario at marketplace level against a mocked or Testcontainers gateway).
- **Reasoning / alternatives:** these are the only approved residuals without an owner gate; A8 and A2 wait for R9; A5 for
  C4; A4 is Phase 3. A7 first because it is bounded and reduces wording risk before more UI is added.
- **Authorization:** owner response 2026-11-03 section 10 ("implement approved Phase 2 tasks").
- **Status:** CLOSED 2026-11-04 (executed as L-007 A1, L-008 A7 blocked, L-009 A3 gateway half).
  - Original status: NEXT (not started).
  - Update 2026-11-04: order changed to A1 first (largest user-visible residual; A7 inspection showed its MISA part sits in Java 17
    legacy code tied to A4, see L-008). A1 done as L-007.
- **Remaining dependencies:** none for A7/A1/A3; Vietnamese copy review stays C10.

### L-007: A1 frontend action screens (2026-11-04)
- **Date:** 2026-11-04. **Phase / task:** Phase 2 A1.
- **Action:** added `frontend/src/avalancheActions.ts`, a pure rule set that mirrors marketplace
  `AvalancheEscrowService.authorize` and offers only actions the recorded data supports: submit (latest submission in review),
  revise (latest submission REVISION_REQUESTED), dispute (recorded open dispute opened by the user), extension-request (date
  input, epoch seconds), extension-approve, release, resolve (non-participant administrator, decision pending), mutual refund.
  Panel rewired to it; mounted on the admin dispute case page as arbiter (hidden for participant admins and for contracts not
  on the Avalanche rail). Chain snapshot display: amount (exact 6-decimal string arithmetic), revisions, deadlines, with the
  scope token label ("USDC mô phỏng" / "Testnet USDC").
- **Reasoning / alternatives:** the UI passes only recorded ids (submission, dispute) and a date; the backend still builds all
  calldata and typed data (M11 rule "UI cannot bypass backend authorization"). Free selection of submissions or disputes was
  rejected. Admin participants are excluded in the UI to match the existing admin page rule; the **backend `resolve` check does
  not exclude participant admins** (recorded here; the on-chain arbiter snapshot O-3 still limits who can resolve on chain).
  Mutual refund by wallet was **not** built: the two signatures must cover the same deadline and there is no exchange for the
  first one (F13, new register row); offering it would produce transactions that revert. Legacy Solana labelling not done: the
  Avalanche view has no rail field and inventing one would change the API.
- **Authorization:** owner response 2026-11-03 section 10 (approved Phase 2 task A1); project lead instruction 2026-11-04.
- **Files:** `frontend/src/avalancheActions.ts` (new), `avalancheActions.test.ts` (new), `AvalancheEscrowPanel.tsx`,
  `avalanchePresentation.ts`, `AvalanchePanel.test.tsx`, `ContractLifecycle.tsx`, `AdminDisputes.tsx`; `PROJECT_STATUS.md`
  (A1, F13).
- **Tests / evidence:** Node 24.19.0: `npx tsc -b` clean; `npx vitest run` **29 files, 859 tests pass** (baseline 28 / 841;
  +10 rule tests, +8 panel/helper tests); `npx vite build` built. No browser or wallet-extension run (B7).
- **Status:** PARTIAL (A1): implemented items tested; mutual-refund wallet path and legacy labelling outstanding.
- **Remaining dependencies:** F13 (Phase 3 proposal item); B7 for a real browser check; C10 Vietnamese copy review.

### L-008: A7 and the marketplace half of A3 blocked by the Java 17 instruction (2026-11-04)
- **Date:** 2026-11-04. **Phase / task:** Phase 2 A7, A3.
- **Action:** inspected A7. The `"solana"` literals are in `marketplace-backend` `SettlementTaxService` (line 80) and
  `TaxCertificateServiceImpl` (`TAX_RECORD_BLOCKCHAIN`), and in `misa-backend` `CreatePayoutTransactionRequest` and
  `PayoutTransaction`; they run only for Solana-rail settlements, because an Avalanche release creates no tax record (A4).
  Per-record chain labels in existing finance screens need `paymentNetworkId` in the marketplace contract DTO (not exposed
  today). No code changed. Filed owner request **R11** and added the rule to `CLAUDE.md` section 6.
- **Reasoning / alternatives:** the 2026-11-04 instruction says "do not modify Java 17 legacy modules". Reading
  `marketplace-backend` as exempt (it already holds the M8/M9 Avalanche code) was rejected as an over-broad reading of an
  explicit restriction. A frontend-only guess of the rail was rejected (it would invent data). A3 was split instead: the
  gateway half is in the Java 21 module (L-009).
- **Authorization:** the restriction itself; the request R11 is the owner's call.
- **Status:** BLOCKED (R11). A7 NOT IMPLEMENTED; the MISA part is functionally inert until A4 (Phase 3).
- **Recommended next action:** owner answers R11; option (c) moves A7 to Phase 3 together with A4.

### L-010: R6 approval package (2026-11-04)
- **Date:** 2026-11-04. **Phase / task:** Phase 3 preparation (R6/C9), no implementation.
- **Action:** wrote [phase-3/01-r6-approval-package.md](phase-3/01-r6-approval-package.md): one-page summary; target
  architecture; seven capability designs (settlement records A4, primary rail, quality gates, stuck-escrow reporting under
  O-6, F13 wallet mutual refund, LOCAL-only clone measurement, Fuji demonstration) with purpose, scope, existing and missing
  code, owner module, proposed contracts, transitions, failures, security, LOCAL tests, dependencies and acceptance; the
  failure matrix; test plans; the mapping of remaining Phase 2 items; a dependency-ordered backlog (about 31 to 47
  engineer-days of LOCAL work, estimates); and decisions D-A to D-H. Linked from the proposal.
- **Reasoning / alternatives:** O-2, O-6, R8 and M2 were treated as fixed inputs. Found while writing: Phase 3 O1/O2 work sits
  mainly in Java 17 modules, so R6 cannot be acted on without D-A; MockUSDC has a blacklist but emits no `Blacklisted` event,
  so LOCAL issuer-event reporting needs a mock change or revert-based detection (recorded as a P3-M0 choice, not decided).
- **Authorization:** project lead instruction 2026-11-04 section 3 (prepare, do not approve).
- **Status:** DONE (documentation). **R6 remains PENDING; Phase 3 NOT APPROVED.**
- **Remaining dependencies:** owner decision on R6 with D-A to D-H.

### L-009: A3 gateway half and Phase 1 E1/E2/E4 work (2026-11-04)
- **Date:** 2026-11-04. **Phase / task:** Phase 2 A3 (gateway half); Phase 1 E1, E2, E4 (E3, E5 to E7 reviewed).
- **Action:** (A3) new `avalanche-integration/src/test/.../tx/CombinedFaultScenarioTest.java`: one sequence, one signer, one
  fault proxy, real Anvil and MySQL: timeout after send, impatient duplicate, revert, 429 storm, null lookups, restart,
  duplicate after restart. Test only; no production code changed. (E1) `check_evidence.mjs` and 6 tests; runbook step 4 uses
  it. (E2) `phase-1/scripts/db_query_validate.sh` and its LOCAL output. (E4) desk re-check, see the Phase 1 report addendum.
- **Reasoning / alternatives:** A3: the marketplace half needs Java 17 test code (blocked, L-008), so the gateway half was
  done separately rather than waiting. First run failed: the test asserted CONFIRMED after the restart, but the record was
  FINALIZED because the later reverting transaction had mined a block on top; that is correct tracker behaviour (depth
  reached), so the **test** assumption was wrong. The assertion was changed to "CONFIRMED or FINALIZED, and FINALIZED only
  with a later block" (stricter than just accepting either); no production code was touched to make it pass. E2: running the
  queries on an empty local schema was chosen over static reading because V2 to V6 had never been checked; labelled not
  evidence. E4: a search summary claimed a Circle help page names `0xA7D7...c664`; the page did not render, so it is not cited.
- **Authorization:** owner response 2026-11-03 section 10; project lead instruction 2026-11-04 sections 4 and 5. No Fuji
  transaction, no key, no database other than a throwaway local container.
- **Files:** `CombinedFaultScenarioTest.java` (new); `phase-1/prototypes/human-signed/check_evidence.mjs`,
  `check_evidence.test.mjs` (new), `README.md`; `phase-1/scripts/db_query_validate.sh` (new);
  `phase-1/evidence/db-validation/LOCAL_SIMULATION-e2-query-validation-20261104.txt` (new);
  `phase-1/db-rail-evidence-request.md`; `phase-1/phase-1-acceptance-report.md` (addendum).
- **Tests / evidence:** JDK 21 module run 105 tests: 101 pass, 3 skipped, 1 failure (the new test, explained above); new
  class re-run after the fix 1/1 pass; **full module re-run after the fix 105 run, 0 failures, 3 optional skipped**; human-signed `node --test` 11/11; E2 script output committed.
- **Status:** A3 PARTIAL (gateway half DONE); E1 BLOCKED (tooling complete); E2 BLOCKED (queries validated); E3 DEFERRED;
  E4 OPEN; E5, E6 INCONCLUSIVE; E7 PARTIAL. Phase 1 PARTIAL.
- **Remaining dependencies:** R11 (marketplace half of A3); R1/X1 (E1); DB operator/X2 (E2); R3, R4, R5.

### L-011: Delegated decisions; A7 and A3 marketplace half (2026-11-04, second update)
- **Decision:** classified every open decision (PROJECT_STATUS "Delegated decision classification"); R11/D-A, D-D, R9/D-E, D-H,
  C11, D-C, R10, R3, R5 ENGINEERING DECIDED; R1, R2 remainder, R4, C10, C8 EXTERNAL; O-8, O-13 kept OWNER DEFERRED; R6 **not
  owner approved**. Implemented A7 (rail-aware MISA label, additive `paymentNetworkId` in the contract summary, chain label in
  finance rows) and the marketplace half of A3.
- **Authority:** Lead instruction 2026-11-04 (delegation of engineering decisions); it superseded the same Lead's earlier
  "no Java 17 changes" rule (L-008), now resolved as R11 option (a).
- **Rationale:** A7: the label is derived from the V6 network id, so every legacy row and every existing caller keeps
  `solana`; misa-backend needs no change because it stores the label it receives. A3: the marketplace scenario chains the faults
  the per-fault tests cover one at a time, on the same contract, through `AvalancheEscrowService`.
- **Alternatives:** changing misa-backend's default label (rejected: would change old callers); a rail enum on the DTO
  (rejected: the network id already exists and is additive); a live single-stack fault run (that is A2, not done).
- **Risks:** a contract whose network id starts with "avalanche" but is not the configured network would be labelled
  avalanche (acceptable: such contracts can only come from the Avalanche rail); the refresh throws if the chain read fails after
  FINALIZED (pinned by the test; transactional rollback, retried next refresh).
- **Files:** `TaxRecordBlockchain.java` (new), `SettlementTaxService`, `TaxCertificateServiceImpl`, `ContractSummaryResponse`,
  `JobServiceImpl`, tests; `Finance.tsx`, `types.ts`, `avalanchePresentation.ts`. Commit `355e019`.
- **Tests:** JDK 17 legacy reactor: payment 72, marketplace 385, misa 20, solana-integration 10, all pass (before: 381).
- **Result:** A7 IMPLEMENTED; A3 IMPLEMENTED (with L-009).

### L-012: A8 EVM wallet linking and F13 wallet mutual refund, end to end (2026-11-04)
- **Decision:** A8: EIP-191 personal_sign challenge, verified by the Java 21 gateway, stored in a new `evm_wallets` table.
  F13: gateway reuses a supplied deadline bounded by the TTL; marketplace holds the first signature in
  `avalanche_pending_authorizations`; both tables in V7.
- **Authority:** delegated (R9/D-E, D-D); design as planned in `phase-2/04` section 3 and the R6 package section 2.5.
- **Rationale:** verifying secp256k1 in the gateway avoids new crypto dependencies in a Java 17 module; a separate EVM table
  keeps every Solana service that reads a user's first `wallets` row unchanged; the challenge is consumed even when rejected
  (`noRollbackFor`), so a challenge cannot be retried with other signatures; the marketplace re-derives the typed-data digest
  from the gateway for the signed deadline, so a party can only sign what the escrow expects; each party writes only its own
  column; a new deadline resets the pair.
- **Alternatives:** EVM rows in `wallets` (rejected: could change which wallet Solana flows pick); web3j in the marketplace
  (rejected: dependency risk on Java 17); refusing a new deadline while a pair is pending (rejected: could deadlock after expiry).
- **Risks:** the marketplace does not itself verify the typed-data signature (the escrow contract does; a bad signature only
  makes the refund revert); linking needs the gateway up (an outage rolls back and is retryable); no real-browser check (B7).
- **Files:** gateway `WalletSignatureVerifier`, `AvalancheController`, `Dtos`, `EscrowActionService` (+tests); marketplace V7,
  `EvmWallet`, `AvalanchePendingAuthorization`, repositories, `EvmWalletLinkService`, `EvmWalletLinkController`,
  `AvalancheClient`, `AvalancheEscrowService`, `AvalancheEscrowController` (+tests, `AvalancheV7MySqlIT`); frontend panel,
  `evmWallet.ts` (`personalSign`), `api.ts`, types (+tests). Commits `2b857ca`, `4d4da3a`.
- **Tests:** gateway JDK 21 110 run, 0 failures, 3 optional skipped (F13 Anvil regression mines blocks between signatures);
  marketplace JDK 17 400 pass; V7 on MySQL 8.4 (Flyway V1 to V7, Hibernate validate) IT 2/2; frontend 865 pass. Two compile
  errors in the first gateway test runs (a newline in a string literal written by a shell heredoc; an undeclared checked
  exception) were fixed in the test code only.
- **Result:** A8 IMPLEMENTED; F13 FIXED; A1 complete apart from B7.

### L-013: Phase 3 under delegated authority: settlement notifications and primary rail (2026-11-04)
- **Decision:** D-B option (b) (existing stages and rules, flag default off); built only the notification part. P3-C2:
  `PrimaryRailPolicy` (`marketplace.primary-rail`, default SOLANA) applied at the single contract-creation point.
- **Authority:** delegated; reversible, simulation-only, default to current behaviour; R6 itself still not owner approved.
- **Rationale:** notifications reuse existing types (no enum or schema change) and fire exactly once when the business effect
  is applied; the primary rail affects new contracts only and fails closed (unknown value stops startup; AVALANCHE without the
  gateway refuses creation, no silent fallback).
- **Alternatives:** new notification types (rejected: needs a migration for nothing); applying the rail at enable time
  (rejected: that is the existing opt-in); a fallback to Solana when the gateway is down (rejected by the scope rules).
- **Risks:** fee, tax and payout are not wired (A4 remains PARTIAL); with AVALANCHE as primary and a unified-flow job, the
  existing unified Solana draft is still created (same as today's opt-in path).
- **Files:** `AvalancheGatewayProperties`, `application.yml`, `AvalancheEscrowService` (+3 tests); `PrimaryRailPolicy` (new),
  `JobServiceImpl`, `PrimaryRailPolicyTest`, `JobAccessAndDiscoveryServiceImplTest`. Commit `a81bced` (notifications); primary
  rail in the commit after the final reactor run.
- **Tests:** marketplace 403 pass after notifications; primary rail: see L-015 (not a clean single full-reactor pass yet).
- **Result:** notifications IMPLEMENTED (default off); primary rail IMPLEMENTED (default SOLANA).

### L-014: CI workflow and forge-std pin (2026-11-04)
- **Decision:** R10 accepted as proposed; workflow written locally; F12 pinned.
- **Authority:** delegated (R10). Nothing pushed, so no runner executed the workflow.
- **Rationale / alternatives:** pin by full hash fetched with `git fetch <sha>` (a tag does not exist for that commit); the
  Slither job of the proposal was left out of the first workflow (needs solc-select setup; recorded as next CI step).
- **Risks:** the workflow is unexecuted; the first real run may need small fixes.
- **Files:** `.github/workflows/avalanche-ci.yml`, `avalanche-contracts/scripts/install-deps.sh`. Commit `61cb35a`.
- **Tests:** both hygiene checks pass locally; the pinned fetch resolved `dc647cc030d9d65910d8f1172a0cde48d7106e32` in the
  Foundry image.
- **Result:** D1 WORKFLOW ADDED (not executed); F12 FIXED.

### L-015: Final regression state and an interrupted run (2026-11-04)
- **What happened:** the first full JDK 17 reactor run after the primary-rail change failed with 49 errors in marketplace (408
  run): 48 because the `ContractCancellationServiceTest` `@DataJpaTest` slice imports `JobServiceImpl` explicitly and lacked the
  new `PrimaryRailPolicy` (plus `AvalancheGatewayProperties`) bean; 1 because the new primary-rail test lacked the deliverable
  and acceptance-criterion stubs that `assignFreelancer` requires. Both were caused by this change; both fixed in **test code
  only** (no production change, no assertion weakened). The three classes re-ran **66/66**.
- **Interrupted:** the second full reactor run was stopped by Claude Code because the host was critically low on memory
  (after payment-backend 72/72). The orphaned Maven container was stopped. Not restarted automatically.
- **Status:** primary rail IMPLEMENTED; **verification PARTIAL**: per-class evidence only for the final state. Next session:
  run `mvn -B -ntp test` on `maven:3.9-eclipse-temurin-17` once memory allows and record the counts (expected marketplace 408).
- **Also not run on 2026-11-04:** Foundry (no contract change).
- **Update 2026-11-04 (resolved):** host had 15.4 GB RAM, 3.8 GB free (browser about 2 GB); Docker VM 8 GB, no running or
  orphaned containers. Diagnosis: without a surefire configuration each JVM sized its heap at 25 % of the 8 GB Docker VM. Re-run
  detached and capped (`--memory=2560m`, `MAVEN_OPTS=-Xmx384m`, `-DargLine=-Xmx1g`; no project file changed): **BUILD SUCCESS,
  exit 0, payment 72, marketplace 408, misa 20, solana-integration 10, 0 failures/errors/skips**, not OOM-killed, about 7 min.
  **Status: primary rail VERIFIED.**

### L-016: A2 single LOCAL stack, and the integration gap it found (2026-11-04)
- **Decision:** A2 runs as a self-contained harness (`phase-2/a2-local-stack/run.sh` + `drive.mjs`): its own Docker network,
  memory-capped containers (MySQL 8.4, Redis 7, Anvil 1.8.7, gateway JDK 21, marketplace JDK 17), per-run throwaway secrets,
  never the owner's `.env`. Party wallets are throwaway LOCAL keys inside the Anvil container (Phase 1 precedent), redacted
  from every error and refused in the evidence. F3: deploy first with Anvil's unlocked account as placeholder roles, start the
  gateway, read its ephemeral signers from the startup log, grant `AUTH_SIGNER_ROLE` and set the default arbiter.
- **Authority:** delegated engineering (A2 approved Phase 2 residual; LOCAL_SIMULATION only).
- **Alternatives:** `docker compose up` (rejected: needs the owner's `.env`, B9); simulated-account execution (rejected:
  marketplace users cannot link the gateway's ephemeral wallets, so it would not test A8); deterministic gateway signers
  (rejected: M2 keeps them ephemeral).
- **Findings (each fixed and documented in the harness README):** (1) **an Avalanche-funded contract could not accept a work
  submission** (`ContractSubmissionService` required a Solana escrow or fiat funding record; the job stayed
  `AWAITING_PAYMENT`): fixed by making the job follow the applied escrow outcome exactly as on the Solana rail and by accepting
  an Avalanche-rail milestone that reached FUNDED through the verified path; legacy rails unchanged; (2) the escrow releases
  only from `Submitted` (D4): the driver submits and anchors first; (3) freelancer registration needs tax and bank details:
  fake demonstration values; (4) **a failed `cast` call echoed a throwaway key into a scratch log and evidence file** (outside
  the repository; the key belonged to a destroyed Anvil chain): redacted in place, and the driver now redacts every error and
  checks the evidence against the generated keys; (5) Windows path and manifest-ordering issues in the harness.
- **Risks:** the harness depends on log-line formats for the signer addresses; the freelancer details are placeholders.
- **Files:** `phase-2/a2-local-stack/{run.sh,drive.mjs,README.md}`; evidence
  `phase-2/evidence/LOCAL_SIMULATION-a2-local-stack-20261104.json`; `ContractSubmissionService`, `AvalancheEscrowService` and
  tests. Commit `3c83392`.
- **Tests:** A2 run **PASS** (wallet links with replay refused; fund, submit, release FINALIZED with contract COMPLETED, job
  COMPLETED and notifications; F13 mutual refund with blocks mined between signatures, contract CANCELLED, signatures spent).
  Marketplace JDK 17 **412 pass** (memory-capped run). Full legacy reactor before the fix: 72 / 408 / 20 / 10 (L-015).
- **Result:** A2 IMPLEMENTED and PASSING.

### L-017: P3-C4, A6, drills, D-B analysis (2026-11-04)
- **Done:** P3-C4 stuck-escrow reporting (gateway issuer-control reader, `/escrows/{id}/alerts`, marketplace view, panel banner;
  read-only, O-6); A6 scanner health endpoint; drill report `phase-3/02-simulation-drills-2026-11-04.md`. Commit `ecd94e8`
  plus the drill commit.
- **Tests:** gateway JDK 21 115/0/3; marketplace JDK 17 414 pass; frontend 867; Foundry 55 pass 1 skipped; A2 with live
  blacklist reported and lifted: PASS.
- **Findings:** F14 arbiter loss on a Disputed escrow (owner request R12). D-B fee/tax/payout needs an Avalanche off-ramp,
  a new custody path: EXTERNAL decision, not built.
- **Not done (usage limit reached):** P3-C6 LOCAL clone measurement (optional); refreshing the CLAUDE.md state lines.
- **Next:** R12 and the D-B off-ramp decision (owner); P3-C6 (engineering, optional); external items unchanged (R1, X1 to X3, B7, R6).

### L-018: P3-C6 LOCAL clone measurement (2026-11-04)
- **Decision:** a Foundry lab in `phase-3/prototypes/escrow-clone-lab` (outside `avalanche-contracts/src`, no script, no
  manifest) measuring EIP-1167 per-escrow vault cost and blacklist blast radius. Authority: O-2 (owner) permits it LOCAL only;
  engineering decided under delegation.
- **Alternatives:** a full initializable copy of the escrow (rejected: duplicates the audited baseline for a measurement).
- **Results:** clone create + init 123,573 gas per escrow (baseline fund about 225k); deposit 62,268; payout 71,755; a
  blacklisted vault blocks only its escrow; a blacklisted shared holder blocks all. 4 tests passed.
- **Result:** P3-C6 DONE (evidence only); baseline unchanged.
