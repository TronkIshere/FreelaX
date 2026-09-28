# FreelaX Marketplace — Screen Architecture

This is the screen contract for the wireframe and implementation phases. [PRODUCT_UI_MEMORY.md](PRODUCT_UI_MEMORY.md) is the higher-level product and IA decision source. This document records the currently checked-in Flutter and marketplace backend contracts; it does not define visual styling or implement routes.

## Contract rules

- App routes below are **proposed navigation identifiers**, not existing Flutter routes. Backend paths are real controller mappings under /api/v1. Responses are wrapped in ResponseAPI; paged endpoints return PageResponse with currentPage, pageSize, totalPages, totalElements, and data.
- The shell has exactly five destinations. CLIENT: **Tổng quan / Công việc / Thanh toán / Hoạt động / Tài khoản**. FREELANCER: **Tổng quan / Công việc / Thu nhập / Hoạt động / Tài khoản**. FREELANCER Công việc contains **Khám phá / Ứng tuyển / Công việc của tôi**.
- A job is the common context for work, payment, payout, and tax. The primary work path is OPEN → IN_PROGRESS → SUBMITTED_FOR_REVIEW → COMPLETED, with SUBMITTED_FOR_REVIEW → REVISION_REQUESTED → SUBMITTED_FOR_REVIEW and OPEN → CANCELLED. Applications are PENDING, ACCEPTED, REJECTED, or CANCELLED.
- JobStatus also declares AWAITING_PAYMENT, but current Java sources do not assign it. If encountered from stored data, show an explicit read-only payment-pending/unknown-transition state, fetch payment status, and block work CTAs until the contract is clarified. Never render it as IN_PROGRESS or COMPLETED.
- Creation with optional freelancerId is a verified direct-assignment path: the service sets IN_PROGRESS immediately after creating a checkout order. Otherwise the job starts OPEN and a client can assign a PENDING applicant. Checkout order existence is not capture or payout success.
- COMPLETED means client approval and checkout capture; payout, off-ramp, and tax export have their own statuses. Always label Mock USDC, DEVNET, simulation, and estimated VND when applicable. Do not present a generic wallet balance, QR, Send/Receive, Solana, or Compare Fee as primary navigation.
- Frontend labels: EXISTING, PARTIAL, STALE, PLACEHOLDER, NEW. Backend labels: SUPPORTED, PARTIAL, GAP. PARTIAL backend means an endpoint exists but some required screen data or workflow capability does not.

## Architecture matrix

| ID | Screen | Role | Primary API | Frontend | Backend |
| --- | --- | --- | --- | --- | --- |
| C01 | Client Overview | CLIENT | GET /api/v1/marketplace/jobs | NEW | PARTIAL |
| C02 | Client My Jobs | CLIENT | GET /api/v1/marketplace/jobs | PARTIAL | PARTIAL |
| C03 | Create Job | CLIENT | POST /api/v1/marketplace/jobs | STALE | SUPPORTED |
| C04 | Client Job Detail | CLIENT | GET /api/v1/marketplace/jobs/{jobId} | NEW | SUPPORTED |
| C05 | Applicants | CLIENT | GET /api/v1/marketplace/jobs/{jobId}/applications | NEW | PARTIAL |
| C06 | Submission Review | CLIENT | GET /api/v1/marketplace/jobs/{jobId}/submissions | NEW | SUPPORTED |
| C07 | Client Payments | CLIENT | GET /api/v1/marketplace/jobs | NEW | PARTIAL |
| C08 | Client Payment Detail | CLIENT | GET /api/v1/marketplace/jobs/{jobId}/payment-status | NEW | SUPPORTED |
| C09 | Client Activity | CLIENT | GET /api/v1/notifications | PLACEHOLDER | SUPPORTED |
| C10 | Client Account | CLIENT | GET /api/v1/auth/me | PARTIAL | PARTIAL |
| F01 | Freelancer Overview | FREELANCER | GET /api/v1/marketplace/jobs | NEW | PARTIAL |
| F02 | Discover Jobs | FREELANCER | GET /api/v1/marketplace/jobs/discover | NEW | SUPPORTED |
| F03 | Job Preview | FREELANCER | Discover / applications-me snapshots | NEW | PARTIAL |
| F04 | My Applications | FREELANCER | GET /api/v1/marketplace/jobs/applications/me | NEW | SUPPORTED |
| F05 | Freelancer My Jobs | FREELANCER | GET /api/v1/marketplace/jobs | NEW | PARTIAL |
| F06 | Freelancer Workspace | FREELANCER | GET /api/v1/marketplace/jobs/{jobId} | NEW | SUPPORTED |
| F07 | Submit Work | FREELANCER | POST /api/v1/marketplace/jobs/{jobId}/submit-work | NEW | SUPPORTED |
| F08 | Submission History | FREELANCER | GET /api/v1/marketplace/jobs/{jobId}/submissions | NEW | SUPPORTED |
| F09 | Earnings | FREELANCER | GET /api/v1/marketplace/jobs | NEW | PARTIAL |
| F10 | Payout Detail | FREELANCER | GET /api/v1/marketplace/jobs/{jobId}/payment-status | NEW | SUPPORTED |
| F11 | Tax Records | FREELANCER | GET /api/v1/marketplace/tax-records | NEW | SUPPORTED |
| F12 | Certificate Detail | FREELANCER | GET /api/v1/marketplace/tax-records/{taxRecordId} | NEW | SUPPORTED |
| F13 | Freelancer Activity | FREELANCER | GET /api/v1/notifications | PLACEHOLDER | SUPPORTED |
| F14 | Freelancer Account | FREELANCER | GET /api/v1/auth/me | PARTIAL | PARTIAL |
| A01 | Login | BOTH | POST /api/v1/auth/sign-in | EXISTING | PARTIAL |
| A02 | Choose Role | BOTH | None; registration passes userType | NEW | SUPPORTED |
| A03 | Client Registration | CLIENT | POST /api/v1/auth/register | STALE | SUPPORTED |
| A04 | Freelancer Registration | FREELANCER | POST /api/v1/auth/register | NEW | SUPPORTED |
| A05 | Email Verification | BOTH | None | NEW | GAP |
| A06 | Forgot Password | BOTH | POST /api/v1/auth/forgot-password/send-otp | EXISTING | PARTIAL |
| A07 | Reset Password | BOTH | POST /api/v1/auth/forgot-password/reset | PARTIAL | PARTIAL |

## Action contract at a glance

The state-specific sections below qualify these actions. “Forbidden” means absent or disabled, not merely discouraged.

| ID | Primary action | Secondary action | Forbidden action |
| --- | --- | --- | --- |
| C01 | Open attention job | All jobs / Create Job | Invent dashboard KPIs |
| C02 | Open job | Filter / Create Job | Mutate job from an unverified list state |
| C03 | Create Job | Cancel draft | Submit without required payer fields |
| C04 | State-specific CTA in matrix | State-specific detail link | Any action disallowed by current job state |
| C05 | Assign PENDING applicant | Return to job | Assign non-PENDING applicant |
| C06 | Approve & Pay | Request Revision | Optimistic completion / approve without submission |
| C07 | Open payment detail | Open linked job | Show generic wallet balance as payment status |
| C08 | Refresh status | Open tax record | Manual payout/retry without endpoint |
| C09 | Open notification target | Mark read | Navigate to inaccessible job |
| C10 | Logout | Recovery / debug settings | Edit profile, tax, or bank |
| F01 | Open priority item | Discover Jobs | Show invented aggregate earnings |
| F02 | Open job preview | Search/filter/sort | Open participant-only detail before assignment |
| F03 | Apply when OPEN and eligible | My Applications / back | Reapply or apply from stale snapshot |
| F04 | Open application | Filter by state | Withdraw without endpoint |
| F05 | Open workspace | Filter by work state | Mix discovery with assigned jobs |
| F06 | Submit/revise or view payout by state | Submission History | Client-only actions |
| F07 | Submit Work | Back to workspace | Attachment upload or milestones |
| F08 | View version | Back to workspace | Mutate past submission |
| F09 | Open payout detail | Tax Records | Present contract budget as received income |
| F10 | Refresh payout detail | Tax record | Manual withdrawal/retry |
| F11 | Open tax record | Open linked job | Issue/cancel certificate without API |
| F12 | Download PDF/XML when available | Sync or retry when allowed | Sign/submit/correct certificate manually |
| F13 | Open notification target | Mark read | Open participant-only job for cancelled applicant |
| F14 | Logout | Recovery / debug settings | Edit tax or payout bank without API |
| A01 | Sign in | Register / Forgot Password | Enter shell without trusted role |
| A02 | Choose CLIENT/FREELANCER | Back to Login | Treat choice as account role switch |
| A03 | Register CLIENT | Back to role choice | Omit userType or demand job payer bank |
| A04 | Register FREELANCER | Back to role choice | Omit required tax/bank fields |
| A05 | None until backend exists | Back to Login | Reuse reset OTP as signup verification |
| A06 | Request recovery OTP | Return to Login / resend | Claim email delivery is live |
| A07 | Reset password | Restart recovery | Put reset token in deep link |

## CLIENT screens

## C01 — Client Overview

### Role
CLIENT

### Purpose
Show jobs that need the client's next decision, especially SUBMITTED_FOR_REVIEW and OPEN jobs with applicants.

### Entry points
CLIENT shell Tổng quan; login/restore after role resolution.

### Route
/client/overview

### Backend
- GET /api/v1/marketplace/jobs?page&size — participant jobs, newest first.
- GET /api/v1/marketplace/jobs/{jobId}/applications — owner-only, only when opening a job; no applicant count in JobResponse.
- GET /api/v1/notifications?page&size — optional recent events.

### Required data
JobResponse id, title, status, budgetUsd, freelancerId, createdAt, updatedAt; page metadata. Applicant count is unavailable without per-job calls. No dashboard aggregate API or payout KPI.

### Sections
1. Needs review; 2. Open jobs; 3. Recent jobs and route to C02.

### State-specific rendering
SUBMITTED_FOR_REVIEW → review card to C06. OPEN → manage card to C04; show applicant count only if fetched. Other states → recent/history, not an invented KPI.

### Loading
Skeleton/cards while first job page loads; preserve previous list on refresh.

### Empty
No jobs → create-job prompt; no attention jobs → calm “Không có việc cần xử lý”.

### Error
Retry list; show applicant/notification failures locally without making job cards disappear.

### Success transitions
Tap attention item → C04 or C06; create → C03.

### Deep links
Notification jobId resolves through C04, then to C06 if its live state allows review.

### Current implementation
NEW — WalletTab is a wallet dashboard, not this screen.

### Backend readiness
PARTIAL — paged job list exists; no server attention or aggregate endpoint.

### Notes
Do not calculate global counts from one page or fire an applicant request for every card.

## C02 — Client My Jobs

### Role
CLIENT

### Purpose
Find and manage every job created by this client.

### Entry points
CLIENT Công việc; C01 “all jobs”; job breadcrumbs.

### Route
/client/jobs

### Backend
- GET /api/v1/marketplace/jobs?page&size.

### Required data
PageResponse<JobResponse>; id, title, description, budgetUsd, status, freelancerId, checkoutOrderId, taxExportStatus, createdAt, updatedAt.

### Sections
All / OPEN / IN_PROGRESS / SUBMITTED_FOR_REVIEW / REVISION_REQUESTED / COMPLETED / CANCELLED filters; paged cards; Create Job FAB.

### State-specific rendering
Cards show work-state badge and available next action. AWAITING_PAYMENT is a separate guarded state if returned. Backend has no status query, so a page-local filter must not be presented as the full result set.

### Loading
First-page skeleton; subsequent page spinner; preserve filters and scroll.

### Empty
No jobs → C03; a filter with no verified matches → clear-filter action.

### Error
Retry same page; show an explicit partial-load error on pagination.

### Success transitions
Card → C04; FAB → C03.

### Deep links
/client/jobs/{jobId} enters C04 after owner fetch.

### Current implementation
PARTIAL — JobListScreen lists mock jobs and posts jobs, but has no role/state filters or pagination.

### Backend readiness
PARTIAL — paged list exists; server status filter/counts are absent.

### Notes
For complete filtered pagination, add a backend status filter later or load all pages with a bounded strategy; do not label loaded-page counts as totals.

## C03 — Create Job

### Role
CLIENT

### Purpose
Create a job and its checkout order with validated payer bank information.

### Entry points
C02 FAB; C01 empty state.

### Route
/client/jobs/new

### Backend
- POST /api/v1/marketplace/jobs.

### Required data
CreateJobRequest: required nonblank title, required positive budgetUsd, required nonblank payerBankCode, payerBankAccountNumber, payerBankAccountHolderName; optional description and freelancerId (UUID). Client identity comes from auth, not a form field.

### Sections
1. Job title/description/budget; 2. Payer bank fields; 3. Optional direct freelancer assignment; 4. Review and submit.

### State-specific rendering
Validate title, positive USD budget, and all three payer fields before submit. A chosen freelancerId must identify a FREELANCER; there is no backend directory/picker. If omitted, success status is OPEN; if supplied, service sets IN_PROGRESS.

### Loading
Disable duplicate submit and preserve form while order creation runs.

### Empty
Initial blank form with no preselected freelancer.

### Error
Show field validation and server error; retain inputs. Payment-backend checkout creation may fail, so do not navigate on failure.

### Success transitions
Use returned JobResponse.id and status → C04; refresh C02.

### Deep links
None into an unsaved form.

### Current implementation
STALE — PostJobScreen sends title/description/budget only and uses the mock repository.

### Backend readiness
SUPPORTED — CreateJobRequest and service implementation exist.

### Notes
Direct assignment bypasses applications. Do not fabricate a freelancer picker or suggest that order creation means captured payment.

## C04 — Client Job Detail

### Role
CLIENT owner

### Purpose
One state-driven job workspace for managing applicants, delivery, and the job's payment context.

### Entry points
C01/C02/C07/C09; returned C03 job.

### Route
/client/jobs/{jobId}

### Backend
- GET /api/v1/marketplace/jobs/{jobId}; PATCH same path for OPEN edits.
- POST /api/v1/marketplace/jobs/{jobId}/cancel for OPEN only.
- GET /api/v1/marketplace/jobs/{jobId}/applications; GET /api/v1/marketplace/jobs/{jobId}/submissions; GET /api/v1/marketplace/jobs/{jobId}/payment-status as section data.

### Required data
JobResponse plus optional applicant list, submission history, and JobPaymentStatusResponse. Owner check is enforced by write paths; participant read alone does not grant client actions.

### Sections
Job header and status; description/budget; state action area; applicants or delivery; payment preview; event links.

### State-specific rendering
| Job state | Visible | Primary CTA | Secondary | Forbidden | Related API |
| --- | --- | --- | --- | --- | --- |
| OPEN | Job facts, applicants, checkout context | View applicants (C05) | Edit title/description/budget; cancel with confirmation | Submit, review, payout claim | GET applications; PATCH job; POST cancel |
| IN_PROGRESS | Assigned freelancer ID, work summary, payment context | View payment (C08) | View submissions if any | Assign again, cancel, approve before submission | GET job/payment-status/submissions |
| SUBMITTED_FOR_REVIEW | Latest submission and pending decision | Review submission (C06) | Payment detail | Assign, edit, cancel | GET submissions; C06 owns decision calls |
| REVISION_REQUESTED | Feedback and submission history | View review history | Payment detail | Approve without new submission; assign/cancel | GET submissions |
| COMPLETED | Approved work, separate payout/tax status | View payment detail | View certificate if present | Edit, cancel, repeat approval | GET payment-status; GET tax-records/jobs/{jobId} |
| CANCELLED | Read-only job and application closure | Back to jobs | View past applicants | Edit, assign, approve, pay | GET job/applications |
| AWAITING_PAYMENT | Guarded payment-pending state | View payment detail | Retry data load | Treat as active/complete work | GET payment-status |

### Loading
Fetch job first, then load section data independently; guard actions until current state is known.

### Empty
OPEN with no applicants → no-applicant state; no submissions → no-delivery state.

### Error
404/ownership error → safe back navigation; section failures stay local; on 409 state conflict refetch job.

### Success transitions
Edit/cancel/assignment/review action → refetch job and relevant section; never infer state locally.

### Deep links
Route with jobId; verify live owner access before displaying actions.

### Current implementation
NEW — current tap opens HireFreelancerScreen, which is the old payment flow.

### Backend readiness
SUPPORTED — state and action endpoints exist; payment data remains separate.

### Notes
This replaces the old one-tap “hire and pay” destination. It is not six duplicated detail screens.

## C05 — Applicants

### Role
CLIENT owner

### Purpose
Review applications to an OPEN job and assign a pending freelancer.

### Entry points
C04 OPEN; C01 open-job alert.

### Route
/client/jobs/{jobId}/applicants

### Backend
- GET /api/v1/marketplace/jobs/{jobId}/applications.
- PATCH /api/v1/marketplace/jobs/{jobId}/assign-freelancer with required freelancerId.

### Required data
JobApplicationResponse: id, jobId, freelancerId, status, createdAt. JobResponse status and title. No applicant name, avatar, rating, bio, or proposal text is returned.

### Sections
Job header; PENDING applicants; resolved applications; assignment confirmation.

### State-specific rendering
OPEN + PENDING → assign action. ACCEPTED/REJECTED/CANCELLED → read-only status. Non-OPEN job → history only, no assignment.

### Loading
Fetch job and applications; disable assign while request is in flight.

### Empty
No applications → “Chưa có ứng tuyển”.

### Error
Retry list; assignment conflict/refused role → refetch job/applications.

### Success transitions
Assign returns IN_PROGRESS; return C04 and refresh C02. Other pending applications become REJECTED in the service.

### Deep links
Requires jobId and owner check; notification jobId can enter C04 first.

### Current implementation
NEW

### Backend readiness
PARTIAL — assignment works, but applicant identity/profile data is only freelancerId.

### Notes
Do not invent a profile card or allow assignment of a non-PENDING applicant.

## C06 — Submission Review

### Role
CLIENT owner

### Purpose
Evaluate the latest submitted delivery with its prior versions and choose revision or approval.

### Entry points
C04 SUBMITTED_FOR_REVIEW; WORK_SUBMITTED notification.

### Route
/client/jobs/{jobId}/review

### Backend
- GET /api/v1/marketplace/jobs/{jobId}/submissions.
- POST /api/v1/marketplace/jobs/{jobId}/request-revision with required nonblank feedback, max 10,000 characters.
- POST /api/v1/marketplace/jobs/{jobId}/approve.

### Required data
JobSubmissionResponse id, version, summary, deliverableUrl, status, reviewerFeedback, reviewedAt, createdAt; JobResponse status and checkoutOrderId.

### Sections
Latest submission; deliverable link; version history; feedback composer or approval confirmation.

### State-specific rendering
SUBMITTED_FOR_REVIEW + latest SUBMITTED → primary **Approve & Pay**, secondary **Request Revision**. For REVISION_REQUESTED/COMPLETED, history is read-only. Approve captures checkout and initiates payout; it does not guarantee completed off-ramp.

### Loading
Load submissions and current job before enabling decisions; disable both CTAs during mutation.

### Empty
No submission → return to C04; never show decision CTAs.

### Error
Validation/409 conflict → retain feedback, refetch state; capture failure → remain on review and show server result.

### Success transitions
Revision → C04 REVISION_REQUESTED. Approval → refetch job, then C04 COMPLETED and C08 for actual payout progress. No optimistic COMPLETED.

### Deep links
WORK_SUBMITTED jobId → C04 access/state check → C06.

### Current implementation
NEW

### Backend readiness
SUPPORTED — submission, revision, and approval service rules exist.

### Notes
Display version order from GET submissions; latest is highest version, not merely the first array item.

## C07 — Client Payments

### Role
CLIENT

### Purpose
Find payment progress by job from the Thanh toán destination.

### Entry points
CLIENT shell Thanh toán; C01/C04.

### Route
/client/payments

### Backend
- GET /api/v1/marketplace/jobs?page&size.
- GET /api/v1/marketplace/jobs/{jobId}/payment-status only when a job's payment detail is opened.

### Required data
JobResponse id, title, budgetUsd, status, checkoutOrderId, taxExportStatus; page metadata. List response has no capture or payout status.

### Sections
Paged job-linked payment rows; navigation to C08; optional status explanation.

### State-specific rendering
Show job status separately from payment status. A row may say “Xem trạng thái thanh toán” rather than infer capture from COMPLETED or checkoutOrderId.

### Loading
Load page; do not call payment-status for every row.

### Empty
No jobs → link to C03/C02.

### Error
Retry page; detail error stays on C08.

### Success transitions
Select job → C08.

### Deep links
Payment notification jobId → C08 after participant check.

### Current implementation
NEW — the wallet balance is unrelated.

### Backend readiness
PARTIAL — no payment list/aggregate endpoint or status fields on job list.

### Notes
No generic account balance, card carousel, or fee comparison tab.

## C08 — Client Payment Detail

### Role
CLIENT participant

### Purpose
Inspect one job's checkout, payout progress, chain evidence, off-ramp, and tax/document outcome.

### Entry points
C04/C07; PAYMENT_SENT notification.

### Route
/client/payments/{jobId}

### Backend
- GET /api/v1/marketplace/jobs/{jobId}.
- GET /api/v1/marketplace/jobs/{jobId}/payment-status.
- GET /api/v1/marketplace/tax-records/jobs/{jobId} when tax record exists.

### Required data
JobResponse title/budget/status; JobPaymentStatusResponse jobId, checkoutOrderId/status, simulation, network, onRampStatus, clientPaymentStatus, onChainOffRampStatus, offRampStatus, taxExportStatus, amounts, timestamps/errors, signatures/explorer URLs, estimatedAmountVnd, rate sources. TaxCertificateResponse when present.

### Sections
1. Job; 2. Client payment/checkout; 3. Payout progress; 4. On-chain evidence; 5. Off-ramp; 6. Tax/document.

### State-specific rendering
Each payment stage has its own pending/confirmed/failed/absent state. DEVNET signatures link to explorer only when backend supplies URL. Show “Mock USDC”, “mô phỏng”, and “VND dự kiến” beside respective data. Tax 404 is an absent record, not a failed certificate.

### Loading
Load job and payment independently; tax section can load afterward.

### Empty
No payout record → checkout block still visible; no tax record → “Chưa có chứng từ”.

### Error
JOB_NOT_PAID or upstream failure → explicit payment status error/retry; keep job facts visible.

### Success transitions
Read-only refresh; certificate row → F12-style shared certificate detail with participant access.

### Deep links
jobId is required; verify participant via GET job.

### Current implementation
NEW

### Backend readiness
SUPPORTED — job payment and tax-by-job reads exist. There is no client retry payout CTA.

### Notes
Masking is provided for payout bank account number; never expose raw bank details or claim bank transfer from estimatedAmountVnd.

## C09 — Client Activity

### Role
CLIENT

### Purpose
Show job and payment notifications with read state and a useful destination.

### Entry points
CLIENT shell Hoạt động; optional overview link.

### Route
/client/activity

### Backend
- GET /api/v1/notifications?page&size.
- PATCH /api/v1/notifications/{id}/read.

### Required data
NotificationResponse id, title, message, type, jobId, read, amount, createdAt; page metadata. Backend lists recipient-only, newest first; no unread-count or unread-only endpoint.

### Sections
Paged activity feed; unread marker; notification detail summary.

### State-specific rendering
WORK_SUBMITTED → C06; PAYMENT_SENT → C08; other known types with jobId → C04. Unknown type/jobId-null → readable item without invented target.

### Loading
First page and load-more separately.

### Empty
No notifications → neutral empty state.

### Error
Retry list or mark-read individually.

### Success transitions
Mark read on tap or explicit action; navigate only after target access check.

### Deep links
Notification jobId is the sole target reference; no backend route string is returned.

### Current implementation
PLACEHOLDER — ActivityScreen is a static transaction-history empty state.

### Backend readiness
SUPPORTED — paged list and mark-read exist.

### Notes
Do not assume an unread badge count from a single page.

## C10 — Client Account

### Role
CLIENT

### Purpose
Show account identity, security entry points, app settings, and logout.

### Entry points
CLIENT shell Tài khoản.

### Route
/client/account

### Backend
- GET /api/v1/auth/me.
- POST /api/v1/auth/sign-out with accessToken.
- POST /api/v1/auth/forgot-password/send-otp and reset flow, if changing a forgotten password through A06/A07.

### Required data
/me runtime currently returns id, email, displayName. UserResponse declares userType and misaTaxpayerId, but AuthController.getCurrentUser does not populate them.

### Sections
Identity; security/reset entry; server configuration (debug); about; logout.

### State-specific rendering
Read-only account fields. Hide edit-profile, bank, tax, and role switch CTAs because no account update endpoint exists.

### Loading
Cached identity may render; refresh /me separately.

### Empty
Unauthenticated → A01.

### Error
Profile fetch failure → show cached identity with retry; auth failure → A01.

### Success transitions
Logout clears local auth and routes to A01.

### Deep links
No account subroute backed by a profile-edit API.

### Current implementation
PARTIAL — SettingsScreen shows name/email, debug server URL, about, and logout.

### Backend readiness
PARTIAL — /me omits userType at runtime and exposes no account update or change-password endpoint.

### Notes
Never show tax/bank data from the User entity as if /me returned it.

## FREELANCER screens

## F01 — Freelancer Overview

### Role
FREELANCER

### Purpose
Surface the next action in priority order: REVISION_REQUESTED, IN_PROGRESS, pending applications, then payout/tax attention.

### Entry points
FREELANCER shell Tổng quan; role-aware login restore.

### Route
/freelancer/overview

### Backend
- GET /api/v1/marketplace/jobs?page&size.
- GET /api/v1/marketplace/jobs/applications/me?page&size&status=PENDING.
- GET /api/v1/notifications?page&size for recent failure notices.
- GET /api/v1/marketplace/tax-records?page&size only for a bounded tax-attention preview.

### Required data
JobResponse id/title/status/updatedAt, MyApplicationResponse id/status/job, notifications, tax record status. No dashboard aggregate endpoint or payout totals.

### Sections
Needs revision; active work; pending applications; payout/tax attention.

### State-specific rendering
REVISION_REQUESTED → F06; IN_PROGRESS → F06; PENDING application → F04/F03; PAYOUT_FAILED/TAX_EXPORT_FAILED → F10/F12. Do not synthesize global metrics from page one.

### Loading
Independent sections with bounded requests.

### Empty
No assigned jobs → F02 discover prompt; no attention items → quiet state.

### Error
Section-level retry; never show “zero earnings” after a failed request.

### Success transitions
Tap a card → role-appropriate destination.

### Deep links
Notification jobId can enter F06 only if assigned; otherwise F04/F03 snapshot.

### Current implementation
NEW — WalletTab is not a freelancer work overview.

### Backend readiness
PARTIAL — underlying lists exist, but no priority/aggregate endpoint.

### Notes
Avoid N+1 payment requests on the overview.

## F02 — Discover Jobs

### Role
FREELANCER

### Purpose
Browse unassigned OPEN jobs and narrow the opportunity list.

### Entry points
FREELANCER Công việc → Khám phá; F01 empty state.

### Route
/freelancer/work/discover

### Backend
- GET /api/v1/marketplace/jobs/discover?page&size&keyword&minBudgetUsd&maxBudgetUsd&sort&application.

### Required data
PageResponse<DiscoverJobResponse>: id, title, description, budgetUsd, status, client{id,displayName}, hasApplied, applicationId, applicationStatus, createdAt. sort = NEWEST/BUDGET_ASC/BUDGET_DESC; application = ALL/APPLIED/NOT_APPLIED. Nonnegative budget range, min ≤ max.

### Sections
Keyword search; min/max budget; sort; application filter; paged result cards.

### State-specific rendering
Only OPEN/unassigned results are returned. Applied badge uses hasApplied/applicationStatus; tap opens F03, not participant-only job detail.

### Loading
Debounced search, cancel/ignore stale responses, page spinner.

### Empty
No matches → clear filters; no jobs → opportunity empty state.

### Error
Retain filters and retry same page; show invalid range inline before request.

### Success transitions
Card → F03; returning after apply refreshes applied state.

### Deep links
Unassigned jobId alone cannot be fetched via participant-only GET /jobs/{jobId}; use a carried DiscoverJobResponse snapshot or a later public-preview endpoint.

### Current implementation
NEW — current JobListScreen is a generic mock list.

### Backend readiness
SUPPORTED — all stated filters and pagination exist.

### Notes
The discovery query searches title/description and excludes assigned or non-OPEN jobs.

## F03 — Job Preview

### Role
FREELANCER, including nonparticipant applicants

### Purpose
Inspect a discovery or application job snapshot and apply only when eligible.

### Entry points
F02 result; F04 PENDING/REJECTED/CANCELLED application.

### Route
/freelancer/work/preview/{jobId} with a typed snapshot payload.

### Backend
- POST /api/v1/marketplace/jobs/{jobId}/apply for eligible OPEN jobs.
- GET /api/v1/marketplace/jobs/discover or GET /api/v1/marketplace/jobs/applications/me to obtain a snapshot.
- Do **not** call participant-only GET /api/v1/marketplace/jobs/{jobId} for an unassigned freelancer.

### Required data
DiscoverJobResponse or MyApplicationResponse.job: title, description, budgetUsd, job status, client display name, application state. The latter is a historical snapshot, not a live public detail fetch.

### Sections
Job facts; client display name if present; application status; apply action.

### State-specific rendering
OPEN + not applied from live discover snapshot → Apply. PENDING/REJECTED/CANCELLED snapshot → read-only status; no reapply endpoint. ACCEPTED → use assigned workspace F06 if GET job permits. A snapshot whose job is no longer OPEN must disable Apply and refresh source.

### Loading
Use passed snapshot immediately; revalidate via discover when possible before Apply.

### Empty
No snapshot/deep link only → explain preview unavailable and route to F02/F04.

### Error
Apply conflict or ALREADY_APPLIED → refetch application list/discover; preserve job facts.

### Success transitions
Apply returns PENDING application; route to F04 or keep F03 read-only pending.

### Deep links
Plain jobId is not reliably resolvable for nonparticipants; this is a backend gap, not permission to use owner detail.

### Current implementation
NEW

### Backend readiness
PARTIAL — snapshots and Apply exist, direct public preview/detail does not.

### Notes
Do not invent avatar, ratings, proposal form, or attachment fields.

## F04 — My Applications

### Role
FREELANCER

### Purpose
Track applications separately from assigned work.

### Entry points
FREELANCER Công việc → Ứng tuyển; F03 Apply success; JOB_CANCELLED notification.

### Route
/freelancer/work/applications

### Backend
- GET /api/v1/marketplace/jobs/applications/me?page&size&status.

### Required data
PageResponse<MyApplicationResponse>: id, status, createdAt, updatedAt, job{id,title,description,budgetUsd,status,clientDisplayName,createdAt}. status query accepts PENDING/ACCEPTED/REJECTED/CANCELLED or omitted for all.

### Sections
All / PENDING / ACCEPTED / REJECTED / CANCELLED tabs; paged cards.

### State-specific rendering
PENDING/REJECTED/CANCELLED → F03 snapshot; ACCEPTED → F06 after participant check. No manual cancel/withdraw API exists.

### Loading
First-page skeleton and load-more; preserve selected status.

### Empty
No applications → F02; filtered empty → clear filter.

### Error
Retry same page; keep prior loaded cards.

### Success transitions
Card → F03 or F06 by status and access.

### Deep links
JOB_CANCELLED jobId → this list with cancelled filter/highlight; a direct application ID lookup endpoint does not exist.

### Current implementation
NEW

### Backend readiness
SUPPORTED — paged status-filtered list exists.

### Notes
The response contains a compact job snapshot, enough for a preview after the job leaves discovery.

## F05 — Freelancer My Jobs

### Role
FREELANCER

### Purpose
Show assigned work only, never discovery or merely pending applications.

### Entry points
FREELANCER Công việc → Công việc của tôi; F01.

### Route
/freelancer/work/my-jobs

### Backend
- GET /api/v1/marketplace/jobs?page&size.

### Required data
PageResponse<JobResponse>; id, title, description, budgetUsd, status, clientUserId, freelancerId, createdAt, updatedAt.

### Sections
IN_PROGRESS / SUBMITTED_FOR_REVIEW / REVISION_REQUESTED / COMPLETED work states; paged jobs.

### State-specific rendering
IN_PROGRESS → submit entry; SUBMITTED_FOR_REVIEW → waiting; REVISION_REQUESTED → revise entry; COMPLETED → history/evidence. Verify freelancerId equals current user before F06.

### Loading
Page skeleton/load-more, preserving state selection.

### Empty
No assigned jobs → F02 discovery.

### Error
Retry page; avoid mixing stale discovery results.

### Success transitions
Card → F06.

### Deep links
Assigned jobId may resolve via GET job; nonparticipant failure returns to F04/F02.

### Current implementation
NEW

### Backend readiness
PARTIAL — assigned jobs are listable, but no server job-status filter for complete paged tabs.

### Notes
Since userType is exclusive, a freelancer's participant list should be assigned jobs; still verify freelancerId. Do not fake filtered totals from loaded pages.

## F06 — Freelancer Workspace

### Role
Assigned FREELANCER

### Purpose
One state-driven work detail for delivery, revision, and completed evidence.

### Entry points
F05; JOB_ASSIGNED/REVISION_REQUESTED/WORK_APPROVED notifications.

### Route
/freelancer/work/jobs/{jobId}

### Backend
- GET /api/v1/marketplace/jobs/{jobId}.
- GET /api/v1/marketplace/jobs/{jobId}/submissions.
- GET /api/v1/marketplace/jobs/{jobId}/payment-status for linked evidence.

### Required data
JobResponse and ordered JobSubmissionResponse list; optional payment status. Participant read is enforced server-side.

### Sections
Job brief; status/action area; latest submission and feedback; history; payout link.

### State-specific rendering
| Job state | Visible | Primary CTA | Secondary | Forbidden |
| --- | --- | --- | --- | --- |
| IN_PROGRESS | Brief, assigned work | Submit Work → F07 | F08 history | Approve, assign, cancel |
| SUBMITTED_FOR_REVIEW | Submitted version and waiting state | None; read-only | F08 | Duplicate submit, approve |
| REVISION_REQUESTED | Feedback and prior version | Submit Revised Work → F07 | F08 | Approve, assign |
| COMPLETED | Approved history and evidence | View Payout Detail → F10 | F08 | Submit or revise |
| OPEN/CANCELLED/AWAITING_PAYMENT | Guarded unexpected state | None | Back to list | All work mutations |

### Loading
Fetch job first; disable CTAs until status and assignment are verified.

### Empty
No submissions in IN_PROGRESS is normal; show first-submission entry.

### Error
403/404 → no participant access; 409 on submit → refetch job.

### Success transitions
F07 success → refetch to SUBMITTED_FOR_REVIEW; completed → F10 remains status-driven.

### Deep links
jobId notification target requires participant fetch; no local cached status authorization.

### Current implementation
NEW

### Backend readiness
SUPPORTED — participant detail, submissions, submit-work, and payment-status exist.

### Notes
Never equate work completion with bank payout completion.

## F07 — Submit Work

### Role
Assigned FREELANCER

### Purpose
Send one delivery or revised delivery for client review.

### Entry points
F06 IN_PROGRESS or REVISION_REQUESTED.

### Route
/freelancer/work/jobs/{jobId}/submit

### Backend
- POST /api/v1/marketplace/jobs/{jobId}/submit-work.

### Required data
SubmitWorkRequest: required nonblank summary, max 10,000 characters; optional deliverableUrl, max 2,048 characters. Current JobResponse status and prior feedback for revisions.

### Sections
Job context; summary; optional deliverable URL; revision feedback context; submit confirmation.

### State-specific rendering
IN_PROGRESS → “Gửi bàn giao”; REVISION_REQUESTED → “Gửi bản chỉnh sửa”. No upload, ZIP, image, milestones, or chat controls.

### Loading
Disable repeat submit; preserve draft until server responds.

### Empty
Blank summary cannot submit; deliverableUrl may remain empty.

### Error
Validation or 409 status change → keep inputs and refetch job before retry.

### Success transitions
Response contains new version/status SUBMITTED; return F06 SUBMITTED_FOR_REVIEW and refresh F08.

### Deep links
Only from an authenticated assigned F06 state, not a public URL.

### Current implementation
NEW

### Backend readiness
SUPPORTED — SubmitWorkRequest and service action exist.

### Notes
The service trims summary and optional URL; it versions each submission automatically.

## F08 — Submission History

### Role
Assigned FREELANCER

### Purpose
Inspect every delivery and review decision in version order.

### Entry points
F06; F10 completed-work link.

### Route
/freelancer/work/jobs/{jobId}/submissions

### Backend
- GET /api/v1/marketplace/jobs/{jobId}/submissions.

### Required data
JobSubmissionResponse version, summary, deliverableUrl, status, reviewerFeedback, reviewedAt, createdAt, updatedAt. Status vocabulary: SUBMITTED, REVISION_REQUESTED, APPROVED.

### Sections
Latest version; chronological version history; per-version feedback and links.

### State-specific rendering
SUBMITTED → awaiting review; REVISION_REQUESTED → show reviewerFeedback/reviewedAt; APPROVED → approved record. Keep distinct from overall job state.

### Loading
Load versions once per entry; refresh after new submission.

### Empty
No submissions → back to F06 IN_PROGRESS.

### Error
Retry history; invalid/unsafe URL must be shown as text rather than blindly opened.

### Success transitions
Read-only; revision CTA remains on F06/F07 based on live job state.

### Deep links
Requires assigned jobId and participant read.

### Current implementation
NEW

### Backend readiness
SUPPORTED — service returns findByJobIdOrderByVersionAsc.

### Notes
Do not fabricate attachments beyond deliverableUrl.

## F09 — Earnings

### Role
FREELANCER

### Purpose
Find job-linked income and payout status from Thu nhập.

### Entry points
FREELANCER shell Thu nhập; F01/F06.

### Route
/freelancer/earnings

### Backend
- GET /api/v1/marketplace/jobs?page&size.
- GET /api/v1/marketplace/tax-records?page&size for a separate tax list.
- GET /api/v1/marketplace/jobs/{jobId}/payment-status only for an opened payout detail.

### Required data
JobResponse id/title/budgetUsd/status; optional tax records by job. There is no public payout-record list or aggregate earnings endpoint.

### Sections
Paged job-linked earnings entries; payment/payout state explanation; tax-record link.

### State-specific rendering
Job budget is contract value, not received income. On the list, say “Xem tiến độ nhận tiền” unless per-job status has been fetched. No aggregate earned/paid/available totals without backend support.

### Loading
Load jobs page; avoid N+1 payment-status calls across all jobs.

### Empty
No assigned jobs → F02; no tax records → F11 empty state.

### Error
Retry list; do not turn missing payout data into a zero balance.

### Success transitions
Job → F10; tax link → F11.

### Deep links
PAYMENT_RECEIVED/PAYOUT_FAILED jobId → F10.

### Current implementation
NEW — wallet balance is not earnings.

### Backend readiness
PARTIAL — per-job status and tax list exist, payout list/aggregate does not.

### Notes
For large histories or totals, plan a server-side aggregate/list endpoint rather than fetching hundreds of payment records in Flutter.

## F10 — Payout Detail

### Role
Assigned FREELANCER

### Purpose
Explain one job's post-approval money path with evidence and honest labels.

### Entry points
F06/F09; PAYMENT_RECEIVED/PAYOUT_FAILED notifications.

### Route
/freelancer/earnings/jobs/{jobId}

### Backend
- GET /api/v1/marketplace/jobs/{jobId}.
- GET /api/v1/marketplace/jobs/{jobId}/payment-status.
- GET /api/v1/marketplace/tax-records/jobs/{jobId} for tax linkage.

### Required data
Job title/budget/status; JobPaymentStatusResponse: checkoutOrderStatus, simulation, network, onRampStatus, amountUsdcReceived, clientPaymentStatus, onChainOffRampStatus, offRampStatus, signatures/explorer URLs, payout bank mask, estimatedAmountVnd, usdcToVndRateSource, taxableAmountVnd, taxRateSource, timestamps and errors.

### Sections
1. Work; 2. Client payment; 3. On-ramp; 4. On-chain payout/evidence; 5. Off-ramp; 6. Estimated VND and rate source; 7. Tax.

### State-specific rendering
Render each stage independently: not started, submitted/pending, confirmed, simulated, completed, failed, unavailable. Current payout creation sets simulation=true; mock on-ramp uses Mock USDC and DEVNET context where reported. Estimated VND is never a bank receipt. Show the network beside each explorer link and distinguish payment, withdrawal, and completion signatures.

### Loading
Load job first, then payment and tax sections; refresh independently.

### Empty
No payout record yet → “Chưa có dữ liệu payout”; absent tax record → “Chưa có chứng từ”.

### Error
JOB_NOT_PAID/upstream failure → status unavailable with retry; retain job context and do not display success.

### Success transitions
Read-only refresh; tax record → F12.

### Deep links
Notification jobId → participant check → F10.

### Current implementation
NEW

### Backend readiness
SUPPORTED — per-job payment and tax-by-job responses exist.

### Notes
Do not expose a manual withdrawal or retry CTA; no such user endpoint is present.

## F11 — Tax Records

### Role
FREELANCER

### Purpose
Find job-linked tax/certificate records and their current issuance status.

### Entry points
F09; F10 tax section; TAX_EXPORT_FAILED notification.

### Route
/freelancer/earnings/tax-records

### Backend
- GET /api/v1/marketplace/tax-records?page&size.
- GET /api/v1/marketplace/jobs/certificates is a separate freelancer-only unpaged certificate summary, not the primary status list.

### Required data
PageResponse<TaxCertificateResponse>: id, jobId/jobTitle, status/statusLabel, amountUsd, taxableIncomeVnd, taxWithheldVnd, certificateNumber/symbol, lookupCode, dates. TaxCertificateStatus includes PENDING_EXPORT, EXPORT_FAILED, DRAFT, SIGNED, SUBMITTING, SUBMITTED, ACCEPTED, REJECTED, CORRECTION_REQUIRED, REPLACED, CANCELLED.

### Sections
Paged record list; status chips; job link; certificate detail.

### State-specific rendering
Display actual statusLabel. EXPORT_FAILED → retry is available from F12; pending/draft is not an issued certificate. Do not treat list absence as “tax exempt”.

### Loading
First-page and load-more.

### Empty
No records → “Chưa có bản ghi thuế”; no fabricated zero-tax claim.

### Error
Retry page; preserve loaded entries.

### Success transitions
Record → F12; job → F06 if participant.

### Deep links
TAX_EXPORT_FAILED jobId → GET tax-records/jobs/{jobId}, then F12 if found; otherwise F11 with error context.

### Current implementation
NEW

### Backend readiness
SUPPORTED — participant list, detail, job lookup, sync and retry exist.

### Notes
Tax list includes records where the caller is freelancer or client; this screen is routed by role, and record access is participant-checked.

## F12 — Certificate Detail

### Role
FREELANCER; CLIENT may open a read-only shared detail from C08

### Purpose
Show one tax record and make only valid document/status actions available.

### Entry points
F11/F10; C08 tax section; TAX_EXPORT_FAILED notification.

### Route
/freelancer/earnings/tax-records/{taxRecordId}; client equivalent /client/payments/tax-records/{taxRecordId}.

### Backend
- GET /api/v1/marketplace/tax-records/{taxRecordId}.
- POST /api/v1/marketplace/tax-records/{taxRecordId}/sync.
- POST /api/v1/marketplace/tax-records/{taxRecordId}/retry-export.
- GET /api/v1/marketplace/tax-records/{taxRecordId}/pdf?inline=false; GET /api/v1/marketplace/tax-records/{taxRecordId}/xml?inline=false.

### Required data
TaxCertificateResponse status/statusLabel, jobId/title, rate and tax amounts, certificateNumber, certificateSymbol, lookupCode, misaCertificateId, transaction/submission/tax-authority references, issuedAt/submittedAt/lastSyncedAt.

### Sections
Job/tax amounts; issuance timeline; lookup fields; document actions; sync/retry status.

### State-specific rendering
Sync only when misaCertificateId exists. Retry export only for EXPORT_FAILED. PDF/XML are server-gated by misaCertificateId and participant ownership; show download only when id exists, and handle upstream file errors. Do not call DRAFT or PENDING_EXPORT an issued certificate merely because a record exists.

### Loading
Load record first; download and sync have independent progress.

### Empty
Unknown taxRecordId/404 → back to F11 or job detail.

### Error
Retry detail/sync; retain status on export or file errors.

### Success transitions
Sync/retry response updates this detail; downloads use returned PDF/XML bytes.

### Deep links
Prefer taxRecordId; notification supplies jobId, so resolve via GET tax-records/jobs/{jobId} first.

### Current implementation
NEW

### Backend readiness
SUPPORTED — all listed endpoints and participant checks exist.

### Notes
There is no frontend endpoint to issue, sign, submit, correct, or cancel a certificate manually. Backend may auto-advance some states.

## F13 — Freelancer Activity

### Role
FREELANCER

### Purpose
Read job, payout, and tax notifications and move to the correct job context.

### Entry points
FREELANCER shell Hoạt động; F01 recent event.

### Route
/freelancer/activity

### Backend
- GET /api/v1/notifications?page&size.
- PATCH /api/v1/notifications/{id}/read.

### Required data
NotificationResponse id, type, title, message, jobId, read, amount, createdAt; PageResponse metadata. NotificationType source includes JOB_ASSIGNED, JOB_CANCELLED, WORK_SUBMITTED, REVISION_REQUESTED, WORK_APPROVED, PAYMENT_SENT, PAYMENT_RECEIVED, TAX_EXPORT_FAILED, PAYOUT_FAILED.

### Sections
Paged feed; unread/read state; destination resolution.

### State-specific rendering
| Type | Destination |
| --- | --- |
| JOB_ASSIGNED | F06 Workspace |
| JOB_CANCELLED | F04 My Applications, cancelled filter/highlight |
| REVISION_REQUESTED | F06 Workspace |
| WORK_APPROVED | F06 Completed Job |
| PAYMENT_RECEIVED | F10 Payout Detail |
| PAYOUT_FAILED | F10 Payout Detail |
| TAX_EXPORT_FAILED | F12 via tax-records/jobs/{jobId}, fallback F11 |
| Other/unknown | Readable item; resolve only if role/access allows |

### Loading
First page and load-more separately.

### Empty
No notifications → quiet empty state.

### Error
Retry page/read mutation; destination 404 → list fallback with explanation.

### Success transitions
Mark read; navigate after current role and target access check.

### Deep links
Backend provides jobId, not a screen route or taxRecordId. JOB_CANCELLED cannot use participant-only job detail for a pending applicant.

### Current implementation
PLACEHOLDER — ActivityScreen has no notification integration.

### Backend readiness
SUPPORTED — notification type, paged list, read mutation exist.

### Notes
No unread-count endpoint; do not imply complete unread counts from one page.

## F14 — Freelancer Account

### Role
FREELANCER

### Purpose
Show identity and safe account/settings actions; make tax and payout-bank registration context understandable.

### Entry points
FREELANCER shell Tài khoản.

### Route
/freelancer/account

### Backend
- GET /api/v1/auth/me.
- POST /api/v1/auth/sign-out.
- Forgot/reset endpoints through A06/A07.

### Required data
/me runtime id, email, displayName only. User entity stores taxCode, identityNumber, nationality, taxAddress, bankCode/account/holder; /me does not expose these fields or userType at runtime.

### Sections
Personal identity; tax info status (only if a verified API later provides it); payout-bank status (same); security; debug settings; logout.

### State-specific rendering
Read-only known fields. Hide edit tax/bank/profile, bank verification, and role-switch actions: no update/readback endpoint for those fields exists.

### Loading
Cached identity first, /me refresh separately.

### Empty
Unauthenticated → A01.

### Error
Profile retry; avoid displaying “missing tax/bank details” when response merely omits them.

### Success transitions
Logout → A01; reset-password entry → A06.

### Deep links
No editable tax/bank destination in current API.

### Current implementation
PARTIAL — SettingsScreen offers name/email, debug server URL, about, logout.

### Backend readiness
PARTIAL — identity read exists, but role/tax/bank readback and account update APIs do not.

### Notes
Registration stores freelancer tax/bank data; absence from /me is a response gap, not evidence that it was never saved.

## AUTH screens

## A01 — Login

### Role
BOTH; role unknown until resolved from a trusted profile response.

### Purpose
Authenticate and enter the correct role-aware shell.

### Entry points
Splash unauthenticated; logout; registration/reset success.

### Route
/auth/login

### Backend
- POST /api/v1/auth/sign-in with email and password.
- GET /api/v1/auth/me after sign-in for a role-aware shell, once the runtime response includes userType.
- POST /api/v1/auth/refresh-token uses an HttpOnly refreshToken cookie, not a JSON body.

### Required data
SignInResponse status, accessToken, refreshToken, userId, email; trusted userType must come from a corrected /me or changed sign-in contract. Current /me implementation omits it.

### Sections
Email; password; forgot-password and registration entry.

### State-specific rendering
Success token without resolved role must stay in a gated loading/error state; never default to CLIENT or FREELANCER. Invalid credentials remain on form.

### Loading
Disable duplicate login; resolve profile before routing.

### Empty
Blank form.

### Error
Inline auth/network/role-resolution errors; keep credentials only as appropriate for retry.

### Success transitions
Verified CLIENT → C01; verified FREELANCER → F01.

### Deep links
After authentication and role check, resume only a permitted target; otherwise role overview.

### Current implementation
EXISTING — LoginScreen calls sign-in, then unconditionally opens HomeShellScreen.

### Backend readiness
PARTIAL — sign-in works, but sign-in lacks userType and /me does not populate it.

### Notes
AuthUser omits/persists no role, and AuthService refreshProfile only runs if displayName is missing. Flutter refresh sends token in JSON while controller reads a cookie; account for this in implementation planning.

## A02 — Choose Role

### Role
BOTH before registration; this chooses the new account type, not a post-login role switch.

### Purpose
Collect CLIENT or FREELANCER intent and open the corresponding registration form.

### Entry points
A01 registration link.

### Route
/auth/choose-role

### Backend
- No choice endpoint. POST /api/v1/auth/register accepts required userType = CLIENT or FREELANCER.

### Required data
Selected UserType enum value; preserve it into A03/A04.

### Sections
Two clear role choices with role-specific obligations.

### State-specific rendering
CLIENT → A03. FREELANCER → A04. The choice is immutable for this registration request; no backend role-switch endpoint exists.

### Loading
None before navigation.

### Empty
No role selected; continue disabled until selection.

### Error
No network call here; invalid local selection stays on screen.

### Success transitions
Navigate to selected form with userType.

### Deep links
Registration links may preselect a role but must still show which role is being created.

### Current implementation
NEW — current RegisterScreen has no role selection.

### Backend readiness
SUPPORTED — registration contract has UserType; a separate endpoint is unnecessary.

### Notes
Do not reinterpret this as switching roles for an existing authenticated account.

## A03 — Client Registration

### Role
CLIENT

### Purpose
Create a client account.

### Entry points
A02 CLIENT choice.

### Route
/auth/register/client

### Backend
- POST /api/v1/auth/register.

### Required data
RegisterRequest: nonblank valid email, password at least 6 characters, nonblank displayName, required userType=CLIENT. Tax/bank registration fields are optional for this role.

### Sections
Display name; email; password; CLIENT role confirmation.

### State-specific rendering
Validation errors stay in form; duplicate email is server error. Do not ask for per-job payer bank details at account signup; CreateJobRequest asks for those at C03.

### Loading
Disable duplicate registration.

### Empty
Blank required fields.

### Error
Keep inputs and show field/server error.

### Success transitions
Registration returns UserResponse → A01 login; no registration email verification call exists.

### Deep links
Only via A02 or explicit role-prefilled registration link.

### Current implementation
STALE — RegisterScreen sends only email/password/displayName and omits userType.

### Backend readiness
SUPPORTED — registration service handles CLIENT.

### Notes
Registration does not auto-sign-in; no verification gate should be shown as if active.

## A04 — Freelancer Registration

### Role
FREELANCER

### Purpose
Create a freelancer account with required tax and payout-bank information.

### Entry points
A02 FREELANCER choice.

### Route
/auth/register/freelancer

### Backend
- POST /api/v1/auth/register.

### Required data
Common required email/password/displayName/userType=FREELANCER; service also requires nonblank taxCode, identityNumber, nationality, taxAddress, plus bankCode and nonblank bankAccountNumber. bankAccountHolderName is optional and defaults to displayName. BankCode enum: VIETCOMBANK, VIETINBANK, BIDV, AGRIBANK, TECHCOMBANK, MBBANK, ACB, VPBANK, SACOMBANK, TPBANK.

### Sections
Personal credentials; tax identity; payout bank; review/submit.

### State-specific rendering
Validate common fields and freelancer-specific tax/bank fields. Do not invent KYC upload or bank verification steps.

### Loading
Disable duplicate submit, preserve all entered fields.

### Empty
Blank form, no silent default bank.

### Error
Field/server error retained in context; TAX_INFO_REQUIRED and BANK_INFO_REQUIRED map to their sections.

### Success transitions
Registration response → A01; no auto-login.

### Deep links
Role-prefilled registration route still confirms FREELANCER.

### Current implementation
NEW — generic RegisterScreen has none of these role-specific fields.

### Backend readiness
SUPPORTED — registration service persists these fields.

### Notes
Current /me does not return saved tax/bank fields for later account display.

## A05 — Email Verification

### Role
BOTH, only if a future signup policy introduces it.

### Purpose
Reserved screen definition so wireframes do not confuse password-reset OTP with signup verification.

### Entry points
None in current registration flow.

### Route
/auth/verify-email (reserved; inactive).

### Backend
- GAP: no registration email-verification send/verify endpoint in AuthController. Existing forgot-password OTP endpoints are for password reset only.

### Required data
Future verification challenge/token and email; not defined by current source.

### Sections
Future code entry/status; no active implementation contract.

### State-specific rendering
Do not place a working verification step between A03/A04 and A01 today.

### Loading
Not applicable until API exists.

### Empty
Not applicable.

### Error
Not applicable; no endpoint to invoke.

### Success transitions
Undefined until backend contract is added.

### Deep links
None supported.

### Current implementation
NEW — OtpVerificationScreen is for password reset, not signup.

### Backend readiness
GAP — no email verification feature is implemented.

### Notes
If product requires verified email, design and implement a distinct backend contract first.

## A06 — Forgot Password

### Role
BOTH

### Purpose
Start password recovery for an existing email.

### Entry points
A01 “Quên mật khẩu?”.

### Route
/auth/forgot-password

### Backend
- POST /api/v1/auth/forgot-password/send-otp with email.
- POST /api/v1/auth/forgot-password/verify-otp with email and otp.

### Required data
ForgotPasswordRequest valid email; VerifyForgotPasswordOtpRequest email and nonblank otp; verify returns reset-token string.

### Sections
Email entry; password-reset OTP entry (existing OtpVerificationScreen).

### State-specific rendering
Send → OTP; verified OTP → A07. The current OTPServiceImpl stores a five-minute OTP in Redis and logs it server-side; it does not deliver email despite controller message.

### Loading
Separate send, verify, and resend progress.

### Empty
Blank email or OTP cannot proceed.

### Error
EMAIL_NOT_FOUND/INVALID_OTP and network failure stay in their step.

### Success transitions
Pass returned reset token to A07.

### Deep links
Never embed reset token in a public/deep-link URL.

### Current implementation
EXISTING — ForgotPasswordScreen and OtpVerificationScreen implement the UI flow.

### Backend readiness
PARTIAL — API exists, but OTP delivery is log-only and unsuitable for a real user flow.

### Notes
Do not tell the user “email sent” until delivery is implemented.

## A07 — Reset Password

### Role
BOTH

### Purpose
Set a new password after a verified recovery OTP.

### Entry points
A06 OTP verification success.

### Route
/auth/reset-password (in-memory reset token only).

### Backend
- POST /api/v1/auth/forgot-password/reset.

### Required data
ResetPasswordRequest: nonblank resetToken, newPassword at least 6 characters, nonblank confirmPassword; service requires password equality.

### Sections
New password; confirmation; submit.

### State-specific rendering
Only a valid reset token enables form. Expired/invalid token returns to A06; success returns A01.

### Loading
Disable repeat submit.

### Empty
No token → A06; blank passwords → inline validation.

### Error
Mismatch/invalid token/network error keeps safe form state and clear retry path.

### Success transitions
Show completion, clear token, navigate A01.

### Deep links
No reset-token URL contract exists.

### Current implementation
PARTIAL — second step inside ForgotPasswordScreen performs reset; OTP delivery remains the blocker.

### Backend readiness
PARTIAL — reset API exists, but end-to-end self-service recovery lacks email delivery.

### Notes
This is password recovery, not an authenticated change-password setting.

## Navigation map

~~~text
AUTH
Login (A01)
├── Choose Role (A02)
│   ├── Client Registration (A03) ──> Login
│   └── Freelancer Registration (A04) ──> Login
├── Forgot Password (A06) ──> OTP ──> Reset Password (A07) ──> Login
└── Email Verification (A05, reserved/inactive)

CLIENT
Tổng quan (C01) ──> attention job ──> Client Job Detail (C04)
Công việc ──> My Jobs (C02) ├── Create Job (C03)
                              └── Job Detail (C04) ├── Applicants (C05)
                                                   ├── Submission Review (C06)
                                                   └── Payment Detail (C08)
Thanh toán ──> Client Payments (C07) ──> Payment Detail (C08)
Hoạt động ──> Client Activity (C09) ──> access-checked job/review/payment
Tài khoản ──> Client Account (C10)

FREELANCER
Tổng quan (F01) ──> priority job/application/payout
Công việc ├── Khám phá (F02) ──> Job Preview (F03) ──> Apply
          ├── Ứng tuyển (F04) ──> Job Preview (F03) / accepted Workspace
          └── Công việc của tôi (F05) ──> Workspace (F06)
                                        ├── Submit Work (F07)
                                        ├── Submission History (F08)
                                        └── Payout Detail (F10)
Thu nhập ──> Earnings (F09) ├── Payout Detail (F10)
                            └── Tax Records (F11) ──> Certificate Detail (F12)
Hoạt động ──> Freelancer Activity (F13) ──> access-checked destination
Tài khoản ──> Freelancer Account (F14)
~~~

## Implementation blockers / follow-ups

### P0 — blocks core user flow

1. **Role cannot be reliably resolved after login/restore.** SignInResponse has no userType; AuthController.getCurrentUser builds UserResponse without setting userType even though the DTO declares it. Flutter AuthUser has no userType or persistence field, and login/splash route unconditionally to the old HomeShellScreen. Fix this contract before enabling role-aware navigation.
2. **Flutter registration cannot create the required marketplace accounts.** RemoteAuthRepository sends only email/password/displayName; RegisterRequest requires userType, and freelancer registration service requires tax and bank fields. Build A02–A04 against the actual contract.
3. **Job data integration is stale.** main.dart selects MockJobRepository; RemoteJobRepository expects a bare job list and old /checkout-order path, while JobController returns PageResponse and has no such linkage endpoint. The Flutter job API layer also lacks PATCH support required for assignment/edit/read-notification actions. Reconcile clients before wiring C/F workflows.

### P1 — blocks complete UX

1. **Job list filtering and dashboards:** GET /jobs has pagination but no status filter or summary endpoint; C01/C02/F01/F05 cannot show complete server-filtered counts or priority lists from one page. Plan server filters/aggregates or a bounded, explicitly partial UI.
2. **Payment/earnings overview:** JobResponse does not expose payout stage, and there is no public payout-record list/aggregate. C07/F09 should link jobs to detail without invented balances or N+1 requests; add a summary API for scaled lists.
3. **Applicant and preview data:** JobApplicationResponse has only IDs/status/time, with no name/bio/rating. Unassigned freelancers cannot GET participant-only /jobs/{jobId}; F03 must use discover/application snapshots. A dedicated preview and richer applicant data are follow-ups.
4. **Account details/editing:** /me omits tax/bank readback and no profile/tax/bank update endpoint exists. C10/F14 remain read-only for these fields.
5. **Password recovery delivery:** OTPServiceImpl logs password-reset OTP rather than emailing it. A06/A07 are API-backed but cannot be presented as delivered-email self-service until fixed.
6. **Session refresh contract:** Flutter RemoteAuthRepository posts refreshToken in JSON; AuthController.refreshToken reads an HttpOnly refreshToken cookie. The current Flutter request is not aligned with the backend's refresh contract.

### P2 — polish/follow-up

1. **Email verification:** A05 has no backend flow; keep inactive unless the product explicitly requires signup verification.
2. **Notification badges and rejected-application messaging:** No unread-count API and no direct application-by-ID lookup; avoid inaccurate badges and route via list/jobId. Assignment notifies the selected freelancer but the current service does not notify rejected applicants.
3. **Unexpected AWAITING_PAYMENT:** Enum value has no assignment in current Java service; keep guarded rendering and decide migration/mapping if existing records surface it.
4. **Old UI assets:** WalletTab, CompareFeeScreen, HireFreelancerScreen, and generic ActivityScreen are not the destination architecture. Reuse shared styling/widgets where appropriate during implementation; do not expose stale wallet/payment claims.

## Audit trail

- Flutter: main.dart; features/home, marketplace, activity, settings, auth; core/models, domain, data, services; shared widgets; splash routing.
- Backend: AuthController, JobController, NotificationController, TaxCertificateController; AuthenticationServiceImpl, JobServiceImpl, NotificationServiceImpl, TaxCertificateServiceImpl, PayoutServiceImpl; matching DTOs, entities, and repositories.
- All paths and fields above were checked against those sources. Older README/reference files describe prior contracts and were not used to invent endpoints.
