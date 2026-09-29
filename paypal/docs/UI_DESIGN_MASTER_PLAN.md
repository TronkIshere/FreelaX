# FreelaX UI Design Master Plan
Status: ACTIVE DESIGN ROADMAP
Date locked: 2026-09-29
Tracking branch: docs/ui-design-tracking-20260929
Implementation authorization: NO

## Governing order
PRODUCT CONTRACT → SCREEN ARCHITECTURE → ART DIRECTION → REAL SCREEN SETS → SHARED DESIGN SYSTEM → IMPLEMENTATION WORKPACKS

Do not reverse this order.

## D0 — Governance & Visual Direction Lock
Status: PUBLISHED / REVIEW PENDING

Locked:
- Kinetic Editorial Brutalism
- no-SaaS identity
- work-first/non-wallet identity
- Set A–E ownership
- non-overlap rules

D0 PASS requires published docs + diff review.

## D1 — Set A: Marketplace Editorial Language
Priority: P0 DESIGN
Status: NOT STARTED

Screens:
- Freelancer Discover
- Freelancer My Applications
- Client My Jobs
- Client Job Detail

Tasks:
D1.1 lock deterministic fixture.
D1.2 produce exactly 3 studies with same fixture: Editorial Grid, Graphic Split, Dense Marketplace List.
D1.3 cover OPEN and application PENDING/ACCEPTED/REJECTED/CANCELLED.
D1.4 desktop/tablet/mobile.
D1.5 human visual QA.
D1.6 select one marketplace grammar.

Exit: approved job-list and job-detail grammar.

## D2 — Set B: Work State / Kinetic Workflow
Priority: P0 DESIGN
Status: NOT STARTED

Screens:
- Freelancer Workspace
- Submit Work
- Client Submission Review

State storyboard:
IN_PROGRESS → SUBMITTED_FOR_REVIEW → REVISION_REQUESTED → SUBMITTED_FOR_REVIEW → COMPLETED

Tasks:
D2.1 storyboard
D2.2 workflow rail
D2.3 CTA ownership handoff
D2.4 submission/revision history
D2.5 functional motion grammar
D2.6 reduced-motion path

Exit: approved workflow/state/motion grammar.

## D3 — Set C: Financial Evidence Language
Priority: P0 DESIGN
Status: NOT STARTED

Screens:
- Client Payment Detail
- Freelancer Payout Detail
- Tax Records
- Certificate Detail

Tasks:
D3.1 separate Job COMPLETED from payment/payout/tax completion.
D3.2 ledger/statement grammar.
D3.3 Mock USDC / Estimated VND / Simulation / Devnet treatment.
D3.4 evidence lifecycle.
D3.5 tax/certificate document treatment.

Exit: approved financial/evidence grammar.

## D4 — Brand Token Lock
Status: BLOCKED BY D1–D3

Compare palettes on identical approved screens, then lock:
- typography
- spacing/divider/border/radius/hard-shadow tokens
- state tokens
- motion tokens

Candidate families:
- Bone / Ink / Vermilion
- Bone / Ink / Yellow + Sage
- Graphite / Bone / Warm Orange
- another justified non-blue direction

Exit: FREELAX_DESIGN_TOKENS_V1.

## D5 — Shared Design System Extraction
Status: BLOCKED BY D4

Derive primitives from approved screens. No universal Card component and no universal status-pill grammar.

## D6 — Set D: Shell / Navigation / Activity
Status: BLOCKED BY D1–D5

Design desktop/mobile shells for both roles and activity/deep-link behavior.

## D7 — Set E: Auth / Identity / Account
Status: BLOCKED BY D4–D5

Design Login, Client Registration, Freelancer Registration, Account/Profile, and supported Security/Sessions.

## D8 — Implementation Planning
Status: BLOCKED

Technical gates:
- G0.1 /auth/me userType integrated/accepted on shared backend baseline
- G0.2 refresh contract accepted and implemented consistently
- G0.3 budget edit remains disabled until job budget and checkout amount are contract-consistent

## D9 — Flutter Implementation
Status: NOT AUTHORIZED

Required loop:
INSPECT → PROPOSE EXACT FILE MANIFEST → APPROVE → IMPLEMENT → TARGETED VALIDATION → DIFF REVIEW → FULL VALIDATION ONCE → COMMIT

Defaults:
DELETE DENY / MOVE-RENAME DENY / BACKEND CHANGE DENY / DEPENDENCY CHANGE DENY / BROAD REFACTOR DENY / PUSH-PR DENY unless explicitly authorized.
