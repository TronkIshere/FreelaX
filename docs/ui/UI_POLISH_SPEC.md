# FREELAX P06 UI POLISH SPEC

## LOCKED VISUAL SYSTEM V2 — 2026-10-07

Authority: `UI_VISUAL_POLISH_HANDOFF_20261006.md`; branch `feat/ui-visual-polish-20261006`; latest frozen source `304dd195cd56fcab7ac92158e7264f0acea8e6e8` (P06.5C Tax evidence; P06.5A/P06.5B and earlier locks remain authoritative).
Client/Freelancer Overview remains FROZEN; Client Work C1/C2/C3 are PASS / FROZEN. Freelancer Explore/Applications/My Work are RE-FROZEN after the approved narrow row-identity override. Stable Job Visual Identity and Global Row Identity / State Rail / Category Plate are LOCKED. P06.5A Finance list is PASS / FROZEN. Current: P06.5C TAX / CHỨNG TỪ THUẾ — PASS / FROZEN. Source `304dd195cd56fcab7ac92158e7264f0acea8e6e8`. Tax Evidence Ledger and Certificate Case File use server TaxRecord truth, unchanged payout/action gates and authenticated Marketplace blobs. Client/Freelancer 1440 and Freelancer 1024 runtime layout checks PASS. P06.5A/P06.5B remain FROZEN and unchanged. Next: P06.5D — ACTIVITY / EDITORIAL EVENT LEDGER — NOT_STARTED. Stop after this freeze. Historical guidance below remains useful; conflicting visual/baseline notes are superseded here.

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

Authority: `UI_VISUAL_POLISH_HANDOFF_20261006.md`; branch `feat/ui-visual-polish-20261006`; latest frozen source `304dd195cd56fcab7ac92158e7264f0acea8e6e8` (P06.5C Tax evidence; P06.5A/P06.5B and earlier locks remain authoritative).

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

Authority: `UI_VISUAL_POLISH_HANDOFF_20261006.md`; branch `feat/ui-visual-polish-20261006`; latest frozen source `304dd195cd56fcab7ac92158e7264f0acea8e6e8` (P06.5C Tax evidence; P06.5A/P06.5B and earlier locks remain authoritative).

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

Current: P06.5C TAX / CHỨNG TỪ THUẾ — PASS / FROZEN. Source `304dd195cd56fcab7ac92158e7264f0acea8e6e8`. Tax Evidence Ledger and Certificate Case File use server TaxRecord truth, unchanged payout/action gates and authenticated Marketplace blobs. Client/Freelancer 1440 and Freelancer 1024 runtime layout checks PASS. P06.5A/P06.5B remain FROZEN and unchanged. Next: P06.5D — ACTIVITY / EDITORIAL EVENT LEDGER — NOT_STARTED. Stop after this freeze.

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
