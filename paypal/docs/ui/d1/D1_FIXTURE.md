# D1.1 — Fixed Marketplace Fixture

**ALL D1 STUDIES MUST USE THIS FIXTURE UNCHANGED.** D1.2 changes composition only. The names, amounts, job facts, application context, visible actions, and temporary palette are identical in A, B, and C. This is deterministic study data, not a claim that these records exist on a server.

## A. Contract sources inspected

- `marketplace-backend/src/main/java/com/marketplace/backend/controller/JobController.java`: `/discover`, `/applications/me`, `/{jobId}`, `/{jobId}/applications`, and `/apply` routes and query parameters.
- `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/JobServiceImpl.java`: role checks, response mapping, participant/owner checks, application and OPEN-job actions.
- `marketplace-backend/src/main/java/com/marketplace/backend/repository/JobRepository.java`: Discover returns `OPEN` jobs with no assigned freelancer; keyword searches title/description.
- Response DTOs: `DiscoverJobResponse`, `JobClientSummaryResponse`, `MyApplicationResponse`, `MyApplicationJobResponse`, `JobResponse`, `JobApplicationResponse`, `PageResponse`.
- Read-only product constraints: `PRODUCT_UI_MEMORY.md` and `SCREEN_ARCHITECTURE.md` on local `feat/marketplace-ui-workflow`.

## B. Supported fields and access

| Surface | Returned facts | Access |
| --- | --- | --- |
| Discover | `PageResponse` metadata and `DiscoverJobResponse`: `id`, `title`, `description`, `budgetUsd`, `status`, `client{id,displayName}`, `hasApplied`, `applicationId`, `applicationStatus`, `createdAt` | FREELANCER only; `OPEN`, unassigned results |
| My Applications | Paged `MyApplicationResponse`: application `id`, `status`, `createdAt`, `updatedAt`, and embedded job `id`, `title`, `description`, `budgetUsd`, `status`, `clientDisplayName`, `createdAt` | FREELANCER only |
| Job Detail | `JobResponse`: `id`, `title`, `description`, `budgetUsd`, `clientUserId`, `freelancerId`, `status`, `checkoutOrderId`, `taxExportStatus`, `createdAt`, `updatedAt` | Client owner or assigned freelancer only; an unassigned discover viewer cannot fetch this detail |
| Client applications | List of `JobApplicationResponse`: `id`, `jobId`, `freelancerId`, `status`, `createdAt` | Client owner only; no applicant display name, profile, or rating in this response |

Discover query controls available: `keyword`, `minBudgetUsd`, `maxBudgetUsd`, `sort` (`NEWEST`, `BUDGET_ASC`, `BUDGET_DESC`), and `application` (`ALL`, `APPLIED`, `NOT_APPLIED`). Page and size are supported. The APPLY action maps to `POST /api/v1/marketplace/jobs/{jobId}/apply`; it must recheck eligibility against current server state. The prototype is static and does not send requests.

## C. Unsupported or intentionally omitted

No rating, applicant/proposal count, skill tag, deadline, category, verified badge, company size, hourly rate, location, remote badge, estimated duration, “top client,” or freelancer reputation appears in the inspected response DTOs. None may appear in these studies. `North Studio` is solely `client.displayName`; it conveys no company verification or business attributes. Do not show named applicants in the client proof. Do not suggest a discover viewer can use participant-only `GET /jobs/{id}`. Payment, payout, and tax details belong to another set and are omitted.

## D. Primary focus job

This is the first item of the shared Discover page. All four records use the same supported schema and one fixed timestamp (`2026-09-29T09:00:00`, as a study value without an implied timezone).

```json
{
  "id": "11111111-1111-4111-8111-111111111111",
  "title": "Build Solana Payment Infrastructure",
  "description": "Implement settlement, reconciliation, and failure handling for a cross-border payout workflow.",
  "budgetUsd": 500.00,
  "status": "OPEN",
  "client": {
    "id": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
    "displayName": "North Studio"
  },
  "hasApplied": false,
  "applicationId": null,
  "applicationStatus": null,
  "createdAt": "2026-09-29T09:00:00"
}
```

## E. Three secondary jobs

```json
[
  {
    "id": "22222222-2222-4222-8222-222222222222",
    "title": "Audit Cross-Border Payout Records",
    "description": "Review payout records and document reconciliation exceptions.",
    "budgetUsd": 420.00,
    "status": "OPEN",
    "client": {"id": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "displayName": "Delta Works"},
    "hasApplied": false,
    "applicationId": null,
    "applicationStatus": null,
    "createdAt": "2026-09-29T09:00:00"
  },
  {
    "id": "33333333-3333-4333-8333-333333333333",
    "title": "Design Contractor Handoff Flow",
    "description": "Map the submission and review handoff for a contractor workflow.",
    "budgetUsd": 360.00,
    "status": "OPEN",
    "client": {"id": "cccccccc-cccc-4ccc-8ccc-cccccccccccc", "displayName": "Atlas Studio"},
    "hasApplied": false,
    "applicationId": null,
    "applicationStatus": null,
    "createdAt": "2026-09-29T09:00:00"
  },
  {
    "id": "44444444-4444-4444-8444-444444444444",
    "title": "Build Invoice Event Webhooks",
    "description": "Implement invoice event delivery and retry handling for a client workflow.",
    "budgetUsd": 640.00,
    "status": "OPEN",
    "client": {"id": "dddddddd-dddd-4ddd-8ddd-dddddddddddd", "displayName": "Field Office"},
    "hasApplied": false,
    "applicationId": null,
    "applicationStatus": null,
    "createdAt": "2026-09-29T09:00:00"
  }
]
```

The static Discover page uses `currentPage: 0`, `pageSize: 10`, `totalPages: 1`, `totalElements: 4`, and these four jobs in the listed fixture order. Equal `createdAt` values do not assert a server tie-break rule.

## F. Freelancer state fixture

- Role: `FREELANCER`; destination: **Công việc / Khám phá**.
- Query: `page=0`, `size=10`, empty keyword and budget range, `sort=NEWEST`, `application=ALL`.
- All four rows: `OPEN`, `hasApplied=false`, `applicationId=null`, `applicationStatus=null`; visible label **Chưa ứng tuyển** and action **Ứng tuyển**.
- The focus job is previewed from its Discover snapshot. The static action expresses the supported APPLY intent; it does not imply that a participant-only detail fetch is available or that the action has succeeded.

## G. Client state fixture

- Role: `CLIENT`, authenticated owner ID `aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa`, viewing the same focus job ID `11111111-1111-4111-8111-111111111111`.
- Client proof uses a display subset of `JobResponse`: the same title, description, `budgetUsd=500.00`, `status=OPEN`, `clientUserId` as above, `freelancerId=null`, and the same fixed `createdAt`/`updatedAt` value. `JobResponse` has no client display name; the proof says **Bạn là người đăng**, not “North Studio.”
- Visible actions: **Xem ứng tuyển**, **Chỉnh sửa tiêu đề & mô tả**, **Hủy công việc**. No applicant count or applicant identity is shown; application records are not loaded in this fixture. Assigning needs a pending application, so it is not offered here.
- Budget is read-only. **JOB BUDGET EDIT = DISABLED** until the checkout/payment amount contract is resolved, even though the current update DTO contains `budgetUsd`.

## H. Constraints for every D1 study

- Preserve the same four jobs, numbers, descriptions, client display names, state, action labels, and order. Only composition grammar may vary.
- Use the same temporary exploration palette from `ART_DIRECTION_FREELAX.md`: Bone `#F2EDDF`, Ink `#161915`, Vermilion `#EF5B3E`, Acid Yellow `#EFD94F`, Sage `#8FA47D`. **Final palette and font family are NOT LOCKED.**
- Display state in words as well as color. Do not invent other fields or switch roles mid-surface.
- Prototype controls are static visual studies with no API calls, persistence, or implemented navigation. D1.3 owns complete state coverage; D1.4 owns the full responsive study.
