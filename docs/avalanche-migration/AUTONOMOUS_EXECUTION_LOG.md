# Avalanche migration: autonomous execution log

Rules for this file: [CLAUDE.md](CLAUDE.md) section 9. Status source of truth: [PROJECT_STATUS.md](PROJECT_STATUS.md); this log
records *what was done, why, and on what authority*, and the resume point. One entry per meaningful task or decision. When
new evidence changes an entry's status, edit its **Status** line and add a dated "Update" line under it; do not delete the
original reasoning. Do not add routine progress entries.

## Current state (updated 2026-11-04)
- **Branch:** `avalanche/phase1-closure-phase3-proposal`. `origin/master` = `1ef0af7`. Local, **unpushed** commits: `e823a4a`,
  `bfb1421`, `14683cf`, `de4c6ac`, `d98aec2`, plus the commit of L-005. Push is **not authorized** (owner response
  2026-11-03 section 10).
- **Phase 1:** PARTIAL. E1 BLOCKED (R1/C1, B1); E2 BLOCKED (DB operator; environment: development database only); E3
  DEFERRED; E4 OPEN (R3); E5, E6 INCONCLUSIVE (R4); E7 PARTIAL (R5). Nothing waived.
- **Phase 2:** IMPLEMENTATION COMPLETE WITH EXTERNAL VALIDATION PENDING. Open authorized residuals: A1, A3, A7 (A6
  optional). Gated: A8 (R9), A2 (A8, F3), A5 (C4). A4 moved to Phase 3 (R8).
- **Phase 3:** PROPOSED, **NOT APPROVED** (R6/C9). No Phase 3 implementation started.
- **Decided 2026-11-03:** O-2 (shared immutable baseline; clone prototype LOCAL_SIMULATION only, not built), O-6
  (stuck-and-reported; no privileged withdrawal, bypass or rerouting), R8 (A4 to Phase 3), R2 (development database only).
- **Last validated baselines (2026-11-03):** reactor payment 72, marketplace 381, misa 20, solana-integration 10,
  avalanche-integration 104 (3 optional skipped); Foundry 55 pass, 1 skipped; Slither 12 results (triaged); frontend 28/28
  files, 841 tests.
- **Waiting on owner:** R1, R2 remainder (DB operator), R3 to R6, R8 remainder (C3 treatment, C4, A5), R9, R10. **Waiting on
  operators:** X1 to X3.
- **Recommended next action:** L-006 (A7 Solana-literal clean-up), then A1, then A3. See L-006.

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
- **Status:** NEXT (not started).
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
