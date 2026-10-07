# START HERE — FREELAX UI VISUAL POLISH

## Current authority — 2026-10-07

Repository: `TronkIshere/FreelaX`. Branch: `feat/ui-visual-polish-20261006`.
Frozen source implementation: `1b2ee56ac9c202994747d60739eee9e6c84f7c49` (P06.5C Tax visual emphasis; P06.5A/P06.5B and earlier frozen surfaces preserved).
A later docs-only commit may be branch HEAD; it does not change this source baseline.

- Overview = FROZEN / APPROVED (both Client and Freelancer).
- Freelancer Explore / Applications / My Work = PASS / RE-FROZEN after the narrow cross-surface row-identity override.
- Client Work C1 (`/work`) = PASS / FROZEN.
- Global Row Identity / Global State Rail / Global Category Plate = LOCKED.
- Strong Success Green `#39B96E` = approved functional success/completion color; Fresh Mint remains a light success surface.
- Stable Job Visual Identity = LOCKED.
- Client Work C2 — Job Authoring (`/work/new`, `/work/:jobId/edit`) = PASS / FROZEN.
- Client Work C3 — Applicants (`/work/:jobId/applications`) = PASS / FROZEN.
- P06.5A Finance list + editorial folder tabs = PASS / FROZEN.
- Current: P06.5C TAX / CHỨNG TỪ THUẾ — PASS / FROZEN. Source `1b2ee56ac9c202994747d60739eee9e6c84f7c49`. Tax Evidence Ledger and Certificate Case File use server TaxRecord truth, unchanged payout/action gates and authenticated Marketplace blobs. Client/Freelancer 1440 and Freelancer 1024 runtime layout checks PASS. P06.5A/P06.5B remain FROZEN and unchanged. Next: P06.5D — ACTIVITY / EDITORIAL EVENT LEDGER — NOT_STARTED. Stop after this freeze.
- P06.5C Tax / Chứng từ thuế = PASS / FROZEN. P06.5D Activity = NOT_STARTED.

Read in this exact order (paths are repository-relative):

1. `docs/ui/UI_VISUAL_POLISH_HANDOFF_20261006.md`
2. `docs/ui/UI_VISUAL_POLISH_SESSION_LOG_20261006.md`
3. `docs/ui/UI_DEVELOPMENT_MEMORY.md`
4. `docs/ui/UI_POLISH_SPEC.md`
5. `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`
6. `docs/ui/WORKPACK_P06_UI_POLISH.md`
7. `marketplace-backend/docs/JOB_DISCOVERY_CONTRACT.md`
8. `docs/mvp-functional-spec.md`
9. Current source: Finance.tsx, Finance.test.tsx, taxPresentation.ts and financeSpine.ts; preserve the shared frozen P06.5B Client/Freelancer 1440/1024 grammar and its source-backed attention/status separation.

Use KINETIC EDITORIAL BRUTALISM and the approved toolkit. Frozen Overview/Explore/Applications/My Work/Client Work C1/C2/C3 demonstrate visual grammar, not generic page templates. Do not reopen for general polish; require a concrete regression. Current frozen detail: P06.5C TAX / CHỨNG TỪ THUẾ — PASS / FROZEN. Source `1b2ee56ac9c202994747d60739eee9e6c84f7c49`. Tax Evidence Ledger and Certificate Case File use server TaxRecord truth, unchanged payout/action gates and authenticated Marketplace blobs. Client/Freelancer 1440 and Freelancer 1024 runtime layout checks PASS. P06.5A/P06.5B remain FROZEN and unchanged. Next: P06.5D — ACTIVITY / EDITORIAL EVENT LEDGER — NOT_STARTED. Stop after this freeze.

P06.5A lock: Client “Thanh toán theo công việc.” / Freelancer “Thu nhập theo công việc.” at `/finance`; four colored blocks, Cream ledger/header tint, shared stable Job identity, semantic state markers, light Xem chi tiết, Acid active folder tabs and one process panel. Hồ sơ trên trang is only the real page count; Funding/Release/Hoàn tiền are workflow guidance, never invented money totals. Show returned simulation markers. 1024 blocks stack, rows reflow and tabs stay horizontal. Full locks and accepted validation are in the handoff/spec.

Client Applicants C3 is the approved CANDIDATE DECISION DESK / EDITORIAL CANDIDATE DOSSIER. Dominant state-aware Job banner (OPEN Acid; waiting/review Acid + Ink; IN_PROGRESS Cobalt; revision Vermilion; COMPLETED Mint + Strong Green; CANCELLED Ink/Cream), enlarged shared semantic artwork/plate, equal Cream applicant dossiers, deterministic identity tiles, 6px/5px application rails and separate status/date/action zones. Application API owns status/date/eligibility; matched FREELANCER public profiles supply supplemental evidence through unique parallel isolated reads. No private email, ranking, inferred candidate skills or fake fields. Sparse profiles stay sparse: structure and kinetic details supply density, not fabricated data; real zero counts remain, null evidence is omitted.

C3 selection requires trusted CLIENT + owner + OPEN + PENDING. First click opens only local Ink “Xác nhận lựa chọn”; copy explains remaining pending applications close, funding precedes work. Duplicate lock/server refresh remain; no optimistic ACCEPTED/REJECTED. Funding/wallet/payout controls remain outside C3. Accepted corrected gate: 66/66 focused tests, build/diff PASS; 1440/1024 and Confirmation 1440 PASS, console 0, focus PASS, reduced motion preserved. Backend/API unchanged; assignment during screenshot QA NOT EXECUTED. No tests/build rerun during freeze.

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
