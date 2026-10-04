# FreelaX MVP Functional Specification

> Trạng thái: Draft 0.1 để chốt quyết định sản phẩm trước khi triển khai.
>
> Tài liệu này đặc tả phạm vi MVP trong `README.md`. Các giá trị ghi **Đề xuất** có thể đổi sau khi review; các mục **Cần chốt** chưa được coi là yêu cầu cuối cùng.

## 1. Mục tiêu và nguyên tắc

MVP phải chứng minh được bốn cam kết:

1. Hai bên biết rõ người mình giao dịch cùng và lịch sử uy tín của họ.
2. Phạm vi, tiền, deadline và số lần chỉnh sửa được chốt trước khi bắt đầu.
3. Mỗi quyết định đều có actor, thời điểm và bằng chứng có thể truy vết.
4. Lỗi của dịch vụ tài chính không làm mất dữ liệu nghiệp vụ hoặc tạo thanh toán lặp.

### 1.1 Phạm vi đề xuất

- Một job có đúng một milestone trong MVP; schema và API dùng `milestone` để có thể mở rộng nhiều milestone sau này.
- Một contract được tạo khi Client chọn Freelancer.
- Freelancer chỉ bắt đầu khi milestone ở trạng thái `FUNDED`.
- Review deadline mặc định 72 giờ tính từ submission hợp lệ gần nhất.
- Tối đa 2 lần Client yêu cầu revision miễn phí.
- Dispute do Admin xử lý thủ công.
- Rating hai chiều chỉ mở sau khi contract đã có kết quả cuối cùng.
- Payment, Solana, off-ramp và MISA vẫn có thể là mô phỏng nhưng phải hiển thị rõ `simulation=true`.

### 1.2 Ngoài phạm vi MVP

- Nhiều milestone cho một contract.
- Chat realtime, video call, file hosting và quét virus nội bộ.
- KYC tự động, xác minh chứng chỉ kỹ năng với bên thứ ba.
- Trọng tài nhiều cấp, appeal và chia tiền theo phần trăm tùy ý.
- Thay đổi vai trò Client/Freelancer trên cùng một tài khoản.
- Tiền thật hoặc tuyên bố bảo chứng/escrow pháp lý.

## 2. Mô hình miền

Không mở rộng `JobStatus` thành một enum khổng lồ. Mỗi aggregate giữ một vòng đời độc lập.

| Aggregate | Vai trò | Trạng thái chính |
| --- | --- | --- |
| `Job` | Tin tuyển dụng và acceptance criteria | `DRAFT`, `OPEN`, `ASSIGNED`, `CLOSED`, `CANCELLED` |
| `Contract` | Thỏa thuận bất biến giữa hai bên | `PENDING_FUNDING`, `ACTIVE`, `UNDER_REVIEW`, `REVISION`, `DISPUTED`, `COMPLETED`, `CANCELLED` |
| `Milestone` | Tiền, deadline và giới hạn revision | `PENDING_FUNDING`, `FUNDED`, `IN_PROGRESS`, `SUBMITTED`, `DISPUTED`, `RELEASE_PENDING`, `RELEASED`, `REFUND_PENDING`, `REFUNDED`, `CANCELLED` |
| `FundingTransaction` | Lần funding/refund/release với provider | `PENDING`, `PROCESSING`, `SUCCEEDED`, `FAILED`, `UNKNOWN` |
| `Submission` | Một phiên bản bàn giao | `SUBMITTED`, `REVISION_REQUESTED`, `APPROVED`, `SUPERSEDED`, `DISPUTED` |
| `Dispute` | Hồ sơ phân xử | `OPEN`, `UNDER_REVIEW`, `RESOLVED_RELEASE`, `RESOLVED_REFUND`, `CANCELLED` |
| `Review` | Rating sau giao dịch | `PENDING`, `PUBLISHED` |

`Contract` và `Milestone` là nguồn sự thật của workflow mới. Các trường payment/settlement chỉ mô tả tài chính, không tự động suy ra contract đã hoàn thành.

## 3. Quy ước API

- Public API chỉ tồn tại tại Marketplace: `/api/v1/...`.
- ID là UUID; thời gian trả về ISO-8601 UTC, ví dụ `2026-10-04T08:00:00Z`.
- Tiền là decimal dạng JSON string để tránh mất chính xác, ví dụ `"150.00"`; currency MVP là `USD`.
- Mutation quan trọng nhận header `Idempotency-Key`: funding, submit, approve, dispute resolution, release/refund.
- Mọi response nghiệp vụ trả `requestId`; log nội bộ truyền cùng correlation ID sang Payment/Solana/MISA.
- API trả trạng thái hiện tại và `allowedActions` để UI không tự suy luận quy tắc.
- Các danh sách dùng `page`, `size`; `size` tối đa 100.
- Không trả identity number, tax code, số tài khoản đầy đủ hoặc signer material qua profile công khai.

### 3.1 Error envelope chuẩn

```json
{
  "timestamp": "2026-10-04T08:00:00Z",
  "requestId": "req_...",
  "code": "REVISION_LIMIT_REACHED",
  "message": "Đã sử dụng hết 2 lần chỉnh sửa",
  "fieldErrors": [],
  "retryable": false,
  "currentState": "SUBMITTED_FOR_REVIEW"
}
```

Quy ước status:

- `400`: input sai cú pháp hoặc validation.
- `401`: chưa xác thực; `403`: đúng danh tính nhưng sai quyền.
- `404`: resource không tồn tại hoặc không thuộc phạm vi người dùng.
- `409`: state transition không hợp lệ, duplicate hoặc optimistic-lock conflict.
- `422`: request hợp lệ về cú pháp nhưng vi phạm quy tắc nghiệp vụ.
- `502/503`: dependency lỗi/tạm thời không sẵn sàng; mutation không được coi là thất bại dứt điểm nếu provider có thể đã nhận request.

## 4. Profile, portfolio và uy tín

### 4.1 Profile

Profile chung:

- `displayName`: 2-80 ký tự.
- `avatarUrl`: HTTPS, tối đa 2048 ký tự; nullable.
- `headline`: tối đa 120 ký tự.
- `bio`: tối đa 2.000 ký tự.
- `countryCode`: ISO 3166-1 alpha-2; chỉ công khai cấp quốc gia.
- `languages[]`: mã BCP 47 và proficiency.
- `verification`: email, identity, payment method; MVP có thể dùng trạng thái mô phỏng/manual.

Freelancer bổ sung `skills[]`, `hourlyRateUsd` (tùy chọn), `availability`. Client bổ sung `companyName` và `companyWebsite` (tùy chọn).

API:

| Method | Path | Quyền | Kết quả |
| --- | --- | --- | --- |
| `GET` | `/api/v1/profiles/me` | đăng nhập | profile riêng, gồm field private phù hợp |
| `PATCH` | `/api/v1/profiles/me` | đăng nhập | cập nhật partial, dùng version để chống ghi đè |
| `GET` | `/api/v1/profiles/{userId}` | đăng nhập | public profile và reputation snapshot |
| `POST` | `/api/v1/profiles/me/avatar` | đăng nhập | ngoài MVP nếu chưa có object storage; ưu tiên URL |

Fallback: nếu avatar lỗi thì UI dùng initials; nếu reputation chưa có giao dịch, trả `null` cho rating và `completedContracts=0`, không trả rating giả bằng 0.

### 4.2 Portfolio và skills

Portfolio item: `title`, `description`, `projectUrl?`, `thumbnailUrl?`, `skills[]`, `completedAt?`, `sortOrder`. Tối đa 12 item, URL HTTPS, không upload file trong MVP.

API: `GET /profiles/{id}/portfolio`; `POST/PATCH/DELETE /profiles/me/portfolio...`; `PUT /profiles/me/skills` thay toàn bộ danh sách, tối đa 20 skill.

Fallback: URL bên ngoài không truy cập được không xóa item; UI đánh dấu không mở được. Nội dung do người dùng khai báo phải được escape và không render HTML thô.

### 4.3 Reputation snapshot

Các chỉ số chỉ tính từ contract thật trong DB:

- Freelancer: số contract hoàn thành, on-time rate, average rating, dispute rate.
- Client: số contract đã funding, payment/release rate, median review time, average rating, dispute rate.
- Chỉ công bố average rating khi có ít nhất 1 review; luôn kèm `reviewCount`.
- Snapshot có `calculatedAt`; có thể tính async và eventual consistency.

## 5. Job và acceptance criteria

### 5.1 Input tạo job

`POST /api/v1/jobs`

```json
{
  "title": "Landing page responsive",
  "description": "Bối cảnh và phạm vi công việc",
  "budget": { "amount": "500.00", "currency": "USD" },
  "deliveryDueAt": "2026-10-15T17:00:00Z",
  "reviewWindowHours": 72,
  "maxRevisions": 2,
  "deliverables": [
    { "title": "Source code", "description": "Repository và hướng dẫn chạy", "required": true }
  ],
  "acceptanceCriteria": [
    { "description": "Hiển thị đúng tại 375px, 768px và 1440px", "required": true }
  ]
}
```

Validation:

- 1-10 deliverables và 1-20 acceptance criteria; không cho item rỗng.
- `deliveryDueAt` phải sau hiện tại tối thiểu 24 giờ (**Đề xuất**).
- `reviewWindowHours` MVP cố định hoặc giới hạn 24-168; mặc định 72.
- `maxRevisions` từ 0-2; mặc định 2.
- Job chỉ publish khi các field bắt buộc hợp lệ.
- Job `OPEN` chỉ được sửa nội dung khi chưa assign. Sau assign, lưu snapshot bất biến trong contract.

Output gồm `job`, public client summary, `allowedActions`, và version.

Fallback: tạo job và tạo payment checkout không nằm trong cùng request. Job phải tạo được dù Payment/Solana đang lỗi; funding là bước sau khi assign.

## 6. Assign, contract và funding

### 6.1 Chọn Freelancer

`POST /api/v1/jobs/{jobId}/assignments`

Input: `applicationId`. Server kiểm tra application thuộc job và đang `PENDING`, sau đó trong một DB transaction:

1. Chốt application được chọn, đóng các application khác.
2. Tạo contract snapshot từ job.
3. Tạo một milestone `PENDING_FUNDING`.
4. Chuyển job sang `ASSIGNED`.

Freelancer chưa được submit khi milestone chưa `FUNDED`.

### 6.2 Funding

`POST /api/v1/contracts/{contractId}/milestones/{milestoneId}/fund`

Input:

```json
{
  "paymentMethodId": "pm_...",
  "expectedAmount": { "amount": "500.00", "currency": "USD" }
}
```

- Chỉ Client của contract được gọi.
- `expectedAmount` chống UI cũ/race; lệch snapshot trả `409 AMOUNT_CHANGED`.
- Header `Idempotency-Key` bắt buộc; cùng key+cùng payload trả kết quả cũ, cùng key+khác payload trả `409`.
- Marketplace tạo `FundingTransaction(PENDING)` trước khi gọi provider.
- Chỉ khi provider xác nhận chắc chắn mới chuyển milestone `FUNDED`, contract `ACTIVE`.
- Freelancer nhận thông báo sau khi DB commit.

Output có `fundingStatus`, `simulation`, `providerReference?`, `nextAction`, `retryAfterSeconds?`.

### 6.3 Fallback tài chính

- Timeout sau khi gửi request: transaction thành `UNKNOWN`, khóa tạo funding mới và scheduler/webhook reconcile bằng provider reference/idempotency key.
- Provider từ chối chắc chắn: `FAILED`, cho Client retry với key mới.
- Provider lỗi trước khi request được gửi: `FAILED_RETRYABLE` hoặc response `503 retryable=true`.
- Không bao giờ chuyển `FUNDED` chỉ dựa trên response từ frontend.
- Nếu Marketplace commit thất bại sau khi provider thành công: reconciliation phải tìm transaction `UNKNOWN/PENDING` và hoàn tất idempotently.

## 7. Submission, review deadline và revision

### 7.1 Submit

`POST /api/v1/contracts/{contractId}/submissions`

Input gồm `summary` (1-10.000), `deliverables[]` (URL HTTPS + mô tả), `acceptanceEvidence[]` ánh xạ `criterionId` sang ghi chú/URL. Ít nhất một deliverable hoặc evidence.

Điều kiện:

- Caller là Freelancer của contract.
- Milestone là `IN_PROGRESS` hoặc contract là `REVISION`.
- Funding vẫn phải ở trạng thái chắc chắn `FUNDED`.
- Sau deadline giao hàng vẫn cho submit nhưng đánh dấu `submittedLate=true`; không tự hủy công việc.
- Server gán version liên tục và tạo `reviewDueAt = submittedAt + reviewWindowHours`.

Mutation phải idempotent để double-click không tạo hai version.

### 7.2 Review actions

`POST /api/v1/contracts/{contractId}/submissions/{submissionId}/decisions`

Approve:

```json
{ "decision": "APPROVE", "note": "Đạt yêu cầu" }
```

Revision:

```json
{
  "decision": "REQUEST_REVISION",
  "feedback": "Thiếu trạng thái mobile",
  "criterionIds": ["..."]
}
```

Dispute:

```json
{ "decision": "OPEN_DISPUTE", "reasonCode": "QUALITY_NOT_MET", "description": "..." }
```

Quy tắc:

- Chỉ review submission mới nhất đang `SUBMITTED`.
- Revision cần feedback cụ thể và ít nhất một criterion hoặc deliverable liên quan.
- Mỗi `REQUEST_REVISION` tăng `revisionUsed`; submission lại không tăng.
- Khi `revisionUsed == maxRevisions`, Client chỉ còn `APPROVE`, `OPEN_DISPUTE`, hoặc `CREATE_CHANGE_REQUEST` (change request ngoài MVP có thể chỉ hiển thị disabled/explanation).
- Approve chuyển milestone `RELEASE_PENDING`; không đánh dấu `RELEASED` trước khi tài chính xác nhận.

### 7.3 Client im lặng

Scheduler lấy submission quá `reviewDueAt` bằng DB lock/claim để tránh xử lý hai lần.

**Đề xuất MVP:**

- Milestone dưới hoặc bằng 500 USD và không có dispute: auto-approve, chuyển `RELEASE_PENDING`.
- Milestone trên 500 USD: mở grace period 24 giờ, gửi cảnh báo; hết grace period thì auto-approve.
- Nếu release lỗi, contract ở `RELEASE_PENDING`, không quay lại review và không tạo release thứ hai.

**Cần chốt:** ngưỡng 500 USD và việc có dùng grace period hay auto-release mọi giá trị sau 72 giờ.

## 8. Cancel và dispute

### 8.1 Cancel policy

`POST /api/v1/contracts/{contractId}/cancellations`

| Trạng thái | Ai có thể yêu cầu | Kết quả MVP |
| --- | --- | --- |
| Trước funding | Client | cancel ngay, không có refund |
| Đã funding, chưa bắt đầu | hai bên | mở yêu cầu; bên còn lại đồng ý thì refund toàn bộ |
| Đang làm, chưa submit | hai bên | cần mutual consent; nếu không đồng ý thì dispute |
| Đã submit/đang review | hai bên | không cancel trực tiếp; chuyển dispute |
| Release/refund đang xử lý hoặc đã xong | không ai | `409` |

MVP không hỗ trợ partial refund tự chọn. Admin chỉ có hai quyết định: release 100% cho Freelancer hoặc refund 100% cho Client. Điều này giữ accounting và on-chain flow kiểm chứng được.

### 8.2 Dispute

`POST /api/v1/contracts/{contractId}/disputes` nhận `reasonCode`, `description`, `evidence[]`; mỗi contract tối đa một dispute đang mở.

Evidence là append-only: actor, timestamp, type, URL/text và hash nếu có. Không cho sửa/xóa sau khi nộp; có thể thêm evidence đến khi Admin khóa hồ sơ.

Admin API:

- `GET /api/v1/admin/disputes?status=OPEN...`
- `GET /api/v1/admin/disputes/{id}` trả contract snapshot, acceptance criteria, submissions, feedback, audit log và tình trạng funding.
- `POST /api/v1/admin/disputes/{id}/claim`
- `POST /api/v1/admin/disputes/{id}/resolve` với `RELEASE_TO_FREELANCER` hoặc `REFUND_TO_CLIENT`, `reason` bắt buộc.

Không cho Admin xử lý dispute do chính tài khoản Admin tham gia; resolution dùng optimistic lock và idempotency key.

Fallback: nếu release/refund provider lỗi, dispute là `RESOLVED_*` nhưng milestone giữ `RELEASE_PENDING/REFUND_PENDING`; scheduler retry. Không đảo quyết định Admin chỉ vì dependency lỗi.

## 9. Settlement và hậu xử lý

Luồng approve/auto-approve/Admin resolution chỉ tạo quyết định release/refund. Worker thực thi tài chính theo thứ tự idempotent:

1. Capture/release hoặc refund tại Payment.
2. Ghi nhận Solana theo khả năng của local runtime.
3. Off-ramp cho Freelancer nếu release.
4. Ghi MISA và tạo chứng từ nếu release.

Payment thành công là mốc tiền chính của MVP; lỗi Solana/MISA sau đó không được biến payment thành thất bại. Mỗi bước có status và retry riêng. UI hiển thị `moneyStatus`, `onChainEvidenceStatus`, `offRampStatus`, `taxDocumentStatus` độc lập.

Contract chỉ `COMPLETED` khi quyết định tiền chính đã thành công; các bằng chứng/hậu xử lý có thể tiếp tục `PENDING/FAILED_RETRYABLE`.

## 10. Rating hai chiều

Sau `COMPLETED`, tạo hai lời mời review. `POST /api/v1/contracts/{id}/reviews`:

```json
{
  "overall": 5,
  "dimensions": {
    "communication": 5,
    "requirementsOrQuality": 4,
    "timeliness": 5
  },
  "comment": "Rõ ràng và đúng hạn"
}
```

- Mỗi actor một review cho mỗi contract; không update sau publish trong MVP.
- Chỉ participant được review đối tác; Admin không review.
- Rating 1-5 integer; comment 0-2.000 ký tự.
- Double-blind: công bố khi cả hai đã review hoặc sau 14 ngày kể từ completion.
- Contract kết thúc bằng refund/cancel không tạo rating; dispute release vẫn được rating và profile hiển thị dispute rate riêng.
- Nội dung bị report có thể ẩn khỏi public view nhưng điểm vẫn giữ cho tới khi Admin vô hiệu hóa có audit reason.

## 11. Notification và scheduler

Sự kiện tối thiểu: assigned/awaiting funding, funded, due soon, submitted, review due soon, revision requested, dispute opened/updated/resolved, release/refund completed/failed, review invitation/published.

- Notification được ghi bằng transactional outbox cùng transaction nghiệp vụ.
- Worker gửi/đồng bộ; lỗi gửi không rollback hành động chính.
- Scheduler phải idempotent, claim theo row/version và có retry backoff.
- Thời gian deadline lưu UTC; UI render theo timezone người dùng.

## 12. Bảo mật, riêng tư và audit

- Mọi authorization kiểm tra tại Marketplace theo actor và resource, không dựa vào role/UI đơn thuần.
- Participant trái phép nhận `404` cho resource nhạy cảm để giảm dò ID.
- Admin là role riêng, bắt buộc audit `actorId`, action, before/after, reason, timestamp, requestId.
- Không ghi raw bank account, identity number, token, API key hoặc submission private URL vào application log.
- Public profile chỉ trả field allowlist.
- URL do người dùng nhập chỉ render dưới dạng link an toàn (`noopener`, không HTML); cân nhắc chặn scheme ngoài HTTPS.
- Entity mutable quan trọng có `version` cho optimistic locking.

## 13. Ma trận edge case bắt buộc kiểm thử

| Tình huống | Kết quả |
| --- | --- |
| Client double-click fund/approve | một transaction nhờ idempotency key |
| Hai tab cùng review | request thắng đầu tiên; request sau `409` với current state |
| Payment timeout nhưng thực tế đã nhận tiền | `UNKNOWN`, khóa retry trực tiếp, reconcile |
| Marketplace restart giữa provider success và DB update | worker reconcile và hoàn tất một lần |
| Freelancer submit đúng lúc Client mở dispute | optimistic lock; chỉ một transition thắng |
| Review deadline và Client approve đồng thời | row claim/version đảm bảo một decision |
| Revision thứ ba | `422 REVISION_LIMIT_REACHED` |
| Freelancer submit khi chưa funded | `409 MILESTONE_NOT_FUNDED` |
| URL deliverable hỏng sau khi submit | giữ immutable evidence; Admin đánh giá theo dữ liệu còn lại |
| Admin resolve hai lần | lần sau trả kết quả idempotent hoặc `409`, không trả/giải ngân lần hai |
| Solana/MISA down sau release | tiền vẫn completed; evidence/tax pending và retry |
| Người ngoài đoán UUID contract | `404`, không lộ participant/trạng thái |
| Rating chỉ một bên đã gửi | ẩn cho tới bên kia gửi hoặc hết 14 ngày |

## 14. Thứ tự triển khai đề xuất

1. Nền tảng: error contract, idempotency, optimistic locking, audit/outbox.
2. Job acceptance criteria và contract snapshot.
3. Funding trước khi làm và state machine độc lập.
4. Submission evidence, deadline, revision limit, auto decision.
5. Cancel/dispute/Admin và settlement retry.
6. Profile/portfolio/reputation.
7. Rating hai chiều.

Profile có thể làm UI sớm, nhưng funding/state machine nên được chốt trước vì ảnh hưởng lớn nhất tới schema và workflow hiện hữu.

## 15. Các quyết định cần chốt trước khi code

1. MVP thật sự chỉ có một milestone/job hay cần nhiều milestone ngay từ đầu?
2. Funding là authorization/hold, capture sớm vào tài khoản nền tảng, hay chỉ mô phỏng ledger? Thuật ngữ UI phải khớp khả năng thực tế.
3. Auto-release áp dụng mọi mức tiền hay có ngưỡng và grace period?
4. Dispute MVP chỉ release/refund 100% hay bắt buộc partial split?
5. Deliverable dùng URL ngoài hay cần upload file trong hệ thống?
6. Admin dùng tài khoản seed/role trong Marketplace hay làm một service/backoffice riêng?
7. Profile verification nào là thật, manual hoặc mô phỏng trong bản demo?

