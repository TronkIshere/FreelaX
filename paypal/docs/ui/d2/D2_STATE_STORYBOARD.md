# D2.1 — Six-Frame Work State Storyboard

Status: deterministic **desktop study awaiting human review**. Use the [fixed fixture](D2_WORKFLOW_FIXTURE.md) unchanged. The six frames follow one job and one assigned Freelancer; they are not six independent records. A visible rail and ascending version history connect the same work through each handoff. Static buttons are evidence of intended actions, not live permissions or API calls.

## Canonical story

`IN_PROGRESS → Freelancer submits V1 → SUBMITTED_FOR_REVIEW → Client requests revision → REVISION_REQUESTED → Freelancer submits new V2 → SUBMITTED_FOR_REVIEW → Client approves → COMPLETED`.

`READY_FOR_REVIEW` is not a status. `AWAITING_PAYMENT` is not in this canonical current path. Rail words such as “Working” and “Review” are visual steps, not extra `JobStatus` values.

| Frame | Role view / JobStatus | Latest submission | Current and next-action owner / waiting party | Primary CTA; supporting action | Visible history | Entering transition → leaving transition |
| --- | --- | --- | --- | --- | --- | --- |
| 01 — Assigned workspace | Freelancer / `IN_PROGRESS` | None | Freelancer owns work and next action; Client waits | **Gửi bàn giao**; inspect job brief | Empty, explicitly “Chưa có bản bàn giao” | Client assignment accepted; job gains Freelancer → opens V1 composer |
| 02 — Submit V1 | Freelancer / `IN_PROGRESS` | None until server accepts | Freelancer owns submission; Client waits | **Gửi bàn giao**; return to workspace | Empty; preview “Bản bàn giao #1” is prospective, not persisted | Opens from frame 01 → backend creates V1 `SUBMITTED`, job enters review |
| 03 — Review V1 | Client / `SUBMITTED_FOR_REVIEW` | V1 `SUBMITTED` | Client owns decision; Freelancer waits | **Yêu cầu chỉnh sửa** or **Duyệt bàn giao**; inspect V1 URL/history | V1 with summary, URL, `createdAt` | Confirmed submit transfers owner Freelancer → Client → this fixture chooses revision |
| 04 — Revision | Freelancer / `REVISION_REQUESTED` | V1 `REVISION_REQUESTED` | Freelancer owns next delivery; Client waits | **Gửi bản sửa**; inspect prior V1 and feedback | V1 and exact reviewer feedback with `reviewedAt` | Client revision transfers owner Client → Freelancer → opens new V2 submission |
| 05 — Review V2 | Client / `SUBMITTED_FOR_REVIEW` | V2 `SUBMITTED` | Client owns decision; Freelancer waits | **Yêu cầu chỉnh sửa** or **Duyệt bàn giao**; compare V2 with V1 | V1 `REVISION_REQUESTED` and feedback remain; V2 is latest | New V2 transfers owner Freelancer → Client → fixture chooses approval |
| 06 — Closed work | Both / `COMPLETED` | V2 `APPROVED` | No work-action owner; neither party has a work CTA | **None**; read-only history/evidence context | V1 revision and V2 approval preserved in ascending version order | Client approval with existing checkout ID and `CAPTURED` server result closes work → no further work transition |

Frame 02's only submission inputs are required `summary` and optional `deliverableUrl`. Frame 03 shows V1 summary, URL, status and `createdAt`. Frame 04 makes the exact feedback prominent without overwriting V1. Frame 05 adds V2 as a separate record. Frame 06 may say “Approval confirms work and triggers settlement processing,” but must not depict payment, payout completion, wallet, or tax evidence.

## Rail and layout semantics

The visual rail reads **Assigned → Working → Submitted → Review → Revision → Resubmitted → Approved**. “Assigned” is the Client-to-Freelancer handoff into `IN_PROGRESS`; “Submitted/Review” both refer to the first `SUBMITTED_FOR_REVIEW` interval; “Resubmitted” refers to the second; “Approved” maps to `COMPLETED`. It is a chronology, not seven job states. Frame position, title, owner sentence, action area, and version history provide meaning without a status pill or animation.

The storyboard inherits D1's Cream canvas, Ink rules/type/action, selective Vermilion attention, Acid revision cue, and Fresh Mint resolved context. Cobalt remains reserved for finance/evidence. It adapts the editorial grammar to a workspace, rail, submission document, and review handoff rather than copying the D1 list layout. Mobile is deferred. Motion and reduced-motion equivalents are specified in [D2_MOTION_SEMANTICS.md](D2_MOTION_SEMANTICS.md); no production animation is built.
