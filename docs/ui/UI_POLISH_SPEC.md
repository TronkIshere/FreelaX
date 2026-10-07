# FREELAX P06 UI POLISH SPEC

## LOCKED VISUAL SYSTEM V2 — 2026-10-08

Authority: `UI_VISUAL_POLISH_HANDOFF_20261006.md`; branch `feat/ui-visual-polish-20261006`; latest frozen source `99e5f081bb89c2b84df5d8736d2965b35dd206d3` (completed review opportunity patch; P06.6/P06.5A/B/C/D and earlier locks remain authoritative).
Client/Freelancer Overview remains FROZEN; Client Work C1/C2/C3 are PASS / FROZEN. Freelancer Explore/Applications/My Work are RE-FROZEN after the approved narrow row-identity override. Stable Job Visual Identity and Global Row Identity / State Rail / Category Plate are LOCKED. P06.5A Finance list is PASS / FROZEN. Current: PRE-P06.7 COMPLETED JOB REVIEW OPPORTUNITY PATCH — PASS / FROZEN. Source `99e5f081bb89c2b84df5d8736d2965b35dd206d3`. Existing ContractReviews remains the single review fetch/eligibility owner; the high completed-work callout anchors to #contract-reviews. Review is optional post-completion feedback and does not gate completion/release/payout. P06.5A/B/C/D and P06.6 remain frozen. 317/317 tests, production build and diff check PASS. Real Client/Freelancer Job Detail is healthy, but no eligible review opportunity exists in current runtime; 1024 callout layout/focus/native anchor passed an isolated presentation fixture. Next: P06.7 — FULL REGRESSION / QA — NOT_STARTED. Stop after this freeze. Historical guidance below remains useful; conflicting visual/baseline notes are superseded here.

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
| OTHER | Legacy skills → title → Generic Development (decorative only) |

Authority: `UI_VISUAL_POLISH_HANDOFF_20261006.md`; branch `feat/ui-visual-polish-20261006`; latest frozen source `926c8fc64c2f8767165b5f69f15293abf91c86b2` (P06.5D Activity; P06.5A/B/C and earlier locks remain authoritative).

Stable identity: `variant = stableHash(job.id) % 3`, `visualKey = family + ':' + variant`; missing ID uses variant 0. WEB_FRONTEND + job A/B may choose different browser variants 0/1/2, but both remain Web/Frontend. Rough decoration uses stable family + job identity; no random/time/order dependence, stable rerender/remount. Reuse the same JobThumbnail for Explore, Applications, My Work and future Client Work. No page-specific thumbnails, database thumbnail ID or upload.

Frozen Overview + Explore + Applications + My Work + Client Work C1/C2/C3 are references for visual grammar, **not reusable page templates**. Screenshot first, human approval before commit; no fake reference-image data. Desktop 1440/1024 is the current gate; mobile is deferred.

### Global row visual grammar — LOCKED

Three independent information channels:

1. **Semantic thumbnail** → what type of Job.
2. **Category plate** → explicit resolved visual semantic family.
3. **State rail + textual status marker** → workflow/application progress.

Never conflate category identity with progress. Secondary rows stay primarily Cream editorial ledger rows, not a colored card wall. Preserve approved geometry, typography, spacing, thumbnail scale, skills, category/status shapes, CTA positions, dividers and motion.

State rail: **6px at 1440; 5px at 1024**, solid saturated color at the left edge of meaningful row content. No gradient, blur, glow or color animation. Status text stays visible for accessibility.

| Job state | Rail / progress marker |
| --- | --- |
| OPEN | Acid `#F5D12F` |
| AWAITING_PAYMENT | Acid `#F5D12F` + Ink structure |
| IN_PROGRESS | Cobalt `#3567E8` |
| SUBMITTED_FOR_REVIEW | Acid `#F5D12F` + Ink |
| REVISION_REQUESTED | Vermilion `#F15A3D` |
| COMPLETED | Strong Success Green `#39B96E` |
| CANCELLED | Ink `#17212B` |

Strong Success Green is the approved functional success/completion accent. Fresh Mint `#B8DFC4` remains a LIGHT successful primary-card surface; approved Client/My Work COMPLETED primaries retain Mint + Strong Green.

| Application state | Rail / marker | Primary surface and text |
| --- | --- | --- |
| PENDING | Acid `#F5D12F` | Acid + Ink; Ink/Cream CTA |
| ACCEPTED | Strong Success Green `#39B96E` | Fresh Mint + Ink; Strong Green marker |
| REJECTED | Vermilion `#F15A3D` | Vermilion + Cream primary text |
| CANCELLED | Ink `#17212B` | Restrained Ink/Cream inactive treatment |

Application state, not Job state, determines Application progress colors. Do not revert PENDING primary Applications to default Vermilion. On Acid, title/description/facts/budget use Ink; skills remain high-contrast Cream/Ink rectangles.

| Resolved visual family | Category plate |
| --- | --- |
| WEB_FRONTEND | WEB / FRONTEND |
| BACKEND_API | BACKEND / API |
| SEO_CONTENT | SEO / NỘI DUNG |
| MOBILE_APP | MOBILE APP |
| UI_UX_DESIGN | UI / UX |
| ECOMMERCE | E-COMMERCE |
| DATA_ANALYTICS | DATA / ANALYTICS |
| BRANDING_GRAPHIC | BRANDING / GRAPHIC |
| Generic Development | KHÁC |

Plate: rectangular editorial label, hard Ink border, saturated locked palette, small hard offset allowed; no rounded SaaS pill or translucent/pastel treatment. Plate and thumbnail backing reuse `jobFamily` from `frontend/src/ui/job-thumbnails/jobFamily.ts`, the same resolver used by JobThumbnail. No duplicated keyword classifier.

Authority: `UI_VISUAL_POLISH_HANDOFF_20261006.md`; branch `feat/ui-visual-polish-20261006`; latest frozen source `926c8fc64c2f8767165b5f69f15293abf91c86b2` (P06.5D Activity; P06.5A/B/C and earlier locks remain authoritative).

### Client Work C1 — PASS / FROZEN

Client `/work` = **CLIENT WORK CONTROL BOARD**: masthead/active nav, editorial hero, real create CTA, truthful totalElements, first server record emphasized, state-aware primary, stable semantic thumbnail, real status/budget/deadline/context, secondary ledger, rails/plates, existing state-derived destinations and server pagination. No fake search/filter/sort, applicant/per-state counts or urgency.

Final accepted C1/global gate: **180/180 focused tests PASS**, production build/diff check PASS, 1440/1024 PASS without overflow, console errors 0, keyboard focus PASS, reduced motion preserved. Backend/API/business/filter behavior unchanged. Tests/build were not rerun for freeze. C2 approval below supersedes the earlier next-task checkpoint.

### Client Work C2 — PASS / FROZEN (2026-10-07)

Source: `846142d8a3fb5f8a3f59204158c29e534ab7ea56`. `/work/new` and `/work/:jobId/edit` are approved EDITORIAL WORK ORDER / BRIEF BUILDER surfaces. Keep one page; no wizard without a new product decision. Previous frozen surfaces remain unchanged.

**CREATE contract:** `title`, `description`, `category`, `skills`, `budgetUsd`, `deliveryDueAt`, `reviewWindowHours`, `maxRevisions`, `deliverables`, `acceptanceCriteria`. Existing business validation stays unchanged: explicit valid category/title required, skills max 10 trimmed 2–40 characters/no case-insensitive duplicates, budget > 0, delivery ≥24 hours ahead, review integer 24–168 hours, revisions integer 0–2, deliverables 1–10 with title/description required, acceptance criteria 1–20 with description required. Both lists preserve required=true.

**EDIT contract:** only `title`, `description`, `category`, `skills`, for trusted CLIENT + owner + OPEN. No controls for budget, deadline, review window, revisions, deliverables or acceptance criteria. Server thumbnail/category/status/skills/budget/deadline are read-only context; updateJob remains metadata-only.

| Authoring section | Marker |
| --- | --- |
| 01 Nội dung công việc | Acid |
| 02 Điều kiện thực hiện | Cobalt |
| 03 Sản phẩm bàn giao | Vermilion |
| 04 Điều kiện nghiệm thu | Mint |

These identify authoring sections, not Job workflow status. Cream controls/Ink borders, readable labels and strong focus; editorial numbered rows/Ink rules, add/remove and separate create-only deliverables/criteria. Structural zero-blur hard shadows only, never every input. Reuse existing motion/reduced-motion. Final Ink action band + Acid CTA: Đăng công việc / Lưu thay đổi, secondary Quay lại, pending Đang lưu…; duplicate protection unchanged.

1440: editor left, sticky **Live Draft Summary** right. 1024: **Hero → 01 → Preview → 02 → 03 → 04 → Actions**; no sticky desktop sidebar forced into this width.

Preview is **UNSAVED local draft truth**, with disclaimer, not server state or Job Detail. Allowed: selected category, draft title/parsed skills/budget/deadline, counts of non-empty deliverables/criteria and at most one actual deliverable title. Empty rows do not count; empty summary says “Chưa có nội dung”. No fake OPEN, applicant/Freelancer/payment state, ranking/score, completion percentage or saved/ready/completed claim.

Authoring category is explicit, never title-inferred. Neutral before selection; selected-category semantic family and Category Plate afterward. A new Job has no ID: deterministic default variant 0, no random identity. Real saved ID later selects its stable variant; preview is not guaranteed the persisted exact variant. Category-only preview does not alter persisted legacy OTHER skills/title fallback. Skills keep comma parsing, trimming and bounds; removable tokens are presentation, API remains string[], no new taxonomy/autocomplete.

Accepted C2 evidence: **50/50 JobEditor tests PASS**, production build/diff check PASS, **Create 1440 PASS / Create 1024 PASS / Edit 1440 PASS**, no overflow/sticky overlap, console **0**, keyboard focus/reduced motion PASS. Backend/API/frozen surfaces unchanged; browser visual-QA draft **NOT SUBMITTED**. Freeze reuses accepted evidence without tests/build rerun. Its then-next C3 checkpoint is superseded by the approved C3 freeze below.

### Client Work C3 — PASS / FROZEN (2026-10-07)

Source `45c54c5a753c1cd4438dd3c968fe7ccec20a0491`; route `/work/:jobId/applications`. **CANDIDATE DECISION DESK / EDITORIAL CANDIDATE DOSSIER**. The final corrected visual replaces rejected plain Cream + lines.

Job context is a dominant colored editorial banner: 2px Ink border, zero-blur hard shadow, enlarged shared semantic thumbnail/Category Plate, real job title/skills/budget/deadline/status. State mapping: OPEN Acid; AWAITING_PAYMENT Acid + Ink; IN_PROGRESS Cobalt; SUBMITTED_FOR_REVIEW Acid/Ink; REVISION_REQUESTED Vermilion/Cream; COMPLETED Fresh Mint + Strong Green marker; CANCELLED Ink/Cream. Do not hardcode Acid across states or revert to a Cream strip.

Candidate dossiers remain equal Cream panels: hard Ink border/restrained hard shadow, state rail, display-order index, larger deterministic public-name initials tile with cut-paper/tape/rays, readable evidence and distinct status/date/action zone. Preserve server order; no featured/recommended first applicant or ranking. Application rails/visible labels: PENDING Acid `#F5D12F`, ACCEPTED Strong Green `#39B96E`, REJECTED Vermilion `#F15A3D`, CANCELLED Ink `#17212B`; 6px at 1440 and 5px at 1024. Application state does not encode Freelancer quality.

Application API owns status/date/eligibility. Public Profile is supplemental: ownership first, unique parallel isolated reads, userId match + FREELANCER. Failed profile leaves Application/status/date/fallback ID/link visible; no portfolio/review-list fan-out. Real returned identity/headline/country/availability/profile.skills and at most three compact reputation facts only; never private email or Job-derived candidate skills. Null is omitted, not converted to rating/on-time zero; explicit zero counts remain. No fake ranking/search/sort/filter/counts.

**Sparse profile rule:** visual density must NOT be achieved by fabricated product data. Use structure, spacing, typography, borders, color, cut-paper identity and kinetic details. Omit absent headline/skills/reputation/bio/city/experience/response speed; no expertise or rating invention. Runtime one PENDING applicant is sparse; reference-image richness does not authorize fabricated fields.

Selection only trusted CLIENT + Job owner + OPEN + PENDING. First click opens local Ink **Xác nhận lựa chọn**, never assigns/marks ACCEPTED. Confirm/cancel and pending duplicate lock preserved. Real copy explains acceptance after server success, remaining pending applications closing/rejecting, funding before Freelancer starts. After assign, server refresh/reconciliation; no manufactured ACCEPTED/REJECTED. Non-OPEN roster read-only with Job destination. Funding/wallet/payout/stablecoin controls stay outside C3.

1440: dominant full-width Job banner and horizontal dossier. 1024: intentional internal stacking with readable identity/evidence/status/date/actions. Small connected marks and micro-motion only; reduced motion and visible focus preserved. Corrected accepted gate: **66/66 focused tests PASS**, build/diff PASS; **1440 PASS / 1024 PASS / Confirmation 1440 PASS**, no overflow/clipping, console **0 errors**, focus-visible PASS. Backend/API unchanged; assignment during screenshot QA **NOT EXECUTED**. No tests/build rerun or further source edits during freeze. Next **P06.5 — FINANCE / TAX / ACTIVITY**, NOT_STARTED; real-contract inspection and human concept/target screenshot approval before implementation.

### Locked My Work composition

`/work/mine` = ACTIVE WORK / DELIVERY TRACKER. Active subnav, editorial “Công việc của bạn.” heading, real server total/order/pagination, first record emphasized, semantic thumbnail, real budget/optional deadline/revision context, state-derived next destination and flat ledger. No invented search/filter/sort.

AWAITING_PAYMENT → waiting/read-only; IN_PROGRESS → Cobalt; SUBMITTED_FOR_REVIEW → Acid; REVISION_REQUESTED → Vermilion; COMPLETED → Fresh Mint; CANCELLED → restrained Ink/Cream. State truth always outranks color. Budget is job value, not payout confirmation; COMPLETED is not paid, RELEASE_PENDING is not paid, REFUND_PENDING is not refunded. No Freelancer funding action; deadline/revision usage only when real.

Accepted My Work validation: 62/62 focused tests, build/diff PASS, 1440/1024 PASS, console errors 0. Stable thumbnail validation: 131/131 focused tests, 36/36 recheck, build/diff and cross-screen identity PASS. Runtime observed 4 My Work jobs with IN_PROGRESS/COMPLETED and sparse legacy category/skills; this is not guaranteed seed content.

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

### Locked NotificationType presentation mapping

| Backend NotificationType | Preserved human label | Visual family | Presentation tone |
| --- | --- | --- | --- |
| JOB_ASSIGNED | Được giao việc | work | success |
| JOB_CANCELLED | Công việc đã hủy | work | closed |
| WORK_SUBMITTED | Có bản bàn giao | work | active |
| REVISION_REQUESTED | Yêu cầu chỉnh sửa | work | attention |
| WORK_APPROVED | Bàn giao được duyệt | work | success |
| FUNDING_CONFIRMED | Funding đã xác nhận | finance | success |
| RELEASE_CONFIRMED | Bản ghi release đã xác nhận | finance | success |
| CANCELLATION_REQUESTED | Đề nghị hủy — công việc tiếp tục | refund | attention |
| CANCELLATION_REJECTED | Đề nghị hủy bị từ chối — công việc tiếp tục | refund | neutral |
| REFUND_PENDING | Hoàn tiền đang đối soát | refund | active |
| REFUND_CONFIRMED | Bản ghi hoàn tiền đã xác nhận | refund | success |
| REVIEW_GRACE_STARTED | Gia hạn review | review | attention |
| REVIEW_AUTO_APPROVED | Máy chủ tự duyệt | review | success |
| DISPUTE_OPENED | Đã mở tranh chấp | dispute | attention |
| DISPUTE_DECIDED | Admin đã quyết định tranh chấp | dispute | neutral |
| REVIEW_INVITED | Mời đánh giá hợp đồng | review | attention |
| REVIEW_PUBLISHED | Đánh giá đã công bố | review | success |
| PAYMENT_SENT | Thanh toán Client | finance | neutral |
| PAYMENT_RECEIVED | Chi trả mô phỏng | finance | success |
| TAX_EXPORT_FAILED | Chứng từ thuế | tax | error |
| PAYOUT_FAILED | Chi trả cần xử lý | finance | error |

Unknown → Cập nhật từ Marketplace / neutral / neutral. Semantic tones describe the notification meaning, not payout finality or read state.

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
