# FREELAX UI DEVELOPMENT MEMORY

> Status: ACTIVE UI MEMORY
> Scope: UI-first polish only
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

Do not invent a funding action merely because the state exists.

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
Current real account data is display name, email, role and user ID. Polish it to look like a marketplace profile shell, but do not invent profile/reputation data that is not yet returned by current APIs.

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
profile persistence
portfolio
reputation/rating
wallet onboarding / Phantom connect
funding mutation button
reviewDueAt countdown
cancel/dispute/Admin actions
auto-release
chat
AI verification
```

## P06 technical boundary

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
- desktop / ~1024px / mobile usability

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
