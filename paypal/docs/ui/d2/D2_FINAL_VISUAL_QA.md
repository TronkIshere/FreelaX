# D2.4 — Final Six-Screen Visual and Contract QA

Result: **PASS for the final D2 desktop source set**, with the rendering limit below. The human explicitly authorized final D2 closure review after D2.1/D2.2 human reviews and D2.3/D2.3a/D2.3b calibration. This QA inspects the final HTML/CSS, written hierarchy, locked fixture, ownership, action, version, palette, and motion contracts. No prototype was edited in D2.4.

| Screen | Primary object | Owner clarity | CTA clarity | Rail clarity | Section signposting | Color hierarchy | History hierarchy | Contract integrity | Result |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [01 IN_PROGRESS](prototypes/d2_3_01_in_progress.html) | Exact job brief remains the largest work object | “Freelancer acts now”; Client waits | Ink **Gửi bàn giao** | Working: filled Mint, Ink border, text, position | Ink current-work cue; Mint submission-history cue | Cream leads; Mint supports ownership | Explicit empty history below work/action | `IN_PROGRESS`, no persisted submission | **PASS** |
| [02 Submit V1](prototypes/d2_3_02_submit_v1.html) | Prospective “Bản bàn giao #1” composer | Freelancer acts; Client waits | Ink **Gửi bàn giao** | Working stays active until accepted submit | Ink draft cue; Mint empty-history cue | Cream and light Mint; form remains neutral | V1 is not falsely persisted | Only required `summary` and optional `deliverableUrl` | **PASS** |
| [03 Review V1](prototypes/d2_3_03_review_v1.html) | Exact latest V1 summary and URL | “Client acts now”; Freelancer waits | Ink **Duyệt bàn giao** primary; revision secondary | Review: Vermilion tint, Ink border, text, position | Vermilion latest-submission cue; Mint history cue | Cream keeps work readable; Vermilion is bounded | V1 row below the full current work | V1 `SUBMITTED`, created 10:00; no approval body | **PASS** |
| [04 Revision](prototypes/d2_3_04_revision_requested.html) | Exact Client feedback leads, then prospective V2 composer | Freelancer acts; Cream ownership band has narrow Acid top strip | Ink **Gửi bản sửa** | Revision: full Acid, Ink border, text, position | Full Acid feedback label; Mint history cue | 26% Acid feedback tint and scoped Ink edge differ from Cream ownership | V1 remains `REVISION_REQUESTED`; V2 is not persisted | Exact feedback, required summary, optional URL; no V1 overwrite | **PASS** |
| [05 Review V2](prototypes/d2_3_05_review_v2.html) | Exact latest V2 summary and URL | Client acts; Freelancer waits | Ink **Duyệt bàn giao** primary; revision secondary | Resubmitted: Vermilion tint; `SUBMITTED_FOR_REVIEW` written | Vermilion latest-submission cue; Mint history cue | Cream dominates; review attention is bounded | V1 feedback remains secondary; V2 is current | New V2 `SUBMITTED`, created 11:00; V1 retained | **PASS** |
| [06 COMPLETED](prototypes/d2_3_06_completed.html) | Approved V2 work record | “Workflow closed”; no work-action owner | **No work CTA** | Approved: filled Mint, Ink border, text, position | Mint approved-record and history cues | Cream plus restrained Mint closure | Ascending V1 revised and V2 approved | `COMPLETED`, V2 `APPROVED`; no wallet, payout, transaction, or certificate action | **PASS** |

## Cross-screen checks

- **Readability:** 38px job title, 36px current-work/feedback headline, 28px revision composer heading, 21px ownership heading, 17px body, and 12–14px workflow/metadata remain within the intended desktop ranges. The work object keeps its own breathing room and is not reduced to metadata. The six screens are separate rather than stacked.
- **Orientation and contrast:** Each current rail cell has a semantic fill, explicit word, position, and Ink border. Past/future steps recede. The content cues separate current work, latest submission, feedback, history, and approved record. Ink text meets at least 4.5:1 on the study Cream, Mint, Acid, stronger Vermilion review tint, and 26% Acid feedback tint (source-color calculation). The feedback body and ownership band are different surfaces.
- **Contract:** `IN_PROGRESS → SUBMITTED_FOR_REVIEW → REVISION_REQUESTED → SUBMITTED_FOR_REVIEW → COMPLETED` is unchanged. `READY_FOR_REVIEW` is absent, `AWAITING_PAYMENT` is not promoted, and the seven rail words are visual steps. No upload, attachment, payment UI, wallet, or approval request fields appear. V1 is retained when V2 is created. Completion does not imply completed payout.
- **Motion:** [D2_MOTION_SEMANTICS.md](D2_MOTION_SEMANTICS.md) retains server confirmation before transition, ownership transfer, rail change, history persistence, focus transfer, opacity plus at most 8px translation in 140–180ms, and a zero-translation reduced-motion path. The prototypes are static; no continuous animation, glow, bounce, or confetti is introduced.
- **Anti-SaaS:** No bento-first composition, floating card system, KPI row, kanban, glass/blur/glow, soft-shadow dashboard, status-pill-only state language, wallet-first framing, or dark Web3 styling.

| Review gate | Result |
| --- | --- |
| D2.1 human workflow review | PASS |
| D2.2 human study review | PASS |
| D2.3 selected grammar and six-state application | PASS |
| D2.3a orientation/signposting | PASS |
| D2.3b contrast/type calibration | PASS, accepted for human-directed progression after source checks |
| D2.4 final source and contract QA | PASS |
| Human-directed progression to D2 lock | APPROVED by Workpack 48404, subject to the checks above |

## Evidence limit

The local browser environment previously crashed for headless Chrome/Edge and blocked opening local `file:` studies through the available browser surface. This workpack did **not** capture or inspect D2.3b screenshots at 1440px or 1280px, and this document does not claim pixel-level verification at those widths. PASS records the human-directed closure plus final source, contrast, structure, and contract checks; any later rendered visual review may identify an issue for a separately authorized correction. No unsafe browser workaround was used. Mobile was not reviewed and remains deferred.
