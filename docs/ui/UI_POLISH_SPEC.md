# FREELAX P06 UI POLISH SPEC

## Design objective

Keep the current FreelaX visual identity while making every screen faster to understand.

Target:

```text
Editorial
Confident
Structured
Trustworthy
Less text-dense
More obvious next action
```

Every primary screen should answer three questions in the first viewport:

1. Tôi đang ở đâu?
2. Trạng thái hiện tại là gì?
3. Tôi nên làm gì tiếp theo?

## Shared hierarchy

Recommended primitives:

### PageHero
Page identity, short description, one side fact.

### StateStrip
Current state, ownership and next action. Use only where it materially improves scanability.

### FactGrid
2–4 meaningful business facts, not a dashboard-card wall.

### DocumentSection
Wide readable surface for brief, deliverables, acceptance criteria, submission and feedback.

### LifecycleRail
Compact lifecycle progress, not a giant wizard.

### EvidenceDisclosure
Collapsed technical evidence using native `<details>` where possible.

### ActionGroup
One primary action plus quieter secondary actions.

Avoid generic `Card`, `DashboardCard`, glass panels, metric tile walls and pill-heavy UI.

## Global shell

Keep:

- FreelaX wordmark
- role identity
- user display name
- logout
- five main destinations
- Freelancer work subnav

Simplify repeated role/context labels if masthead and top context say the same thing.

## Overview

Preferred structure:

```text
PAGE HERO
↓
NEEDS ATTENTION
↓
ACTIVE / RECENT WORK
↓
SECONDARY CONTEXT
```

Avoid long explanatory paragraphs.

## Jobs / Discovery

Desktop row hierarchy:

```text
Title
Short description
Budget
Status
Deadline / revision terms only when useful
Ownership/application state
Next action
```

Rules:

- description: max 1–2 lines
- budget: strong numeric weight
- one status treatment
- next action visibly distinct
- metadata quieter
- do not overload every row with all Contract/Milestone fields

## Job Detail / Workflow

Highest-priority screen group.

Preferred structure:

```text
JOB IDENTITY
Budget · Current status · Participant context

CURRENT STATE
State / owner / next action

MAIN DOCUMENT
Brief / description

SCOPE
Deliverables
Acceptance criteria

TERMS
Delivery due date
Review window
Max revisions

CONTRACT / MILESTONE
Only when contract data exists

LATEST WORK EVIDENCE
Submission OR feedback depending on state

PRIMARY ACTION
Only actions already supported by current API

LIFECYCLE
Compact rail

SUPPORTING FACTS
dates / ownership / application context

TECHNICAL EVIDENCE
collapsed
```

The page should feel like one document, not many unrelated cards.

Latest submission is primary. Older versions become history.

Revision feedback should be visually tied to the submission it refers to.

`revisionsUsed / maxRevisions` may be shown only when backed by the current ContractSummary.

Do not create a review countdown until the backend returns an actual review due timestamp.

Do not create a “Fund” button until a current Marketplace API mutation exists for funding.

## Finance / Tax

Preferred structure:

```text
JOB / PAYMENT IDENTITY
↓
PRIMARY AMOUNT
↓
MAIN FINANCIAL STATE
↓
INDEPENDENT STATUS ROWS
Payment
On-chain
Off-ramp
Tax
↓
USEFUL ACTION
certificate / download
↓
TECHNICAL EVIDENCE
collapsed
```

Rules:

- financial stages remain independent
- simulation language stays visible
- signatures/PDA/provider IDs stay secondary
- do not turn finance into a crypto dashboard

## Activity

Preferred row:

```text
EVENT TYPE          TIME
Title
One-line message
Action →
```

Unread state uses one restrained signal.

Avoid nested cards.

## Account / Profile shell

Current account source still supports only current identity/session fields unless a newer profile API is added.

Do not fabricate:

- skills
- portfolio
- rating
- verification
- wallet
- reputation

## Auth

Keep the FX editorial poster.

Login:

```text
strong headline
short support sentence
email
password
primary CTA
register link
```

Register:

- role selection first
- shared identity fields second
- Freelancer tax/bank group third
- reduce explanatory copy
- preserve every field required by current backend

Mobile: FX art becomes an intentional banner/crop, not squeezed desktop art.

## Responsive

### Desktop
Wide editorial layout.

### ~1024px
Reduce columns, preserve hierarchy.

### Mobile
Single-column. Keep primary action near current state. Do not create long metadata walls.

## Accessibility

Preserve:

- visible focus
- semantic headings
- form labels
- error roles
- aria-live status behavior
- readable contrast
- keyboard navigation

Any new motion must remain brief and non-essential.

## Non-goals

P06 does not implement backend/business features absent from current APIs, including:

```text
profile persistence APIs
portfolio CRUD
reputation calculation
wallet onboarding
Phantom connect
funding mutation
review deadline enforcement
dispute/Admin
rating
chat
AI skill verification
```
