# START HERE — FREELAX UI VISUAL POLISH

## Current authority — 2026-10-08

Repository: `TronkIshere/FreelaX`. Branch: `feat/ui-visual-polish-20261006`.
Frozen source implementation baseline: `7fc31555b9cd3f50a23872401968ee67c5275f30` (delayed review invitation reconciliation; all earlier frozen visual surfaces preserved).
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
- Current: P06.7 FULL REGRESSION / QA — PASS / FROZEN. Source `7fc31555b9cd3f50a23872401968ee67c5275f30` fixes delayed contract review invitations. Real modern contract-backed lifecycle, scheduler settlement/invitations, both reviews and public reputation are verified. ContractReviews remains the single review read/eligibility owner. Focused tests 99/99 and full frontend tests 793/793 (22 files), production build and diff check PASS. Client/Freelancer 1440/1024 regression PASS. Unexpected JS/page errors 0; unexpected network/runtime failures 0; overflow 0; eight handled missing-TaxRecord 404 resource messages are documented expected network absence. Solana/on-chain is not fully reconciled; off-ramp/tax NOT_STARTED. P06.5A/B/C/D and P06.6 remain frozen. Next: P06.8 — FINAL FREEZE / HANDOFF — NOT_STARTED. Stop after this freeze.
- P06.5C Tax / Chứng từ thuế = PASS / FROZEN. P06.5D Activity = PASS / FROZEN. P06.6 Account / Profile = PASS / FROZEN. Next: P06.8 Final freeze / handoff = NOT_STARTED.

Read in this exact order (paths are repository-relative):

1. `docs/ui/UI_VISUAL_POLISH_HANDOFF_20261006.md`
2. `docs/ui/UI_VISUAL_POLISH_SESSION_LOG_20261006.md`
3. `docs/ui/UI_DEVELOPMENT_MEMORY.md`
4. `docs/ui/UI_POLISH_SPEC.md`
5. `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`
6. `docs/ui/WORKPACK_P06_UI_POLISH.md`
7. `marketplace-backend/docs/JOB_DISCOVERY_CONTRACT.md`
8. `docs/mvp-functional-spec.md`
9. Current frozen Activity source: Activity.tsx, activityPresentation.ts, Polish.test.tsx and Activity-scoped styles.css. Preserve P06.5A/B/C Finance/Tax source-backed semantics and shared frozen grammar. P06.6 Account / Profile is frozen; P06.7 Full regression / QA is next only when separately requested.

Use KINETIC EDITORIAL BRUTALISM and the approved toolkit. Frozen Overview/Explore/Applications/My Work/Client Work C1/C2/C3 demonstrate visual grammar, not generic page templates. Do not reopen for general polish; require a concrete regression. Current frozen detail: P06.5D ACTIVITY VISUAL EMPHASIS PATCH — PASS / FROZEN. Source `c71c554bd73d7e8aff21032aa317e5cab8e4a89c`. Messages over 240 characters clamp to three lines with local original-text disclosure; stronger semantic tags, Acid unread stamps and Ink/Cream mark-read actions. Client/Freelancer 1440/1024 runtime QA, screenshots, focus and console 0 PASS; 146/146 focused tests, production build and diff check PASS. P06.5A/B/C unchanged. Next: P06.6 — ACCOUNT / PROFILE — NOT_STARTED. Stop after this freeze.

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

## 2026-10-08 — P06.7 FULL REGRESSION / QA — FROZEN

This accepted finalization supersedes earlier P06.7 NOT_STARTED and strict-console BLOCKED checkpoints. Source: `7fc31555b9cd3f50a23872401968ee67c5275f30` — `fix(frontend): reconcile delayed contract review invitations`. Only ContractReviews.tsx and ReviewUI.test.tsx changed; backend/API contracts and frozen P06.5A/B/C/D, P06.6 and earlier visual surfaces are preserved.

**REAL CONTRACT-BACKED E2E = PASS.** Client creates Job → Freelancer applies → Client assigns → WorkContract/Milestone created → funding SUCCEEDED → contract ACTIVE → contract-backed submission → Client approval → RELEASE_PENDING → scheduler settlement → moneyStatus SUCCEEDED → milestone RELEASED → contract COMPLETED → Job COMPLETED → scheduler-created two-sided invitations → Client review → Freelancer review → both published → public reputation/profile updated. Legacy `contract:null` Jobs were not used as E2E proof. User-facing mutations used the real UI/API. No direct database INSERT/UPDATE or manual scheduler invocation manufactured the lifecycle; SELECT-only verification supplied internal fields omitted from public DTOs.

The real runtime defect was invitations arriving after ContractReviews mounted without another review read. The fix keeps ContractReviews as the single read owner and the existing server-authoritative eligibility/callout callback. Read-only reconciliation every 30 seconds while awaiting own invitation or publication skips hidden pages, in-flight reads and mutation locks. Focus reconciles server truth; time never grants eligibility. Polling stops once an unsubmitted own invitation is present or own review is published, and timers/listeners/generation are cleaned up on unmount. Two new regression tests failed before the patch and pass after it, covering delayed invitations, focus, unchanged settlement eligibility and cleanup.

The original Job's reviews were already published, so the workpack's regression exception used one additional legitimate Job: `5b020abd-1e92-4f5c-af8d-2429ce8a78f1`. Its completed review section was present at 17:52:47Z; scheduler invitations appeared at 17:53:36Z; the already-open Client page discovered the opportunity at 17:53:47Z without hard reload. Both real review CTAs anchor to `#contract-reviews`; Client/Freelancer 1024 callout/form checks preceded submission. Both reviews were server-published after the second submission; both public profiles show actual published comments and reputation. Final server reputation: two published reviews, average 5/5 for each QA participant. Ratings/comments are synthetic local QA evidence, not claims about a real person's performance.

**Expected network absence policy — exact endpoint only:** `GET /api/v1/marketplace/tax-records/jobs/{jobId}` returning HTTP 404 / frontend ApiError code 4010 is non-blocking only when the TaxRecord genuinely does not exist, `missingTax(...)` handles it, tax=null and taxError='' remain truthful, the UI says `Chưa có chứng từ`, no JS/page exception or 5xx/CORS/runtime failure occurs, and independent financial state proves tax is not complete. Do not extend this exception to arbitrary 404s or alter frozen Finance/Tax/backend semantics to hide browser logging.

| Final gate | Accepted evidence |
| --- | --- |
| Unexpected JS/page errors | 0 |
| Unexpected network/runtime failures | 0 |
| Expected handled missing-TaxRecord resource messages | 8 HTTP 404 responses |
| Horizontal overflow | 0 |
| Runtime gate | PASS WITH DOCUMENTED EXPECTED NETWORK ABSENCE |
| Desktop regression | 46 page/role/width checks; Client/Freelancer 1440 and 1024 PASS, visible keyboard focus |
| Focused verification | `npm test -- src/ReviewUI.test.tsx src/ContractLifecycle.test.tsx`: 99/99 PASS |
| Full regression | `npm test`: 793/793 PASS, 22 files; all prior tests retained |
| Production build | PASS on this exact source state; reused rather than rebuilt during finalization; known bundle-size warning non-blocking |
| Diff check | PASS |

Activity exposes the real assignment, funding, submission, approval, release, invitation and publication events. Finance preserves separate funding/release/downstream truth. Existing ACCEPTED certificate PDF/XML authenticated Blob downloads passed for both roles; no binary was parsed as JSON.

**Independent downstream truth:** Solana RPC was unavailable during final regression. On-chain remains UNKNOWN / ON_RAMP_AWAITING_RECONCILIATION, off-ramp NOT_STARTED, tax NOT_STARTED. Primary settlement/review success does not claim bank transfer, Solana completion, tax export or certificate acceptance for the new QA Jobs. No downstream success was fabricated.

The compact real evidence remains two 1440 screenshots outside Git: Client completed Job review callout and Freelancer public profile after publication. No additional screenshots, secrets, credentials or machine-local paths are committed. Mobile optimization is deferred; current MVP delivery target is desktop/laptop at 1440/1024.

**P06.7 FULL REGRESSION / QA — FROZEN. NEXT: P06.8 — FINAL FREEZE / HANDOFF — NOT_STARTED. STOP.**
