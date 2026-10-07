# FreelaX visual polish session decision / QA log — 2026-10-06

This is the chronological decision record for the accepted visual stream. The current continuation authority is [UI_VISUAL_POLISH_HANDOFF_20261006.md](UI_VISUAL_POLISH_HANDOFF_20261006.md). Entries summarize accepted session evidence; no tests, builds or runtime mutations were repeated for this docs-only handoff.

Current latest freeze: source `a0e16f9090efae89b3431be511da2c418e21ac68`; P06.6 Account/Profile. See the final dated entry below. P06.7 remains NOT_STARTED.

## 1. Integrated MVP and visual branch

Integrated MVP ancestor: `35ba34e9b68be67b9405d3407159a2fde010911c` (`merge: complete FreelaX MVP integration`). The visual stream created/used `feat/ui-visual-polish-20261006`; existing integrated business behavior was retained.

`a47c865` — `feat(frontend): establish visual hierarchy and overview shell` established the initial frontend hierarchy/shell work. This was a starting point, not the final visual approval.

## 2. Human correction and direction lock

Human review rejected the early dark Overview action card and generic SaaS/pastel styling. The visual direction became KINETIC EDITORIAL BRUTALISM: Cream paper, Ink masthead, Acid active nav, confident flat color and editorial composition.

The flat Vermilion slab was rejected: a dominant surface needed Cream main typography, connected browser/cut-paper artwork, tape, rays and hard depth. Random unrelated stickers were rejected; decoration must belong to content/actions. Soft shadows and generic metric-card walls were rejected.

Lucide was adopted for functional icons, Rough.js/CSS/SVG for editorial marks, Motion for short interactions with reduced-motion handling. No looping decoration or fake trends were accepted.

## 3. Secondary typography correction and Overview freeze

Tiny functional text remained a human-review issue after subtle scale changes. Successive corrections increased navigation, metadata/actions and metric labels without redesigning the approved composition.

Final supporting scale: hero 18px/1.5/500; account-count label 16px/1.4/500; status caption 16px/1.4/600; footer brand 15px/1.4/500.

Human screenshot approval froze Client/Freelancer Overview and the global visual foundation. Accepted gate: 61/61 focused tests PASS, production build PASS, diff check PASS, console errors 0, desktop 1440/1024 PASS.

`01c7e5517fc25b13e2b6ae9248b8e5f09703474e` — `feat(frontend): finalize kinetic editorial overview` was normally pushed. Overview remains FROZEN; no further taste-polish is authorized.

## 4. Explore concept and semantic thumbnails

Next page was Freelancer → Công việc → Khám phá. Approved direction used a search area, subordinate work subnav, filter sidebar and editorial result board. The first result can carry Vermilion emphasis without claiming recommendation authority.

Semantic job thumbnails were chosen instead of random job-ID-only shapes: Web, Backend/API, SEO/Content, Mobile, UI/UX, E-commerce, Data/Analytics, Branding and Generic Development. At this historical checkpoint, real category was primary authority and stored OTHER stayed Generic Development. The approved decorative OTHER fallback in section 10 supersedes that earlier rule without modifying stored category.

## 5. Real category/skills API gap and approved foundation

Discovery initially lacked real stored category and job skills. Human approval allowed a narrow backend/product-data exception to add truthful category/skill authoring, response fields and server filtering. No title/profile backfill was accepted; legacy rows use OTHER and empty skills.

`302ae0621b3b37a6b12cc65d32bdc05730a72aeb` — `feat(marketplace): add job category and skill discovery filters`. The exception is COMPLETE; it does not permit unrelated backend expansion.

Remote/Hybrid/Onsite controls were deliberately excluded because no real work-mode field exists. Skill suggestions were not fabricated. Filtering composes on the server and preserves real pagination; skill matching is ANY, exact after trim and case-insensitive.

## 6. Explore runtime and final visual correction

Earlier focused frontend evidence: 115/115 PASS across Jobs/API/taxonomy/kinetic. Runtime category, budget, application, keyword, combined and Clear filters passed. An initial sparse runtime row (OTHER, no skills) could not alone demonstrate rich thumbnail/token visuals.

The local “Landing page redesign” job was then edited through the real Client API to WEB_FRONTEND with HTML, CSS and Responsive Design. This is runtime-only evidence, not fresh-database seed data or a production fixture.

Final human correction reviewed “phù hợp.”, two ray clusters, filter treatment, category selector, skill tokens, semantic thumbnail, strong first Vermilion row and the 1024 layout. Accepted final evidence: category/skill/combined/exclusion/Clear runtime PASS; 54/54 focused correction tests PASS; frontend build PASS; Marketplace package PASS; diff check PASS; 1440/1024 visual QA PASS with no horizontal overflow.

## 7. Explore freeze and publication

Human screenshot approval froze Freelancer Explore; Overview remained untouched and FROZEN. Data foundation and visual implementation were split into separate commits.

`f42e44a5dcde0b8713fea532aa43345e943629b5` — `feat(frontend): finalize kinetic freelancer explore` was normally pushed after `302ae06`. Local/remote HEAD matched and the worktree was clean.

This is the frozen Explore **source** baseline. Applications advances the source baseline as recorded below; later docs-only commits do not change product implementation.

## 8. Handoff and next page

The documentation pass synchronizes current authority while retaining dated historical P06/Workpack gates. Earlier environment-blocked smoke remains recorded as blocked; later Explore live PASS does not rewrite history or certify unrelated features.

At the initial handoff, next was Freelancer → Công việc → Ứng tuyển, NOT_STARTED. That checkpoint is superseded by the approved Applications freeze below.

## 9. Applications final micro-polish, approval and freeze

Human screenshot approval: **Freelancer → Công việc → Ứng tuyển = PASS / FROZEN**. Overview and Freelancer Explore remain FROZEN; no general polish is authorized on these surfaces.

`2f5f0e4b0667cf9fefbc3f7897fe63a904fa12bc` — `feat(frontend): finalize kinetic freelancer applications` was normally pushed. Source files: `frontend/src/Workflow.tsx`, `frontend/src/Workflow.test.tsx`, `frontend/src/styles.css`.

Approved APPLICATION STATUS / EDITORIAL TRACKER reuses Explore's semantic thumbnails, status rail, editorial rows and kinetic toolkit. Final correction removes the redundant top-right filter label, increases lower-row functional text to 15–16px and aligns first-card status/budget/action. No API/backend changes; only real PENDING, ACCEPTED, REJECTED and CANCELLED statuses. No fake timeline/status/counts; `totalElements` is used only for the truthful server total of the active query.

Accepted final validation: 35/35 focused tests PASS; production build PASS; `git diff --check` PASS; 1440 PASS; 1024 PASS; no overflow; console errors 0. No tests/build were rerun for this freeze task.

At this checkpoint, next was **Freelancer → Công việc → Công việc của tôi**, `/work/mine`, **NOT_STARTED**. The approved My Work freeze below supersedes that checkpoint.

## 10. My Work + stable Job visual identity approval and freeze

Human approval: **Freelancer → Công việc → Công việc của tôi = PASS / FROZEN**; **Stable Job Visual Identity = LOCKED**. Overview, Explore and Applications remain FROZEN; no general taste-polish reopening.

`ca8b93d45a42249f779c13f0348f945ac275d7e0` — `feat(frontend): finalize kinetic freelancer my work` was normally pushed. Source files: `frontend/src/WorkLifecycle.tsx`, `frontend/src/Workflow.test.tsx`, `frontend/src/WorkflowLifecycle.test.tsx`, `frontend/src/styles.css`, `frontend/src/ui/job-thumbnails/JobThumbnail.tsx`, `jobFamily.ts`, `jobFamily.test.ts`.

Approved `/work/mine` concept: ACTIVE WORK / DELIVERY TRACKER, with active subnav, editorial heading, real server total/order, first record emphasized, state-aware surface, semantic thumbnail, real budget/optional deadline/revision context, next-action copy, flat ledger and real pagination. No invented search/filter/sort. AWAITING_PAYMENT is waiting/read-only; IN_PROGRESS Cobalt; SUBMITTED_FOR_REVIEW Acid; REVISION_REQUESTED Vermilion; COMPLETED Mint; CANCELLED restrained Ink/Cream. State truth outranks color. Budget is job value; COMPLETED does not confirm payout. No Freelancer funding action; RELEASE_PENDING is not paid and REFUND_PENDING is not refunded. Deadline/revision usage only when real.

Approved thumbnail correction supersedes the old OTHER rule: meaningful stored category → legacy skills → legacy title → Generic Development. OTHER is unspecified for decoration: try skills, then title, otherwise Generic Development; stored category/business semantics unchanged. Job ID selects only a stable variant inside the resolved family (`stableHash(id) % 3`, absent ID → 0). Stable family + job identity seeds Rough marks; no random/time/order dependence, stable rerender/remount. Web jobs may choose browser variants 0/1/2 but remain visibly Web/Frontend. Explore, Applications, My Work and future Client Work reuse the same component; no page-specific thumbnails, database thumbnail ID or uploads.

Accepted My Work gate: **62/62 focused tests PASS**, build/diff PASS, **1440/1024 PASS**, no overflow, console errors **0**. Stable identity gate: **131/131 focused tests PASS**, thumbnail recheck **36/36 PASS**, build/diff PASS, **cross-screen identity PASS**. Exact thumbnail markup matched across all three screen adapters in tests; runtime matched the overlapping Explore/Applications job and all four Applications/My Work jobs. No tests/build rerun during freeze. No backend/API/database/dependency changes; security inspection found no secrets, credentials, screenshots or machine-local paths added.

Current runtime observation: **4 My Work jobs**, encountered **IN_PROGRESS / COMPLETED**; legacy category/skills may remain sparse. These are observations, not guaranteed seed content. No runtime data mutation was performed in this freeze.

At this checkpoint, next was **CLIENT WORK SURFACES — NOT_STARTED**. The approved C1/global freeze below supersedes that checkpoint.

## 11. Client Work C1 + global row identity approval and freeze

Human decision: **Client Work C1 = APPROVED / PASS / FROZEN**. **Global Row Identity / Global State Rail / Global Category Plate / Strong Progress Color System = LOCKED**. Stable Job Visual Identity remains LOCKED. Overview remains FROZEN. Freelancer Explore, Applications and My Work were reopened only for the narrow approved row-identity override and are now **RE-FROZEN**; no general taste-polish reopening.

Source: `dc440ec96e4c960ac0ea285c07b42e0d823bc221` — `feat(frontend): finalize client work and global row identity`, normally pushed to the existing visual branch. Exactly seven source files: `frontend/src/Jobs.tsx`, `frontend/src/Jobs.test.tsx`, `frontend/src/WorkLifecycle.tsx`, `frontend/src/Workflow.tsx`, `frontend/src/styles.css`, `frontend/src/ui/JobRowIdentity.tsx`, `frontend/src/ui/JobRowIdentity.test.tsx`. This source commit contains C1 and the approved shared identity/color correction together.

Client `/work` = **CLIENT WORK CONTROL BOARD**: masthead/active nav, editorial hero and create CTA, truthful totalElements, server order/pagination, first record emphasized, state-aware primary, stable semantic thumbnail, real budget/status/deadline/context, secondary editorial ledger and existing state-derived destinations. No fake search/filter/sort, applicant counts, per-status totals or urgency.

Three separate channels are authoritative: semantic thumbnail = Job type; category plate = explicit visual semantic family; rail/status marker = workflow/application progress. Secondary rows stay Cream, not a colored card wall. Solid saturated rails: **6px at 1440 / 5px at 1024**, no gradient/blur/glow/color animation, textual status retained. Strong Success Green **#39B96E** is approved for COMPLETED/ACCEPTED; Fresh Mint **#B8DFC4** is a light successful primary background. Job progress: OPEN/AWAITING_PAYMENT/SUBMITTED_FOR_REVIEW Acid, IN_PROGRESS Cobalt, REVISION_REQUESTED Vermilion, COMPLETED Strong Green, CANCELLED Ink. Application progress: PENDING Acid, ACCEPTED Strong Green, REJECTED Vermilion, CANCELLED Ink. Primary Applications: PENDING Acid/Ink, ACCEPTED Mint/Green, REJECTED Vermilion/Cream, CANCELLED restrained Ink/Cream inactive. Original fixed Vermilion PENDING treatment is superseded; Client/My Work current Mint/Green COMPLETED primaries are preserved.

Category plate/thumbnail backing and JobThumbnail share existing `jobFamily`: meaningful stored category → legacy skills → legacy title → Generic Development. Stored OTHER remains OTHER in business/API/database/editor/filter; inference is decorative only. Legacy REST API → BACKEND / API; SEO → SEO / NỘI DUNG; unresolved → KHÁC. ID chooses only stable within-family variant; family/variant/Rough identity/plate remain consistent across Explore, Applications, My Work, Client Work and future Client surfaces. Exact labels/style rules are locked in UI_POLISH_SPEC.

Final accepted global evidence: **180/180 focused tests PASS**, production build PASS, diff check PASS, **1440/1024 PASS**, no overflow/clipping, console **0**, keyboard focus PASS, reduced motion preserved. Backend/API/business/filter behavior unchanged. Client runtime: **5 real jobs, page 1/1**, OPEN/IN_PROGRESS/COMPLETED observed; Applicants/Job Detail/create navigation PASS. REJECTED/CANCELLED Applications are tested where absent from runtime; statuses were not mutated. Freeze reuses accepted evidence; no tests/build or runtime smoke rerun. Security inspection found no added secrets, credentials, local screenshots or machine-local absolute paths.

At this checkpoint, next was **CLIENT WORK C2 — Job Authoring**, `/work/new`, `/work/:jobId/edit`, **NOT_STARTED**. C2 was not started during that C1 freeze. The approved C2 freeze below supersedes this checkpoint.

## 12. Client Work C2 approval and freeze — 2026-10-07

Human decision: **CLIENT WORK C2 / EDITORIAL WORK ORDER = APPROVED / PASS / FROZEN**, routes `/work/new` and `/work/:jobId/edit`. Overview, Freelancer Explore/Applications/My Work, Client C1, Global Row Identity/State Rail/Progress Color/Category Plate and Stable Job Visual Identity remain frozen/locked.

Source: `846142d8a3fb5f8a3f59204158c29e534ab7ea56` — `feat(frontend): finalize kinetic client job authoring`, normally pushed. Exactly three files: `frontend/src/JobEditor.tsx`, `frontend/src/JobEditor.test.tsx`, `frontend/src/styles.css`. No further source edits during freeze; no amend/merge or force push.

**CREATE** remains full work-order payload: `title`, `description`, `category`, `skills`, `budgetUsd`, `deliveryDueAt`, `reviewWindowHours`, `maxRevisions`, `deliverables`, `acceptanceCriteria`. Existing validation stays unchanged: explicit category/title required, skills max 10/trimmed 2–40/comma parser/no case-insensitive duplicates, budget > 0, deadline ≥24 hours ahead, review integer 24–168, revisions integer 0–2, deliverables 1–10 with title/description required, criteria 1–20 with description required; required=true preserved.

**EDIT** remains metadata-only: `title/description/category/skills`, trusted CLIENT + owner + OPEN. Immutable budget/deadline/review/revisions/deliverables/criteria controls remain absent; real server identity/budget/deadline may display read-only context.

Approved one-page sections: 01 Nội dung công việc (Acid), 02 Điều kiện thực hiện (Cobalt), 03 Sản phẩm bàn giao (Vermilion), 04 Điều kiện nghiệm thu (Mint). Section colors are not workflow statuses. 1440 editor left/sticky Live Draft Summary right. 1024 Hero → 01 → Preview → 02 → 03 → 04 → Actions. No wizard or misplaced Preview before 01.

Summary is UNSAVED local draft only, with disclaimer: explicit category/title/parsed skills/budget/deadline, non-empty deliverable/criteria counts and at most one actual deliverable title. Empty rows do not count; empty summary “Chưa có nội dung”. No fake OPEN/participants/payment/ranking/score/percentage or saved/ready/completed claims. Authoring category is never title-inferred; neutral before selection, selected semantic family/default variant 0 without job ID, stable within-family ID variant after saving (preview is not guaranteed final exact variant). Persisted OTHER fallback remains unchanged. Reuse shared thumbnails/plates/toolkit.

Create-only deliverables/criteria retain separate numbered rows, Cream fields, Ink rules, add/remove and bounds. Controls have readable labels/focus; hard zero-blur shadows on structural surfaces only. Final Ink band/Acid CTA, pending duplicate locks and existing motion/reduced-motion remain unchanged.

Accepted final C2 validation: **50/50 JobEditor tests PASS**, production build/diff check PASS, **Create 1440 PASS / Create 1024 PASS / Edit 1440 PASS**, no horizontal overflow/sticky overlap, console **0**, keyboard focus/reduced motion PASS. Backend/API/frozen surfaces unchanged; browser visual-QA draft **NOT SUBMITTED**. No tests/build or runtime smoke rerun during freeze. Source security check found no secrets, credentials, .env values, screenshots or machine-local absolute paths added.

At this C2 checkpoint, next was **CLIENT WORK C3 → APPLICANTS**, `/work/:jobId/applications`, **NOT_STARTED**. C3 was not started during that freeze. The approved corrected C3 freeze below supersedes this checkpoint.

## 13. Client Work C3 final corrected approval and freeze — 2026-10-07

Human decision: **CLIENT APPLICANTS C3 = APPROVED / PASS / FROZEN**, `/work/:jobId/applications`; **CANDIDATE DECISION DESK / EDITORIAL CANDIDATE DOSSIER**. The first plain Cream + lines visual was rejected; the final corrected dominant banner/dossier is now locked. Overview, all Freelancer work surfaces, Client C1/C2 and shared Stable Job Identity/Row Identity/State Rail/Progress Colors/Category Plates remain frozen.

Source `45c54c5a753c1cd4438dd3c968fe7ccec20a0491` — `feat(frontend): finalize kinetic client applicants`, normally pushed. Exactly `frontend/src/Workflow.tsx`, `frontend/src/Workflow.test.tsx`, `frontend/src/styles.css`. No further source edits, amend, merge or force push during freeze.

Approved Job context: dominant state-aware banner, Ink 2px border/zero-blur shadow, enlarged shared semantic thumbnail/category plate and real Job title/skills/budget/deadline/status. OPEN Acid; AWAITING_PAYMENT/review Acid + Ink; IN_PROGRESS Cobalt; revision Vermilion/Cream; COMPLETED Mint + Strong Green marker; CANCELLED Ink/Cream. Runtime OPEN Acid is approved; no universal Acid or Cream-strip regression.

Approved candidate: equal Cream editorial dossier with hard Ink border/restrained hard shadow, application state rail, display-order index, enlarged deterministic initials tile/tape/rays, public evidence and separated status/date/action area. Server order unchanged; no recommendation/ranking/featured first applicant. PENDING Acid, ACCEPTED Strong Green, REJECTED Vermilion, CANCELLED Ink; 6px/5px rails and visible text. State is not candidate quality.

Application API owns status/date/eligibility; matched FREELANCER public profiles supply supplemental real evidence through unique parallel isolated reads after ownership. One failed profile preserves Application/status/date/fallback ID/link. No private email, portfolio/review-list fan-out, Job-inferred candidate skills or null-to-zero reputation. Maximum three useful actual reputation facts. Runtime has one sparse PENDING applicant without headline/skills. **Visual density must NOT be achieved by fabricated product data**; use structure, spacing, typography, borders, color, cut-paper identity and kinetic details. No fake city/bio/experience/response speed/rating/counts, filters, match percentage or AI ranking.

Selection is CLIENT + owner + OPEN + PENDING. First click opens local Ink “Xác nhận lựa chọn” only; “Xác nhận chọn” / “Quay lại”, pending “Đang phân công…”, duplicate lock preserved. Copy explains selected acceptance after success, remaining pending applications closing/rejecting, funding before work starts. Assignment refresh/reconciles server truth; no premature ACCEPTED/REJECTED. Non-OPEN read-only with Job destination. Funding/wallet/payout/stablecoin controls stay outside C3.

Accepted corrected evidence: **66/66 focused tests PASS**, production build/diff PASS, **Client Applicants 1440 PASS / 1024 PASS / Confirmation 1440 PASS**, no overflow/clipped controls, console **0 errors**, focus-visible PASS, reduced motion preserved. Rich profiles/multiple applicants/assignment outcomes covered by tests where absent from runtime. Backend/API unchanged; screenshot assignment **NOT EXECUTED**. Freeze reused accepted evidence without tests/build rerun or new runtime mutation. Source security check found no added secrets/credentials/.env values/screenshots/machine-local paths.

Next: **P06.5 — FINANCE / TAX / ACTIVITY**, **NOT_STARTED**. Inspect real Client Thanh toán, Freelancer Thu nhập and Hoạt động contracts/source first. Create and obtain human approval for visual concept and target screenshots before implementation; do not invent the UI independently. P06.5 was not started during this freeze.

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

## QA discipline retained

1. Screenshot first, report second for visual acceptance.
2. Tests prove functionality, not visual quality.
3. Commit visual work only after human screenshot approval.
4. Review 1440 first, then 1024. Mobile optimization is deferred.
5. Fake reference-image data must never enter source.
6. Identify runtime-only data edits as runtime evidence.
7. Avoid feeding many conflicting visual references.
8. Frozen screens reopen only for a concrete regression.
9. Keep secrets, credentials, local screenshots and machine-local paths out of committed handoff material.

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
