# FREELAX UI IMPLEMENTATION PROGRESS

## VISUAL POLISH STREAM — 2026-10-08

Current branch: `feat/ui-visual-polish-20261006`.
Frozen source implementation baseline: `7fc31555b9cd3f50a23872401968ee67c5275f30` (delayed review invitation reconciliation; all earlier frozen visual surfaces preserved).
Documentation commits may advance HEAD without changing this source baseline.

| Stage | Status | Accepted source/evidence |
| --- | --- | --- |
| VP.0 Visual branch/direction lock | PASS | Integrated MVP `35ba34e`, initial foundation `a47c865` |
| VP.1 Kinetic toolkit + Overview | PASS / FROZEN | `01c7e55`; 61/61 focused tests, build/diff PASS, console 0, 1440/1024 PASS |
| VP.2 Job discovery data foundation | PASS / FROZEN | `302ae06`; real category/skills authoring/filtering, Marketplace package PASS |
| VP.3 Freelancer Explore | PASS / RE-FROZEN | `f42e44a` original; `dc440ec` narrow identity override; layout/filter/search frozen |
| VP.4 Freelancer Applications | PASS / RE-FROZEN | `2f5f0e4` original; `dc440ec` rails/plates and state-aware primary; 180/180 final global gate |
| VP.5 Freelancer My Work | PASS / RE-FROZEN | `ca8b93d` original; `dc440ec` rails/plates/progress accents; primary/layout preserved |
| Stable Job Visual Identity | LOCKED | `ca8b93d`; 131/131 focused tests, 36/36 thumbnail recheck, build/diff and cross-screen identity PASS |
| Client Work C1 | PASS / FROZEN | `dc440ec`; CLIENT WORK CONTROL BOARD, real server order/count/pagination/actions |
| Global Row Identity / State Rail / Category Plate | LOCKED | `dc440ec`; 180/180 focused tests, build/diff PASS, 1440/1024 PASS, console 0 |
| Client Work C2 — Job Authoring | PASS / FROZEN | `846142d`; `/work/new`, `/work/:jobId/edit`; 50/50 JobEditor tests, build/diff PASS, accepted desktop QA |
| Client Work C3 — Applicants | PASS / FROZEN | `45c54c5`; `/work/:jobId/applications`; 66/66 focused tests, build/diff and corrected desktop/confirmation QA PASS |
| P06.5A Finance list | PASS / FROZEN | `96cafbe`; 30/30 final Finance tests, type-check/build/diff PASS, accepted Client/Freelancer desktop QA |
| P06.5B Finance detail / Money Evidence Spine | FULLY FROZEN, Client/Freelancer 1440/1024 | Client `9c76ffb`, shared source `837bcf0`; 51/51 focused tests, build/diff PASS; role/focus/overflow/console 0 PASS; zero responsive source changes |
| P06.5C Tax / Chứng từ thuế | PASS / FROZEN | `1b2ee56` visual patch; 81/81 focused Finance tests, build/diff PASS, Client ledger/Freelancer detail 1440 and 1024 layout QA PASS |
| P06.5D Activity / Editorial Event Ledger | PASS / RE-FROZEN — visual emphasis patch | `c71c554`; 146/146 focused tests, build/diff PASS; Client/Freelancer 1440/1024; original-text disclosure, focus, console 0 |
| P06.6 Account / Profile | PASS / FROZEN | `a0e16f9`; 220/220 focused tests, build/diff PASS; Client/Freelancer and partner 1440/1024; console 0 |
| PRE-P06.7 Completed Job review opportunity | PASS / FROZEN | `99e5f08`; 317/317 tests, build/diff PASS; runtime absent opportunity reported; isolated 1024 layout PASS |
| P06.7 Full regression / QA | PASS / FROZEN | `7fc3155`; real contract E2E, 99/99 focused + 793/793 full tests, accepted expected TaxRecord absence |
| P06.8 Final freeze / handoff | NOT_STARTED | Next separately authorized phase |

Explore live category/skill/combined/exclusion/Clear PASS is newer scoped runtime evidence. Earlier 115/115 focused frontend gate also passed. Local “Landing page redesign” category/skills edit is runtime evidence, not seed/source data. The approved narrow backend exception `302ae06` is complete; no unrelated backend expansion is authorized.

Client/Freelancer Overview remains FROZEN. Client Work C1/C2/C3 are PASS / FROZEN. Freelancer Explore, Applications and My Work are RE-FROZEN after the approved narrow cross-surface identity override; no general polish reopening. Applications retains only real PENDING, ACCEPTED, REJECTED and CANCELLED statuses and truthful `totalElements` totals; no fake timeline/status/counts. P06.5A Finance list is PASS / FROZEN. Current: P06.7 FULL REGRESSION / QA — PASS / FROZEN. Source `7fc31555b9cd3f50a23872401968ee67c5275f30` fixes delayed contract review invitations. Real modern contract-backed lifecycle, scheduler settlement/invitations, both reviews and public reputation are verified. ContractReviews remains the single review read/eligibility owner. Focused tests 99/99 and full frontend tests 793/793 (22 files), production build and diff check PASS. Client/Freelancer 1440/1024 regression PASS. Unexpected JS/page errors 0; unexpected network/runtime failures 0; overflow 0; eight handled missing-TaxRecord 404 resource messages are documented expected network absence. Solana/on-chain is not fully reconciled; off-ramp/tax NOT_STARTED. P06.5A/B/C/D and P06.6 remain frozen. Next: P06.8 — FINAL FREEZE / HANDOFF — NOT_STARTED. Stop after this freeze.

C3 accepted concept: CANDIDATE DECISION DESK / EDITORIAL CANDIDATE DOSSIER. Final corrected state-aware colored Job banner replaces rejected plain Cream + lines: OPEN Acid; AWAITING_PAYMENT/review Acid + Ink; IN_PROGRESS Cobalt; revision Vermilion; COMPLETED Mint + Strong Green; CANCELLED Ink/Cream. Shared semantic thumbnail/plate and real Job facts; equal Cream dossiers with hard Ink border/zero-blur shadow, enlarged deterministic identity tiles, separate evidence/status/action zones. Application rails: PENDING Acid, ACCEPTED Strong Green, REJECTED Vermilion, CANCELLED Ink; 6px/5px and visible labels.

C3 truth: Application API owns status/date/eligibility; matched FREELANCER public profiles enrich unique IDs in parallel with isolated failures. Server order/equal weight preserved; no private email, ranking, match score, filters or invented fields/counts. Sparse runtime profile stays sparse; visual density uses structure/typography/color/cut-paper details, never fabricated content. Null reputation omitted, returned zero counts remain. Selection only CLIENT + owner + OPEN + PENDING; local Ink confirmation first, actual acceptance/closed pending applications and funding-before-work copy, duplicate lock and server refresh/reconciliation. Funding mutations stay outside C3.

C3 corrected accepted gate: **66/66 focused tests PASS**, production build/diff check PASS, **1440 PASS / 1024 PASS / Confirmation 1440 PASS**, no overflow/clipping, console **0 errors**, focus-visible PASS, reduced motion preserved. Backend/API contracts unchanged; screenshot assignment **NOT EXECUTED**. Runtime one sparse PENDING applicant; richer profiles/multiple applicants/assignment outcomes covered by tests. Source `45c54c5` normally pushed; no further source changes or tests/build rerun during freeze.

C2 accepted one-page work order: 01 Nội dung công việc (Acid), 02 Điều kiện thực hiện (Cobalt), 03 Sản phẩm bàn giao (Vermilion), 04 Điều kiện nghiệm thu (Mint); section colors are not workflow states. CREATE retains the full `title/description/category/skills/budgetUsd/deliveryDueAt/reviewWindowHours/maxRevisions/deliverables/acceptanceCriteria` contract and existing limits. EDIT remains `title/description/category/skills` only for trusted CLIENT + owner + OPEN; other server facts are read-only context.

1440 editor left/sticky Live Draft Summary right; 1024 Hero → 01 → Preview → 02 → 03 → 04 → Actions. Preview is UNSAVED local draft truth only, non-empty deliverable/criteria counts, at most one actual deliverable title and a disclaimer; no fake workflow/payment/progress claims. Explicit category selection, neutral before selection, selected-family default variant 0 before saving; real job ID later selects a stable variant. Skills retain comma parsing/normalization; create-only deliverables/criteria retain numbered rows, add/remove, bounds and required=true.

Final accepted C2 gate: **50/50 JobEditor tests PASS**, production build/diff check PASS, **Create 1440 PASS / Create 1024 PASS / Edit 1440 PASS**, no horizontal overflow/sticky overlap, console **0**, keyboard focus and reduced motion PASS. Backend/API/frozen surfaces unchanged. Browser visual-QA draft **NOT SUBMITTED**. No tests/build or runtime smoke rerun during freeze.

Final accepted C1/global evidence: 180/180 focused tests PASS; production build/diff check PASS; 1440/1024 no overflow; console errors 0; keyboard focus PASS; reduced motion preserved. Backend/API/business/filter behavior unchanged. Tests/build were not rerun for freeze. C1 runtime: 5 real jobs, page 1/1, OPEN/IN_PROGRESS/COMPLETED observed; Applicants, Job Detail and `/work/new` navigation PASS. Runtime lacks REJECTED/CANCELLED Applications; focused tests cover those states without data mutation.

Global grammar LOCKED: semantic thumbnail = Job type; category plate = explicit resolved visual family; state rail/status marker = progress. Secondary rows stay Cream, with saturated 6px rails at 1440 and 5px at 1024. Strong Success Green `#39B96E` marks COMPLETED/ACCEPTED; Fresh Mint is a light primary success background. Thumbnail/plate/backing share existing `jobFamily`, with meaningful category → legacy skills → title → Generic Development; stored OTHER remains OTHER. ID selects only stable within-family variant/Rough identity. Primary Applications: PENDING Acid/Ink, ACCEPTED Mint/Green, REJECTED Vermilion/Cream, CANCELLED restrained Ink/Cream. Exact colors/labels are locked in the visual spec.

My Work `/work/mine` is an ACTIVE WORK / DELIVERY TRACKER: first server record emphasized, state-aware treatment, semantic thumbnail, real budget/optional deadline/revision context, state-derived next destination, flat ledger and server pagination. No invented search/filter/sort. Runtime observed 4 jobs, IN_PROGRESS and COMPLETED; sparse legacy category/skills are runtime observations, not guaranteed seed data. Budget is job value; COMPLETED is not payout confirmation. AWAITING_PAYMENT is read-only for Freelancer; RELEASE_PENDING/REFUND_PENDING do not claim payment/refund completion.

Stable thumbnails: meaningful category → skills → title → Generic Development; OTHER permits decorative skills/title fallback without modifying stored category. Job ID only selects a deterministic variant within family (three approved variants; absent ID → 0), with stable family/job Rough seed. Same job has identical family/variant/marks across Explore, Applications and My Work; future Client Work must reuse this component.

Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px.

## Historical P06 and integration records

The original P06.0–P06.x and Workpack entries below are preserved. Their earlier ENVIRONMENT BLOCKED runtime gates remain blocked historical evidence; the newer Explore smoke does not retrospectively mark those gates PASS. Old baseline/next-task wording below is superseded by the current visual stream above.

> Baseline reviewed: `master` at `3332d30ad542a5d49f721e1a1f46d9da8b8203c3`

Status legend:

```text
NOT_STARTED
IN_PROGRESS
BLOCKED
PASS
DEFERRED
```

## P06.0 — UI reconnaissance and baseline lock

Status: `PASS`

Completed:

- README reviewed.
- `docs/mvp-functional-spec.md` reviewed.
- Current frontend source inventory reviewed.
- Kinetic Editorial Brutalism locked.
- UI-only scope locked.
- Main feedback captured: visually strong but too text-dense.
- Master refreshed to `3332d30`.
- Contract/Milestone foundation reviewed and incorporated into UI memory.
- Real fields now recognized: deliverables, acceptance criteria, delivery due date, review window, max revisions, revisions used, contract status and milestone status.

## P06.1 — Shared hierarchy foundation

Status: `PASS`

Target:

- spacing rhythm
- typography hierarchy
- section heading hierarchy
- metadata treatment
- disclosure/technical evidence treatment
- primary vs secondary action hierarchy

Expected files:

```text
frontend/src/components.tsx
frontend/src/styles.css
```

Gate:

- no route changes
- no business/data changes
- targeted tests PASS

Completed:

- Refined existing PageHeading and StatePanel without changing their content or action contracts.
- Added shared type sizes, readable text measure and 24/32px section rhythm.
- Quieter eyebrow, category and cell labels; stronger distinction between Ink primary actions and outlined secondary actions.
- Added narrow presentation primitives: SectionHeading (h2/h3), FactGrid (native definition list), EvidenceDisclosure (native details, collapsed by default) and ActionGroup.
- Reused existing section-heading and technical-evidence CSS; screen-specific adoption remains for later stages.
- Extended common focus-visible treatment to textarea and summary; retained error/status announcements and reduced-motion behavior.
- Removed the rigid body minimum width that caused overflow at a 320px viewport with a vertical scrollbar.
- Targeted shared-component and consumer tests: 40/40 PASS.
- Production build: PASS. No route, business, API, backend or dependency changes.

## P06.2 — Shell / navigation / global framing

Status: `PASS`

Expected files:

```text
frontend/src/App.tsx
frontend/src/components.tsx
frontend/src/styles.css
```

Target:

- masthead
- primary nav
- role identity
- top context
- Freelancer subnav
- page heading density
- footer
- desktop/tablet/mobile behavior

Completed:

- Simplified the wordmark and removed the duplicate role/workspace context strip and explanatory shell footer text.
- Kept trusted role, full display name and logout together in the masthead; reused the P06.1 ActionGroup.
- Unified primary active navigation with a Vermilion edge, pale Vermilion surface and stronger label; retained native NavLink aria-current.
- Made Freelancer work subnav quieter with an Ink underline and smaller type, preserving its three labels and paths.
- Consolidated shell gutters, content width and footer rhythm; CSS alone handles desktop and mobile stacking/wrapping.
- Kept all five destinations per role, route definitions, session handlers and business/API behavior unchanged.
- Made the skip-link destination programmatically focusable and retained visible Cobalt focus.
- Targeted tests: 18/18 PASS. Production build: PASS. Isolated shell visual gate: PASS at 1440/1024/390/320px for both roles, including long display names.
- Authenticated runtime smoke: `ENVIRONMENT BLOCKED` — local Marketplace upstream unavailable; no runtime or backend workaround.

## P06.3 — Overview + Jobs / Discovery

Status: `PASS`

Expected files:

```text
frontend/src/Overview.tsx
frontend/src/Jobs.tsx
frontend/src/styles.css
```

Target:

- reduce intro copy
- emphasize needs-attention items
- simplify job row scan order
- strengthen budget/status/next-action hierarchy
- show real deadline/revision context only where it helps
- reduce duplicate metadata

Completed:

- Put Cần xử lý first, with real Client review/recruiting/awaiting-payment states and Freelancer revision/working states prioritized from the existing returned page. Awaiting payment links to status only; no funding mutation.
- Separate attention items from the recent-work ledger; keep Freelancer application responses, empty/error/retry states and truthful page coverage visible without extra API calls.
- Shortened page introductions and removed repeated role/source/process labels and decorative row numbering.
- Reused P06.1 SectionHeading, FactGrid and ActionGroup; preserved P06.2 shell/navigation.
- Simplified job rows to title/short brief, prominent budget/single status, useful delivery/revision terms, quieter ownership/application context and explicit existing-route action.
- Show terms only when returned; contract snapshot due date/revision values take precedence when present, including zero revisions. No countdown or fabricated contract/milestone data.
- Preserve all Discovery filters, draft/apply validation, pagination, hasApplied/applicationStatus and detail-before-apply behavior.
- Targeted tests: 38/38 PASS. Production build: PASS. Responsive UI preview: PASS at 1440/1024/390/320px.
- Authenticated runtime smoke: `ENVIRONMENT BLOCKED` — existing local Marketplace unavailable; backend/runtime unchanged.

## P06.4 — Contract-backed Workflow Integration + UI Polish

Status: `PASS` (frontend contract/UI/test/build gate; authenticated real-stack smoke `ENVIRONMENT BLOCKED`)

Priority: `HIGHEST`

Expected files:

```text
frontend/src/Workflow.tsx
frontend/src/WorkLifecycle.tsx
frontend/src/styles.css
```

Target reading order:

```text
job identity
→ current state
→ next action
→ brief/document
→ deliverables + acceptance criteria
→ contract/milestone context
→ latest submission/feedback
→ lifecycle
→ supporting metadata
→ technical evidence
```

Rules:

- integrate P06.4A bank/funding before P06.4B contract evidence/decisions
- integrate P06.4C server deadlines before P06.4D visual polish
- preserve server-owned versions and immutable contract scope
- render acceptance criteria only from API data
- render revision usage only from API-backed contract data
- countdown uses returned reviewDueAt only; never approves in browser
- use verified funding/contract decisions including minimal OPEN_DISPUTE
- RELEASE_PENDING is not released money

Completed:

- P06.4A `PASS`: Client bank readiness/save with masked response and cleared full-number inputs; immutable decimal funding amount, explicit simulation confirmation, stable per-attempt idempotency and bounded reconciliation. Freelancer receives a waiting/read-only view. Unfunded contract finance routes use funding instead of legacy payment-status.
- P06.4B `PASS`: contract snapshot evidence, HTTPS links, exact request validation, stable submission payload/key across uncertainty, server versions/history and Client approval/revision/minimal dispute decisions. Contract-backed jobs do not call legacy workflow mutations.
- P06.4C `PASS`: server UTC review/grace timestamps, display-only countdown, custom review window, late/automatic-review markers, revision quota and boundary/focus/actual-notification reconciliation. Zero does not approve in the browser.
- P06.4D `PASS`: document-first desktop hierarchy, current ownership/action, grouped scope/terms, latest evidence, associated feedback, quiet history and collapsed technical references. DISPUTED is frozen; RELEASE_PENDING is processing, never payout complete.
- Full frontend suite: 152/152 PASS. Final narrowly scoped funding unmount guard verified with 14/14 focused tests; final production build PASS.
- Isolated actual-App preview: desktop 1440px and 1024px PASS, no horizontal overflow or console errors. Keyboard forms and visible Cobalt focus verified. Synthetic preview data is UI evidence only, not real API/runtime proof.
- Authenticated real-stack smoke: `ENVIRONMENT BLOCKED` — read-only localhost:9191/api/v1/auth/me could not connect (HTTP 000). Backend/runtime unchanged; live contract integration remains unverified.
- Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px.
- No commit or push. P06.5–P06.8 remain `NOT_STARTED`.

## P06.5 — Finance + Tax + Activity

Status: `PARTIAL` — P06.5A Finance list FROZEN; P06.5B Finance Detail FULLY FROZEN for Client/Freelancer 1440/1024; remaining Tax / Activity polish NOT_STARTED. The target notes below do not authorize implementation without human concept approval.

## P06.5A Finance list — PASS / APPROVED / FROZEN (2026-10-07)

Source: `96cafbe985c68c4fdcc0e53a52141375335ca387` — `feat(frontend): finalize kinetic finance overview`. Approved files: `frontend/src/Finance.tsx`, `frontend/src/Finance.test.tsx`, `frontend/src/styles.css`. Normal push to `origin/feat/ui-visual-polish-20261006` completed. No additional source changes or tests/build reruns during freeze.

- Client `/finance`: **Thanh toán theo công việc.** Track payment/funding/release/refund truth per Job.
- Freelancer `/finance`: **Thu nhập theo công việc.** Track release/payment/tax evidence per assigned Job; same structure with role-aware language.
- Four approved colored summary/workflow blocks: **Hồ sơ trên trang** uses only the real current-page Finance count. **Funding / Release / Hoàn tiền are workflow guidance**, not monetary KPIs unless an existing API explicitly returns numerical aggregate truth. No fabricated totals for funding, escrow/wallet balances, pending release or platform earnings.
- Ledger stays predominantly Cream: lightly tinted structured header; shared stable Job thumbnail/resolved Category Plate; real Job value, state, financial update date/evidence and returned USDC/VND only. Missing fields are omitted or identified as unavailable; no invented filters, values, dates or progress. Same Job keeps its existing visual identity across work and Finance surfaces.
- Row CTA **Xem chi tiết** stays light/Cream with Ink text, clear border, directional arrow and visible focus; never a large Ink/black-filled action.
- Finance subnav is **EDITORIAL FOLDER TABS**: 01 Theo công việc; 02 Chứng từ thuế. Both are horizontal on one Ink baseline, with square corners, Lucide icons and numeric index. Active always Acid/Ink with 2px Ink border and zero-blur 3–4px hard shadow; inactive Cream/Ink with restrained Cobalt index. `/finance` selects 01; `/finance/tax-records` and its detail routes select 02. No third tab, pills, dropdown or permanent per-tab active colors.
- At 1024 desktop, top blocks stack vertically, rows reflow, tabs stay horizontal, controls remain readable and actions unclipped with no horizontal overflow. Desktop/laptop 1440/1024 is the gate; mobile optimization remains deferred.
- Exactly one bottom panel: Client **Quy trình thanh toán**, Freelancer **Quy trình nhận tiền**. No extra explanatory/color cards, graphs, wallet/crypto balance dashboard, glass, blur or gradients. Major colors are confined to the four top blocks, header tint, semantic badges/rails, active tab and the one process panel.
- Preserve server-driven **Mô phỏng** markers. Simulation is not production settlement finality; funding/CAPTURED is not release/refund proof; release is not proof of completed bank payout. Existing Finance API, pagination, settlement/tax/release/refund/simulation semantics are unchanged.
- Overview, Freelancer Explore/Applications/My Work, Client C1/C2/C3 and Stable Job Visual Identity / Global Row Identity / Global State Rail / Progress Color / Category Plate systems remain frozen. No general-polish reopening without a concrete regression. Backend/API unchanged; no credentials, secrets, environment values, screenshots or machine-local paths added.

Accepted implementation validation (tests/build not rerun during freeze): Finance focused tests **30/30 PASS** after folder tabs; earlier Finance visual gate **36/36 PASS**; type-check PASS; accepted production build PASS; `git diff --check` PASS; Client 1440 PASS, Freelancer 1440 PASS, Client 1024 PASS; console errors **0**; keyboard focus PASS; reduced motion preserved.

Current: P06.6 ACCOUNT / PROFILE — PASS / FROZEN. Source `a0e16f9090efae89b3431be511da2c418e21ac68`. Trusted /auth/me identity passport remains distinct from editable /profiles/me dossier. Profile/skills versioning, display-name session reconciliation, portfolio uncertainty guards and partner-profile privacy preserved. Client/Freelancer Account and authenticated partner profile 1440/1024 PASS; console 0, Cobalt focus, no overflow, reduced motion PASS. 220/220 focused tests, production build and diff check PASS. P06.5A/B/C/D unchanged. Next: P06.7 — FULL REGRESSION / QA — NOT_STARTED. Stop after this freeze.


Expected files:

```text
frontend/src/Finance.tsx
frontend/src/Activity.tsx
frontend/src/styles.css
```

Finance target:

```text
amount
→ main state
→ independent stages
→ useful action
→ certificate
→ technical disclosure
```

Activity target:

```text
event type
→ title
→ one-line message
→ time
→ action
```

## P06.6 — Account / Profile

Status: `PASS / FROZEN` — source `a0e16f9090efae89b3431be511da2c418e21ac68`.

Trusted session passport, Marketplace profile/editor, Freelancer portfolio and authenticated partner profile. Auth remains unchanged. Current scope and validation are recorded in the final dated freeze below; earlier Auth-polish planning is superseded.

## P06.7 — Responsive / accessibility / regression

Status: `PASS / FROZEN` — source `7fc31555b9cd3f50a23872401968ee67c5275f30`.

Real contract-backed lifecycle/reviews/reputation and Client/Freelancer desktop 1440/1024 regression passed. Exact validation, expected missing-TaxRecord network absence and independently unfinished downstream state are recorded in the final dated P06.7 freeze below. Mobile is deferred; earlier mobile/auth-register planning does not describe this gate.

## P06.8 — Final UI review

Status: `NOT_STARTED`

Required evidence:

```text
git diff --stat
git diff --check
frontend tests
frontend build
browser smoke
responsive smoke
working tree status
```

Final verdict:

```text
P06 UI POLISH — PASS
```

or exact blocker.

## Deferred future product work

Not part of P06 unless current master gains the corresponding API and Product Owner expands scope:

```text
Profile persistence / Portfolio / Reputation backend
Full release/settlement orchestration
Cancellation/refund and Admin dispute resolution
Rating
Auto-release
Wallet onboarding
Chat
AI / blockchain skill verification
```

## Progress update template

After every implementation stage append:

```text
### YYYY-MM-DD — P06.X

Status:
Baseline HEAD:
Files modified:
Files created:
Tests:
Build:
Browser smoke:
Responsive:
Backend changed:
Blockers:
Next:
```

### 2026-10-04 — P06.1

Status: `PASS`
Baseline HEAD: `1f602bbc5d5d25cacc41920fd35ea99892fd71bf`
Files modified: `frontend/src/components.tsx`, `frontend/src/styles.css`, `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`
Files created: `frontend/src/components.test.tsx`
Tests: `node node_modules/vitest/vitest.mjs run src/components.test.tsx src/Polish.test.tsx src/Jobs.test.tsx src/Workflow.test.tsx src/Finance.test.tsx` — 5 files, 40 tests PASS (8 focused shared-component tests). Existing React Router future-flag warnings only.
Build: `npm run build` — PASS using the existing installed npm executable.
Browser smoke: isolated shared-component preview PASS, no console errors; native disclosure keyboard activation and visible focus verified. App session restoration returned HTTP 500 from the Vite proxy for `/api/v1/auth/refresh-token`; the proxy logged upstream `ECONNREFUSED`. Authenticated route smoke was not performed; runtime/backend were not changed.
Responsive: shared preview at 1440px, 1024px, 390px and 320px; no horizontal overflow, including an expanded long technical identifier.
Accessibility: semantic h1/h2/h3 and dl/dt/dd preserved; native details/summary, error/status roles, polite announcements, disabled actions, visible Cobalt focus and reduced motion retained.
Backend changed: NO
Blockers: none for the P06.1 gate; unavailable local API upstream limits live authenticated smoke.
Next: stop for P06.1 review. P06.2–P06.8 remain `NOT_STARTED`.
Git: uncommitted; no push.

### 2026-10-04 — P06.2

Status: `PASS` (shell UI/test/build gate)
Baseline HEAD: `1406a430a457e334662e73ce5580c728ef00adb9`
Files modified: `frontend/src/App.tsx`, `frontend/src/styles.css`, `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`
Files created: `frontend/src/App.test.tsx`
Tests: `npm test -- src/App.test.tsx src/components.test.tsx` — 18 tests PASS (10 shell/navigation tests, 8 shared-component regression tests).
Build: `npm run build` — PASS.
Browser smoke: temporary isolated preview rendered the actual App with test-only session values; no production session/data changes and no console errors. This is UI evidence, not authenticated API validation.
Responsive: Client and Freelancer inspected at 1440px, 1024px, 390px and 320px. No horizontal overflow; primary nav and Freelancer subnav readable; normal long and unbroken long names wrap; logout remains accessible.
Accessibility: semantic navigation landmarks, NavLink aria-current, native links/buttons, visible Cobalt focus and keyboard skip-link activation verified. Pending/recoverable/successful logout behavior covered by tests.
Authenticated runtime smoke: `ENVIRONMENT BLOCKED` — existing localhost:8080 page cannot restore its session; direct read-only request to localhost:9191/api/v1/auth/me could not connect (HTTP 000). Backend/runtime unchanged.
Backend changed: NO
Business/API behavior changed: NO
Unsupported feature introduced: NO
Blockers: none for the P06.2 UI/test/build gate; authenticated runtime smoke requires the existing Marketplace service to be available.
Next: stop after P06.2 review. P06.3–P06.8 remain `NOT_STARTED`.
Git: uncommitted; no push.

### 2026-10-04 — P06.3

Status: `PASS` (Overview/Jobs UI/test/build gate)
Baseline HEAD: `2469997d24bdce6bb6f8b3007aa585c46ff1b059` — P06.2 already committed; starting worktree clean.
Files modified: `frontend/src/Overview.tsx`, `frontend/src/Jobs.tsx`, `frontend/src/styles.css`, `frontend/src/Jobs.test.tsx`, `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`
Files created: `frontend/src/Overview.test.tsx`
Tests: `npm test -- src/Overview.test.tsx src/Jobs.test.tsx src/Polish.test.tsx src/App.test.tsx` — 4 files, 38 tests PASS. Existing React Router future-flag warnings in the unchanged Polish test harness only.
Build: `npm run build` — PASS using the existing Node installation.
Browser smoke: actual App and SessionProvider rendered in a temporary loopback-only test preview with an isolated mock API. Overview role priority, Discovery filter interaction and query values verified; no console errors. Test data is not part of production source/runtime. Temporary preview removed after validation.
Responsive: Client Overview, Freelancer Overview, Client Jobs and Freelancer Discovery inspected at 1440px, 1024px, 390px and 320px. No horizontal overflow, including long unbroken job/client names. Descriptions clamp to two lines; budget, status and next action remain readable. Mobile filters usable.
Accessibility: h1/h2/h3 hierarchy, labelled regions/forms, native definition lists, links/buttons, error/status announcements retained. Keyboard interaction and visible Cobalt focus verified on mobile filter controls.
Authenticated runtime smoke: `ENVIRONMENT BLOCKED` — localhost:8080 reports that Marketplace cannot be reached; read-only localhost:9191/api/v1/auth/me request returned HTTP 000 (connection unavailable). No service restart, auth workaround or backend change.
Backend changed: NO
Business/API behavior changed: NO — API calls, filter/query semantics, routes, role authority, applications and Contract/Milestone logic preserved.
Unsupported feature introduced: NO
Blockers: none for the P06.3 UI/test/build gate; authenticated runtime smoke requires the existing Marketplace service to be available.
Next: stop after P06.3 review. P06.4–P06.8 remain `NOT_STARTED`.
Git: uncommitted; no push.

### 2026-10-05 — P06.4

Status: `PASS` (frontend contract/UI/test/build gate)
Baseline HEAD: `cf0594f90af99375570e8557cbde283b51cd50f5`, branch `feat/p06-ui-polish-20261004`; four intentional workflow WIP files preserved. Synced backend contracts at `50841a3` and checklist `6c605a9` inspected read-only.
Files modified: `frontend/src/Workflow.tsx`, `frontend/src/WorkLifecycle.tsx`, `frontend/src/styles.css`, `frontend/src/WorkflowLifecycle.test.tsx`, `frontend/src/api.ts`, `frontend/src/api.test.ts`, `frontend/src/types.ts`, `frontend/src/status.ts`, `frontend/src/Finance.tsx`, `frontend/src/Activity.tsx`, `docs/ui/START_HERE_UI.md`, `docs/ui/UI_DEVELOPMENT_MEMORY.md`, `docs/ui/UI_POLISH_SPEC.md`, `docs/ui/WORKPACK_P06_UI_POLISH.md`, `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`.
Files created: `frontend/src/Funding.tsx`, `frontend/src/Funding.test.tsx`, `frontend/src/ContractLifecycle.tsx`, `frontend/src/ContractLifecycle.test.tsx`, `frontend/src/workflowContracts.ts`.
Tests: full `npm test` — 12 files, 152 tests PASS. After the final funding unmount guard, focused `Funding.test.tsx` — 14/14 PASS. API, legacy workflow and finance regressions included in the full run; no repeated full-suite cycle.
Build: final `npm run build` PASS after the last source edit.
Browser smoke: isolated actual App/session UI with test-only API; Client funding/unknown reconciliation, masked bank save, Freelancer waiting/composer, review/revision, release pending and disputed states inspected. No real funding or backend workflow mutation was executed.
Responsive: 1440px PASS; 1024px PASS; Mobile DEFERRED — not required for current MVP. Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px.
Accessibility: semantic headings/forms/fieldsets, native links/buttons/disclosures, error/status announcements, disabled duplicate actions and visible Cobalt keyboard focus retained.
Security/privacy: full bank number/holder are transient and cleared after save; no bank details in persistent storage. Scoped sessionStorage contains only idempotency attempt metadata/submission draft. HTTPS evidence excludes credential URLs; text is escaped, external links use safe target attributes; technical IDs collapsed.
Backend changed: NO. Runtime/env/dependencies changed: NO. Browser calls internal services directly: NO.
Blockers: authenticated real-stack smoke `ENVIRONMENT BLOCKED` because Marketplace localhost:9191 is unavailable; contract integration with the running stack remains unverified. No blocker for the frontend UI/test/build gate.
Next: stop after P06.4 review. P06.5–P06.8 remain `NOT_STARTED`.
Git: 20 intended frontend/docs paths, uncommitted; no push; baseline HEAD/branch preserved.

### 2026-10-05 — UI Steps 1–5 P0 integration

Status: `PASS` (P0 frontend implementation/test/build gate); authenticated desktop runtime smoke `ENVIRONMENT BLOCKED`.
Branch: `feat/mvp-ui-step1-5-20261005`; clean start at `6b866fb5fc71aa6bb7e0f75decbce3f20236be2d`. Backend Steps 1–5 controllers/DTOs re-verified read-only.

Implemented:

- Typed nullable settlement/cancellation reads and exact cancellation request/decision payloads in the existing MarketplaceApi. Settlement amount accepts numeric/string decimals; cancellation amount follows the backend string serialization. No browser settlement mutation or refund key was invented.
- Submission APPROVED no longer implies permanent release pending. Ownership reads settlement money state and completed contract/milestone state independently; confirmed primary release survives downstream errors. A confirmed initial release also refreshes workflow state.
- Contract cancellation region covers Client pre-funding cancellation, participant funded proposals, counterparty ACCEPT/REJECT, rejected/continuing work, refund pending and final cancellation/refund record. Pre-funding eligibility requires no funding attempt, including FAILED; local unresolved funding intent also blocks cancellation.
- Eligibility/history/actions re-read before mutation, shared financial operation lock, exact persisted cancellation intent through uncertainty, GET reconciliation before explicit replay, and 30-second lifecycle-safe reads. REQUESTED does not stop work. REFUND_PENDING locks incompatible work actions and is not a final refund. ACCEPT is withdrawn when submission starts; REJECT remains available when the server permits it.
- Funding is reused inside the contract lifecycle with small optional mutation guards; pre-funding and cancelled contract routes retain their work document and records. Existing evidence/submission/revision implementations retained.
- Release/refund ledger simulation is explicit and does not claim a real bank transfer. Settlement tax-stage success is separate from certificate ACCEPTED. References/errors remain collapsed; no fake financial data in production UI.

Validation:

- Focused final gate: 6 files, `130/130 PASS` (financial, API, contract workflow, funding, legacy finance mapping and job workflow).
- Added 37 focused contract financial/cancellation tests and 3 API contract tests; existing contract lifecycle test mocks/copy updated for the intentional integration.
- Full frontend suite run once after implementation: 13 files, `193/193 PASS`. Existing React Router future-flag warnings only.
- Production build: TypeScript `tsc -b` + Vite build `PASS`. npm was unavailable in this session's PATH, so the exact test/build script tools were invoked with the existing Node executable and installed local packages; no software/dependency installation.
- Desktop real-stack smoke at 1440px/1024px: `ENVIRONMENT BLOCKED`. Read-only probes to localhost:3000, localhost:8080 and localhost:9191/api/v1/auth/me all timed out, including outside the filesystem sandbox. No runtime restart, backend workaround or mocked runtime PASS.
- Mobile optimization is deferred. Current MVP delivery target remains desktop/laptop; no mobile validation performed in this P0 pass.
- Final `git diff --check`: PASS. Backend/runtime/env/dependencies unchanged; browser API boundary remains Marketplace `/api/v1`.

P1/P2 remain pending: Finance list/evidence migration for pending release/refund records, Overview attention routing, Step 4/5 Activity labels/destinations, broader financial polish and documentation cleanup. Steps 6/8/9 and full Admin/dispute remain out of scope.
Git: 13 intended frontend/progress paths; uncommitted; no push/merge/branch switch. Stop after P0 review.

### 2026-10-05 — UI Steps 1–5 P1 integration

Status: implementation/test/build gates `PASS`; final P1 runtime gate `ENVIRONMENT BLOCKED` (desktop smoke unavailable).
Input gate: `feat/mvp-ui-step1-5-20261005`, clean HEAD `0e790a8d65f65831d751eed14f4110ef15753bf2` (accepted P0 commit). Backend controllers, response allowlists, notification types and contract/milestone transitions verified read-only.

Implemented:

- Finance includes contract-backed records before Job COMPLETED, including pending release/refund and unknown/retryable reconciliation. The existing API has no batch financial projection; parallel per-contract reads are bounded to a 20-job server page. Legacy completed-job evidence remains supported.
- Contract Finance distinguishes funding, primary release, cancellation outcome and refund. REQUESTED/REJECTED remain continuing work; REFUND_PENDING is not final. Neither CAPTURED checkout nor funding success is used as release/refund proof. Pre-funding cancellation has no funding mutation on the Finance page; existing workflow remains the action destination.
- Independent downstream on-chain/VND/tax states, available USDC/VND amounts and masked bank destination. Technical references/errors remain collapsed. Simulation wording does not claim external bank settlement; tax-stage success does not imply certificate ACCEPTED. Actual certificates link into the existing authenticated tax/download UI without duplicating its logic.
- Contract evidence reconciles with current job state every 30 seconds while non-terminal, pauses automatic reads when hidden, resumes when visible, ignores obsolete responses and removes timers/listeners on unmount. Confirmed primary money survives later read/downstream errors. Unfunded Finance does not call legacy payment/tax APIs.
- Overview verifies contract/milestone plus settlement/cancellation before recommending review/submission. Pending money routes to Finance; confirmed/final records remove incompatible work actions. REQUESTED/REJECTED preserve eligible work. Financial read errors fail closed; focus/notification refreshes ignore stale responses.
- Activity uses the five verified Step 4/5 notification types. Release/refund events link to the existing role-shared Finance route; cancellation proposals/rejections link to workflow. Unknown events retain escaped text and a safe fallback.

Validation:

- Added 37 P1 regressions across Finance, Overview, Activity and financial mapping, including pending list visibility, stale ownership, refund/release truth, recovery/visibility polling, obsolete responses and actual App destinations for both trusted roles.
- Final affected gate: 7 files, `139/139 PASS`; existing P0 financial/cancellation and contract lifecycle regressions included.
- Full frontend suite run once: 13 files, `230/230 PASS`. Existing React Router future-flag warnings only.
- Production build: `tsc -b` + Vite build `PASS`, using the existing Node executable/local packages (same tools as npm scripts); no installation or dependency change.
- 1440px and 1024px authenticated runtime smoke: `ENVIRONMENT BLOCKED`. Both sandboxed and external read-only probes to localhost:3000, localhost:8080 and localhost:9191/api/v1/auth/me returned HTTP 000 / connection refused. No service restart, backend workaround or mocked runtime PASS.
- Mobile remains deferred; no mobile implementation/validation. Backend, API/type contracts, shell, workflow production code, runtime/env and dependencies unchanged. Browser remains within Marketplace `/api/v1`.

Remaining: resume the real desktop Finance/Overview/Activity/workflow smoke when the existing runtime is available. P2 broader financial polish/documentation cleanup remains pending; Steps 6/8/9 and full Admin/dispute remain out of scope.
Git: 9 intended frontend/progress files, uncommitted; no push/merge/branch switch. Stop after P1 report.

### 2026-10-05 — UI Steps 1–5 P2 final polish

Input gate: branch `feat/mvp-ui-step1-5-20261005`, clean accepted P1 HEAD `7099bfa`.
Current accepted baseline: P0 `PASS` (`0e790a8`); P1 implementation/test/build `PASS` (`7099bfa`), P1 runtime `ENVIRONMENT BLOCKED`. The earlier P0/P1 entries above record their original report states; their implementation is now committed.
P2 implementation/test/build: `PASS`. Final Steps 1–5 runtime gate: `ENVIRONMENT BLOCKED`; do not claim final browser/UI PASS.

Polished:

- Shared Vietnamese release/refund simulation and tax/certificate caveats. Funding remains separate from release. REQUESTED wording now works for either participant; REQUESTED/REJECTED still mean continuing work. Confirmed primary money survives downstream failures and is not a claim of bank settlement.
- State-first financial hierarchy using the locked palette, compact structural status cues, quieter supporting headings/spacing and clear cancellation confirmation treatment. Pending release ownership no longer shares the confirmed financial emphasis. Shared primitives and keyboard/focus semantics retained; no shell redesign or mobile-specific work.
- Collapsed technical evidence uses readable Vietnamese labels and only populated, explicitly allowlisted references/errors. Empty evidence has one quiet message instead of repeated missing fields. Bank masking remains intact; unexpected provider secrets/tokens/internal keys are not rendered.
- Failed reads are distinct from genuinely empty responses. Finance has one read-only reconciliation CTA on financial read failure; stored proof is retained. Unread tax evidence before release is not labelled an empty server response. Overview financial attention metadata follows financial truth instead of stale job status.
- Minimal corrections in current START_HERE, UI memory/spec/workpack remove obsolete claims that Step 4/5 release/cancellation/refund support is absent. Historical audit entries/backend checklists and unrelated docs remain intact.

Validation:

- Added 5 focused regressions plus updated copy/hierarchy assertions: failed-vs-empty records, one read-only retry, allowlisted/collapsed evidence, confirmed money through downstream failure and clear cancellation confirmation. Existing funding/cancellation/settlement, stale Overview, pending Finance and safe Activity fallback tests preserved.
- Final affected tests: 7 files, `148/148 PASS`.
- Full frontend suite run once at closure: 13 files, `235/235 PASS`. Existing React Router future-flag warnings only.
- Production frontend build: TypeScript `tsc -b` + Vite `PASS`, using existing installed Node/local packages (the npm script tools); no dependencies/software installed.
- 1440px/1024px runtime smoke: `ENVIRONMENT BLOCKED`. Read-only probes inside and outside the sandbox to localhost:3000, localhost:8080 and localhost:9191/api/v1/auth/me returned HTTP 000 / connection refused. ContractLifecycle, Cancellation, Finance, Overview and Activity have not been verified in a live desktop browser in this pass. No fake browser PASS or service/auth workaround.
- `git diff --check`: `PASS`. No backend, API/type contract, business flow, runtime/env or dependency changes. No backend suites or full backend E2E rerun.

Remaining known limitations: live desktop validation requires the existing frontend/Marketplace runtime. Steps 6/8/9 remain backend-dependent future UI work and are not implemented or marked complete; full Admin dispute resolution, profile/reputation/rating and other unsupported features remain outside this pass.
Git: 15 intended frontend/current UI documentation files, uncommitted and unstaged; HEAD/branch preserved. No commit/push/merge. Stop after P2 report.

### 2026-10-06 — UI Workpack A: Step 6 participant dispute and Admin workspace

Input gate: `feat/mvp-ui-steps6-9-20261005`, clean HEAD `e10000b2380fb10735ab8cb4d3d574d0b1f99747` (`merge: expose trusted admin capability`).
Implementation/test/build: `PASS`. Real desktop/runtime gate: `ENVIRONMENT BLOCKED`; overall Step 6 UI acceptance remains pending live validation.

Implemented:

- Typed participant dispute read/create and bare-array evidence append through existing Marketplace APIs. Missing GET data is normalized to no case. Description is nonblank and capped at 2,000, reasonCode at 60; evidence batches at 10, TEXT at 2,000, credential-free HTTPS LINK at 2,048 and optional SHA-256 at exactly 64 hex characters. Evidence history is immutable; there are no upload/edit/delete/cancel-dispute endpoints or controls.
- Existing Client review `OPEN_DISPUTE` remains available and now enforces the same 2,000 limit. The authoritative case is reread after the mutation. Eligible Freelancer and pre-submission/revision participants can use direct dispute creation. Backend remains authoritative for eligibility and one-case uniqueness.
- Current ownership uses dispute plus contract/milestone and settlement/refund state. Historical `DISPUTED` submissions remain readable history, without keeping resolved cases waiting for Admin. Pending release/refund remains pending; resolved release reads existing settlement truth and retains primary success despite downstream failure. Refund shows only returned status/reference and final contract truth, without invented amounts, retry metadata, simulation or bank proof.
- Trusted `ROLE_ADMIN` adds a secondary masthead `Quản trị` entry; the five primary destinations and UserType stay unchanged. Direct Admin routes deny missing authorities. Queue uses a distinct Spring Page adapter; detail exposes only actual contract facts, requirements, submissions, evidence, funding and Admin audit. Participant UI never renders Admin audit. Admin participants cannot claim/resolve their own contract; resolution requires the current claimant, reason and explicit confirmation for one irreversible full-value outcome.
- Evidence and Admin decisions retain exact payload/idempotency key across ambiguous responses and reload. Manual replay reads authoritative state first; decisions already persisted are not reposted. Shared mutation locks prevent duplicate clicks. Failed authority reads disable mutation; later 403/404 drops cached privileged detail. HTTP support requestId is collapsed and distinguished from audit/idempotency references. Numeric business-code consumers are preserved alongside safe symbolic code/retryable metadata.
- Nonterminal cases refresh lightly while visible; listeners/timers are cleaned up. `DISPUTE_DECIDED` links to existing workflow and triggers authoritative refresh. No frontend release/refund mutation, direct Payment/MISA/Solana call, or Step 8/9 UI was added.

Validation:

- Added 48 focused Step 6 tests (22 contract/API and 26 UI), plus updated role-entry and existing workflow test mocks. Coverage includes 2,000/2,001 boundaries, safe evidence, exact arrays/keys, role access, claim lock, confirmed/pending outcomes, historical ownership, replay/reload, duplicate prevention, actual minimal review API and safe 403/support details.
- Initial affected gate: 7 files, 185/185 PASS. After fixing duplicate React keys and adding minimal-path/revoked-access regressions, affected UI/workflow gate: 3 files, 97/97 PASS.
- Full frontend suite run once: 15 files, 290/290 PASS. Final review then tightened malformed HTTPS slash/backslash rejection to match backend URI validation and added three URL boundary cases. Final affected gate covering those changes plus dispute/workflow/Finance/auth: 7 files, 190/190 PASS (including all 48 Step 6 tests). No failures. Existing React Router future-flag warnings only; duplicate-key warnings removed. The full suite was not repeated.
- Production build: TypeScript `tsc -b` and Vite build PASS using the existing installed Node/local packages (the npm script tools); no software or dependency changes.
- 1440px and 1024px authenticated runtime smoke: ENVIRONMENT BLOCKED. Read-only probes both within and outside the sandbox to localhost:9191/api/v1/auth/me, localhost:8080/api/v1/auth/me and localhost:3000/api/v1/auth/me were unavailable. Real open/append/claim/release/refund/Finance smoke and browser console/overflow checks were not performed; automated tests are not runtime proof.
- Mobile optimization is deferred. Current MVP delivery target is desktop/laptop at 1440px and 1024px. No mobile work or validation.
- Backend, runtime/env, dependencies and financial business logic unchanged. No backend suites/E2E rerun. No commit/push/merge or branch switch. Changes remain uncommitted for review.

Remaining: run real desktop participant/Admin and both resolution outcomes when the existing runtime is available. No backend contract mismatch found. Do not mark overall Step 6 UI PASS until the live gate is complete; Steps 8/9 remain outside Workpack A.

### 2026-10-06 — UI Workpack B: Step 8 profiles/portfolio and Step 9 reviews

Input gate: `feat/mvp-ui-steps6-9-20261005`, clean HEAD `11f0611c6c03e05f6906d8989081cd7c06715d14` (`feat(frontend): integrate dispute and admin workflow`). Committed Workpack A/Step 6 is preserved. Steps 8/9 implementation, tests and build: PASS. Overall UI integration remains ENVIRONMENT BLOCKED pending real desktop validation.

Implemented:

- Account retains identity/logout controls and adds authenticated own-profile read/edit. Shared fields and language entries honor backend bounds; Client company fields and Freelancer rate/availability/skills/portfolio remain separate. PATCH is allowlisted and carries server version. Skill replacement carries current profile version. Successful profile mutations reread authoritative profile; changed display name reconciles the masthead through trusted `/auth/me`, never the draft. Failed identity reconciliation offers a read-only retry.
- Stale/uncertain profile writes preserve the draft and freeze resubmission until explicit latest-version reconciliation. Current server facts remain visible for comparison. Portfolio uses existing list/create/update/delete routes, HTTPS URL fields only, 12-item limit and safe external links. Create omits version; update uses item version. Delete requires confirmation and a shared mutation lock. Uncertain create/update/delete reads the server list before explicit manual retry; create has no exactly-once/idempotency guarantee, so its warning does not claim otherwise.
- Authenticated `/profiles/:userId` renders only public allowlisted fields, real portfolio and server reputation. No private email/bank/tax/auth material is rendered. Null reputation metrics remain unavailable or omitted, never fabricated zero, badges or browser-calculated aggregates. Verification is read-only server status in subordinate disclosure. Job participants and reviews link to real profile IDs.
- Completed contract workflow reads participant review invitations and existing settlement/cancellation truth. Composer requires the current participant's unsubmitted server invitation, matching successful primary money state and no refund/cancellation/read error; Admin cannot submit. Backend POST remains final eligibility authority because the current DTO has no full eligibility decision or money-success timestamp. REVIEW_INELIGIBLE blocks composition and rereads job/settlement/cancellation/reviews; no fake success.
- All four rating dimensions are required integers 1–5, optional comment capped at 2,000. One review is immutable; duplicate clicks are locked. Uncertain submission GETs authoritative rows before replay, freezes the exact submitted draft, and does not resend when the server confirms submission. Own submitted review is readable before publication. Masked counterpart scores/comment/timestamps remain concealed, without claiming they have not submitted. There is no inferred publication countdown.
- Public reviews use the actual raw-list page/size contract with no invented totals. Only server-published visible records render; hidden public comments do not render while numeric scores remain. Reporting is restricted to the published reviewee, requires nonblank reason up to 2,000 and explicitly does not claim removal. Profile/review lists and server reputation refresh on relevant local rating events and window focus.
- The same trusted ROLE_ADMIN workspace now has dispute/review sections; the five role primary destinations and Freelancer work subnav stay intact. Reported-review queue, detail and actual audit fields use existing Admin APIs. Contract participants cannot moderate. HIDE_CONTENT and INVALIDATE have separate consequences, bounded reason and explicit irreversible confirmation. Successful moderation rereads detail, public listing and server profile reputation. Ambiguous results do not automatically repeat a decision; revoked 403/404 drops privileged cached detail. No restore/unhide/edit controls.
- Actual REVIEW_INVITED/REVIEW_PUBLISHED notifications link to `/work/{jobId}#contract-reviews`; no invented profile/contract notification fields. Unknown fallback and Step 6 destinations remain. Overview appends only invitation-backed review attention after existing workflow/financial priorities; submitted reviews/refund outcomes do not get active review CTA. Safe symbolic/numeric error metadata and requestId remain in the existing API/error path.
- Existing shared editorial primitives, locked palette, native form semantics, keyboard focus and technical disclosure are reused. No backend, runtime/env, dependency, financial business logic or direct Payment/MISA/Solana calls changed. No mobile work.

Validation:

- Added 83 focused Step 8/9 tests in profileContracts, ProfileUI and ReviewUI, plus 2 App/session/Admin navigation tests. Existing workflow/dispute/financial test mocks add only the new review read. Coverage includes bounds/URL safety, exact payloads/versions, stale recovery, trusted display-name refresh, public privacy/null metrics, portfolio CRUD/double-click/uncertainty, review visibility and eligibility, exact replay, reports, both moderation confirmations, revoked access, authoritative invalidation/reputation refresh and Activity/Overview connections.
- Affected gate: 11 files, 271/271 PASS. Full frontend suite run once: 18 files, 377/377 PASS. Final review added one explicit nonparticipant composer guard/test; final affected Profile/Review/Overview gate: 4 files, 99/99 PASS. Full suite was not repeated. Existing React Router future-flag warnings only.
- Final production frontend build: TypeScript `tsc -b` and Vite build PASS, using existing installed Node/local packages (the npm script tools). No software/dependency installation.
- 1440px and 1024px real desktop smoke: ENVIRONMENT BLOCKED. Read-only probes both inside and outside the sandbox to localhost:3000/api/v1/auth/me, localhost:8080/api/v1/auth/me and localhost:9191/api/v1/auth/me returned HTTP 000 / connection refused. Real profile/skills/portfolio, two-sided review publication/reporting and Admin moderation/Step 6 browser flows, overflow and console checks remain unverified. Automated tests are not browser/runtime proof; no services or auth configuration were altered to work around this.
- Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px when the existing runtime is available.
- `git diff --check`: PASS. No backend tests or full backend E2E rerun. No proven backend contract mismatch. Branch/HEAD unchanged; all Workpack B changes uncommitted and unstaged. No commit/push/merge.

Remaining: run the real 1440px/1024px authenticated Step 8/9 and preserved Step 6/Steps 1–5 smoke once the existing frontend/Marketplace runtime is available. No anonymous profile access, uploads, profile verification actions, review edit/delete, moderation undo or publication deadline API exists. Review GET has no explicit eligibility flag; UI conservatively relies on invitations plus existing financial truth and backend POST. Overall verdict: STEP 8 / 9 UI BLOCKED — existing local runtime unavailable; implementation/test/build gates passed.

## P06.5B CLIENT FINANCE DETAIL — FROZEN (2026-10-07)

Source `9c76ffb6c7a03db7a876ed8d871043b14e13d6c2`; `/finance?jobId=...`; Client 1440 approved only. Full composition, runtime-truth mapping and semantic authority: `UI_VISUAL_POLISH_HANDOFF_20261006.md`, P06.5B section.

CURRENT = attention order (first applicable error, otherwise first unfinished), not backend enum; CURRENT != PROCESSING. Real raw/source state + stage tone determine wording; current alone never claims processing/completion/error/finality. Completed Mint/check/solid path; current strongest; upcoming pending Cream/dashed; real error Vermilion/`Cần kiểm tra`. `Chờ bằng chứng` is supported pending presentation copy. Missing record != read failure; earlier error does not propagate to later upcoming stages. No optimistic stage advancement; server refresh/reconciliation owns state. Simulation/localnet/devnet truth and collapsed secondary technical evidence remain visible/accurate. P06.5A and earlier frozen surfaces unchanged.

Final gate: 49/49 focused Finance tests PASS, production build/diff check PASS, clean real SEO Client 1440 screenshot, console errors 0, no overflow, keyboard focus preserved. Backend/API unchanged; no runtime data mutation. At the Client freeze, Freelancer 1440 was not yet frozen; the later Freelancer freeze below supersedes that checkpoint. Client/Freelancer 1024 are FROZEN by the final responsive closure below. Do not begin Tax/Activity in this freeze.

## P06.5B FREELANCER FINANCE DETAIL 1440 — FROZEN (2026-10-07)

Source `837bcf02f7321a72ce597693660c4f63bc224be3` — `feat(frontend): finalize freelancer finance detail`. Route `/finance?jobId=...`; Freelancer 1440 HUMAN VISUAL PASS / FROZEN. Real `/auth/me` proof: FREELANCER / Freelancer Seed. Active nav `Thu nhập`, breadcrumb `FREELANCER / THU NHẬP`, description `Theo dõi release, chi trả và chứng từ của công việc này.`, back link `Lịch sử thu nhập`. `Thanh toán Client` remains the truthful shared payer lifecycle stage name, not a role leak.

The exact frozen Client Money Evidence Spine grammar is reused: single-Job case file, Acid statement, five stages, semantic connectors, compact summary, collapsed technical evidence and hard Ink depth. Only role-specific description/back-link and role assertions changed; no separate Freelancer redesign. CURRENT != PROCESSING: source state plus stage tone own the status wording; attention priority alone never claims processing. Financial states remain server-driven, with no fabricated values or optimistic advancement. Bank details remain masked; technical refs remain allowlisted/collapsed; no secret/private or Client-private fields, fake certificate or inferred tax acceptance.

Approved runtime proof only, not product defaults: SEO job `af2a0ed3-d2df-4029-8649-d01c8cbdc67a`, Job value $300.00, 298.5 Mock USDC, estimated 7,440,112 VND; simulation=true / Mô phỏng, localnet. Stages 01–04 completed; existing TaxRecord EXPORT_FAILED → ERROR + CURRENT / `Cần kiểm tra`, not `Đang xử lý`. Job value is not an amount received, estimated VND is not completed bank payout, and simulation is not real bank settlement.

Final freeze gate: 51/51 focused Finance tests PASS; production frontend build run once PASS (known >500 kB warning non-blocking); diff check PASS. Approved real Freelancer 1440 screenshot: console errors 0, no overflow, focus preserved; local screenshot not committed. Backend/API/database/proxy/ports unchanged; no runtime data mutation. Client 1440 and P06.5A remain FROZEN and unchanged.

At this 1440 checkpoint, both 1024 targets were still pending. The final responsive closure below supersedes that checkpoint: all four P06.5B targets are now FROZEN. P06.5C remains NOT_STARTED.

## P06.5B FINANCE DETAIL — FULLY FROZEN (2026-10-07)

| Target | Final status | Validation authority |
| --- | --- | --- |
| Client 1440 | FROZEN | Existing human-approved baseline preserved; not reopened |
| Freelancer 1440 | FROZEN | Existing human-approved baseline preserved; not reopened |
| Client 1024 | FROZEN | Prior HUMAN VISUAL PASS preserved without redo; zero source changes |
| Freelancer 1024 | FROZEN | Real Freelancer Seed session, 1024px / 100% zoom; runtime PASS; zero source changes |

Shared source remains `837bcf02f7321a72ce597693660c4f63bc224be3`; Client source authority remains `9c76ffb6c7a03db7a876ed8d871043b14e13d6c2`. No responsive source commit: **NONE — zero source changes**. Final validation started from documentation HEAD `a1308676d97d288ea85d3d9dbb1775afb7bc53a2`.

Both roles share the frozen five-stage Money Evidence Spine, connectors, completed/current/upcoming/error grammar and case summary. Role-specific nav, description and back links remain correct. Freelancer proof: active `Thu nhập`, breadcrumb `FREELANCER / THU NHẬP`, description `Theo dõi release, chi trả và chứng từ của công việc này.`, back `Lịch sử thu nhập`. `Thanh toán Client` remains the valid payer-stage name.

Real Freelancer 1024 record: SEO job `af2a0ed3-d2df-4029-8649-d01c8cbdc67a`; stages 01–04 completed; existing TaxRecord EXPORT_FAILED makes stage 05 ERROR + CURRENT / `Cần kiểm tra`. Simulation=true / `Mô phỏng`, localnet remain explicit. No financial data was mutated or fabricated. No horizontal overflow, clipping, nav/account collision, stage/connector misalignment or footer overlap; long evidence wraps, summary remains readable, disclosure and visible keyboard focus work; console errors **0**.

Financial semantics remain LOCKED: **CURRENT != PROCESSING**. `Chờ bằng chứng` is supported frontend presentation copy, not a backend status. Missing record != read failure; an earlier error does not propagate into later upcoming stages. No optimistic, animation or local-time stage advancement; server/API refresh and reconciliation remain authoritative. Simulation/localnet/devnet labels follow returned source truth. Technical evidence stays secondary, allowlisted and collapsed by default; bank masking/privacy remain preserved, with no Client-private fields, credentials, fake certificate or inferred tax-authority acceptance.

Final gate: **51/51 focused Finance tests PASS**, production frontend build **PASS** (run once; known >500 kB warning non-blocking), `git diff --check` **PASS**. P06.5A, both 1440 baselines, backend/API/database/proxy/ports remain unchanged. Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px; no mobile implementation or validation was added.

**NEXT: P06.5C — TAX / CHỨNG TỪ THUẾ — NOT_STARTED.** P06.5C/Tax/Activity were not started during this freeze. Stop here; do not reopen P06.5B for general polish.

## P06.5C TAX / CHỨNG TỪ THUẾ — FROZEN — 2026-10-07

Source: `304dd195cd56fcab7ac92158e7264f0acea8e6e8` — `feat(frontend): finalize tax evidence experience`.
Input gate: branch `feat/ui-visual-polish-20261006`, HEAD `1b4f4cdfc495ab96c0933d14b8817ef7161c26d0`, clean worktree.

- **Tax Evidence Ledger**: `/finance/tax-records`; real paginated rows, current-page count, Job title/reference, optional certificate number, status and taxable income. Document markers only; no N+1 Job enrichment or inferred category artwork.
- **Certificate Case File**: `/finance/tax-records/:taxRecordId`; status statement, distinct taxable/withheld amounts, tax fact ledger, action/document zone, technical disclosure and server refresh. Reuses frozen 01/02 folder tabs; 02 active on both Tax routes.
- TaxRecord/API is authoritative. `payment.taxExportStatus=SUCCESS` means export success, never authority acceptance. Certificate existence also does not imply acceptance. Only explicit `record.status=ACCEPTED` receives accepted treatment.
- ACCEPTED → Mint/Strong Green; EXPORT_FAILED/REJECTED → error/Vermilion; CORRECTION_REQUIRED (`Cần điều chỉnh`) → attention/Acid; CANCELLED/REPLACED → restrained closed; pending and unknown future statuses → pending/neutral. Display exact server `statusLabel || status`; never infer a final state.
- Sync gate unchanged: off-ramp `COMPLETED` + certificate ID + DRAFT/SIGNED/SUBMITTING/SUBMITTED/CORRECTION_REQUIRED. Retry gate unchanged: off-ramp `COMPLETED` + EXPORT_FAILED. Existing duplicate locking retained; returned TaxRecord owns mutation results, with no optimistic ACCEPTED.
- PDF/XML controls require `misaCertificateId`. Existing authenticated Marketplace Blob download/session behavior remains unchanged; no binary-to-JSON parsing or client-generated certificate.
- `amountUsd`, `taxableIncomeVnd`, `taxWithheldVnd` and `usdToVndRate` remain distinct; source/rate observation and real timestamps are retained. Null is `—`, not zero; no derived official percentage. Missing TaxRecord does not imply tax exemption.
- Payment support read failure keeps the loaded TaxRecord, reports a separate error and locks sync/retry. Primary TaxRecord refresh failure shows error/retry rather than stale evidence. Refresh disables overlapping mutations; server authority is preserved.
- Simulation is explicit only when returned `payment.simulation=true`. Technical evidence stays allowlisted and collapsed: record/Job/certificate/payout IDs, certificate number/symbol, lookup code, transaction/submission/authority refs. No arbitrary payload fields, private credentials or full bank numbers.
- Client and Freelancer share the same participant Tax evidence model; trusted session and backend ownership remain unchanged. No role switch, wallet, payment, authority-approval or unsupported controls.

Validation: **77/77 focused Finance tests PASS** (51 preserved + 26 Tax tests); production build PASS after correcting a TypeScript-rejected RoughUnderline prop; one completed Vite production build, known >500 kB warning non-blocking; `git diff --check` PASS. Runtime Client / Nguyen Huu Trong and Freelancer / Freelancer Seed list/detail at 1440 PASS. Freelancer 1024 live DOM/layout QA PASS: no overflow, clipped cells or column collisions, horizontal tabs, readable amounts/facts, keyboard disclosure and 3px Cobalt focus. Console errors **0**. Reduced-motion safe CSS, no looping animation.

Real QA records: SEO TaxRecord `02241f4d-e683-403e-a46e-9e08d45d7e74` = EXPORT_FAILED, taxable income 7,779,918 VND, withheld amount absent; P04 TaxRecord `5bb07cf8-4eae-41b6-8676-b2366f17c209` = ACCEPTED, certificate `00000001`, taxable 2,591,743 VND, withheld 259,174 VND. These are runtime observations, not defaults/fixtures. Existing ACCEPTED PDF/XML visibility PASS; an optional PDF download event capture timed out in the browser tool, so actual file completion is not asserted by this pass. No financial/tax data mutation.

Two representative 1440 screenshots saved outside Git: Client Tax ledger and Freelancer SEO case file. Screenshots are never committed. P06.5A and P06.5B source/semantics preserved; all new CSS is Tax-specific. Backend/API/database/dependencies/proxy/ports unchanged. Mobile optimization is deferred; today's target is desktop/laptop. No Activity implementation.

**NEXT: P06.5D — ACTIVITY / EDITORIAL EVENT LEDGER — NOT_STARTED.** Earlier P06.5C NOT_STARTED checkpoints are historical and superseded by this freeze. Do not reopen frozen Finance/Tax surfaces for general polish.

## FREELAX VISUAL EMPHASIS V1 — P06.5C RE-FROZEN — 2026-10-07

Source: `1b2ee56ac9c202994747d60739eee9e6c84f7c49` — `style(frontend): strengthen tax evidence hierarchy`.
Authority input: `5d2d2056703dbad474d5f43202b2ee2f049e78b5`, correct branch and clean worktree. This is a visual-only reinforcement of `304dd19`, not a new Tax/business flow.

- Important records need a meaningful document visual anchor: layered Cream paper, Ink outline/hard shadow, a small source-tone tape. Tax ledger plate 64x76px at 1440 and 52x64px at 1024; no fake category, certificate preview or stock imagery.
- Strengthen title/status/value hierarchy: ledger titles 24px / 20px, status 16px / 15px, taxable amount 25px / 22px at 1440 / 1024. Case-file amounts 30px / 25px, tabular numerals; body/metadata retain readable scale.
- Status surface and state rail are source-driven. ACCEPTED uses Mint + Strong Green; EXPORT_FAILED/REJECTED a stronger Vermilion tint; CORRECTION_REQUIRED Acid; DRAFT/SIGNED/SUBMITTING/SUBMITTED Cobalt tint; CANCELLED/REPLACED and unknown future states Cream/Ink. Server label/status is unchanged. `active` is a presentation tone, never a new API status or claim of processing completion.
- Rails are 8px; status has a 2px Ink border and at least 44px height. Cream remains the primary ledger surface. At most two strong semantic zones per row: rail and status. Document tape is a small related accent; amount/action cells remain Cream. No rainbow cells, gradients, glow or blurred depth.
- Detail information architecture stays intact. The main status statement has an 8px state edge and stronger semantic surface; the document/action panel has a related tinted heading band and 7px edge, with Cream body. Light row actions retain Ink border/arrow/hard depth; no black detail-link CTA.
- Recognition at 1024 is preserved through wrapping and existing column rhythm, not tiny type or removal of the visual anchor. No mobile/hamburger work. Reduced-motion handling retained for paper details and actions.
- Business semantics unchanged: TaxRecord/API remains authority; export SUCCESS and certificate ID do not imply ACCEPTED. Sync/retry/download gates, null `—`, explicit simulation, payment-read failure isolation, returned-record mutations and collapsed allowlisted technical evidence are preserved. No backend/API/data mutation.

Validation: **81/81 focused Finance tests PASS** (all 77 baseline cases retained, plus one SUBMITTING tone case and three action-zone/source-tone locks). TypeScript + Vite production build PASS; one completed production build, known bundle >500 kB warning non-blocking; diff check PASS. Frozen Finance.tsx and CSS prefixes compare identical; P06.5A/P06.5B semantics and styling preserved.

Runtime evidence: Client / Nguyen Huu Trong Tax ledger 1440 contains real SEO EXPORT_FAILED and P04 ACCEPTED records; Freelancer / Freelancer Seed 1440 case-file screenshot uses the real SEO record. P04 ACCEPTED case briefly verified with PDF/XML visibility. Client ledger and Freelancer detail 1024 live DOM/layout PASS, no clipped cells, no overflow, horizontal folder tabs; technical disclosure keyboard PASS, 3px Cobalt focus, console errors 0. 1024 screenshot capture timed out in the browser tool; no 1024 screenshot is claimed. No financial/tax mutation or download completion claim.

Two new 1440 screenshots are saved outside Git: Client Tax Evidence Ledger and Freelancer Tax Certificate Case File. Screenshot data/amounts/statuses are real runtime values; no fabricated references/timestamps and no secrets or screenshots committed.

**P06.5C TAX / CHỨNG TỪ THUẾ — VISUAL EMPHASIS PATCH — FROZEN.** This section supersedes earlier weaker Tax typography/tone styling. P06.5A/P06.5B and all earlier frozen surfaces remain locked. **NEXT: P06.5D — ACTIVITY / EDITORIAL EVENT LEDGER — NOT_STARTED. STOP.**

## P06.5D Activity runtime recovery / final freeze — 2026-10-07

Source commit: `926c8fc64c2f8767165b5f69f15293abf91c86b2` — `feat(frontend): finalize editorial activity ledger`. Resume authority: `d71dfd10bc87e4741ccdbe97f89e51a379ac5ea3`; only the four intended Activity files were dirty.

- **P06.5D ACTIVITY / EDITORIAL EVENT LEDGER — FROZEN.** Marketplace notifications for the authenticated account are the source of truth. This is not a complete system audit log; editorial ledger names the presentation only. The existing truthful disclaimer remains.
- `activityPresentation.ts` preserves all 21 human labels and resolves visual family/tone from explicit `Notification.type` only. Unknown/future/prototype-key types safely use neutral Cream/Ink and “Cập nhật từ Marketplace”; server title/message remain React text, never HTML. The mapping below is presentation, not new backend statuses.
- Type-based document plates use Lucide family icons, Cream paper layers, Ink outlines/hard shadows and small tone-linked tape; no fake Job/category/avatar imagery. Visual Emphasis v1: readable 24px/22px titles at 1440/1024, 16px messages, 15px type/action and 14px metadata; 70x76px/56x64px plate, 7px semantic rail. At most two strong related zones; Cream row base, no rainbow fills.
- Read/unread is independent of event outcome. `read=true` never means resolved; confirmed/error/cancelled meaning stays type-derived. “Chưa đọc” is a restrained Acid-tint reading stamp; “Đã đọc” is neutral. Runtime exposed an inherited legacy unread Vermilion marker background; a single Activity-scoped selector override removes it. No redesign or resolver rewrite during recovery.
- Current-page unread count comes only from loaded records; account total comes from `totalElements`. No account-wide unread inference, fake KPI, timeline progression or fabricated event data. `createdAt` and server order remain unchanged.
- `Notification.amount` is omitted because its DTO has no currency/unit discriminator. `PayoutServiceImpl.notifyPayoutSimulated` supplies `amountVndEstimated` for PAYMENT_RECEIVED, while other notification producers omit amounts. Existing server message text is preserved, including its own explicitly stated simulation/units; no USD/VND/USDC unit is guessed for the separate numeric field.
- Mark-read retains Marketplace PATCH, synchronous duplicate lock, pending/error states, and the returned notification object as truth; no optimistic success or invented mark-all. Existing `freelax:rating-update` / `freelax:review-update` dispatches remain. Runtime history was not mutated; mark-read outcomes are covered by focused tests.
- Destinations unchanged: RELEASE_CONFIRMED/REFUND_PENDING/REFUND_CONFIRMED → Finance detail; rating events → Job `#contract-reviews`; other Job-linked events → Job detail; no jobId → no invented link. No speculative PAYMENT/TAX rerouting. Server pagination `notifications(page, 10)` remains; no fetch-all or infinite scroll. Loading/read-error/retry/empty/action-error semantics are retained.
- Recovery: browser integration still failed with `setup refresh had errors`; existing bundled Playwright + installed Chrome successfully rendered the real product. Existing Vite was restarted at localhost:3000; Marketplace returned HTTP 401 at 127.0.0.1:9191 before login. Ports/proxy and application runtime configuration were not changed.
- Real sessions verified from `/auth/me`, including reload/session restoration: CLIENT / Nguyen Huu Trong has 7 total, 7 loaded, 7 unread on page 1; FREELANCER / Freelancer Seed has 13 total, 10 loaded, 10 unread on page 1. Active nav is Hoạt động for both. Notifications are account-scoped responses; no cross-role injection, seeding or financial/business mutation.
- Client/Freelancer 1440 and 1024 visual + DOM/layout QA PASS at 100% zoom: no horizontal overflow, clipped content or action collision; visible plates/text and desktop navigation, no hamburger. Freelancer page 1 → page 2 → page 1 exercises real pagination; Client is a real single-page result. All visible runtime records are unread; read=true and missing families are verified by automated tests, not fabricated screenshots.
- Keyboard destination and mark-read traversal PASS, visible 3px Cobalt focus; decorative icons/marks aria-hidden. Event labels/body and reading-state text carry meaning beyond color. 160ms micro-feedback only, no loops; reduced-motion transition is 0s. Activity console errors and page errors = 0 for both roles.
- Screenshots captured outside Git: `client-activity-1440.png`, `freelancer-activity-1440.png`, `client-activity-1024.png`, `freelancer-activity-1024.png`. Real account/notification content, no accidental focus outline or credential screenshots. Runtime report and browser scripts are external artifacts, not repository additions.
- Final validation: **138/138 focused tests PASS** (Polish 57, Finance 81); TypeScript + Vite production build PASS after the proven CSS fix; known >500 kB bundle warning non-blocking; `git diff --check` PASS. Frozen CSS prefix compares identical; P06.5A/B/C source/API/business semantics unchanged. No backend, API, dependency or auth changes.

**NEXT: P06.6 — ACCOUNT / PROFILE — NOT_STARTED. STOP.** Earlier P06.5D NOT_STARTED checkpoints are historical and superseded by this freeze; do not reopen frozen surfaces for general polish.

## P06.5D Activity visual emphasis patch — re-frozen (2026-10-07)

**P06.5D ACTIVITY VISUAL EMPHASIS PATCH — FROZEN.** Source `c71c554bd73d7e8aff21032aa317e5cab8e4a89c` — `style(frontend): strengthen activity notification hierarchy`. Input authority `e38d10dc2ab855e58b728bbdf0a428d5f66c8fa1`, clean worktree. This narrow patch supersedes the initial Activity message/tag/read-action styling; the original `926c8fc` freeze remains historical.

- Long means `message.length > 240`, presentation only. Same original server text defaults to three lines at 16px/1.55, max 62ch; technical tokens wrap anywhere. No fabricated summary, free-form parsing or inferred amount/currency/status/evidence. Activity remains a notification-level surface; richer technical evidence belongs primarily to existing destination/detail pages.
- Local real buttons “Xem thêm nội dung” / “Thu gọn” expose `aria-expanded` and `aria-controls`; full original message remains accessible as React text. Expanded secondary Cream evidence has a restrained structural edge. No API fetch, read mutation, routing change or height animation. Replaced server message resets local expansion. Short messages receive no unnecessary disclosure.
- Tags: error Vermilion/Cream, active Cobalt/Cream, attention Acid/Ink, success Mint/Ink, closed Ink/Cream, unknown neutral Cream/Ink. Rectangular 2px Ink borders and 15px/800 labels. All 21 resolver mappings stay unchanged.
- Unread: compact full Acid/Ink, 2px Ink, 14px/850, visible “Chưa đọc”; already-read remains neutral/subordinate. Event severity still derives only from type and reading state only from read; an unread error has a Vermilion tag plus Acid reading stamp, never a fully recolored row.
- Mark-read: Ink/Cream, 2px Ink, 800 weight, hard small offset shadow; pending “Đang lưu…” is disabled/legible. Server-returned object still owns read state, with duplicate lock/error recovery and existing cross-surface dispatches unchanged. No optimistic success.
- Counts, account scoping, server order/pagination and existing Job/Finance/rating destinations unchanged. Unitless Notification.amount remains omitted. No backend/API/auth/dependency changes. Activity-scoped CSS only; frozen Finance/Tax CSS prefix verified byte-equivalent after line-ending normalization. P06.5A/B/C and earlier frozen surfaces remain unchanged.
- Real /auth/me sessions: Freelancer Seed (FREELANCER), 13 total / 10 loaded / 10 unread on page 1; Nguyen Huu Trong (CLIENT), 7 total / 7 loaded / 7 unread. The real “Hoàn tất mô phỏng payout” message is compact by default and expands to the identical server text. Two long messages on the Freelancer page; none on the Client page. No runtime notification or business mutation; real Freelancer pagination exercised.
- Client/Freelancer 1440 and 1024 PASS at 100% zoom: no horizontal overflow, clipping or action collision; expanded references wrap. Destination, disclosure and mark-read keyboard focus PASS with 3px Cobalt; reduced-motion transition 0s, no loops. Activity console/page errors = 0. Read/pending/error outcomes and absent event families remain automated-test evidence; runtime rows are all unread.
- Screenshots returned from outside Git: Freelancer 1440 collapsed + expanded, Client 1440, and Client/Freelancer 1024. No credentials or screenshots committed; external browser scripts/report stay outside Git.
- Final gate: **146/146 focused tests PASS** (Polish 65 + Finance 81; original 138 preserved + 8 new regression cases), single TypeScript/Vite production build PASS, diff check PASS. Existing bundle-size warning is non-blocking.

**NEXT: P06.6 — ACCOUNT / PROFILE — NOT_STARTED. STOP.** Do not reopen this freeze for general polish.

## P06.6 ACCOUNT / PROFILE — FROZEN — 2026-10-07

This dated freeze supersedes earlier Account/Profile NOT_STARTED and Auth-polish planning notes. Auth was not reopened. NEXT: **P06.7 — FULL REGRESSION / QA — NOT_STARTED**; do not start it during this freeze.

- Input branch `feat/ui-visual-polish-20261006`; clean authority `a67affa853bb4c9e6b8c3a4fecee7277c025659b`. Source commit `a0e16f9090efae89b3431be511da2c418e21ac68` — `feat(frontend): finalize account and profile surfaces`.
- Account/session identity remains trusted `/auth/me` data. Acid passport, deterministic initials/role plate, readable name/email/role, collapsed account reference and separate Ink logout zone. No new auth/security controls.
- Editable Marketplace profile remains `/profiles/me`: Mint dossier masthead, role stamp, conditional bio/facts, actual language/skill tokens, reputation evidence ledger and collapsed raw verification. Visual Emphasis v1 applied. Null/missing facts omitted; null rating says `Chưa có đánh giá công bố`; real zero counts remain zero. No fabricated trust score, avatar, skills, image, bio or reputation.
- Grouped profile editor preserves exact validation and PATCH/version semantics. Freelancer skills remain a separate versioned mutation. Pending/duplicate/stale locks and retained drafts remain; explicit latest-version reconciliation is required before retry.
- Profile displayName updates still reconcile with trusted session identity after server save. Successful profile save and failed identity refresh remain distinct; read-only identity retry does not repeat PATCH.
- Freelancer portfolio remains server-authoritative GET/POST/PATCH/DELETE through the existing API. Local folio plates, explicit safe HTTPS project/thumbnail links; no remote avatar/thumbnail images auto-loaded. No invented category or project imagery. Existing sortOrder/completedAt/skills fields, max 12, future-date/URL validation and item version preserved. Explicit delete confirmation and uncertain-result reconciliation prevent blind retry; already-deleted items reconcile safely.
- Authenticated `/profiles/:userId` is a partner dossier, not Account: no email/account UUID, own editor or logout block in profile content. Freelancer portfolio only; existing submitted/published review filtering, hidden-comment protection, report permissions and 20-record next-page heuristic unchanged. No invented review totals. Generation/key protection prevents prior-partner content leaking after route changes; 403/404/read retry preserved.
- Runtime: Freelancer Seed and Nguyen Huu Trong authenticated through real `/auth/me`; Account, editor open/focus/close and partner dossier validated at 1440 and 1024. No profile/portfolio mutations executed. Public Freelancer profile also viewed by Client. Actual runtime profiles are sparse: no headline/bio/languages/skills/portfolio/reviews; real zero reputation and missing averages shown truthfully. Rich/null/long-content, mutation and error cases covered by focused automated tests rather than runtime seeding.
- Validation: Polish 85/85, Finance 81/81, ProfileUI 17/17, profileContracts 37/37 = **220/220 PASS**. Original 65 Polish + 81 Finance baseline retained. Production TypeScript/Vite build PASS (existing-style bundle-size warning); diff check PASS. Client/Freelancer 1440/1024 no overflow/clipped controls, console errors 0, keyboard Cobalt focus and reduced motion PASS.
- Exactly two real 1440 screenshots outside Git: Freelancer Account and materially distinct partner dossier. No Client/editor/1024 screenshot matrix. No screenshots, real credentials, tokens, .env values, local machine paths or secrets committed.
- Source scope: Account.tsx, Profile.tsx, Portfolio.tsx, PublicProfile.tsx, Polish.test.tsx and appended Account/Profile-scoped styles.css. Original frozen CSS prefix retained. P06.5A/B/C/D and earlier frozen surfaces, backend, API/types/contracts/dependencies unchanged. Mobile optimization deferred; current MVP desktop/laptop 1440/1024.

**P06.6 ACCOUNT / PROFILE — FROZEN. NEXT: P06.7 — FULL REGRESSION / QA — NOT_STARTED. STOP.**

## 2026-10-08 — PRE-P06.7 COMPLETED JOB REVIEW OPPORTUNITY PATCH — FROZEN

Source: `99e5f081bb89c2b84df5d8736d2965b35dd206d3` — `feat(frontend): surface completed job review opportunity`.

- The two-sided review system already existed. This patch improves discoverability only: a Mint/Strong Green completion follow-up note sits after the ownership band, before deep workflow/history; the existing composer stays at `#contract-reviews`.
- Submission → approval → release/settlement → Job/Contract completion → optional review → server publication. Review never causes or gates completion, release, settlement or payout; a failed review cannot change completed financial truth.
- The existing `reviewOpportunity` authority drives both composer and callout. `ContractReviews` owns the single API read and reports only the derived boolean; load/read failure, participant/contract changes and unmount clear availability. No duplicate review API fetch or parallel eligibility rule.
- Eligibility requires actual non-admin participant, Job/Contract COMPLETED, milestone RELEASED, matching contract/job/milestone settlement with moneyStatus SUCCEEDED, an unsubmitted server invitation for the reviewer/reviewee pair, and no cancellation/refund/dispute/read-failure block. Completion alone is insufficient.
- Participant IDs choose the counterpart: Client sees **Đánh giá Freelancer**; Freelancer sees **Đánh giá Client**. Both are native keyboard-accessible anchors to the existing review section, with 32px scroll margin and Cobalt focus. No new route, modal or form.
- The callout disappears after authoritative submission/reconciliation confirms own submitted review, not optimistically on click. Uncertain mutations retain existing reconciliation and duplicate-submission guards.
- Score/payload, review immutability, ratingUpdated profile refresh and publication semantics remain unchanged. Submitted does not mean publicly published; public visibility remains server-authoritative.
- Visual: restrained Mint note, 8px Strong Green rail, 2px Ink border, zero-blur shadow, local document/check plate with small Acid tape. Heading 27px (25px at 1024), body/CTA 16px. Meaning is textual; reduced motion removes decorative transforms/transitions.
- Gate: **317/317 PASS** — ReviewUI 34, ContractLifecycle 63, Polish 85, Finance 81, ProfileUI 17, profileContracts 37. Includes all **220/220** frozen P06.6 baseline tests and 35 new tests. Production build PASS (known bundle-size warning non-blocking); diff check PASS.
- Real runtime: verified Client **Nguyen Huu Trong** and Freelancer **Freelancer Seed**. Each has three completed legacy Jobs with `contract: null`; no contract review invitation/settlement proof exists, no review read is issued, and the callout correctly stays absent. This is absent contract data, not a fetch failure. **REAL RUNTIME REVIEW OPPORTUNITY NOT AVAILABLE**.
- Real Job Detail 1440/1024 remains healthy with no horizontal overflow and console errors **0**. No Job/payment/review state was mutated for QA. No eligible Client screenshot was produced; no fake runtime data or screenshot was introduced.
- Callout-only **isolated presentation fixture** at 1440/1024: readable, no clipping/overflow, keyboard CTA/Cobalt focus PASS, native hash destination visible PASS, reduced-motion plate transform none. This verifies layout/accessibility, not real runtime review eligibility. No fixture or screenshot is committed.
- Source scope: `ContractLifecycle.tsx`, `ContractReviews.tsx`, their two focused test files, and appended scoped `styles.css` rules only. No backend/API/types/Finance/Activity/Profile implementation changed; frozen CSS prefix preserved. No credentials, secrets, local paths or screenshots added to tracked files.
- **P06.5A/B/C/D + P06.6 remain FROZEN.** NEXT: **P06.7 — FULL REGRESSION / QA — NOT_STARTED**. Do not begin P06.7 without a separate request.

## 2026-10-08 — P06.7 FULL REGRESSION / QA — FROZEN

This accepted finalization supersedes earlier P06.7 NOT_STARTED and strict-console BLOCKED checkpoints. Source: `7fc31555b9cd3f50a23872401968ee67c5275f30` — `fix(frontend): reconcile delayed contract review invitations`. Only ContractReviews.tsx and ReviewUI.test.tsx changed; backend/API contracts and frozen P06.5A/B/C/D, P06.6 and earlier visual surfaces are preserved.

**REAL CONTRACT-BACKED E2E = PASS.** Client creates Job → Freelancer applies → Client assigns → WorkContract/Milestone created → funding SUCCEEDED → contract ACTIVE → contract-backed submission → Client approval → RELEASE_PENDING → scheduler settlement → moneyStatus SUCCEEDED → milestone RELEASED → contract COMPLETED → Job COMPLETED → scheduler-created two-sided invitations → Client review → Freelancer review → both published → public reputation/profile updated. Legacy `contract:null` Jobs were not used as E2E proof. User-facing mutations used the real UI/API. No direct database INSERT/UPDATE or manual scheduler invocation manufactured the lifecycle; SELECT-only verification supplied internal fields omitted from public DTOs.

The real runtime defect was invitations arriving after ContractReviews mounted without another review read. The fix keeps ContractReviews as the single read owner and the existing server-authoritative eligibility/callout callback. Read-only reconciliation every 30 seconds while awaiting own invitation or publication skips hidden pages, in-flight reads and mutation locks. Focus reconciles server truth; time never grants eligibility. Polling stops once an unsubmitted own invitation is present or own review is published, and timers/listeners/generation are cleaned up on unmount. Two new regression tests failed before the patch and pass after it, covering delayed invitations, focus, unchanged settlement eligibility and cleanup.

The original Job's reviews were already published, so the workpack's regression exception used one additional legitimate Job: `5b020abd-1e92-4f5c-af8d-2429ce8a78f1`. Its completed review section was present at 17:52:47Z; scheduler invitations appeared at 17:53:36Z; the already-open Client page discovered the opportunity at 17:53:47Z without hard reload. Both real review CTAs anchor to `#contract-reviews`; Client/Freelancer 1024 callout/form checks preceded submission. Both reviews were server-published after the second submission; both public profiles show actual published comments and reputation. Final server reputation: two published reviews, average 5/5 for each QA participant. Ratings/comments are synthetic local QA evidence, not claims about a real person's performance.

**Expected network absence policy — exact endpoint only:** `GET /api/v1/marketplace/tax-records/jobs/{jobId}` returning HTTP 404 / frontend ApiError code 4010 is non-blocking only when the TaxRecord genuinely does not exist, `missingTax(...)` handles it, tax=null and taxError='' remain truthful, the UI says `Chưa có chứng từ`, no JS/page exception or 5xx/CORS/runtime failure occurs, and independent financial state proves tax is not complete. Do not extend this exception to arbitrary 404s or alter frozen Finance/Tax/backend semantics to hide browser logging.

| Final gate | Accepted evidence |
| --- | --- |
| Unexpected JS/page errors | 0 |
| Unexpected network/runtime failures | 0 |
| Expected handled missing-TaxRecord resource messages | 8 HTTP 404 responses |
| Horizontal overflow | 0 |
| Runtime gate | PASS WITH DOCUMENTED EXPECTED NETWORK ABSENCE |
| Desktop regression | 46 page/role/width checks; Client/Freelancer 1440 and 1024 PASS, visible keyboard focus |
| Focused verification | `npm test -- src/ReviewUI.test.tsx src/ContractLifecycle.test.tsx`: 99/99 PASS |
| Full regression | `npm test`: 793/793 PASS, 22 files; all prior tests retained |
| Production build | PASS on this exact source state; reused rather than rebuilt during finalization; known bundle-size warning non-blocking |
| Diff check | PASS |

Activity exposes the real assignment, funding, submission, approval, release, invitation and publication events. Finance preserves separate funding/release/downstream truth. Existing ACCEPTED certificate PDF/XML authenticated Blob downloads passed for both roles; no binary was parsed as JSON.

**Independent downstream truth:** Solana RPC was unavailable during final regression. On-chain remains UNKNOWN / ON_RAMP_AWAITING_RECONCILIATION, off-ramp NOT_STARTED, tax NOT_STARTED. Primary settlement/review success does not claim bank transfer, Solana completion, tax export or certificate acceptance for the new QA Jobs. No downstream success was fabricated.

The compact real evidence remains two 1440 screenshots outside Git: Client completed Job review callout and Freelancer public profile after publication. No additional screenshots, secrets, credentials or machine-local paths are committed. Mobile optimization is deferred; current MVP delivery target is desktop/laptop at 1440/1024.

**P06.7 FULL REGRESSION / QA — FROZEN. NEXT: P06.8 — FINAL FREEZE / HANDOFF — NOT_STARTED. STOP.**
