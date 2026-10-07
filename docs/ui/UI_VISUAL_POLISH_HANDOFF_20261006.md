# FreelaX visual polish handoff — 2026-10-06

## Repository, branch and source authority

- Repository: `TronkIshere/FreelaX`.
- Active branch: `feat/ui-visual-polish-20261006`.
- Integrated MVP ancestor: `35ba34e9b68be67b9405d3407159a2fde010911c`.
- Frozen last product/source implementation commit: `a0e16f9090efae89b3431be511da2c418e21ac68` (P06.6 Account/Profile; P06.5A/B/C/D and earlier locks preserved).

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
| `45c54c5` | Client Applicants C3: final corrected candidate decision desk |
| `96cafbe` | P06.5A role-based Finance list + editorial folder tabs |
| `9c76ffb` | P06.5B Client Finance Detail + current-vs-processing guard; Client 1440 |
| `837bcf0` | P06.5B Freelancer Finance Detail 1440; shared grammar and role-correct copy |
| `304dd19` | P06.5C Tax Evidence Ledger / Certificate Case File |
| `1b2ee56` | P06.5C visual emphasis reinforcement; business semantics unchanged |
| `926c8fc` | P06.5D editorial Marketplace notification ledger + narrow unread CSS inheritance guard |
| `c71c554` | P06.5D visual emphasis: original-text disclosure, stronger event/read/action hierarchy |
| `a0e16f9` | P06.6 identity passport, Marketplace profile and portfolio/partner dossiers |

A later documentation commit may be branch HEAD. It does not replace `a0e16f9` as the latest frozen source implementation baseline; `9c76ffb` remains the Client 1440 source authority. Read the current HEAD with Git; do not mistake a docs-only HEAD for new product implementation. Current source wins over older conflicting documentation. Explicit Product Owner instructions remain authoritative.

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
| Client Work C3 — Applicants (`/work/:jobId/applications`) | PASS / FROZEN |
| P06.5A Finance list | PASS / FROZEN; `96cafbe` |
| P06.5B Finance detail / Money Evidence Spine | FULLY FROZEN; Client/Freelancer 1440/1024 PASS; Client source `9c76ffb`, shared source `837bcf0`; zero responsive source changes |
| P06.5C Tax / Chứng từ thuế | PASS / FROZEN; `304dd19` + visual patch `1b2ee56` |
| P06.5D Activity / Editorial Event Ledger | PASS / FROZEN; `926c8fc`; real Client/Freelancer 1440/1024 QA |
| P06.6 Account / Profile | PASS / FROZEN |
| P06.7 Full regression / QA | NEXT / NOT_STARTED |

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

Frozen Overview, Explore, Applications, My Work, Client C1/C2/C3 are references for visual grammar, **not reusable page templates**. New page composition must follow its own real task/data. Do not reopen frozen surfaces for general polish; only a concrete regression justifies a fix.

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

## Client Work C3 — APPROVED / PASS / FROZEN (2026-10-07)

Route `/work/:jobId/applications`; concept **CANDIDATE DECISION DESK / EDITORIAL CANDIDATE DOSSIER**. Source `45c54c5a753c1cd4438dd3c968fe7ccec20a0491` — `feat(frontend): finalize kinetic client applicants`, normally pushed. Files: `frontend/src/Workflow.tsx`, `frontend/src/Workflow.test.tsx`, `frontend/src/styles.css`. The final corrected visual replaces the rejected plain Cream + lines treatment. Previous frozen surfaces and shared identity/progress/category systems remain locked.

**Job Context Banner:** dominant state-aware colored editorial surface, Ink 2px border/zero-blur hard shadow, enlarged shared semantic thumbnail/Category Plate, real title/job.skills/budget/deadline/status. OPEN → Acid; AWAITING_PAYMENT → Acid + Ink; IN_PROGRESS → Cobalt; SUBMITTED_FOR_REVIEW → Acid/Ink; REVISION_REQUESTED → Vermilion/Cream; COMPLETED → Fresh Mint surface + Strong Green marker; CANCELLED → Ink/Cream. Do not revert to a Cream strip or hardcode Acid for every state.

**Candidate dossier:** equal Cream panels with hard Ink border/restrained zero-blur shadow, application-state rail, display-order index, enlarged deterministic initials tile with cut-paper/tape/rays, public evidence and separated status/date/action zone. Server order stays unchanged; no featured first applicant, ranking or recommendation. PENDING → Acid `#F5D12F`; ACCEPTED → Strong Green `#39B96E`; REJECTED → Vermilion `#F15A3D`; CANCELLED → Ink `#17212B`. Rails 6px at 1440 / 5px at 1024, visible status text. Application state is not candidate quality.

**Public evidence:** Application API owns status/date/eligibility; Public Profile is supplemental. Unique Freelancer IDs, parallel isolated reads, matching profile.userId and FREELANCER required. A failed profile preserves its Application, fallback ID/status/date and public profile link. No portfolio/review-list fan-out. Names/headline/country/availability/skills and at most three reputation facts come only from returned fields. Job skills never become candidate skills; private email never renders. Null rating/on-time rate never becomes zero; actual returned zero counts are truthful.

**Sparse-data rule:** visual density must NOT be achieved by fabricated product data. Use structure, spacing, typography, borders, color, cut-paper identity and kinetic details. Runtime had one PENDING applicant without headline/skills; omit absent fields rather than invent city, experience, bio, response speed, expertise, rating or match score. No search/sort/filter, fake counts or AI ranking controls.

**Selection:** trusted CLIENT + owner + OPEN Job + PENDING Application only. First click opens local Ink confirmation titled “Xác nhận lựa chọn”; never assigns or marks ACCEPTED. “Xác nhận chọn” / “Quay lại”, pending “Đang phân công…”, duplicate lock retained. Copy explains selected acceptance after success, remaining pending applications closing/rejecting, Job proceeding to funding and work starting only after required funding. After api.assign, refresh/reconcile server truth; no manufactured statuses. Non-OPEN roster is read-only with next Job destination. Funding/wallet/payout/stablecoin mutations remain outside C3.

Accepted corrected gate: **66/66 focused tests PASS**, production build/diff check PASS; **1440 PASS / 1024 PASS / Confirmation 1440 PASS**, no overflow/clipped controls, console **0 errors**, focus-visible PASS, reduced motion preserved. Backend/API contracts unchanged; assignment during screenshot QA **NOT EXECUTED**. Rich profiles, multiple applicants and assignment outcomes were tested where absent from runtime. No tests/build rerun during freeze; no further source changes. Security inspection found no added secrets, credentials, .env content, screenshots or machine-local paths.

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

## Next phase — NOT_STARTED

P06.5B is fully frozen for Client/Freelancer at 1440/1024. Next: P06.5C — TAX / CHỨNG TỪ THUẾ — NOT_STARTED. Tax/Activity remain unstarted. Stop after this freeze.

## P06.5B CLIENT FINANCE DETAIL — FROZEN (2026-10-07)

Source: `9c76ffb6c7a03db7a876ed8d871043b14e13d6c2` — `feat(frontend): finalize client finance evidence spine`. Route `/finance?jobId=...`. Human-approved Client 1440 composition only: single-Job financial case file, dominant Acid statement with stable Job identity, five-stage vertical Money Evidence Spine, semantic connectors, compact right-side case summary and secondary/collapsed technical evidence. Existing P06.5A list/folder tabs and earlier frozen surfaces are unchanged. Freelancer 1440 is frozen by the later role freeze below; Client/Freelancer 1024 are FROZEN by the final responsive closure below; P06.5C/Tax/Activity are not started by this freeze.

### Financial truth and attention semantics — LOCKED

- Legacy stages: Thanh toán Client → Quyết toán USDC → Rút on-chain → Chi trả VND → Chứng từ thuế. Contract records retain independent Funding / Release or Refund / on-chain / VND / tax semantics; no funding-to-release inference.
- COMPLETED follows the stage's own confirmed source evidence. Mint/check/solid completed connector. Successful tax export alone does not establish certificate ACCEPTED.
- CURRENT is frontend attention order, not a backend enum or financial status: first applicable ERROR, otherwise first applicable unfinished stage. CURRENT != PROCESSING. It never changes tone/status/completion/error/finality; status wording and icons follow raw/source truth plus stage tone, not `current === true`.
- ERROR uses real failure/read-error evidence and `Cần kiểm tra`. Pending uses `Chờ bằng chứng` as a frontend presentation label; raw NOT_STARTED supports `Chưa bắt đầu`. A pending current stage remains pending, never automatically processing or error.
- UPCOMING is a later pending stage; preceding failure does not propagate ERROR into it. Cream/subdued treatment and dashed connectors do not suggest processing or an ETA. All stages resolved means no invented current step. Non-applicable refund stages remain explicitly non-applicable.
- Missing record != read failure: NOT_ATTEMPTED plus no TaxRecord supports `Chưa lập chứng từ` / `Chưa có chứng từ`; a failed tax read uses `Chưa đọc được chứng từ`. Existing pending/error records are not absent records.
- No optimistic/local-time/animation advancement. Server refresh/reconciliation is authoritative; motion is only brief feedback after fetched state changes. Reduced motion renders immediately.
- Simulation/localnet/devnet markers remain source-truthful. The observed runtime is simulation/localnet, not DEVNET or production bank finality. Money, masked bank destination, timestamps and references come only from returned evidence; omit unavailable facts.

### Final validation and real runtime observations

49/49 focused Finance tests PASS (44 previous + 5 guard cases); production build PASS; known >500 kB bundle warning is non-blocking; diff check PASS. Clean Client 1440 screenshot captured outside the repository, no accidental focus outline. Console errors 0 in the final QA window; no horizontal overflow; keyboard focus verified and CSS preserved. No backend/API/database/proxy/port changes or runtime data mutations.

- SEO job `af2a0ed3-d2df-4029-8649-d01c8cbdc67a`: CAPTURED; CONFIRMED/CONFIRMED; withdrawal CONFIRMED; off-ramp COMPLETED; existing TaxRecord EXPORT_FAILED. Stages 01–04 completed, 05 ERROR + CURRENT, never a processing claim.
- Previously verified P04 job `0f8dcb6c-a2e4-4f85-8123-03fa7ce4467f`: CAPTURED; clientPaymentStatus FAILED; withdrawal/off-ramp NOT_STARTED; tax NOT_ATTEMPTED/no TaxRecord. Stage 02 ERROR + CURRENT; 03–05 pending/upcoming, not propagated errors. This final pass re-opened only the SEO case.

Regression guard covers current payment/tax errors, current NOT_STARTED evidence, upcoming stages after an earlier failure, and unchanged financial tone/status when only attention selection changes. No secrets, credentials, screenshots or machine-local paths are included in the commits.

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
