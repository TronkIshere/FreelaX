# START HERE — FREELAX UI VISUAL POLISH

## Current authority — 2026-10-07

Repository: `TronkIshere/FreelaX`. Branch: `feat/ui-visual-polish-20261006`.
Frozen source implementation: `846142d8a3fb5f8a3f59204158c29e534ab7ea56` (Client Job Authoring C2; includes the earlier frozen C1/global baseline `dc440ec`).
A later docs-only commit may be branch HEAD; it does not change this source baseline.

- Overview = FROZEN / APPROVED (both Client and Freelancer).
- Freelancer Explore / Applications / My Work = PASS / RE-FROZEN after the narrow cross-surface row-identity override.
- Client Work C1 (`/work`) = PASS / FROZEN.
- Global Row Identity / Global State Rail / Global Category Plate = LOCKED.
- Strong Success Green `#39B96E` = approved functional success/completion color; Fresh Mint remains a light success surface.
- Stable Job Visual Identity = LOCKED.
- Client Work C2 — Job Authoring (`/work/new`, `/work/:jobId/edit`) = PASS / FROZEN.
- Client Work C3 — Applicants (`/work/:jobId/applications`) = NEXT / NOT_STARTED.

Read in this exact order (paths are repository-relative):

1. `docs/ui/UI_VISUAL_POLISH_HANDOFF_20261006.md`
2. `docs/ui/UI_VISUAL_POLISH_SESSION_LOG_20261006.md`
3. `docs/ui/UI_DEVELOPMENT_MEMORY.md`
4. `docs/ui/UI_POLISH_SPEC.md`
5. `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`
6. `docs/ui/WORKPACK_P06_UI_POLISH.md`
7. `marketplace-backend/docs/JOB_DISCOVERY_CONTRACT.md`
8. `docs/mvp-functional-spec.md`
9. Current source: inspect `ClientApplicants` in `frontend/src/Workflow.tsx`, the Applicant DTO/API, assignment authorization, profile access and current tests before C3; reuse the shared kinetic toolkit and locked row identity.

Use KINETIC EDITORIAL BRUTALISM and the approved toolkit. Frozen Overview/Explore/Applications/My Work/Client Work C1/C2 demonstrate visual grammar, not generic page templates. Do not reopen for general polish; require a concrete regression. Exact next visual area: CLIENT WORK C3 — Applicants, `/work/:jobId/applications`, NOT_STARTED. Inspect its real contracts/permissions first; do not begin during this freeze task.

Client Work C2 is an approved one-page EDITORIAL WORK ORDER / BRIEF BUILDER. **CREATE** sends `title`, `description`, `category`, `skills`, `budgetUsd`, `deliveryDueAt`, `reviewWindowHours`, `maxRevisions`, `deliverables`, `acceptanceCriteria`, with existing validation unchanged. **EDIT** is metadata-only: `title`, `description`, `category`, `skills`; trusted CLIENT + owner + OPEN only. No immutable-field controls. Four authoring sections: 01 Nội dung công việc (Acid), 02 Điều kiện thực hiện (Cobalt), 03 Sản phẩm bàn giao (Vermilion), 04 Điều kiện nghiệm thu (Mint). These are section markers, not workflow colors.

1440: editor left, sticky Live Draft Summary right. 1024: Hero → 01 → Preview → 02 → 03 → 04 → Actions. Preview is **UNSAVED local draft only**, with its disclaimer: selected category/title/skills/budget/deadline, non-empty deliverable/criteria counts and at most one actual deliverable title. Empty rows are not content. No invented status, participants, payment, ranking, score, saved/ready/completed claims or percentage. Category is explicit, never title-inferred during authoring; neutral before selection, selected family/default variant 0 before job ID exists, stable ID variant after saving (not guaranteed identical to preview). Persisted legacy OTHER fallback is unchanged. Full locks are in the handoff/spec.

Accepted C2 validation: 50/50 JobEditor tests PASS, production build/diff check PASS; Create 1440/1024 and Edit 1440 PASS, no overflow/sticky overlap, console 0, keyboard focus/reduced motion PASS. Backend/API/previously frozen surfaces unchanged; visual-QA draft NOT SUBMITTED. Freeze reuses accepted evidence without tests/build rerun.

Final C1/global accepted gate: 180/180 focused tests PASS; production build and diff check PASS; 1440/1024 PASS without overflow; console errors 0; keyboard focus PASS; reduced motion preserved. No tests/build rerun during freeze. Backend/API/business/filter behavior unchanged. Secondary rows stay Cream with saturated 6px/5px state rails. Thumbnail = semantic Job type; plate = explicit resolved visual family; rail/status = progress. All three channels remain independent. Thumbnail and plate share `jobFamily`; OTHER may resolve decoratively from skills/title without changing stored category. PENDING primary Applications is Acid/Ink, never default Vermilion. See the visual spec for exact colors and labels.

My Work accepted validation: 62/62 focused tests PASS, build/diff PASS, 1440/1024 PASS, console errors 0. Stable thumbnail correction: 131/131 focused tests PASS, thumbnail recheck 36/36 PASS, build/diff PASS, cross-screen identity PASS. Runtime observed 4 My Work jobs with IN_PROGRESS/COMPLETED; legacy category/skills remain sparse. These are observations, not guaranteed seed data. No tests/build were rerun during freeze.

Locked thumbnail authority: meaningful stored category → legacy skills → legacy title → Generic Development. OTHER is unspecified for decoration, so try skills/title without changing stored category. Job ID selects only a stable variant within family (three approved variants, missing ID → 0); Rough marks use stable family + job identity. Reuse the shared JobThumbnail across Explore, Applications, My Work and future Client Work; no page-specific thumbnails, database thumbnail ID or upload.

Applications final accepted validation: 35/35 focused tests PASS, production build PASS, `git diff --check` PASS, 1440 PASS, 1024 PASS, no overflow, console errors 0. No API/backend changes. Only real PENDING, ACCEPTED, REJECTED and CANCELLED statuses; no fake timeline/status/counts. Semantic thumbnails reuse Explore, and `totalElements` is used only for truthful server totals. These accepted gates were not rerun during freeze/commit.

Commit `302ae06` is the completed, explicitly approved category/job-skills backend exception for truthful discovery UI. It gives no authority for unrelated backend expansion.

Explore live category/skill/combined/exclusion/Clear smoke and 1440/1024 visual gates passed. Older environment-blocked records remain historical and do not describe the active Explore freeze. No new runtime validation is claimed by this documentation pass.

Mobile optimization is deferred. Current MVP delivery target is desktop/laptop, validated at 1440px and 1024px. Preserve existing responsive CSS.

Current source/API is authoritative where older notes conflict. Do not treat future product proposals in the MVP spec as already implemented. Never store secrets, credentials, local screenshots or machine-local paths in handoff material.
