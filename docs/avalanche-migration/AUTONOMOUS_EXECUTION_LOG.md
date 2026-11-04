# Avalanche migration: autonomous execution log

Rules for this file: [CLAUDE.md](CLAUDE.md) section 9. Status source of truth: [PROJECT_STATUS.md](PROJECT_STATUS.md); this log
records *what was done, why, and on what authority*, and the resume point. One entry per meaningful task or decision. When
new evidence changes an entry's status, edit its **Status** line and add a dated "Update" line under it; do not delete the
original reasoning. Do not add routine progress entries.

## Current state (updated 2026-11-04, after L-010)
- **Branch:** `avalanche/phase1-closure-phase3-proposal`. `origin/master` = `1ef0af7`. All later commits are **local and
  unpushed** (see `git log --oneline origin/master..HEAD`). Push is **not authorized**.
- **Phase 1:** PARTIAL. E1 BLOCKED (R1/C1, B1; keyless tooling and evidence checker ready); E2 BLOCKED (DB operator;
  development DB only; queries validated on an empty local schema); E3 DEFERRED; E4 OPEN (R3); E5, E6 INCONCLUSIVE (R4); E7
  PARTIAL (R5). Nothing waived.
- **Phase 2:** IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING. A1 PARTIAL (wallet mutual refund blocked by F13,
  legacy labelling open); A3 PARTIAL (gateway half done; marketplace half waits for R11); A7 BLOCKED (R11); A8 waits for R9;
  A2 for A8 and F3; A5 for C4; A4 is Phase 3 (R8); A6 optional.
- **Phase 3:** PROPOSED, **NOT APPROVED** (R6). Approval package ready: `phase-3/01-r6-approval-package.md` (D-A to D-H).
- **Rule added 2026-11-04:** no changes to Java 17 legacy modules (including their tests) until R11.
- **Baselines (2026-11-04):** frontend 29 files / 859 tests; gateway JDK 21 105 run, 0 failures, 3 optional skipped; human-signed tooling 11 tests. Unchanged since 2026-11-03: legacy reactor payment 72, marketplace 381,
  misa 20, solana-integration 10; Foundry 55 pass, 1 skipped.
- **Waiting on owner:** R1, R2 remainder (DB operator), R3 to R6 (with D-A to D-H), R8 remainder, R9, R10, R11. **Operators:**
  X1 to X3.
- **Recommended next action:** owner answers R11 and R6 (with D-A, D-B). Engineering, no gate: if R11 (a) or (b), the
  marketplace half of A3; if R9, A8 then A2. Nothing else is authorized and unblocked.

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
