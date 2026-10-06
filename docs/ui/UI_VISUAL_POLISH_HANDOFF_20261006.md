# FreelaX visual polish handoff — 2026-10-06

## Repository, branch and source authority

- Repository: `TronkIshere/FreelaX`.
- Active branch: `feat/ui-visual-polish-20261006`.
- Integrated MVP ancestor: `35ba34e9b68be67b9405d3407159a2fde010911c`.
- Frozen last product/source implementation commit: `846142d8a3fb5f8a3f59204158c29e534ab7ea56` (Client Job Authoring C2, approved 2026-10-07).

Accepted source chain, oldest first:

| Commit | Accepted change |
| --- | --- |
| `a47c865` | Visual hierarchy and Overview shell |
| `01c7e55` | Final kinetic editorial Overview |
| `302ae06` | Real job category and skill discovery filters |
| `f42e44a` | Final kinetic Freelancer Explore |
| `2f5f0e4` | Final kinetic Freelancer Applications |
| `ca8b93d` | Final kinetic Freelancer My Work + stable Job visual identity |
| `dc440ec` | Client Work C1 + global row identity / state rails / category plates |
| `846142d` | Client Job Authoring C2: approved create/edit work order |

A later documentation commit may be branch HEAD. It does not replace `846142d` as the frozen source implementation baseline. Read the current HEAD with Git; do not mistake a docs-only HEAD for new product implementation. Current source wins over older conflicting documentation. Explicit Product Owner instructions remain authoritative.

## Current UI progress matrix

Statuses below refer to this visual stream, not to absence of existing functional pages.

| Surface | Current visual status |
| --- | --- |
| Auth | Existing MVP/Auth poster direction; not reopened here |
| Global shell / navigation | APPROVED |
| Client Overview | FROZEN / APPROVED |
| Freelancer Overview | FROZEN / APPROVED |
| Freelancer → Công việc → Khám phá | PASS / RE-FROZEN after narrow row-identity override |
| Job discovery data foundation | FROZEN / IMPLEMENTED |
| Freelancer → Công việc → Ứng tuyển | PASS / RE-FROZEN after narrow row-identity override |
| Freelancer → Công việc → Công việc của tôi | PASS / RE-FROZEN after narrow row-identity override |
| Stable Job Visual Identity | LOCKED |
| Client Work C1 (`/work`) | PASS / FROZEN |
| Global Row Identity / State Rail / Category Plate | LOCKED |
| Client Work C2 — Job Authoring (`/work/new`, `/work/:jobId/edit`) | PASS / FROZEN |
| Client Work C3 — Applicants (`/work/:jobId/applications`) | NEXT / NOT_STARTED |
| Finance / Income / Payment | Later |
| Activity | Later |
| Account / Profile | Later unless explicitly reprioritized |

## Locked visual direction

**KINETIC EDITORIAL BRUTALISM**

| Token | Value | Use |
| --- | --- | --- |
| Cream | `#FFF7E8` | Paper base; main text on Vermilion |
| Ink | `#17212B` | Masthead, structural typography, borders, CTA |
| Vermilion | `#F15A3D` | Dominant action/attention surfaces |
| Acid | `#F5D12F` | Active navigation and compact labels |
| Fresh Mint | `#B8DFC4` | Positive/confirmed accents |
| Cobalt | `#3567E8` | Hard secondary accent and visible focus |
| Strong Success Green | `#39B96E` | Approved functional COMPLETED/ACCEPTED progress accent |

Non-negotiable grammar:

- Cream main headline, value and supporting typography directly on dominant Vermilion. Ink text only inside small contrasting labels/buttons there. Ink CTA uses Cream text.
- 1.5–2px Ink borders; hard offset shadows with zero blur. No soft/translucent depth.
- Cut-paper layers, tape/tab, Rough.js arrows, underline and bursts; deliberate asymmetry and modest radius are allowed.
- Deterministic decorative output. Every decorative element connects to content, an action or illustration.
- Micro-motion only; `prefers-reduced-motion` is mandatory. Preserve semantic controls and visible keyboard focus.
- Keep important functional desktop text readable through weight, color, spacing and placement, not miniature type. Approximately 14px is the functional floor for this approved stream; retain the exact Overview scale below.

Rejected: generic SaaS, pastel SaaS cards, soft shadows, glass, blur, glow, gradient dashboard surfaces, pill-heavy UI, bento dashboard language, generic crypto dashboards, random sticker scatter, random job-ID-only semantic thumbnails, and stock imagery as product identity.

Frozen Overview, Explore, Applications, My Work, Client C1 and C2 are references for visual grammar, **not reusable page templates**. New page composition must follow its own real task/data. Do not reopen frozen surfaces for general polish; only a concrete regression justifies a fix.

## Current toolkit

Dependencies: `roughjs@4.6.6`, `motion@14.0.0`, `lucide-react@1.52.0`.

`frontend/src/ui/kinetic/` contains exactly:

```text
README.md
index.ts
kinetic.css
kinetic.test.tsx
motion.ts
palette.ts
paper.tsx
primitives.tsx
rough.tsx
```

Lucide supplies functional icons. Rough.js / CSS / SVG supply editorial personality. Motion supplies micro-interactions. Reuse the existing module and shared heading/fact/disclosure/action semantics.

| Behavior | Current preset |
| --- | --- |
| Hard-card lift | x/y −1px; hard shadow 6px → 8px |
| Ledger row nudge | x +3px |
| Action arrow shift | x +4px |
| Thumbnail tilt | rotation +1°; y −1px |
| Sticker tilt | +1° relative to fixed paper angle |
| Underline reveal | 65% → 100% sweep |

Presets use 160ms ease-out tweens, hover and descendant keyboard focus. No looping animation, springs, bounce or parallax. Reduced-motion preference changes are subscribed to while mounted and cleaned up; unknown/SSR preference defaults static. CSS also suppresses motion. Static paper placement remains.

The toolkit's `KineticThumbnail` is abstract identity decoration used by frozen Overview. Explore's semantic `JobThumbnail` is a separate job-family system; do not use abstract ID variation to select a semantic family.

## Frozen Overview baseline

**OVERVIEW VISUAL BASELINE = FROZEN.** Do not reopen for taste-polish; only a concrete regression justifies a fix.

Approved composition: Ink masthead, Acid active nav, “Công việc của bạn.”, Vermilion attention card with Cream headline/value, Ink CTA/Cream text, category/editorial artwork, Recent Work editorial ledger and real server metrics. No fake trends or KPIs.

| Supporting text | Locked size / line-height / weight |
| --- | --- |
| Hero supporting copy | 18px / 1.5 / 500 |
| Account-count description | 16px / 1.4 / 500 |
| Status-summary caption | 16px / 1.4 / 600 |
| Footer brand | 15px / 1.4 / 500 |

Accepted evidence: 61/61 focused tests PASS; production build PASS; `git diff --check` PASS; console errors 0; desktop 1440 PASS and 1024 PASS. These are accepted prior gates, not rerun during this documentation pass.

## Frozen Freelancer Explore baseline

**FREELANCER EXPLORE VISUAL BASELINE = FROZEN.** Route `/work`: Freelancer → Công việc → Khám phá.

Composition: work subnav → “Tìm công việc phù hợp.” → support copy → search → filter sidebar + result board → pagination/footer. “Tìm công việc” is Ink, “phù hợp.” is Vermilion. RoughUnderline and two ray/blink clusters are present.

Real controls: Danh mục, Kỹ năng, Ngân sách tối thiểu, Ngân sách tối đa, Trạng thái ứng tuyển, Áp dụng bộ lọc, Xóa bộ lọc, search and sort. No Remote / Hybrid / Onsite / work-mode filter: no real work-mode domain field exists.

The first result may have a stronger Vermilion visual emphasis. This is presentation, not server authority to label it Featured, Hot or Recommended.

Accepted runtime evidence: category PASS, skill PASS, combined PASS, exclusion PASS, Clear PASS. Final correction: 54/54 focused tests PASS; build PASS; `git diff --check` PASS; 1440/1024 visual QA PASS; no horizontal overflow. Earlier focused frontend gate: 115/115 PASS (Jobs 31, API 37, taxonomy 23, kinetic 24). Marketplace package PASS was accepted before freeze.

Runtime-only evidence: “Landing page redesign” was edited through the real Client API to category `WEB_FRONTEND`, skills `HTML`, `CSS`, `Responsive Design`. This is local visual/smoke evidence, not guaranteed seed content in a fresh database. No production fixture or hardcoded job content was added. The earlier runtime row was legacy `OTHER` with empty skills; richer category/skill behavior also has focused test coverage.

## Job category / skills foundation

Authoritative contract: [JOB_DISCOVERY_CONTRACT.md](../../marketplace-backend/docs/JOB_DISCOVERY_CONTRACT.md).

Stored enum: `WEB_FRONTEND`, `BACKEND_API`, `SEO_CONTENT`, `MOBILE_APP`, `UI_UX_DESIGN`, `ECOMMERCE`, `DATA_ANALYTICS`, `BRANDING_GRAPHIC`, `OTHER`.

Job skills are job-specific, independent of Freelancer profile skills: max 10; trimmed; 2–40 characters each; no blank/null entries; no case-insensitive duplicates; display casing retained. Legacy rows are `OTHER` and `[]`; no title/profile-derived backfill.

Create requires explicit category; optional omitted/null skills mean empty. PATCH category/skills are optional; omitted/null preserves values, `skills: []` clears them. Current authoring retains existing eligibility and budget guards.

Discovery: `GET /api/v1/marketplace/jobs/discover` with `keyword`, `minBudgetUsd`, `maxBudgetUsd`, `category`, `skills`, `application`, `sort`, `page`, `size`. Skills use repeated query parameters, ANY selected skill, exact matching after trim, case-insensitive. Category/skills/keyword/budget/application compose with AND. Pagination and filtering are server-driven; no client-side page-only fake filtering.

Commit `302ae06` was an **explicitly approved narrow backend/product-data exception** for truthful filtering and thumbnails. It is COMPLETE and does not authorize unrelated backend expansion.

## Locked thumbnail taxonomy

| Category | Semantic visual |
| --- | --- |
| WEB_FRONTEND | Browser / layout / cursor |
| BACKEND_API | Code / server / database |
| SEO_CONTENT | Magnifier / search / chart / document |
| MOBILE_APP | Smartphone / app UI |
| UI_UX_DESIGN | Wireframe / design controls / cursor |
| ECOMMERCE | Storefront / cart / bag |
| DATA_ANALYTICS | Dashboard / charts |
| BRANDING_GRAPHIC | Logo / typography / color swatches |
| OTHER | Legacy skills → title → Generic Development (decorative only) |

Authority: meaningful stored category → legacy skills fallback → legacy title fallback → Generic Development. OTHER is semantically unspecified for decorative classification: try skills, then title, otherwise Generic Development. Stored backend category is unchanged; no title/profile backfill. The legacy source adapter also recognizes category/type strings when no meaningful persisted enum exists. Job ID never chooses semantic family.

Stable Job Visual Identity = **LOCKED**. `variant = stableHash(job.id) % 3`; `visualKey = family + ':' + variant`. Three restrained variants per family; missing ID → variant 0. WEB_FRONTEND + job A/B may receive different browser variants, but both remain visibly Web/Frontend. Rough marks use stable family + hashed job identity, never visible UUID text. Deterministic with no Math.random, Date.now or render-order dependence; exact rerender/remount stability.

Same job must retain identical family, variant and Rough decoration across Explore, Applications, My Work and future Client Work. Reuse the shared JobThumbnail; no page-specific Job thumbnails, database thumbnail ID or upload. Cross-screen focused test verifies exact markup across all three screens; runtime verifies identical artwork for the overlapping Explore/Applications job and all four Applications/My Work jobs.

Source: `frontend/src/ui/job-thumbnails/JobThumbnail.tsx`, `jobFamily.ts`, `jobFamily.test.ts`.

## Historical source manifest through Explore

Scope: `git diff --name-status 35ba34e9b68be67b9405d3407159a2fde010911c..f42e44a5dcde0b8713fea532aa43345e943629b5`. A = added; M = modified. Exactly **46 files**, each listed once. This is the accepted source stream, not this docs-only commit's file list.

### 1. Frontend foundation / Overview — 7

```text
M frontend/package-lock.json
M frontend/package.json
M frontend/src/App.test.tsx
M frontend/src/Overview.test.tsx
M frontend/src/Overview.tsx
M frontend/src/components.tsx
M frontend/src/styles.css
```

### 2. Kinetic toolkit — 9

```text
A frontend/src/ui/kinetic/README.md
A frontend/src/ui/kinetic/index.ts
A frontend/src/ui/kinetic/kinetic.css
A frontend/src/ui/kinetic/kinetic.test.tsx
A frontend/src/ui/kinetic/motion.ts
A frontend/src/ui/kinetic/palette.ts
A frontend/src/ui/kinetic/paper.tsx
A frontend/src/ui/kinetic/primitives.tsx
A frontend/src/ui/kinetic/rough.tsx
```

### 3. Explore / Job authoring UI — 7

```text
M frontend/src/App.tsx
A frontend/src/JobEditor.test.tsx
A frontend/src/JobEditor.tsx
M frontend/src/Jobs.test.tsx
M frontend/src/Jobs.tsx
M frontend/src/Workflow.tsx
A frontend/src/jobDiscovery.ts
```

### 4. Job thumbnail system — 3

```text
A frontend/src/ui/job-thumbnails/JobThumbnail.tsx
A frontend/src/ui/job-thumbnails/jobFamily.test.ts
A frontend/src/ui/job-thumbnails/jobFamily.ts
```

### 5. Frontend API/types — 3

```text
M frontend/src/api.test.ts
M frontend/src/api.ts
M frontend/src/types.ts
```

### 6. Marketplace discovery backend — 12

```text
M marketplace-backend/src/main/java/com/marketplace/backend/controller/JobController.java
M marketplace-backend/src/main/java/com/marketplace/backend/dto/request/job/CreateJobRequest.java
M marketplace-backend/src/main/java/com/marketplace/backend/dto/request/job/UpdateJobRequest.java
M marketplace-backend/src/main/java/com/marketplace/backend/dto/response/job/DiscoverJobResponse.java
M marketplace-backend/src/main/java/com/marketplace/backend/dto/response/job/JobResponse.java
M marketplace-backend/src/main/java/com/marketplace/backend/dto/response/job/MyApplicationJobResponse.java
M marketplace-backend/src/main/java/com/marketplace/backend/entity/Job.java
A marketplace-backend/src/main/java/com/marketplace/backend/entity/JobCategory.java
M marketplace-backend/src/main/java/com/marketplace/backend/repository/JobRepository.java
M marketplace-backend/src/main/java/com/marketplace/backend/service/JobService.java
A marketplace-backend/src/main/java/com/marketplace/backend/service/JobSkills.java
M marketplace-backend/src/main/java/com/marketplace/backend/service/impl/JobServiceImpl.java
```

### 7. Marketplace tests — 4

```text
A marketplace-backend/src/test/java/com/marketplace/backend/controller/JobDiscoveryControllerTest.java
A marketplace-backend/src/test/java/com/marketplace/backend/repository/JobDiscoveryRepositoryTest.java
M marketplace-backend/src/test/java/com/marketplace/backend/service/impl/JobAccessAndDiscoveryServiceImplTest.java
M marketplace-backend/src/test/java/com/marketplace/backend/service/impl/JobWorkflowServiceImplTest.java
```

### 8. Backend discovery contract documentation — 1

```text
A marketplace-backend/docs/JOB_DISCOVERY_CONTRACT.md
```

## Runtime / environment notes

- Browser communicates only with Marketplace `/api/v1`; no direct Payment/MISA/Solana calls.
- Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px.
- Frontend build produced a JS chunk warning >500kB. It is NON-BLOCKING for frozen visual pages; optimize separately later.
- Solana local RPC `:9123` is outside Compose and may be unavailable. This limitation does not invalidate frozen Overview/Explore.
- Never copy secrets/credentials into screenshots or docs. `.env` content is never documentation material.
- Older P06/Workpack runtime blockers remain historical evidence. Successful Explore live smoke does not retrospectively certify every earlier feature/runtime gate.

## Frozen Freelancer Applications baseline

**Freelancer → Công việc → Ứng tuyển = PASS / FROZEN**, route `/work/applications`.

Source commit: `2f5f0e4b0667cf9fefbc3f7897fe63a904fa12bc` — `feat(frontend): finalize kinetic freelancer applications`.

Approved APPLICATION STATUS / EDITORIAL TRACKER: left status rail, kinetic heading with underline/rays, state-aware primary application record, compact editorial ledger and real pagination. The later `dc440ec` override replaces original fixed Vermilion emphasis: PENDING Acid/Ink, ACCEPTED Mint/Strong Green, REJECTED Vermilion/Cream, CANCELLED restrained Ink/Cream. Final micro-polish removes the redundant result-header filter label, locks lower-row descriptions/actions at 16px and facts/status/budget at 15px, and groups first-card status/budget/action without moving thumbnail/main copy. Overview remains FROZEN; Explore/Applications/My Work are RE-FROZEN after narrow approved row updates.

Final accepted validation: 35/35 focused tests PASS; production build PASS; `git diff --check` PASS; 1440 PASS; 1024 PASS; no horizontal overflow; console errors 0. These are prior accepted gates, not rerun during the freeze/commit task. No API/backend changes.

Semantic thumbnails reuse Explore's category-first taxonomy. `totalElements` supplies only the truthful server total for the active query, never invented per-status counts. No fake timeline, statuses, counts or mutation controls were introduced.

Current implementation is `MyApplications` in `frontend/src/Workflow.tsx`. It calls `MarketplaceApi.myApplications(page, status, size)` in `frontend/src/api.ts`, using `GET /api/v1/marketplace/jobs/applications/me` with page/size and optional status. Server pagination is adapted by the existing API stack.

Actual application statuses: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`; labels live in `frontend/src/status.ts`. Types `MyApplication` and `JobApplicationStatus` live in `frontend/src/types.ts`. Current rows show application and job status, real created/updated timestamps, Client name and budget. Job detail links are shown for ACCEPTED applications or OPEN jobs; this list currently offers status filters, pagination and read retry, not invented mutation controls. Backend remains permission authority.

Do not invent Client viewed, selection probability, fake response deadline, interview stage, response countdown or fake activity timeline. Timeline/status visuals must use server facts.

## Frozen Freelancer My Work baseline

**Freelancer → Công việc → Công việc của tôi = PASS / FROZEN**, route `/work/mine`. Stable Job Visual Identity = **LOCKED**.

Source commit: `ca8b93d45a42249f779c13f0348f945ac275d7e0` — `feat(frontend): finalize kinetic freelancer my work`. It includes My Work and the stable shared thumbnail correction in one approved source commit:

```text
frontend/src/WorkLifecycle.tsx
frontend/src/Workflow.test.tsx
frontend/src/WorkflowLifecycle.test.tsx
frontend/src/styles.css
frontend/src/ui/job-thumbnails/JobThumbnail.tsx
frontend/src/ui/job-thumbnails/jobFamily.ts
frontend/src/ui/job-thumbnails/jobFamily.test.ts
```

Approved concept: **ACTIVE WORK / DELIVERY TRACKER**. Active subnav; editorial “Công việc của bạn.” heading; real total; first server record emphasized; state-aware primary surface; semantic thumbnail; real budget; deadline/revision context only when returned; state-derived next-action copy; flat ledger below; real pagination. No invented search/filter/sort. Existing detailed workflow and Overview remain unchanged; Explore/Applications layout remains frozen, with only the approved shared thumbnail identity correction.

| Actual job state | Visual treatment |
| --- | --- |
| AWAITING_PAYMENT | Waiting/read-only |
| IN_PROGRESS | Cobalt work |
| SUBMITTED_FOR_REVIEW | Acid review |
| REVISION_REQUESTED | Vermilion attention |
| COMPLETED | Fresh Mint |
| CANCELLED | Restrained Ink/Cream |

State truth outranks decorative color. Contract DISPUTED, RELEASE_PENDING and REFUND_PENDING context remains explicit. Budget is job value, not payout confirmation; COMPLETED does not mean paid. AWAITING_PAYMENT exposes no Freelancer funding action. RELEASE_PENDING is not paid; REFUND_PENDING is not refunded. Deadline/revision usage only when real.

Accepted My Work validation: **62/62 focused tests PASS**, build PASS, `git diff --check` PASS, **1440 PASS / 1024 PASS**, no overflow, console errors **0**. Stable thumbnail correction: **131/131 focused tests PASS**, thumbnail recheck **36/36 PASS**, build/diff PASS, cross-screen identity PASS. These accepted gates were not rerun for freeze/commit. No backend/API/database/dependency changes.

Runtime observation: **4 My Work jobs**, encountered statuses **IN_PROGRESS / COMPLETED**. Legacy category/skills remain sparse; OTHER skills/title fallback was observed for SEO/API work. This is current runtime evidence, not guaranteed seed data or proof that every state exists in the local database.

## Client Work C1 + global row system — APPROVED / FROZEN / LOCKED

Source: `dc440ec96e4c960ac0ea285c07b42e0d823bc221` — `feat(frontend): finalize client work and global row identity`. Seven source files:

```text
frontend/src/Jobs.tsx
frontend/src/Jobs.test.tsx
frontend/src/WorkLifecycle.tsx
frontend/src/Workflow.tsx
frontend/src/styles.css
frontend/src/ui/JobRowIdentity.tsx
frontend/src/ui/JobRowIdentity.test.tsx
```

Client `/work` is the approved **CLIENT WORK CONTROL BOARD**: editorial hero/create CTA; truthful totalElements/server order/pagination; first server record emphasized; state-aware primary; stable thumbnail; real status/budget/deadline/context; secondary ledger with rails/plates and existing state-derived actions. No fake search/filter/sort, applicant/per-status counts or urgency. Overview unchanged.

Global grammar LOCKED: thumbnail = semantic Job type; plate = explicit visual family; rail/status = workflow/application progress. Independent channels; secondary rows remain Cream, not a colored card wall. Rail solid/saturated 6px at 1440 and 5px at 1024, no gradient/blur/glow/color animation, visible status text retained. Job progress: OPEN/AWAITING_PAYMENT/SUBMITTED_FOR_REVIEW Acid, IN_PROGRESS Cobalt, REVISION_REQUESTED Vermilion, COMPLETED Strong Green, CANCELLED Ink. Application progress: PENDING Acid, ACCEPTED Strong Green, REJECTED Vermilion, CANCELLED Ink. Strong Success Green `#39B96E` is approved; Fresh Mint remains a light primary success surface. See UI_POLISH_SPEC for exact colors/plates/primary contrast rules.

`JobRowIdentity` plate/backing use existing `jobFamily`, also used by JobThumbnail. Meaningful stored category → skills → title → Generic Development. OTHER stays OTHER in API/database/editor/filter; inferred family is decoration only. Legacy REST API → BACKEND / API, SEO → SEO / NỘI DUNG, no signal → KHÁC. ID only selects stable within-family variant; same Job retains family/variant/Rough/plate across all four surfaces and future Client surfaces. No classifier duplication.

Explore / Applications / My Work = **RE-FROZEN** after narrow approved row updates. Layout/filter/search remain frozen. Applications PENDING primary is Acid/Ink, not default Vermilion; Client/My Work COMPLETED primary Mint/Strong Green is preserved.

Final accepted evidence: **180/180 focused tests PASS**, production build/diff check PASS, **1440/1024 PASS**, no overflow/clipping, console **0**, keyboard focus PASS, reduced motion preserved. Backend/API/business/filter behavior unchanged. Runtime Client: **5 real jobs, page 1/1**, OPEN/IN_PROGRESS/COMPLETED; Applicants, Job Detail and `/work/new` navigation PASS. Runtime lacks REJECTED/CANCELLED Applications; focused tests verify them without status mutation. Freeze did not rerun tests/build or mutate runtime. Security check found no added secrets, credentials, local screenshots or machine-local paths.

## Client Work C2 — APPROVED / PASS / FROZEN (2026-10-07)

**CLIENT JOB AUTHORING / EDITORIAL WORK ORDER** is frozen for `/work/new` and `/work/:jobId/edit`. Source: `846142d8a3fb5f8a3f59204158c29e534ab7ea56` — `feat(frontend): finalize kinetic client job authoring`, normally pushed to the existing visual branch. Exactly three source files:

```text
frontend/src/JobEditor.tsx
frontend/src/JobEditor.test.tsx
frontend/src/styles.css
```

Overview, Freelancer Explore/Applications/My Work, Client C1, Global Row Identity/State Rail/Progress Color/Category Plate and Stable Job Visual Identity remain frozen/locked. Freeze adds no source changes or backend/API changes.

### CREATE — full work-order contract

Route `/work/new`; payload remains:

```text
title
description
category
skills
budgetUsd
deliveryDueAt
reviewWindowHours
maxRevisions
deliverables
acceptanceCriteria
```

Unchanged validation: title required; explicit valid category; skills max 10, comma parsing, trim, 2–40 characters, case-insensitive duplicate rejection; budget > 0; delivery ≥24 hours ahead; reviewWindowHours integer 24–168; maxRevisions integer 0–2; deliverables 1–10 with title/description required; acceptance criteria 1–20 with description required. Both lists preserve required=true. Visual approval does not authorize business-rule changes.

One-page **EDITORIAL WORK ORDER / BRIEF BUILDER**; no multi-step wizard without a new product decision:

| Section | Editorial marker |
| --- | --- |
| 01 Nội dung công việc | Acid |
| 02 Điều kiện thực hiện | Cobalt |
| 03 Sản phẩm bàn giao | Vermilion |
| 04 Điều kiện nghiệm thu | Mint |

These colors identify authoring sections, not OPEN/IN_PROGRESS/COMPLETED workflow states.

### Live Draft Summary — local truth only

**1440:** work-order editor left, sticky Live Draft Summary right. **1024:** Hero → 01 Nội dung công việc → Draft Preview → 02 Điều kiện thực hiện → 03 Sản phẩm bàn giao → 04 Điều kiện nghiệm thu → Final actions. Keep Preview after Section 01; do not force the 1440 sidebar into 1024.

Preview is explicitly **UNSAVED**, derived only from local draft state. Keep its disclaimer. Allowed: selected category/plate, semantic thumbnail preview, draft title, parsed skill tags, entered budget/deadline, count of non-empty deliverables, at most one actual draft deliverable title and count of non-empty acceptance criteria. Blank editor rows do not count as content; empty summary uses “Chưa có nội dung”.

Forbidden: fake OPEN/status, applicant count, Freelancer, payment state, ranking, recommendation score, completion percentage and saved/ready/completed claims. Summary is not persisted server truth, Job Detail or a workflow/payment state.

Category selection is **explicit**; never infer from title while authoring. Neutral before selection, shared semantic family/Category Plate after selection. Category-only preview does not use legacy title/skills inference. A new unsaved Job has no ID: selected family + deterministic default visual variant **0**, no Math.random or temporary random identity. After actual creation, saved job.id selects the stable approved within-family variant normally; preview is not guaranteed the final exact persisted variant. Existing taxonomy and persisted OTHER decorative fallback remain unchanged.

Skills retain the comma parser, trim/bounds and case-insensitive duplicate validation. Removable visual tokens are presentation only; API still receives `skills: string[]`. No new skills taxonomy/autocomplete.

Deliverables and acceptance criteria remain distinct, **create-only** editorial numbered rows: Cream fields, Ink rules, add/remove, max 10/max 20 and required=true. No card wall or project-builder abstraction.

### EDIT — metadata-only contract

Route `/work/:jobId/edit`; available only for **trusted CLIENT + Job owner + OPEN**. updateJob fields **only**:

```text
title
description
category
skills
```

No controls for budget, deadline, review window, max revisions, deliverables or acceptance criteria. The context panel may display real server thumbnail/category/status/skills/budget/deadline read-only. It does not expand the update contract or become an unsaved create summary.

### Form / action grammar and accepted evidence

Cream controls, Ink borders, readable labels, strong visible focus, editorial section hierarchy. Zero-blur hard shadows only on structural surfaces, never every field. Reuse the approved kinetic toolkit/motion/reduced-motion. Final Ink action band with Acid CTA: Create **Đăng công việc**, Edit **Lưu thay đổi**, secondary **Quay lại**, pending **Đang lưu…**. Existing pending locks and duplicate-submit protection remain unchanged.

Accepted final C2 gate: **50/50 JobEditor focused tests PASS**, production build PASS, `git diff --check` PASS; **Create 1440 PASS / Create 1024 PASS / Edit 1440 PASS**. At 1024, Section 01 precedes Preview; at 1440 the sticky summary shows truthful local content. No horizontal overflow or sticky overlap; console errors **0**, keyboard focus **PASS**, reduced motion **PASS**. Backend/API/previously frozen Client/Freelancer surfaces unchanged. Browser draft used for visual QA: **NOT SUBMITTED**.

Freeze reuses this accepted evidence: no tests/build or runtime smoke rerun, no new validation claims. Security inspection found no added secrets, credentials, .env values, screenshots or machine-local absolute paths.

## Exact next task — NOT_STARTED

**CLIENT WORK C3 → APPLICANTS**, route `/work/:jobId/applications`, status **NOT_STARTED**. Before implementation inspect current ClientApplicants source, Applicant DTO/API, assignment authorization, profile access and current tests. Reuse the approved kinetic toolkit and locked semantic identity under a separately authorized workpack.

### Files to inspect first in the next session

1. `frontend/src/App.tsx` — trusted Client role routes/navigation.
2. `frontend/src/Workflow.tsx`, `frontend/src/Workflow.test.tsx` — current ClientApplicants, eligibility/actions and focused tests.
3. `frontend/src/api.ts` and `frontend/src/api.test.ts` — Applicant reads, assignment API, response adaptation and contracts.
4. `frontend/src/types.ts` and `frontend/src/status.ts` — current Applicant DTO/application/job/contract fields and labels.
5. Current frontend profile source/tests — actual Applicant profile access and supported public fields; inspect before changing UI.
6. `frontend/src/WorkLifecycle.tsx`, `frontend/src/ContractLifecycle.tsx` — existing detailed work destinations; frozen My Work is visual grammar only.
7. `frontend/src/ui/kinetic/` and `frontend/src/ui/job-thumbnails/` — approved reusable visual grammar.
8. `marketplace-backend/docs/JOB_DISCOVERY_CONTRACT.md` — real category/skills authority.
9. `marketplace-backend/src/main/java/com/marketplace/backend/controller/JobController.java` — read-only verification of existing job routes if needed.

Read documentation first in the order in `START_HERE_UI.md`. Do not begin C3 during this freeze/documentation task. Client C1/C2 and Freelancer surfaces remain frozen.
