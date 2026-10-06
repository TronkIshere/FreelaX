# FREELAX P06 UI POLISH SPEC

## LOCKED VISUAL SYSTEM V2 — 2026-10-06

Authority: `UI_VISUAL_POLISH_HANDOFF_20261006.md`; branch `feat/ui-visual-polish-20261006`; frozen source `f42e44a5dcde0b8713fea532aa43345e943629b5`.
Client/Freelancer Overview and Freelancer Explore are FROZEN. Next Freelancer Applications is NOT_STARTED. Historical hierarchy guidance below remains useful; conflicting visual/baseline notes are superseded here.

**KINETIC EDITORIAL BRUTALISM**: Cream `#FFF7E8`, Ink `#17212B`, Vermilion `#F15A3D`, Acid `#F5D12F`, Fresh Mint `#B8DFC4`, Cobalt `#3567E8`.

- Cream paper; Ink masthead/structural type; Acid active nav; Vermilion attention; Cobalt secondary accent; Mint confirmed/positive accents.
- Dominant Vermilion uses Cream main headline/value/body. Ink is allowed in small contrasting labels/buttons; primary CTA is Ink/Cream.
- 1.5–2px Ink borders and hard offset shadows with zero blur. Cut-paper layers, tape/tab, seeded Rough arrows/underline/bursts and deliberate asymmetry connect to content. Modest radius allowed.
- Reject generic SaaS/pastel cards, soft shadows, glass/blur/glow, gradient dashboard surfaces, pill-heavy/bento/crypto dashboards, unrelated stickers, random job-ID-only semantic thumbnails and stock-image product identity.
- Lucide functional icons, Rough.js/CSS/SVG personality, Motion micro-interactions; reuse `frontend/src/ui/kinetic/`. 160ms presets, no loops; visible keyboard focus and live reduced-motion opt-out required.
- Functional desktop typography should read at roughly 14px or more; hierarchy comes from weight/color/spacing/placement. Overview exact supporting scale: hero 18px/1.5/500, account-count 16px/1.4/500, status caption 16px/1.4/600, footer 15px/1.4/500. Preserve stronger titles and amounts.

Semantic job thumbnails:

| Category | Visual |
| --- | --- |
| WEB_FRONTEND | Browser/layout/cursor |
| BACKEND_API | Code/server/database |
| SEO_CONTENT | Magnifier/search/chart/document |
| MOBILE_APP | Smartphone/app UI |
| UI_UX_DESIGN | Wireframe/design controls/cursor |
| ECOMMERCE | Storefront/cart/bag |
| DATA_ANALYTICS | Dashboard/charts |
| BRANDING_GRAPHIC | Logo/typography/color swatches |
| OTHER | Generic Development |

Authority: real stored category → legacy skills → legacy title → Generic Development; stored OTHER never infers another family. Fallback is for missing recognized categories; see the source adapter/contract. No job ID chooses family.

Frozen Overview + Explore are the best references for visual grammar, **not reusable page templates**. Applications must express real application status as an editorial tracker. Screenshot first, human approval before commit; no fake reference-image data. Desktop 1440/1024 is the current gate; mobile is deferred.

## Historical P06 hierarchy guidance

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

P06.4 backend sync now supplies both contracts. Integrate bank readiness and simulated funding confirmation, then contract evidence and Client decisions. Use immutable scope IDs and server deadlines; preserve idempotency across uncertainty/reload. Keep minimal dispute opening separate from future Admin case management.

Reading order: identity → current state/valid action → brief → scope → terms → funding/contract/milestone → latest evidence/feedback/deadline → primary action → compact history → metadata → collapsed references. RELEASE_PENDING means a work decision exists and money processing remains; never claim payout completion. No browser-generated approval or grace timestamp.

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

The current Step 8 API supports versioned profiles, role-specific fields, skills and Freelancer portfolio. Use it in Account while preserving identity/session controls. Step 9 supports participant reviews, authenticated public listings/reporting and trusted Admin moderation. Render only server-returned reputation; no local aggregates or publication countdown.

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
Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px. Preserve existing mobile-compatible CSS without further optimization or validation for P06.4.

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
backend profile persistence / portfolio implementation (frontend integrates existing Step 8 APIs)
reputation calculation
wallet onboarding
Phantom connect
backend funding/review implementation (frontend integrates existing APIs)
backend release/refund orchestration implementation (frontend integrates existing Step 4/5 APIs)
backend Admin dispute resolution implementation (frontend integrates existing Step 6 APIs)
backend rating/publication implementation (frontend integrates existing Step 9 APIs)
chat
AI skill verification
```
