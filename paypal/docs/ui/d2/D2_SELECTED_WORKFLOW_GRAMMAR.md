# D2.3 — Selected Workflow Grammar

Status: **selected hybrid for desktop work screens; full-state readability review pending**. D2.2 human visual review: **PASS**. The working grammar is **FreelaX Kinetic Document Workflow**. This decision does not lock global palette tokens or authorize Flutter implementation.

## Selected hybrid

| Source | Retained contribution | D2.3 adjustment |
| --- | --- | --- |
| Study B — Document-First Submission Review | The current brief, submission, feedback, or approved record is the page's dominant object. | Give it more breathing room and readable body type; place supporting facts after the work. |
| Study A — Rail-First Editorial Workspace | A visible seven-step chronology and adjacent current-owner/action band. | Quiet past and future steps; keep the band compact and put the actual action after the document. |
| Study C — Handoff + Version Ledger | A traceable ascending version ledger and FROM → TO handoff meaning. | Compress history below current work. Show handoff as transient confirmed-transition feedback, never permanent page chrome. |

## Canonical screen order

Job identity → compact workflow rail → ownership band → **current work** → current action → applicable feedback → compact version ledger. On the revision screen, the exact Client feedback leads the current-work area and precedes the prospective V2 composer. The five reading questions are: what is this work, who acts, what action is permitted, what is the state, and what happened before? The current work has the strongest visual weight; the action follows, then owner/state, rail, and history.

Each of the six HTML files represents **one current state**, not a stacked storyboard. `IN_PROGRESS` workspace and prospective V1 composer are two views of the same job state; the composer does not assert that V1 already exists. The second review remains `SUBMITTED_FOR_REVIEW`, with V2 as the latest submission and V1 retained. `COMPLETED` shows the approved work record and has no work CTA. The rail words Assigned, Working, Submitted, Review, Revision, Resubmitted, Approved are visual steps, not seven backend statuses.

## Color and information density

Use the D1 study values only: Cream `#FFF7E8` canvas, Ink `#17212B` text/primary action, Vermilion `#F15A3D` bounded Client review attention, Acid `#F5D12F` revision feedback attention, and Fresh Mint `#B8DFC4` restrained Freelancer ownership or resolved context. Cobalt is reserved. Color always has a written state and owner cue. The global palette remains **NOT LOCKED**.

Level 1, always visible: title, state, owner, current brief/submission/feedback, and permitted primary action. Level 2, supporting: budget, timestamps, URL, and rail. Level 3, historical: older versions, review time, and continuity notes. The ledger stays visible but visually quieter than the latest work. It never replaces V1 with V2, and it does not repeat the full current submission.

## Extension and exclusion rules

- Extend the document grammar to later work states by naming the real `JobStatus`, current owner, current work object, and permitted action before styling. A live implementation must verify role, participant, state, latest submission, and server result.
- Submission has only required `summary` and optional `deliverableUrl`. Revision feedback is a Client decision input, not a Freelancer submission field. Approval has no request body. No payment or payout step appears inside this D2 work screen.
- Keep confirmed-transition motion short: opacity plus at most 8px translation over 140–180ms, followed by focus transfer. Reduced motion applies the same content and focus instantly or with opacity alone. Static screens communicate the full state without motion.
- Avoid an always-on handoff sidebar, dense hard-rule grids, repeated uppercase micro-labels, floating cards, bento dashboards, status-pill-only meaning, decorative color, upload/attachments, chat, milestones, wallet or payout actions, and invented confirmation fields. D3 owns financial continuation.

See [D2_READABILITY_CALIBRATION.md](D2_READABILITY_CALIBRATION.md) for the visual reduction and [D2_FULL_STATE_APPLICATION.md](D2_FULL_STATE_APPLICATION.md) for all six state contracts.
