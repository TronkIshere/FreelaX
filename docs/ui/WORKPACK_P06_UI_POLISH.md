# WORKPACK P06 — UI DE-CLUTTER & MARKETPLACE POLISH

## CURRENT CONTINUATION OVERRIDE — 2026-10-06

This override supersedes old active branch/master baseline and next-stage instructions below. Preserve the historical workpack as context; current source and explicit Product Owner instructions remain authoritative.

- Branch: `feat/ui-visual-polish-20261006`.
- Frozen source baseline: `f42e44a5dcde0b8713fea532aa43345e943629b5`. Later docs-only HEADs do not change it.
- FROZEN: Client/Freelancer Overview and Freelancer Explore.
- NEXT: Freelancer → Công việc → Ứng tuyển; NOT_STARTED in this visual stream.
- First read: handoff, session log and the order in `START_HERE_UI.md`.
- Use the approved kinetic toolkit; create an application-status/editorial-tracker concept from real MyApplications contracts before implementation. No mechanical Explore copy, invented viewed/deadline/probability/interview/timeline data or taste-polish reopening of frozen screens.
- One-time backend/product-data exception `302ae06` added real job category/skills and discovery filtering. COMPLETE; no unrelated backend expansion authorized.
- The current task is documentation synchronization only. Do not implement Applications in this pass.

New-session preflight (for the next session, not executed during this docs-only task):

```bash
git switch feat/ui-visual-polish-20261006
git pull --ff-only
git status --short
git log -5 --oneline
```

If source/worktree differs from the accepted baseline, inspect the delta before editing. Do not reset, clean or discard user work. Mobile optimization is deferred; current desktop gate is 1440/1024.

## Historical workpack

## Authority

Use this order:

1. Current explicit Product Owner instruction.
2. This workpack.
3. `docs/ui/UI_DEVELOPMENT_MEMORY.md`.
4. `docs/ui/UI_POLISH_SPEC.md`.
5. Current frontend source and API behavior.
6. `docs/mvp-functional-spec.md` for future-facing structure.
7. README.

## Historical P06 baseline (superseded for current continuation)

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

P06.4 explicitly authorizes api/types/status updates for the verified synced bank/funding and contract submission/review APIs. Backend source remains authoritative; never add imagined contracts.

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
Admin dispute resolution / case management
auto-release
chat
AI verification
```

A visible state alone does not authorize a mutation. P06.4 verified the synced FundingController, ClientBankAccountController and ContractSubmissionController before wiring actions.

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

P06.4 now executes four ordered sub-stages on synced HEAD `cf0594f`:

1. **P06.4A — Bank / Funding:** masked Client bank GET/PUT, immutable contract amount, confirmed simulated capture, per-attempt sessionStorage key, latest/exact reads and bounded PENDING/PROCESSING/UNKNOWN reconciliation. Finance must not call payment-status for an unfunded contract.
2. **P06.4B — Contract Submission / Review:** structured HTTPS evidence using snapshot IDs, stable key plus exact draft, GET before ambiguous retry, server-owned versions, latest/history hierarchy, revision quota/related IDs, approve and minimal OPEN_DISPUTE. Contract-backed jobs never use legacy mutations (409/4029 guard).
3. **P06.4C — Review Deadline:** server UTC due/grace timestamps and reviewedAutomatically; <=500.00 USD due policy, >500.00 USD 24h grace. Display-only countdown; zero waits for server. Refetch at boundaries/focus/handled actual review notifications, with timer cleanup and bounded polling. Never approve in browser.
4. **P06.4D — Workflow Polish:** preserve Kinetic Editorial Brutalism, reduce repeated labels/cards, group real scope/terms, keep feedback tied to its version and technical references collapsed. Milestone RELEASE_PENDING is not RELEASED or payout completion; Job/Contract/Submission states remain independent.

Verify actual ClientBankAccountController, FundingController, ContractSubmissionController, services, DTOs and ErrorCode before changes. Backend files are read-only. P0/P1 now integrate verified Step 4/5 settlement and cancellation/refund APIs; Admin resolution/profile/rating remain unsupported. Do not implement new backend orchestration in a frontend workpack.

Final gate: targeted tests during work; full `npm test` and production build once at closure, diff check, both roles at 1440px and 1024px, keyboard/focus/privacy checks. Real-stack smoke uses existing local services only; unavailable services are ENVIRONMENT BLOCKED and must not trigger runtime/auth workarounds. Keep P06.5+ NOT_STARTED. No commit/push.

Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px. Do not roll back compatible CSS; no mobile optimization, screenshots or validation gate for P06.4.

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
1440px desktop
1024px laptop / narrow desktop
mobile DEFERRED for current MVP
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
