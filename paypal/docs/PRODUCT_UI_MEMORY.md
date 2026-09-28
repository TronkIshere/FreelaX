# FreelaX Marketplace — Product UI Memory

Status: **locked IA and workflow decisions** for the next Flutter design stage. This is a product design reference, not a claim that the current app implements these screens or that every payment step is live.

## Product frame and roles

FreelaX is a job marketplace. A job is the organizing object for work, payment, payout, and tax evidence. The interface must use the authenticated marketplace `userType` (`CLIENT` or `FREELANCER`) to choose the appropriate experience. These are product roles, not interchangeable labels for the same wallet dashboard. A user should only see actions available to their role and to the current job or application state.

- **CLIENT** posts and manages jobs, reviews applications and submissions, requests revisions or approves work, and tracks payment by job.
- **FREELANCER** discovers jobs, applies, tracks applications and assigned work, submits deliverables, responds to revision requests, and tracks earnings and tax documents by job.

## Primary navigation

Keep five stable destinations per role. Use the exact Vietnamese labels below.

| Position | CLIENT | FREELANCER | Purpose |
| --- | --- | --- | --- |
| 1 | Tổng quan | Tổng quan | Role-specific priorities, status summaries, and next actions |
| 2 | Công việc | Công việc | Jobs and their workflows |
| 3 | Thanh toán | Thu nhập | Client payments / freelancer payouts and tax evidence, linked to jobs |
| 4 | Hoạt động | Hoạt động | Relevant job, application, submission, and payment events |
| 5 | Tài khoản | Tài khoản | Profile, account, and configuration |

The FREELANCER **Công việc** destination has three sections: **Khám phá** (available jobs), **Ứng tuyển** (my applications), and **Công việc của tôi** (assigned jobs). CLIENT **Công việc** is the place to create and manage posted jobs, inspect applicants, review delivery, and open each job's payment context.

Wallet, Solana, Compare Fee, QR, and Send/Receive are **not primary navigation**. If relevant, surface a payment method, network detail, fee explanation, or utility action within the job or account context. Do not make the marketplace feel like a generic payment wallet.

## Job and application lifecycle

Use these six job states as the product's primary work vocabulary:

| Job state | Product meaning | Main next action |
| --- | --- | --- |
| `OPEN` | Posted, accepting applications or awaiting assignment | FREELANCER applies; CLIENT reviews applicants, edits or cancels while allowed |
| `IN_PROGRESS` | A freelancer is assigned and doing the work | FREELANCER submits work |
| `SUBMITTED_FOR_REVIEW` | A delivery awaits the client's decision | CLIENT approves or requests a revision |
| `REVISION_REQUESTED` | Client feedback requires another delivery | FREELANCER revises and resubmits |
| `COMPLETED` | Client approved the work | Both roles review the job record and its separate payment/payout status |
| `CANCELLED` | Job was cancelled | Read-only record; show any relevant application closure |

The main path is `OPEN → IN_PROGRESS → SUBMITTED_FOR_REVIEW → COMPLETED`. A review can take `SUBMITTED_FOR_REVIEW → REVISION_REQUESTED → SUBMITTED_FOR_REVIEW` through a new submission. `OPEN → CANCELLED` is possible when cancellation is allowed. Show these as job states, not as a single combined payment state.

Applications have their own state, independent of the job: `PENDING`, `ACCEPTED`, `REJECTED`, `CANCELLED`. Show the status on **Ứng tuyển** and in the job's applicant list. In the current backend, assigning one pending applicant accepts that application and rejects other pending applications; cancelling an open job cancels pending applications. Do not imply every `CANCELLED` application was manually withdrawn by its freelancer.

The current backend enum also contains `AWAITING_PAYMENT`. It is outside the six-state primary product vocabulary above; Screen Architecture must explicitly map any returned `AWAITING_PAYMENT` value to honest payment-in-progress UI rather than silently treating it as `IN_PROGRESS` or `COMPLETED`.

## Delivery and review

Submission is a **job-scoped** action available to the assigned freelancer in `IN_PROGRESS` or `REVISION_REQUESTED`. The submission form uses `summary` (required) plus `deliverableUrl` (optional), matching the current backend request. Do not design an upload-only or file-required flow as if it exists. Keep prior submissions, version order, review feedback, and the latest decision visible in the job detail. The client can request a revision with feedback or approve a submitted delivery; a revision creates another pass through the same job.

## Payment, payout, and tax IA

All money information starts from a **job** and keeps its job ID/title visible. The CLIENT **Thanh toán** destination is an overview and route into each job's payment details. The FREELANCER **Thu nhập** destination is an overview and route into each job's payout, conversion, and tax evidence. A job detail should connect its work timeline to the payment timeline without collapsing them into one status.

- **Client view:** job budget and checkout/payment progress, next required action, and the payment record for that job.
- **Freelancer view:** job amount, payout progress, any conversion/off-ramp estimate, and a job-linked tax/certificate record when available.
- **Shared rule:** separate job completion from checkout capture, payout, off-ramp, and tax export. Show each actual status and failure/pending state. A completed job alone is not proof that VND reached a bank account or that a tax certificate was issued.

The backend exposes job payment status and job-linked certificate/tax information. Screen Architecture should use those as the source for UI states and mark missing or unavailable data explicitly. Fee comparison and on-chain identifiers can appear as supporting details in this flow, not as stand-alone top-level destinations.

## Truthful money and network language

Preserve **DEVNET**, **simulation**, and **estimated payout** labels wherever those conditions apply. Use wording such as “Solana DEVNET”, “mô phỏng” and “VND dự kiến” next to the relevant value or event; do not bury these labels in a disclaimer. Do not describe a simulated or estimated payout as settled cash, a DEVNET transaction as mainnet payment, or an in-progress tax record as an issued certificate. Use the backend's status and `simulation` / `network` / `estimatedAmountVnd` fields when available; do not infer success from a job state or a transaction signature alone.

## Known Flutter gaps — record only, do not implement in this stage

The current `paypal` Flutter app predates this IA. These are design and integration gaps for later stages:

1. `HomeShellScreen` has a wallet-led home, one generic Marketplace tab, Compare Fee, Activity, and Settings. It has no CLIENT/FREELANCER navigation split or role-specific overview, **Thanh toán**, **Thu nhập**, or **Tài khoản** destination.
2. `AuthUser` does not carry marketplace `userType`, so the app cannot choose the role-aware shell from its current user model.
3. `main.dart` still wires `MockJobRepository`; the Flutter job service covers a small create/list/detail/link-order contract. `RemoteJobRepository` and its old `/checkout-order` linkage reflect an earlier backend contract, and its list expects a bare list rather than the current paged response. Do not assume flipping the repository switch alone completes integration.
4. `JobListScreen`, `PostJobScreen`, and `HireFreelancerScreen` are a single client-like path. They do not cover discovery filters, **Ứng tuyển**, applicant review/assignment, **Công việc của tôi**, submissions, revision review, or state-specific job details. The hire screen asks for a raw PayPal payee ID and creates/captures an order outside the current job workflow.
5. `ActivityScreen` is a static transaction empty state. The wallet still foregrounds balance, Send/Receive, QR, and fee comparison placeholders. Payment, payout, simulation/DEVNET, estimated VND, and tax states are not yet presented as job-linked screens.

These notes describe the checked-in Flutter source, not a request to change it here. Older handoff documents describe previous backend behavior; use the current code and API contracts when planning implementation.

## Next stage: Screen Architecture

Translate this memory into a screen map for both roles: destination → list/section → job detail → state-specific action and result. Define each screen's entry point, permissions, empty/loading/error states, data fields, and truthful money copy. Resolve the `AWAITING_PAYMENT` display mapping and the current Flutter/backend contract gaps there before changing Flutter or backend code.
