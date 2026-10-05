# FREELAX UI IMPLEMENTATION PROGRESS

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

Status: `NOT_STARTED`

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

## P06.6 — Account/Profile shell + Auth polish

Status: `NOT_STARTED`

Expected files:

```text
frontend/src/Account.tsx
frontend/src/Auth.tsx
frontend/src/styles.css
```

Account:

- make current account screen feel like a marketplace profile shell
- show only current API-backed fields
- do not invent skills/rating/verification/portfolio

Auth:

- preserve FX poster
- reduce copy
- improve role selection/form grouping
- polish mobile layout

## P06.7 — Responsive / accessibility / regression

Status: `NOT_STARTED`

Required checks:

- desktop
- ~1024px
- mobile
- keyboard navigation
- focus-visible
- no horizontal overflow
- loading/error/empty states
- Client five routes
- Freelancer five routes
- contract/milestone display smoke
- finance/tax read-only smoke
- auth login/register
- no console errors

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
Build: `npm run build` — PASS using the installed `C:/Program Files/nodejs/npm.cmd`.
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
