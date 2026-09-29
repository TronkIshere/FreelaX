# D2.1 — Fixed Work Workflow Fixture

**ALL D2 WORKFLOW STUDIES MUST USE THIS FIXTURE UNCHANGED UNTIL HUMAN APPROVAL.** The IDs, timestamps, text, and capture result below are deterministic design data, not records claimed to exist on a server. Timestamps mirror backend `LocalDateTime` values and do not imply a timezone.

## Job and identities

| Fact | Fixture value | Evidence boundary |
| --- | --- | --- |
| `jobId` | `11111111-1111-4111-8111-111111111111` | Same D1 focus-job concept |
| `title` | Build Solana Payment Infrastructure | Supported `JobResponse.title` |
| `description` | Implement settlement, reconciliation, and failure handling for a cross-border payout workflow. | Supported `JobResponse.description` |
| `budgetUsd` | `500.00` USD | Supported `JobResponse.budgetUsd`; read-only study fact |
| `clientUserId` | `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa` | Supported ID; “North Studio” is the D1 study's Client name, not a `JobResponse` field |
| `freelancerId` | `bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb` | Supported ID; display only **Assigned Freelancer** as a role label |
| `checkoutOrderId` | `cccccccc-cccc-4ccc-8ccc-cccccccccccc` | Present in job for approval precondition; never user input |
| selected `applicationId` | `55555555-5555-4555-8555-555555555555` | Design fixture application: `PENDING → ACCEPTED` on assignment |
| job `createdAt` | `2026-09-29T09:00:00` | Fixed study time |
| assignment | `2026-09-29T09:10:00` | Client assigns the pending applicant; job enters `IN_PROGRESS` |

The assignment service rejects other pending applications if any. This one-job storyboard does not invent another applicant or identity.

## Versioned submissions and decisions

| Event | Time | Submission snapshot | Job state after event |
| --- | --- | --- | --- |
| First submit | `2026-09-29T10:00:00` | V1 `dddddddd-dddd-4ddd-8ddd-dddddddddddd`, version `1`, `SUBMITTED`; summary: “Implemented the settlement flow, reconciliation handling, and initial failure recovery path.”; `deliverableUrl`: `https://example.invalid/freelax-demo/v1` | `SUBMITTED_FOR_REVIEW` |
| Client revision | `2026-09-29T10:15:00` | V1 becomes `REVISION_REQUESTED`; `reviewerFeedback`: “Please add retry handling for failed settlement callbacks and document the reconciliation edge cases.”; `reviewedAt`: `2026-09-29T10:15:00` | `REVISION_REQUESTED` |
| Second submit | `2026-09-29T11:00:00` | New V2 `eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee`, version `2`, `SUBMITTED`; summary: “Added retry handling for failed settlement callbacks and documented reconciliation edge cases.”; `deliverableUrl`: `https://example.invalid/freelax-demo/v2` | `SUBMITTED_FOR_REVIEW` |
| Client approval | `2026-09-29T11:15:00` | V2 becomes `APPROVED`; `reviewedAt`: `2026-09-29T11:15:00`; V1 remains `REVISION_REQUESTED` in history | `COMPLETED` |

V1 `createdAt` is `10:00`, V1 `updatedAt` after revision is `10:15`; V2 `createdAt` is `11:00`, V2 `updatedAt` after approval is `11:15`. Both submissions carry the same job and freelancer IDs above. No V3 exists. History is ordered V1, then V2.

## Approval precondition and invariants

For this success-path fixture, the job's checkout order ID is present and the backend capture result is **`CAPTURED`**. This allows the service to set the job to `COMPLETED` and invoke settlement processing. It does not claim the payout is received or the tax record is ready. Capture is not a visual user step or payment form.

The job never enters `READY_FOR_REVIEW` or `AWAITING_PAYMENT` in this primary story. Submission form inputs are only `summary` and optional `deliverableUrl`; revision input is only required `feedback`; approval has no request body. Prior versions and feedback remain visible. The prototype uses these values unchanged and performs no requests.
