# FreelaX visual polish handoff — 2026-10-06

## Repository, branch and source authority

- Repository: `TronkIshere/FreelaX`.
- Active branch: `feat/ui-visual-polish-20261006`.
- Integrated MVP ancestor: `35ba34e9b68be67b9405d3407159a2fde010911c`.
- Frozen last product/source implementation commit: `2f5f0e4b0667cf9fefbc3f7897fe63a904fa12bc`.

Accepted source chain, oldest first:

| Commit | Accepted change |
| --- | --- |
| `a47c865` | Visual hierarchy and Overview shell |
| `01c7e55` | Final kinetic editorial Overview |
| `302ae06` | Real job category and skill discovery filters |
| `f42e44a` | Final kinetic Freelancer Explore |
| `2f5f0e4` | Final kinetic Freelancer Applications |

A later documentation commit may be branch HEAD. It does not replace `2f5f0e4` as the frozen source implementation baseline. Read the current HEAD with Git; do not mistake a docs-only HEAD for new product implementation. Current source wins over older conflicting documentation. Explicit Product Owner instructions remain authoritative.

## Current UI progress matrix

Statuses below refer to this visual stream, not to absence of existing functional pages.

| Surface | Current visual status |
| --- | --- |
| Auth | Existing MVP/Auth poster direction; not reopened here |
| Global shell / navigation | APPROVED |
| Client Overview | FROZEN / APPROVED |
| Freelancer Overview | FROZEN / APPROVED |
| Freelancer → Công việc → Khám phá | FROZEN / APPROVED |
| Job discovery data foundation | FROZEN / IMPLEMENTED |
| Freelancer → Công việc → Ứng tuyển | PASS / FROZEN |
| Freelancer → Công việc → Công việc của tôi | NEXT / NOT_STARTED in this visual stream |
| Client Work surfaces | Not yet visually polished in this stream |
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

Non-negotiable grammar:

- Cream main headline, value and supporting typography directly on dominant Vermilion. Ink text only inside small contrasting labels/buttons there. Ink CTA uses Cream text.
- 1.5–2px Ink borders; hard offset shadows with zero blur. No soft/translucent depth.
- Cut-paper layers, tape/tab, Rough.js arrows, underline and bursts; deliberate asymmetry and modest radius are allowed.
- Deterministic decorative output. Every decorative element connects to content, an action or illustration.
- Micro-motion only; `prefers-reduced-motion` is mandatory. Preserve semantic controls and visible keyboard focus.
- Keep important functional desktop text readable through weight, color, spacing and placement, not miniature type. Approximately 14px is the functional floor for this approved stream; retain the exact Overview scale below.

Rejected: generic SaaS, pastel SaaS cards, soft shadows, glass, blur, glow, gradient dashboard surfaces, pill-heavy UI, bento dashboard language, generic crypto dashboards, random sticker scatter, random job-ID-only semantic thumbnails, and stock imagery as product identity.

Frozen Overview, Explore and Applications are references for visual grammar, **not reusable page templates**. New page composition must follow its own real task/data. Do not reopen frozen surfaces for general polish; only a concrete regression justifies a fix.

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
| OTHER | Generic Development |

Authority: real stored category → legacy skills fallback → legacy title fallback → Generic Development. A recognized stored `OTHER` always means Generic Development. Fallback applies only when there is no recognized persisted category; the legacy source adapter also recognizes category/type strings before skills. Job ID never chooses semantic family.

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

Approved APPLICATION STATUS / EDITORIAL TRACKER: left status rail, kinetic heading with underline/rays, first Vermilion application record, compact editorial ledger and real pagination. Final micro-polish removes the redundant result-header filter label, locks lower-row descriptions/actions at 16px and facts/status/budget at 15px, and groups first-card status/budget/action without moving thumbnail/main copy. Overview and Explore remain unchanged and FROZEN.

Final accepted validation: 35/35 focused tests PASS; production build PASS; `git diff --check` PASS; 1440 PASS; 1024 PASS; no horizontal overflow; console errors 0. These are prior accepted gates, not rerun during the freeze/commit task. No API/backend changes.

Semantic thumbnails reuse Explore's category-first taxonomy. `totalElements` supplies only the truthful server total for the active query, never invented per-status counts. No fake timeline, statuses, counts or mutation controls were introduced.

Current implementation is `MyApplications` in `frontend/src/Workflow.tsx`. It calls `MarketplaceApi.myApplications(page, status, size)` in `frontend/src/api.ts`, using `GET /api/v1/marketplace/jobs/applications/me` with page/size and optional status. Server pagination is adapted by the existing API stack.

Actual application statuses: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`; labels live in `frontend/src/status.ts`. Types `MyApplication` and `JobApplicationStatus` live in `frontend/src/types.ts`. Current rows show application and job status, real created/updated timestamps, Client name and budget. Job detail links are shown for ACCEPTED applications or OPEN jobs; this list currently offers status filters, pagination and read retry, not invented mutation controls. Backend remains permission authority.

Do not invent Client viewed, selection probability, fake response deadline, interview stage, response countdown or fake activity timeline. Timeline/status visuals must use server facts.

## Exact next task — NOT_STARTED

**Freelancer → Công việc → Công việc của tôi**, route `/work/mine`. Status: **NOT_STARTED** in this visual stream. Reuse the approved kinetic toolkit and semantic thumbnails; inspect current `MyWork` in `frontend/src/WorkLifecycle.tsx` and its real contract before a separately authorized implementation.

### Files to inspect first in the next session

1. `frontend/src/App.tsx` — trusted role routes/subnav and `/work/mine`.
2. `frontend/src/WorkLifecycle.tsx` — actual `MyWork` component and existing workflow destinations.
3. `frontend/src/WorkLifecycle.test.tsx` — existing MyWork/workflow tests.
4. `frontend/src/api.ts` and `frontend/src/api.test.ts` — existing MyWork calls, response adaptation and contracts.
5. `frontend/src/types.ts` and `frontend/src/status.ts` — application/job fields and labels.
6. `frontend/src/Jobs.tsx`, `frontend/src/Jobs.test.tsx`, `frontend/src/jobDiscovery.ts` — frozen Explore reference and real discovery controls.
7. `frontend/src/ui/kinetic/` and `frontend/src/ui/job-thumbnails/` — approved reusable visual grammar.
8. `marketplace-backend/docs/JOB_DISCOVERY_CONTRACT.md` — real category/skills authority.
9. `marketplace-backend/src/main/java/com/marketplace/backend/controller/JobController.java` — read-only verification of existing job routes if needed.

Read documentation first in the order in `START_HERE_UI.md`. Do not begin My Work during this freeze/documentation task.
