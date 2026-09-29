# FreelaX UI Design Set Plan
Status: DESIGN PREPARATION
Implementation: NOT AUTHORIZED

Each set owns one visual question. No set may redefine product IA, backend contracts, permissions, state transitions, or screen inventory.

## Set A — Marketplace Editorial Language
Owns:
- Freelancer Discover
- Freelancer My Applications
- Client My Jobs
- Client Job Detail

Must answer how a job looks without generic cards, how role/context changes hierarchy, and how editorial composition survives mobile.

Excludes: payout/tax, auth, shell, final palette.

## Set B — Work State / Kinetic Workflow
Owns:
- Freelancer Workspace
- Submit Work
- Client Submission Review

States:
IN_PROGRESS → SUBMITTED_FOR_REVIEW → REVISION_REQUESTED → SUBMITTED_FOR_REVIEW → COMPLETED

Must answer workflow rail, state hierarchy, CTA ownership, revision history, functional motion, reduced-motion equivalent.

Excludes: discovery, finance, auth, shell.

## Set C — Financial Evidence Language
Owns:
- Client Payment Detail
- Freelancer Payout Detail
- Tax Records
- Certificate Detail

Must separate Job COMPLETED from payment/payout/tax completion and define ledger, evidence, Devnet/simulation honesty, payout lifecycle, certificate treatment.

Excludes: discovery, auth, shell, wallet-first concepts.

## Set D — Shell / Navigation / Activity
Owns role-aware navigation, overview framing and activity/deep links.

CLIENT:
Tổng quan / Công việc / Thanh toán / Hoạt động / Tài khoản

FREELANCER:
Tổng quan / Công việc / Thu nhập / Hoạt động / Tài khoản

Excludes detailed marketplace/work/financial internals.

## Set E — Auth / Identity / Account
Owns:
- Login
- Client Registration
- Freelancer Registration
- Account/Profile
- Security/Sessions where supported

Keep these surfaces calmer than marketplace/work screens.

## Sequence
1. Set A
2. Set B
3. Set C
4. Lock tokens
5. Extract shared design system
6. Set D
7. Set E

Do not invent a generic component library before Sets A–C. Derive shared primitives from approved real screens.

## Palette rule
Do not lock palette globally before Sets A–C have been tested on the SAME content/state fixtures.

## Approval gate
A set is approved only when:
- canonical art direction is preserved
- product semantics are unchanged
- ownership does not overlap another set
- desktop/mobile behavior is coherent
- states do not rely on color alone
- reduced-motion behavior exists where needed
- UI does not regress into SaaS/Web3-template patterns
