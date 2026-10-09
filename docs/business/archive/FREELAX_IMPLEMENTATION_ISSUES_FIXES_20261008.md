# FreelaX — Những gì đã làm, lỗi đã gặp và cách khắc phục

> **Lưu trữ theo mốc ngày.** Xem [tổng quan hiện tại](../README.md) và [trạng thái kiểm chứng](../VERIFICATION.md) trước khi dùng các kết luận bên dưới.

**Mục tiêu:** ghi lại tiến trình theo hướng sản phẩm/nghiệp vụ, tránh sa vào chi tiết code. Phần 1–8 là lịch sử **P06 tại thời điểm freeze**, không phải tuyên bố rằng toàn bộ nhánh escrow hiện tại hay luồng đối tác USD→VND đã chạy E2E.

## Bài học nghiệp vụ sau P06: phải gọi đúng nơi giữ tiền

| Phát hiện | Tác động đến cách thiết kế và demo |
| --- | --- |
| Ledger Payment P06 ghi tiền mô phỏng; một trạng thái `SUCCEEDED` không chứng minh USD nằm ở tài khoản đối tác | Mỗi rail cần nguồn xác nhận tiền riêng. Chỉ cho Freelancer bắt đầu sau khi nguồn đó xác nhận funding đủ và đối soát được. |
| Escrow Solana nhánh hiện tại giữ mock token trong vault, không đại diện cho USD ngân hàng | Luồng token được demo riêng; một Job/khoản tiền không được hạch toán đồng thời trong vault và tài khoản đối tác mock. |
| Tài chính theo từng Job không trả lời được tổng tiền đang giữ có khớp tổng nghĩa vụ hay không | Luồng đối tác mock cần sao kê độc lập, màn hình tổng đối soát theo USD, chênh lệch và khoản đang chờ. |
| Phí thu trước làm sai yêu cầu kinh doanh mới | Rail `PARTNER_ESCROW_MOCK` ghi **phí FreelaX 3% do Freelancer chịu chỉ sau chi thành công**; hoàn đủ USD cho Client không thu phí. Rail Solana chưa áp phí này. |
| Trạng thái duyệt, lệnh chi và chi tiền là ba mốc khác nhau | Sau duyệt là `RELEASE_PENDING`; chỉ xác nhận từ bên giữ tiền mới đưa sang `PAID`. Tranh chấp hoặc kết quả chi chưa rõ phải giữ trạng thái chờ. |

Luồng mục tiêu, phép tính tiền, tình huống hoàn và các mốc nghiệp vụ còn mở nằm ở [tài liệu đối tác](PARTNER_ESCROW_BUSINESS_GAP_20261008.md). Bằng chứng token E2E và giới hạn kiểm chứng nằm ở [báo cáo escrow local](SOLANA_ESCROW_LOCAL_E2E_20261008.md).

## 1. Cách tổ chức công việc

P06 được chia thành các phase nhỏ, mỗi phase có phạm vi rõ ràng và chỉ freeze khi đạt đủ source/runtime/test evidence.

```text
Inspect contract
   ↓
Implement đúng phạm vi
   ↓
Focused tests
   ↓
Runtime QA
   ↓
Screenshot tối thiểu cần thiết
   ↓
Regression
   ↓
Commit source
   ↓
Update authority docs
   ↓
Freeze
```

Sau khi số screenshot bắt đầu quá nhiều, workflow được tối ưu thành:

- 1 screenshot 1440 chính mặc định;
- thêm ảnh thứ hai chỉ khi có state/detail thực sự khác;
- 1024 chủ yếu là runtime/layout validation;
- không dừng chờ approval screenshot nếu không có regression thật.

## 2. Các phase chính đã hoàn tất

| Phase | Nghiệp vụ / kết quả |
| --- | --- |
| P06.5A | Finance list theo Job, không bịa aggregate tiền |
| P06.5B | Finance detail + Money Evidence Spine; funding/release/downstream tách riêng |
| P06.5C | Tax Evidence Ledger + Certificate Case File |
| P06.5D | Activity notification ledger + progressive disclosure |
| P06.6 | Account/Profile/Portfolio/Public Profile |
| Pre-P06.7 | Review opportunity callout sau completed Job |
| P06.7 | Full contract-backed E2E + review/reputation + regression |
| P06.8 | Final freeze, README/docs reconciliation, handoff |

## 3. Những lỗi/vấn đề quan trọng đã gặp

### 3.1 Marketplace runtime `127.0.0.1:9191` từ chối kết nối

**Problem:** browser/Codex không thể tiếp tục runtime QA vì Marketplace connection refused.

**Business impact:** không thể xác nhận session và màn hình bằng dữ liệu thật.

**Cause:** local Marketplace runtime không reachable, không phải lỗi authorization hay lý do đổi API/proxy.

**Fix:**

- khởi động lại service bằng Docker Compose mà không đổi port/proxy;
- kiểm tra `/api/v1/auth/me`;
- HTTP `401 UNAUTHENTICATED` khi chưa đăng nhập được dùng như bằng chứng service đã reachable.

**Bài học:** 401 ở auth endpoint có thể là connectivity PASS, không phải runtime failure.

**Result:** runtime reachable trở lại qua port/proxy hiện có; QA tiếp tục với session thật, không tạo mock API.

### 3.2 Activity hiển thị notification quá dài như technical log

**Problem:** notification payout chứa Job, Mock USDC, route, hash/reference, VND estimate, FX, tax... làm một row cao bất thường và phá hierarchy.

**Business impact:** người dùng khó scan sự kiện cần chú ý.

**Cause:** message đầy đủ được hiển thị ngay ở hàng tóm tắt, làm technical evidence lấn át thông báo.

**Fix:**

- không sửa server message;
- mặc định clamp khoảng 3 dòng;
- thêm `Xem thêm nội dung / Thu gọn` cho message dài;
- full original server message vẫn truy cập được;
- technical detail tiếp tục thuộc Finance/Tax detail.

**Result:** Activity trở lại đúng vai trò notification summary thay vì biến thành finance evidence page.

### 3.3 Event tag, `Chưa đọc`, `Đánh dấu đã đọc` quá nhạt

**Problem:** trạng thái/read action không đủ contrast để scan nhanh.

**Business impact:** khó phân biệt mức độ sự kiện với trạng thái đã/chưa đọc.

**Cause:** visual hierarchy của nhãn/action chưa đủ mạnh; không phải lỗi dữ liệu notification.

**Fix:**

- event failure: Vermilion + Cream text;
- unread: Acid + Ink;
- mark-as-read: Ink + Cream;
- giữ event severity và read/unread là hai semantic axis độc lập.

**Result:** contrast/read-state được xác nhận trong Activity runtime QA, nội dung server và hành vi read không đổi.

### 3.4 Các Job completed cũ có `contract:null`

**Problem:** nhìn UI thấy Job đã completed nhưng không thể dùng để chứng minh review lifecycle mới.

**Business impact:** dùng record đó làm proof sẽ tạo kết luận sai về Contract/Milestone và review eligibility.

**Cause:** đây là legacy Jobs, không đi qua WorkContract/Milestone hiện đại.

**Fix:** tạo một Job P06.7 mới từ đầu và đi qua workflow thật:

Job → Application → Assignment → Contract/Milestone → Funding → Submission → Approval → Settlement → Completion → Reviews.

**Result:** chứng minh được modern contract-backed E2E thật, không dùng DB để manufacture state.

### 3.5 Review invitation được scheduler tạo sau khi page đã mount nhưng UI không thấy

**Problem:** backend đã sinh review invitation tự nhiên sau completion, nhưng page đang mở vẫn không xuất hiện CTA nếu không reload.

**Business impact:** Client/Freelancer bỏ lỡ cơ hội đánh giá hợp lệ và reputation không được cập nhật qua UI đang mở.

**Cause:** ContractReviews chỉ đọc invitations ở mount time.

**Fix:** thêm reconciliation đọc lại delayed scheduler-created invitation với guard/cleanup phù hợp; không tạo duplicate business rule và không tạo fake invitation.

**Source fix authority:** `7fc31555b9cd3f50a23872401968ee67c5275f30`.

**Result:** second real Job chứng minh CTA xuất hiện trên page đang mở, không hard reload; hai review submit và publish bình thường.

Luồng chứng minh: `Đánh giá Freelancer` → Client review → `Đánh giá Client` → Freelancer review → cả hai publish → public reputation cập nhật từ server. Reconciliation chỉ đọc lại, không cấp eligibility hoặc tạo lời mời.

### 3.6 Missing TaxRecord trả HTTP 404 làm console có resource-error message

**Problem:** strict console-zero gate xem các `404` như lỗi dù TaxRecord thực tế chưa tồn tại.

**Business impact:** record thiếu đúng contract bị nhầm thành product failure, dù UI đang hiển thị trạng thái chưa có chứng từ.

**Cause:** frontend đã có `missingTax(...)` coi `404`/code `4010` là **expected absence** và hiển thị `Chưa có chứng từ`, `taxError` rỗng.

Phạm vi chính xác: chỉ `GET /api/v1/marketplace/tax-records/jobs/{jobId}` khi record thực sự chưa tồn tại, tax=null/taxError trống và độc lập biết tax chưa hoàn tất. Read/fetch failure, arbitrary 404, CORS, 5xx và JS/page error không được miễn trừ.

**Fix:** không sửa backend chỉ để xóa dòng đỏ của browser. Gate được làm rõ thành:

- 0 unexpected JS/page errors;
- 0 unexpected network/runtime failures;
- contract-defined missing TaxRecord 404 được ghi nhận riêng là expected absence.

**Result:** P06.7 ghi riêng 8 expected missing-TaxRecord 404; P06.8 read-only smoke ghi riêng 4. Không claim QA Job mới có certificate chỉ vì record khác có ACCEPTED PDF/XML.

### 3.7 Solana RPC unavailable / on-chain chưa reconcile

**Problem:** primary settlement đã thành công nhưng on-chain state là `UNKNOWN / ON_RAMP_AWAITING_RECONCILIATION`.

**Business impact:** không thể chứng minh stablecoin rail/fiat/tax hoàn tất; phải giữ giới hạn này trong handoff.

**Cause:** Solana RPC local không khả dụng trong final regression, downstream chưa fully reconciled.

**Fix:** không ép/fake success; giữ downstream độc lập.

**Result:** Job/Contract/Review business flow vẫn được chứng minh; handoff ghi rõ on-chain chưa fully reconciled, off-ramp/tax NOT_STARTED.

### 3.8 README/documentation drift

**Problem:** tài liệu cũ vẫn ghi profile, portfolio, acceptance criteria, funding milestone, review hai chiều... là “chưa có”, trong khi source đã triển khai.

**Business impact:** người nhận bàn giao có thể hiểu sai năng lực và phạm vi hiện hành.

**Cause:** các draft/phân tích cũ chưa tách rõ baseline lịch sử khỏi current source truth.

**Fix:** Ở P06.8, reconcile README và tạo final handoff để current truth rõ ràng; lịch sử cũ vẫn giữ nhưng không dùng làm current product truth.

**Result:** source authority `7fc31555...` và docs authority `519e77c5...` được chốt riêng; import business docs chỉ bổ sung diễn giải, không thay đổi product baseline.

### 3.9 Review E2E ban đầu chưa có modern contract-backed runtime proof

**Problem:** tests/source và legacy completed Jobs chưa đủ chứng minh chuỗi review/reputation sau primary release của contract mới.

**Business impact:** có thể báo review hoàn chỉnh mà chưa chứng minh invitation, publication và reputation liên kết đúng giao dịch.

**Cause:** thiếu một real Job đi qua WorkContract/Milestone/Funding/Settlement hiện đại; không phải bằng chứng business rule bị sai.

**Fix:** P06.7 Gate A chạy Job mới qua UI/API thật, scheduler tự nhiên và read-only verification; không INSERT/UPDATE DB hay tạo invitation thủ công.

**Result:** primary runtime chain và hai review ở business flow doc chứng minh funding/release/completion/publication/reputation. Downstream on-chain/off-ramp/tax vẫn ghi riêng là chưa hoàn tất.

## 4. Các quyết định sản phẩm quan trọng

### Không bịa dữ liệu để làm UI đẹp

- không fake Job category/skills;
- không fake monetary aggregate;
- không fake rating/reputation;
- không fake TaxRecord;
- không fake review invitation;
- không fake Solana success.

### Business state và evidence state tách nhau

Ví dụ:

- submission APPROVED không có nghĩa release xong;
- primary settlement SUCCEEDED không có nghĩa off-ramp/tax xong;
- review submitted không có nghĩa review đã public;
- read notification không có nghĩa event business đã resolved.

### Server là authority

UI không tự suy ra stronger claim khi backend chưa chứng minh.

Với luồng đối tác mục tiêu, server cũng không được suy ra số dư ký quỹ từ chính ledger Marketplace rồi gọi đó là đối soát. Sao kê đối tác mock phải là nguồn riêng; khi lệch, báo lỗi và giữ các lệnh tiền liên quan ở trạng thái chờ đến khi xử lý.

## 5. Full E2E được chứng minh như thế nào

P06.7 Gate A yêu cầu mọi transition quan trọng đi qua real UI/API, scheduler chạy tự nhiên và chỉ dùng read-only verification để quan sát trạng thái.

Không dùng:

- manual DB INSERT/UPDATE;
- fake review invitation;
- manual settlement row;
- hardcode Job ID trong product code;
- legacy contract:null Job làm proof.

## 6. Validation/freeze evidence

- P06.7 focused tests: **99/99 PASS**.
- Full frontend: **793/793 PASS**, 22 files.
- Production build: PASS.
- 1440/1024 role checks: PASS.
- Unexpected JS/page errors: 0.
- Horizontal overflow: 0.
- Final worktree: CLEAN / remote aligned.

## 7. Commit authority quan trọng

| Commit | Ý nghĩa |
| --- | --- |
| `96cafbe...` | Finance list |
| `9c76ffb...` / `837bcf0...` | Finance detail Client/Freelancer |
| `1b2ee56...` | Tax visual emphasis |
| `c71c554...` | Activity content hierarchy/read emphasis |
| `a0e16f9...` | Account/Profile |
| `99e5f08...` | Completed Job review opportunity callout |
| `7fc31555...` | Delayed review invitation reconciliation; final source authority |
| `519e77c5...` | P06.8 final documentation authority |

## 8. Exact source paths theo nhóm

### Work / Contract

- `frontend/src/Jobs.tsx`
- `frontend/src/JobEditor.tsx`
- `frontend/src/Workflow.tsx`
- `frontend/src/ContractLifecycle.tsx`
- `frontend/src/Funding.tsx`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/JobServiceImpl.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/FundingService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractSubmissionService.java`

### Settlement / Finance / Tax

- `frontend/src/Finance.tsx`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/SettlementService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/scheduler/SettlementScheduler.java`
- `solana-integration/`
- `solana-stablecoin-payout/`

### Review / Reputation

- `frontend/src/ContractReviews.tsx`
- `frontend/src/Profile.tsx`
- `frontend/src/PublicProfile.tsx`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractReviewService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/scheduler/ContractReviewScheduler.java`

### Activity / Account

- `frontend/src/Activity.tsx`
- `frontend/src/Account.tsx`
- `frontend/src/Portfolio.tsx`

### Authority docs

- `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md`
- `docs/ui/UI_VISUAL_POLISH_HANDOFF_20261006.md`
- `docs/ui/UI_VISUAL_POLISH_SESSION_LOG_20261006.md`
- `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`
- `docs/mvp-functional-spec.md`
