# WORKPACK P06 — UI DE-CLUTTER & MARKETPLACE POLISH

## Authority

Use this order:

1. Current explicit Product Owner instruction.
2. This workpack.
3. `docs/ui/UI_DEVELOPMENT_MEMORY.md`.
4. `docs/ui/UI_POLISH_SPEC.md`.
5. Current frontend source and API behavior.
6. `docs/mvp-functional-spec.md` for future-facing structure.
7. README.

## Current baseline

Reviewed branch:

```text
master
```

Reviewed HEAD:

```text
3332d30ad542a5d49f721e1a1f46d9da8b8203c3
```

This baseline includes the Contract/Milestone foundation and exposes new frontend/API data.

Before editing:

```bash
git fetch origin
git status --short
git log -1 --oneline
```

If HEAD changed, inspect the new delta before continuing.

## Goal

Polish the current React frontend so it is:

- less text-dense
- easier to scan
- clearer about current state
- clearer about next action
- stronger as a marketplace product
- able to present current Contract/Milestone foundation cleanly

without adding unsupported backend/business functionality.

## Default allowed files

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
frontend/src/*test*
docs/ui/**
```

Read but do not modify by default:

```text
frontend/src/api.ts
frontend/src/types.ts
frontend/src/status.ts
```

These files are current product contracts. Modify only if a proven UI integration bug requires it and report first.

## Forbidden by default

Do not modify:

```text
marketplace-backend/**
payment-backend/**
misa-backend/**
solana-integration/**
solana-stablecoin-payout/**
docker-compose.yml
.env
.env.example
database/schema
dependencies
```

If a proven frontend integration blocker requires backend/runtime change, stop and report before expanding scope.

## Current real Contract/Milestone data

UI may render the following when returned by current API:

```text
deliveryDueAt
reviewWindowHours
maxRevisions
deliverables
acceptanceCriteria
contract.id
contract.status
contract.milestoneId
contract.milestoneStatus
contract.amount
contract.currency
contract.revisionsUsed
AWAITING_PAYMENT
```

Do not fabricate values when absent.

## Unsupported controls/data to avoid

Do not add live UI controls/data for:

```text
profile persistence
portfolio/reputation/rating
wallet connect
funding mutation
reviewDueAt countdown
dispute/Admin
auto-release
chat
AI verification
```

A visible `PENDING_FUNDING` or `AWAITING_PAYMENT` state does not imply the frontend already has a funding mutation.

## Visual lock

Keep:

```text
Cream #FFF7E8
Ink #17212B
Vermilion #F15A3D
Acid #F5D12F
Fresh Mint #B8DFC4
Cobalt #3567E8
```

Keep Kinetic Editorial Brutalism.

Do not convert the product into generic SaaS, admin dashboard or crypto trading UI.

## Implementation stages

### Stage A — shared hierarchy

Primary files:

```text
frontend/src/components.tsx
frontend/src/styles.css
```

Goal:

- shared section hierarchy
- primary/secondary action hierarchy
- technical evidence disclosure
- grouped facts
- cleaner metadata treatment

Do not create a broad design-system rewrite.

### Stage B — shell + Overview + Jobs

Primary files:

```text
frontend/src/App.tsx
frontend/src/Overview.tsx
frontend/src/Jobs.tsx
frontend/src/styles.css
```

Goal:

- reduce repeated orientation text
- stronger needs-attention area
- faster job-row scanning
- use real delivery/revision terms selectively
- better responsive stacking

### Stage C — Workflow / Work Lifecycle

Primary files:

```text
frontend/src/Workflow.tsx
frontend/src/WorkLifecycle.tsx
frontend/src/styles.css
```

Priority: HIGHEST

Required reading order:

```text
job
→ state
→ next action
→ document
→ deliverables / acceptance criteria
→ contract / milestone
→ latest submission / feedback
→ lifecycle
→ metadata
→ technical evidence
```

Preserve existing mutations. Do not invent new ones.

### Stage D — Finance / Activity / Account / Auth

Primary files:

```text
frontend/src/Finance.tsx
frontend/src/Activity.tsx
frontend/src/Account.tsx
frontend/src/Auth.tsx
frontend/src/styles.css
```

Rules:

- Finance: amount/status/action first.
- Activity: timeline/list, not card wall.
- Account: marketplace-profile shell using current real fields only.
- Auth: keep FX poster and reduce copy.

## Testing

During editing, prefer targeted tests.

At final UI gate run:

```bash
cd frontend
npm test
npm run build
```

Also verify:

```text
desktop
~1024px
mobile
no horizontal overflow
keyboard/focus-visible
Client five routes
Freelancer five routes
Contract/Milestone display
finance/tax read-only views
auth login/register
no console errors
git diff --check
```

Do not repeatedly rerun full suites after every small CSS change.

## Git safety

Do not:

```text
git reset --hard
git clean -fd
force push
history rewrite
delete unrelated files
repo-wide formatting
```

Do not push until explicitly authorized.

## Progress memory update

After each completed stage update:

```text
docs/ui/UI_IMPLEMENTATION_PROGRESS.md
```

## Final report format

```text
FREELAX P06 UI POLISH REPORT

Baseline:
HEAD:

Files created:
Files modified:
Files deleted:

A. Shared hierarchy:
B. Shell:
C. Overview/Jobs:
D. Workflow:
E. Contract/Milestone presentation:
F. Finance:
G. Activity:
H. Account:
I. Auth:
J. Responsive:
K. Accessibility:
L. Tests:
M. Build:
N. Backend changed:
O. API behavior changed:
P. Unsupported features introduced:
Q. git diff --check:
R. working tree:

FINAL VERDICT:
P06 UI POLISH — PASS
```

or exact blocker.
