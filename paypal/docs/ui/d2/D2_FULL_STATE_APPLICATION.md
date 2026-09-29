# D2.3 — Full-State Application

One deterministic D2.1 job appears across six **separate desktop screens**. Screens 01 and 02 are `IN_PROGRESS`; the V1 form in 02 is prospective. Screens 03 and 05 are both `SUBMITTED_FOR_REVIEW`, with different latest versions. The rail is visual chronology; only the `JobStatus` column states the backend job state. The D2.1 fixture, timestamps, summaries, feedback, IDs, and URLs remain unchanged.

| Screen | Role | JobStatus | Latest submission | Owner | Primary object | Primary action | Secondary action | Semantic color | History treatment |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 01 · [Workspace](prototypes/d2_3_01_in_progress.html) | FREELANCER | `IN_PROGRESS` | None | Freelancer | Job work brief | Gửi bàn giao | None | Cream + light Mint ownership | Empty, explicit |
| 02 · [Submit V1](prototypes/d2_3_02_submit_v1.html) | FREELANCER | `IN_PROGRESS` | None; V1 draft only | Freelancer | `summary` + optional `deliverableUrl` composer | Gửi bàn giao | None | Cream + light Mint ownership | Empty; draft is not history |
| 03 · [Review V1](prototypes/d2_3_03_review_v1.html) | CLIENT | `SUBMITTED_FOR_REVIEW` | V1 `SUBMITTED`, created 10:00 | Client | Exact V1 summary and URL | Duyệt bàn giao | Yêu cầu chỉnh sửa | Cream + bounded Vermilion attention | V1 `SUBMITTED`, compact |
| 04 · [Revision](prototypes/d2_3_04_revision_requested.html) | FREELANCER | `REVISION_REQUESTED` | V1 `REVISION_REQUESTED`; V2 draft only | Freelancer | Exact Client feedback, then V2 composer | Gửi bản sửa | None | Cream + Acid feedback | V1 and review time retained; no persisted V2 |
| 05 · [Review V2](prototypes/d2_3_05_review_v2.html) | CLIENT | `SUBMITTED_FOR_REVIEW` | V2 `SUBMITTED`, created 11:00 | Client | Exact V2 summary and URL | Duyệt bàn giao | Yêu cầu chỉnh sửa | Cream + bounded Vermilion attention | V1 feedback retained; V2 current |
| 06 · [Completed](prototypes/d2_3_06_completed.html) | BOTH | `COMPLETED` | V2 `APPROVED`, reviewed 11:15 | None for work | Approved V2 work record | None | None | Cream + quiet Mint closure | V1 revised, V2 approved; ascending |

The approval endpoint has no request body. `COMPLETED` follows a successful backend capture in the locked fixture, but the UI does not introduce a checkout, wallet, transaction, payout, tax, or certificate action. Work completion does not assert payout completion. Motion notes describe a confirmed handoff only; no prototype calls an API or animates continuously. D2 overall remains in progress until full-state human review.
