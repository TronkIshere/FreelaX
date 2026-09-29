# D1.3 — Marketplace State Matrix

Status: **Set A desktop list and detail recognition study**. The rows describe supported product states and safe next-action categories, not new endpoints or implemented transitions. See `PRODUCT_UI_MEMORY.md` and `SCREEN_ARCHITECTURE.md` for role and action contracts.

| Surface / state | Owner | Meaning | Attention | Next action category | Provisional color treatment | Non-color signal |
| --- | --- | --- | --- | --- | --- | --- |
| Freelancer Discover: `OPEN`, not applied | Client owns job; freelancer may apply | Unassigned public opportunity | Opportunity | Apply after server eligibility recheck; preview uses the Discover snapshot | Bone row; focused item may gain Acid rail and small Vermilion anchor | `OPEN`, “Chưa ứng tuyển”, explicit “Ứng tuyển” |
| Application `PENDING` | Client has next decision | Application received, no assignment yet | Waiting | Read-only preview / monitor application | Acid rail on focused item | `PENDING`, “Đang chờ Client”; no invented withdraw action |
| Application `ACCEPTED` | Freelancer owns assigned work | Application accepted and job participation begins | Active | Open assigned work only after participant check | Restrained Sage owner transition | `ACCEPTED`, “Đã nhận việc”, “Mở công việc” |
| Application `REJECTED` | No current action owner | Application not selected | Historical | Read-only preview from stored snapshot | Bone and Ink; reduced visual emphasis | `REJECTED`, “Không được chọn”; no red error treatment or reapply |
| Application `CANCELLED` | No current action owner | Application closed; may follow job cancellation | Historical | Read-only preview from stored snapshot | Bone and Ink; reduced visual emphasis | `CANCELLED`, “Ứng tuyển đã đóng”; no invented reason or withdraw action |
| Client Job `OPEN` | Client owns posting | Accepting applicants or awaiting assignment | Owned | View applications; title/description edit or cancel only in owner detail | Sage ownership context; Bone row | `OPEN`, “Bạn là người đăng”, “Xem ứng tuyển”; budget read-only |
| Client Job `IN_PROGRESS` | Freelancer does work | Assigned work underway | Active | Open job / inspect work context | Bone row with Ink active marker | `IN_PROGRESS`, “Đang thực hiện”; no progress percentage |
| Client Job `SUBMITTED_FOR_REVIEW` | Client has next decision | Delivery awaits review | Act now | Open job then review in Set B flow | Small Vermilion attention zone | `SUBMITTED_FOR_REVIEW`, “Client cần xem”, explicit open action |
| Client Job `REVISION_REQUESTED` | Freelancer has next delivery | Feedback issued; client is waiting | Waiting | Open job to view history | Acid waiting rail, quieter than client review | `REVISION_REQUESTED`, “Đang chờ bản sửa”; no client approve-before-resubmit |
| Client Job `COMPLETED` | Both may inspect record | Work approved; payout/tax remain separate | Resolved | Open job record; separate payment detail if needed | Restrained Sage resolved marker | `COMPLETED`, “Công việc đã duyệt”; not a payout claim |
| Client Job `CANCELLED` | No current work owner | Job closed | Historical | Read-only job record | Bone and Ink, reduced emphasis | `CANCELLED`, “Công việc đã hủy”; no editing or payment action |

`AWAITING_PAYMENT` is a guarded backend enum value outside this primary six-state Set A study. If returned in a future live UI, show a read-only payment-pending/unknown-transition state and fetch payment status; never silently map it to active or completed work.

`REJECTED` and `CANCELLED` applications can retain a historical job snapshot even when the job is no longer discoverable. The prototype scenarios are fixed illustrative records, not a claim of current server data. D2 owns the submission/revision interaction details; D3 owns financial evidence.
