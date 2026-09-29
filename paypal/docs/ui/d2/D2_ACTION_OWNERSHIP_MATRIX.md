# D2.1 — Action Ownership Matrix

The next action belongs to a role and a verified job state. The waiting role receives a readable status, not a duplicate mutation CTA. The assigned Freelancer label is a role cue, not profile data.

| Workflow position | JobStatus | Current/action owner | Waiting party | Permitted primary work action | Latest submission |
| --- | --- | --- | --- | --- | --- |
| Assigned work | `IN_PROGRESS` | Freelancer | Client | Gửi bàn giao | None |
| First review | `SUBMITTED_FOR_REVIEW` | Client | Freelancer | Yêu cầu chỉnh sửa **or** Duyệt bàn giao | V1 `SUBMITTED` |
| Revision | `REVISION_REQUESTED` | Freelancer | Client | Gửi bản sửa | V1 `REVISION_REQUESTED` |
| Second review | `SUBMITTED_FOR_REVIEW` | Client | Freelancer | Yêu cầu chỉnh sửa **or** Duyệt bàn giao | V2 `SUBMITTED`; V1 retained |
| Closed work | `COMPLETED` | None | Neither for work actions | None; read-only history/evidence context | V2 `APPROVED`; V1 retained |

| Handoff | Action initiator → next owner | Persistent evidence |
| --- | --- | --- |
| Assign | Client → Freelancer | Selected `PENDING` application becomes `ACCEPTED`; job gains `freelancerId` and enters `IN_PROGRESS` |
| Submit V1 | Freelancer → Client | V1 `SUBMITTED`; job enters review |
| Request revision | Client → Freelancer | V1 feedback and `reviewedAt`; job enters revision |
| Submit V2 | Freelancer → Client | New V2 `SUBMITTED`; V1 remains in history |
| Approve | Client → lifecycle closed | V2 `APPROVED`, job `COMPLETED`, both versions retained |

The two review choices have different effects and must not be styled or described as equivalent. Neither the waiting party nor a nonparticipant receives a work mutation. A live implementation must confirm role, `freelancerId`/Client ownership, job state, and latest submission with the server before enabling a CTA. The static storyboard does not perform that check.
