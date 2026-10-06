# FREELAX UI DEVELOPMENT MEMORY

## CURRENT CONTINUATION — 2026-10-06

This section supersedes older conflicting branch, baseline, scope and active-task notes below. Read `UI_VISUAL_POLISH_HANDOFF_20261006.md` first, then the dated session log.

- Repository: `TronkIshere/FreelaX`; branch `feat/ui-visual-polish-20261006`.
- Frozen source baseline: `dc440ec96e4c960ac0ea285c07b42e0d823bc221` — `feat(frontend): finalize client work and global row identity`; later documentation HEADs do not change source authority.
- Client/Freelancer Overview = FROZEN / APPROVED (`01c7e55`). Freelancer Explore = FROZEN / APPROVED (`f42e44a`), Applications = PASS / FROZEN (`2f5f0e4`), My Work = PASS / FROZEN (`ca8b93d`). No taste-polish reopening without concrete regression.
- Client Work C1 (`/work`) = PASS / FROZEN: CLIENT WORK CONTROL BOARD, first server record emphasized, truthful totalElements/order/pagination, real status/budget/deadline/context and existing state-derived actions. No fake search/filter/sort, applicant/status counts or urgency.
- Freelancer Explore / Applications / My Work = RE-FROZEN after the narrow approved row-identity override. Overview remains FROZEN. Global Row Identity / State Rail / Category Plate = LOCKED; do not reopen for general taste-polish.
- NEXT: CLIENT WORK C2 — Job Authoring, NOT_STARTED. Routes `/work/new`, `/work/:jobId/edit`; inspect `frontend/src/JobEditor.tsx` fully before implementation. Do not start during this freeze task.
- Global row channels are independent: thumbnail = semantic Job type; plate = explicit visual family; rail/status = progress. Secondary rows remain Cream editorial ledger rows. Solid saturated rail: 6px at 1440, 5px at 1024; visible text retained, no gradient/blur/glow/color animation.
- Job progress: OPEN/AWAITING_PAYMENT/SUBMITTED_FOR_REVIEW Acid `#F5D12F` (Ink structure for waiting/review); IN_PROGRESS Cobalt `#3567E8`; REVISION_REQUESTED Vermilion `#F15A3D`; COMPLETED Strong Success Green `#39B96E`; CANCELLED Ink `#17212B`. Fresh Mint `#B8DFC4` remains a light success surface.
- Application progress: PENDING Acid, ACCEPTED Strong Green, REJECTED Vermilion, CANCELLED Ink. Primary surfaces: PENDING Acid/Ink, ACCEPTED Mint/Green marker, REJECTED Vermilion/Cream, CANCELLED restrained Ink/Cream inactive. Never revert PENDING to default Vermilion.
- `JobCategoryPlate` and `JobIdentityCluster` reuse existing `jobFamily`, also used by JobThumbnail. Meaningful category → skills → title → Generic Development; OTHER stays OTHER in API/domain/editor/filter. Legacy REST API → BACKEND / API, SEO → SEO / NỘI DUNG, no signal → KHÁC. All labels/color rules are authoritative in UI_POLISH_SPEC.
- Final C1/global accepted gate: 180/180 focused tests PASS; production build/diff PASS; 1440/1024 PASS without overflow; console 0; keyboard focus PASS; reduced motion preserved; backend/API/business/filter unchanged. C1 runtime: 5 real jobs, page 1/1, OPEN/IN_PROGRESS/COMPLETED, Applicants/Job Detail/create navigation PASS. No tests/build rerun for freeze; no runtime mutation. REJECTED/CANCELLED primary Applications verified by tests where absent from runtime.
- Toolkit: `frontend/src/ui/kinetic/`; `roughjs@4.6.6`, `motion@14.0.0`, `lucide-react@1.52.0`. Locked KINETIC EDITORIAL BRUTALISM; hard zero-blur depth, Cream main type on Vermilion, deterministic connected marks, micro-motion and reduced-motion.
- Stored category/job-skills foundation is implemented/frozen. Nine categories include OTHER; job skills max 10, trim, 2–40 chars, no null/blank/case-insensitive duplicates. Legacy OTHER/[]; no title/profile backfill.
- Real discover parameters: keyword, minBudgetUsd, maxBudgetUsd, category, repeated skills, application, sort, page, size. ANY exact trimmed case-insensitive skill match; filters compose with AND; server pagination. No work-mode/Remote/Hybrid/Onsite field/control.
- Stable Job Visual Identity = LOCKED: meaningful stored category → legacy skills → legacy title → Generic Development. OTHER is unspecified for decorative classification: try skills, then title, otherwise Generic Development; stored backend category is unchanged. Job ID never chooses family; it chooses a deterministic variant within family, `stableHash(id) % 3`, default 0 when absent. Rough marks use stable family + job identity. Same job keeps its visual across Explore, Applications, My Work and future Client Work; reuse JobThumbnail, no page-specific system, database thumbnail ID or upload.
- Commit `302ae06` was an explicitly approved narrow backend/product-data exception required for truthful filters/thumbnails. COMPLETE; it does not authorize unrelated backend expansion. Older frontend/docs-only and backend-unchanged notes are historical scope records.
- Accepted Overview: 61/61 focused tests, build/diff PASS, console 0, 1440/1024 PASS. Accepted final Explore: 54/54 focused correction tests, build/diff PASS, real category/skill/combined/exclusion/Clear smoke PASS, 1440/1024 no overflow. Do not reinterpret older blocked runtime entries as passed.
- The local Client API edit of “Landing page redesign” to WEB_FRONTEND + HTML/CSS/Responsive Design is runtime evidence only, not guaranteed fresh-DB seed content.
- My Work `/work/mine` is the approved ACTIVE WORK / DELIVERY TRACKER: first server record emphasized, state-aware primary surface, semantic thumbnail, real budget/optional deadline/revisions, next-action copy, flat ledger and real pagination. No invented search/filter/sort. AWAITING_PAYMENT → waiting/read-only; IN_PROGRESS → Cobalt; SUBMITTED_FOR_REVIEW → Acid; REVISION_REQUESTED → Vermilion; COMPLETED → Mint; CANCELLED → restrained Ink/Cream. State truth outranks color.
- Money/contract truth: budget is job value, COMPLETED is not payout confirmation, Freelancer gets no funding action, RELEASE_PENDING is not paid, REFUND_PENDING is not refunded. Show deadline/revision usage only when real.
- Accepted My Work: 62/62 focused tests, build/diff PASS, 1440/1024 PASS, console 0. Stable thumbnail correction: 131/131 focused tests, 36/36 recheck, build/diff and cross-screen identity PASS. Runtime observed 4 My Work jobs, IN_PROGRESS/COMPLETED and sparse legacy category/skills; not guaranteed seed data. Freeze reused accepted validation without rerunning tests/build; no API/backend/dependency changes.
- Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px.

## Historical P06 / integrated MVP memory

The records below retain earlier product/workflow context and their original evidence. Their old “active/current” wording is subordinate to the continuation above; verify against current source before reuse.

> Status: ACTIVE UI MEMORY
> Scope: P06.4 verified contract integration + UI polish; frontend/docs only
> Repository: `TronkIshere/FreelaX`
> Baseline reviewed: `master` at `3332d30ad542a5d49f721e1a1f46d9da8b8203c3`
> Product spec: `docs/mvp-functional-spec.md` — Draft 0.1
> Runtime baseline before this product expansion: P05.6 local demo reached `LOCAL DEMO READY — PASS`

## Active goal

Make the existing FreelaX frontend easier to scan, less text-dense, and more product-like without expanding into unrelated backend/business implementation.

Current product journey:

```text
Client creates/manages job
→ Freelancer discovers and applies
→ Client assigns
→ Contract + one Milestone foundation is created
→ Assigned work may enter AWAITING_PAYMENT / pending funding
→ Freelancer work lifecycle continues when backend state allows it
→ Submission / revision / approval
→ finance/tax evidence follows completion
```

## Current API-backed product fields added on master

The current master now exposes real UI data for the Contract/Milestone foundation.

Current `Job` / `DiscoverJob` can include:

```text
deliveryDueAt
reviewWindowHours
maxRevisions
deliverables[]
acceptanceCriteria[]
```

Participant `Job` can also include:

```text
contract
```

Current `ContractSummary` includes:

```text
id
status
milestoneId
milestoneStatus
amount
currency
deliveryDueAt
reviewWindowHours
maxRevisions
revisionsUsed
deliverables[]
acceptanceCriteria[]
```

Therefore these values are no longer automatically considered “future/fake”. They may be rendered when the API actually returns them.

Current frontend also recognizes the job state:

```text
AWAITING_PAYMENT
```

Funding controls are now supported by verified Marketplace contract APIs (backend sync `cf0594f`). Never infer funding success from a click; refetch the job.

## Current frontend source inventory

```text
frontend/src/App.tsx
frontend/src/components.tsx
frontend/src/styles.css
frontend/src/Overview.tsx
frontend/src/Jobs.tsx
frontend/src/Workflow.tsx
frontend/src/WorkLifecycle.tsx
frontend/src/Finance.tsx
frontend/src/Activity.tsx
frontend/src/Account.tsx
frontend/src/Auth.tsx
frontend/src/api.ts
frontend/src/types.ts
frontend/src/status.ts
```

## Visual language — LOCKED

**KINETIC EDITORIAL BRUTALISM**

Palette:

```text
Cream       #FFF7E8
Ink         #17212B
Vermilion   #F15A3D
Acid        #F5D12F
Fresh Mint  #B8DFC4
Cobalt      #3567E8
```

Keep the current identity. Do not redesign into generic SaaS.

Avoid glass, blur, glow, bento dashboards, dark crypto aesthetics, rounded wallet cards, giant balances and pill-heavy UI.

## Main UI problem

Team feedback:

> “Màu đẹp nhưng nhìn hơi rối chữ.”

Target:

```text
LESS TEXT
LESS CARD FRAGMENTATION
CLEARER STATUS
CLEARER CTA
STRONGER HIERARCHY
TECHNICAL DETAIL DEEPER
```

Per-screen information priority:

1. Current state
2. What the user should do next
3. Primary business evidence
4. Contract/Milestone context when real
5. Secondary metadata
6. Technical evidence hidden in disclosure/details

## Density rules

- One strong page title.
- One short description.
- One primary CTA per state whenever possible.
- Do not repeat one status in title + badge + paragraph + timeline.
- Prefer grouped facts over many micro-cards.
- Hide UUID/requestId/signature/PDA/provider references under technical disclosure.
- Keep empty/error/loading states short and actionable.
- Preserve visible keyboard focus and readable contrast.

## Current navigation — LOCKED

Client:

```text
Tổng quan
Công việc
Thanh toán
Hoạt động
Tài khoản
```

Freelancer:

```text
Tổng quan
Công việc
Thu nhập
Hoạt động
Tài khoản
```

Freelancer work subnav:

```text
Khám phá
Ứng tuyển
Công việc của tôi
```

No real role-switch control.

## UI direction by screen

### Auth
Keep FX poster. Reduce explanatory copy, improve role-choice readability, form spacing and mobile crop.

### Overview
Prioritize greeting → needs attention → active/recent work. Reduce long explanation blocks.

### Jobs / Discovery
Prioritize title → budget → status → deadline/revision context only when meaningful → ownership/application state → next action.

### Job Detail / Workflow
Highest-priority surface:

```text
Job identity
→ Current state
→ Next action
→ Brief/document
→ Deliverables + acceptance criteria
→ Contract/Milestone state when present
→ Latest submission or feedback
→ Compact lifecycle
→ Supporting metadata
→ Technical evidence
```

The page should feel like one document, not many unrelated cards.

### Finance / Tax
Prioritize amount → main state → independent financial stages → useful action → certificate/download → collapsed technical evidence.

### Activity
Timeline/list hierarchy: event type → title → one-line message → time → action.

### Account / Profile shell
The account workspace retains trusted identity/session controls and integrates authenticated own profile editing. Step 8 provides versioned profiles, role-specific fields, skills and Freelancer portfolio. Public profiles exclude private fields; nullable reputation values stay unavailable rather than becoming zero. Step 9 review lists/publication and reputation are server-owned; no invented trust score or publication countdown.

## What is real vs still future

### Real on current master

May be shown when returned by API:

```text
deliverables
acceptance criteria
delivery due date
review window hours
max revisions
revisions used
contract status
milestone status
contract amount/currency
AWAITING_PAYMENT job state
```

### Still unsupported unless a newer API proves otherwise

Do not add live controls/data for:

```text
wallet onboarding / Phantom connect
auto-release
chat
AI verification
```

## P06 technical boundary

### Verified P06.4 contracts — 2026-10-05

- GET/PUT `/api/v1/payment-methods/bank-account`: Client owner only, masked saved state. Full account/holder are transient form values and cleared after save; no storage/logging.
- POST and GET latest/exact `/api/v1/contracts/{contractId}/milestones/{milestoneId}/fund`: simulated capture, `BANK_ACCOUNT_ON_FILE`, immutable decimal contract amount, one sessionStorage key per Client/contract/milestone attempt. PENDING/PROCESSING/UNKNOWN reconcile; UNKNOWN is not definitive failure. Rotate only after SUCCEEDED/FAILED.
- POST/GET `/api/v1/contracts/{contractId}/submissions`: structured HTTPS evidence using snapshot IDs, stable key plus exact draft per uncertain attempt. Server owns versions; GET before retry.
- POST `/api/v1/contracts/{contractId}/submissions/{submissionId}/decisions`: APPROVE, REQUEST_REVISION with related snapshot IDs and feedback, minimal OPEN_DISPUTE with reason/description. Latest active submission only; revision quota enforced by server.
- Contract-backed legacy submit/revision/approve reject with 409/4029. Legacy frontend methods remain only for jobs without contracts.
- Server returns UTC reviewDueAt, nullable reviewGraceDueAt, reviewedAutomatically. <=500 USD eligible at due; >500 USD adds 24h grace. Countdown is display only; due/grace/focus/handled review notifications refetch state. No browser approval.
- Approval may leave Job SUBMITTED_FOR_REVIEW and Contract UNDER_REVIEW while Milestone RELEASE_PENDING. Show work decision complete / money processing; RELEASE_PENDING is not released money. P0/P1 integrate participant settlement and cancellation/refund APIs. Workpack A integrates trusted Admin dispute handling; Workpack B integrates the existing profile/portfolio/review APIs. Live desktop integration remains pending the runtime gate.
- Verified Step 4/5: GET `/api/v1/contracts/{contractId}/settlement`; GET/POST `/api/v1/contracts/{contractId}/cancellations`; POST `/api/v1/contracts/{contractId}/cancellations/{cancellationId}/decisions`. Public UI has no release/refund mutation; the decision API lets the server orchestrate refund. REQUESTED/REJECTED keep work continuing; REFUND_PENDING is not final. Primary money success is a simulated ledger confirmation, not bank settlement; settlement tax success is not certificate ACCEPTED.
- Marketplace is the only browser-facing API; no backend/runtime changes in this stage. P06.5+ remain NOT_STARTED.

Allowed by default:

```text
frontend/src/**
frontend tests related to changed UI
docs/ui/**
```

Do not modify by default:

```text
marketplace-backend/**
payment-backend/**
misa-backend/**
solana-integration/**
solana-stablecoin-payout/**
docker-compose.yml
.env*
database/schema
API contracts
dependencies
```

If UI work exposes a proven integration blocker, stop and report before expanding scope.

## Validation expectation

Each batch must preserve:

- current role behavior
- current routes
- current auth/session behavior
- current contract/milestone foundation behavior
- current job workflow
- current finance/tax behavior
- simulation honesty
- no invented API calls
- no console errors
- no horizontal overflow
- keyboard focus
- desktop/laptop usability at 1440px and 1024px for current MVP

Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px. Existing responsive CSS remains; no mobile validation gate applies to P06.4.

Final P06 gate:

```text
frontend tests PASS
frontend production build PASS
git diff --check PASS
Client route smoke PASS
Freelancer route smoke PASS
finance/tax read-only smoke PASS
backend changed: NO
unsupported feature introduced: NO
```
