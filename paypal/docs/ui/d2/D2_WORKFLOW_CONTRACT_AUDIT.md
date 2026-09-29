# D2.1 — Work Workflow Contract Audit

Status: read-only audit of shared master `0922d23baa52f4761edf095c4cbba983e7750cac`. This records current backend behavior for static design evidence; it does not change a service or authorize implementation.

## Sources inspected

`marketplace-backend/src/main/java/com/marketplace/backend/`:

- `controller/JobController.java`
- `service/impl/JobServiceImpl.java`
- `entity/JobStatus.java`, `JobSubmissionStatus.java`, `JobApplicationStatus.java`
- `dto/request/job/AssignFreelancerRequest.java`, `SubmitWorkRequest.java`, `RequestRevisionRequest.java`
- `dto/response/job/JobResponse.java`, `JobSubmissionResponse.java`

The product and screen constraints come from `PRODUCT_UI_MEMORY.md`, `SCREEN_ARCHITECTURE.md`, and the locked D1 decision. All paths below are relative to `/api/v1/marketplace/jobs` and return data inside `ResponseAPI`.

## Exact status vocabulary

- `JobStatus`: `OPEN`, `AWAITING_PAYMENT`, `IN_PROGRESS`, `SUBMITTED_FOR_REVIEW`, `REVISION_REQUESTED`, `COMPLETED`, `CANCELLED`.
- `JobSubmissionStatus`: `SUBMITTED`, `REVISION_REQUESTED`, `APPROVED`.
- `JobApplicationStatus`: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`.

`AWAITING_PAYMENT` is an enum value but is not assigned by the audited service path. It is outside the canonical D2 storyboard. If live data returns it, the Screen Architecture's guarded, read-only treatment applies. No `READY_FOR_REVIEW` state exists.

## Transition contracts

| Operation | Route and request | Preconditions and effects |
| --- | --- | --- |
| Assign | `PATCH /{jobId}/assign-freelancer`, `{ "freelancerId": UUID }` | Client owner, `OPEN`, valid Freelancer, selected application `PENDING`; selected application becomes `ACCEPTED`, other `PENDING` applications become `REJECTED`, `job.freelancerId` is set, job becomes `IN_PROGRESS`. A direct-assignment create path also exists, but this fixture uses applicant assignment. |
| Submit or resubmit | `POST /{jobId}/submit-work`, `{ "summary": string, "deliverableUrl"?: string }` | Authenticated assigned Freelancer; job `IN_PROGRESS` or `REVISION_REQUESTED`. `summary` is nonblank and at most 10,000 characters; `deliverableUrl` is optional and at most 2,048. Service trims values, creates a **new** submission at `count + 1`, marks it `SUBMITTED`, and moves job to `SUBMITTED_FOR_REVIEW`. No upload field exists. |
| Request revision | `POST /{jobId}/request-revision`, `{ "feedback": string }` | Client owner; job `SUBMITTED_FOR_REVIEW`; latest submission `SUBMITTED`; feedback nonblank, max 10,000. Latest submission becomes `REVISION_REQUESTED` with `reviewerFeedback` and `reviewedAt`; job becomes `REVISION_REQUESTED`. |
| Approve | `POST /{jobId}/approve`, **no request body** | Client owner; job `SUBMITTED_FOR_REVIEW`, checkout order ID present, latest submission `SUBMITTED`. Backend captures checkout, requires result `CAPTURED`, then sets job `COMPLETED`, latest submission `APPROVED` and `reviewedAt`, and invokes `payoutService.settle(job)`. No client-supplied checkout or payment fields. |
| History | `GET /{jobId}/submissions`, no body | Client owner or assigned Freelancer only. Returns submissions in ascending version order, mapped to `JobSubmissionResponse`. |

`JobSubmissionResponse` exposes `id`, `jobId`, `freelancerId`, `version`, `summary`, `deliverableUrl`, `status`, `reviewerFeedback`, `reviewedAt`, `createdAt`, `updatedAt`. `JobResponse` exposes job title, description, budget, client/freelancer IDs, status, checkout order ID and timestamps, but **no freelancer display name**. The storyboard uses “Assigned Freelancer” as a role label. “North Studio” is retained only as a D1 study identity; it is not asserted as a field on the workflow `JobResponse`.

## Boundary and forbidden inventions

The approval capture is a server-side precondition, not a user payment step. A `COMPLETED` job denotes approved work under this service path; it is not proof of completed payout, off-ramp, or tax evidence. D3 owns those surfaces. D2 must not show an upload/dropzone, attachments beyond `deliverableUrl`, chat, milestones, time tracking, progress percentages, due dates, rating, skills, applicant counts, escrow, wallet, Solana transaction, checkout/payment form, manual checkout ID, manual payout trigger, or an approval body. Live UI must recheck the current job, participant, latest submission, and server result before offering actions or claiming a transition.
