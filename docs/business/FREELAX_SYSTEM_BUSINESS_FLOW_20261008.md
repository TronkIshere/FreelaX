# FreelaX — Hệ thống và luồng nghiệp vụ tổng thể

> Tài liệu này mô tả baseline P06 đã freeze. Track nâng cấp escrow sau P06 đã có implementation trong working tree trên nhánh `feat/solana-milestone-escrow`; local-validator E2E và devnet verification chưa hoàn tất. Xem [trạng thái và checklist escrow](SOLANA_ESCROW_IMPLEMENTATION_PLAN.md).

**Ngày chốt:** 2026-10-08

**Trạng thái:** BUSINESS HANDOFF / SOURCE-BACKED

**Final product/source authority:** `7fc31555b9cd3f50a23872401968ee67c5275f30`

**Final documentation authority trước business addendum:** `519e77c5d689319009ddcd288de932d617fe78d0`

**Branch:** `feat/ui-visual-polish-20261006`

## 1. FreelaX giải quyết bài toán gì?

FreelaX là marketplace hai phía dành cho **Client** và **Freelancer**. Giá trị chính của hệ thống không nằm ở việc chỉ đăng tin và ứng tuyển, mà ở việc biến một thỏa thuận làm việc thành một quy trình có trạng thái, bằng chứng và trách nhiệm rõ ràng.

Các vấn đề nghiệp vụ mà FreelaX tập trung giải quyết:

- Client và Freelancer cần chốt rõ phạm vi, deliverable, tiêu chí nghiệm thu, deadline và giới hạn revision trước khi làm.
- Freelancer cần biết ngân sách milestone đã được funding trước khi bắt đầu công việc.
- Client cần có bằng chứng bàn giao để approve, yêu cầu revision hoặc mở dispute.
- Hai phía cần theo dõi riêng trạng thái công việc, trạng thái tiền và trạng thái hậu xử lý; không gom tất cả thành một trạng thái “thành công” giả định.
- Sau khi hợp đồng hoàn tất, hai phía cần review lẫn nhau để tạo reputation dựa trên giao dịch thật.
- Các bằng chứng tài chính cần có khả năng đối soát qua Payment, Solana, off-ramp và chứng từ thuế mà không làm frontend phụ thuộc trực tiếp vào các hệ thống này.

## 2. Các vai trò nghiệp vụ

### Client

Client có thể:

- tạo và chỉnh sửa Job khi còn đủ điều kiện;
- định nghĩa category, skills, deliverables, acceptance criteria, deadline, review window và revision limit;
- xem ứng viên và chọn Freelancer;
- funding milestone;
- xem submission thật của Freelancer;
- approve, yêu cầu revision hoặc đi vào dispute theo trạng thái;
- theo dõi Finance, Tax và Activity;
- quản lý Account/Profile;
- đánh giá Freelancer sau khi contract hoàn tất và có review opportunity hợp lệ.

### Freelancer

Freelancer có thể:

- khám phá Job và lọc theo category/skills/ngân sách;
- ứng tuyển;
- theo dõi Applications và My Work;
- chỉ bắt đầu contract-backed workflow sau khi funding hợp lệ;
- nộp submission kèm bằng chứng;
- nhận feedback/revision và nộp phiên bản tiếp theo;
- theo dõi thu nhập, bằng chứng release, downstream và Tax;
- quản lý Profile/Portfolio;
- đánh giá Client sau khi contract hoàn tất và có review opportunity hợp lệ.

### Admin / hệ thống điều phối

Admin và scheduler không thay thế hai participant. Vai trò của chúng là xử lý các tình huống cần điều phối như dispute, reconciliation, delayed review invitation và các tác vụ nền đã được backend định nghĩa.

## 3. Kiến trúc nghiệp vụ

```text
Người dùng trình duyệt
        |
        v
Frontend FreelaX
        |
        | chỉ gọi /api/v1
        v
Marketplace Backend
        |
        +---- Job / Contract / Milestone / Submission / Review
        |
        +---- Payment Backend
        |       `-- funding / primary release / refund evidence
        |
        +---- Solana Gateway
        |       `-- on-chain evidence / stablecoin-related reconciliation
        |
        +---- Off-ramp adapter
        |       `-- stablecoin-to-fiat stage / separate payout evidence
        |
        +---- MISA Backend
        |       `-- tax record / certificate evidence
        |
        +---- MySQL / Redis
```

**Nguyên tắc kiến trúc quan trọng:** trình duyệt chỉ giao tiếp với **Marketplace**. Payment, Solana Gateway, MISA và Solana RPC là hệ thống phía sau. Điều này giữ quyền, trạng thái và business rule tập trung tại Marketplace thay vì để frontend tự phối hợp nhiều backend.

## 4. Luồng nghiệp vụ end-to-end đã được kiểm chứng

P06.7 đã chứng minh một Job mới đi xuyên **workflow contract-backed thật**, không dùng legacy Job `contract:null` và không INSERT/UPDATE DB để tạo trạng thái giả.

```text
Client tạo Job
        |
        v
Job OPEN
        |
        v
Freelancer ứng tuyển
        |
        v
Client chọn Freelancer
        |
        +--> WorkContract được tạo
        +--> Milestone được tạo
        |
        v
AWAITING_PAYMENT / PENDING_FUNDING
        |
        v
Client funding
        |
        v
Funding SUCCEEDED
Contract ACTIVE
Milestone FUNDED
        |
        v
Freelancer thực hiện và submit bằng chứng
        |
        v
SUBMITTED_FOR_REVIEW / UNDER_REVIEW
        |
        v
Client review
        +--> REQUEST_REVISION -> Freelancer sửa và submit lại
        +--> OPEN_DISPUTE     -> quy trình dispute
        `--> APPROVE          -> RELEASE_PENDING
                                      |
                                      v
                             Settlement Scheduler
                                      |
                                      v
                              moneyStatus SUCCEEDED
                                      |
                     +----------------+----------------+
                     |                                 |
                     v                                 v
              Milestone RELEASED              Contract/Job COMPLETED
                                                        |
                                                        v
                                           Review Scheduler tạo 2 lời mời
                                                        |
                             +--------------------------+--------------------------+
                             |                                                     |
                             v                                                     v
                  Client đánh giá Freelancer                           Freelancer đánh giá Client
                             |                                                     |
                             +--------------------------+--------------------------+
                                                        |
                                                        v
                                           Cả hai review được publish
                                                        |
                                                        v
                                            Public reputation cập nhật
```

## 5. Mốc trạng thái quan trọng

### Trước khi giao việc

- Job ở `OPEN`.
- Freelancer có thể apply.
- Chưa có WorkContract.

### Sau khi Client assign

- WorkContract được tạo.
- Milestone được tạo.
- Job chuyển sang chờ funding.
- Đây là điểm phân biệt với các Job legacy `contract:null`.

### Sau funding

- Funding phải được backend xác nhận `SUCCEEDED`.
- Contract chuyển `ACTIVE`.
- Milestone chuyển `FUNDED`.
- Freelancer mới có contract-backed submission path hợp lệ.

### Sau submission

- Submission có version và evidence.
- Contract sang `UNDER_REVIEW`.
- Client có quyền approve/revision/dispute theo business rule.

### Sau approve

- Submission `APPROVED`.
- Milestone `RELEASE_PENDING`.
- **Approve không đồng nghĩa tiền đã release xong.**

### Sau primary settlement

- `moneyStatus = SUCCEEDED` là mốc tiền chính của MVP.
- Milestone `RELEASED`.
- Contract và Job `COMPLETED`.
- Solana/off-ramp/tax vẫn có thể đang pending hoặc fail độc lập.

### Sau completion

- Scheduler tạo review invitation cho cả hai phía nếu contract đủ điều kiện.
- Review là **hậu hoàn tất**, không phải điều kiện làm Job completed hoặc nhận release.

## 6. Real E2E evidence đã chốt

| Bản ghi | ID / trạng thái |
| --- | --- |
| Job | `ab23f32f-9240-4582-b342-300b968bdb03` / COMPLETED |
| Application | `76f9f080-51b0-478f-8cd6-51a13819d214` |
| Contract | `1fb7b2ee-2bf4-4bf7-84a8-a6379646ed93` / COMPLETED |
| Milestone | `cf9a24bc-c496-48d9-b2db-0447747ab8ba` / RELEASED |
| Funding | `0839c00d-7cf9-4508-b51e-ccc4b1ec80e1` / SUCCEEDED |
| Submission | `e436a7bf-973f-4d5a-b13e-6fdd6d8a140d` / APPROVED |
| Settlement | `021e70a7-abd2-4d7e-a0eb-42d835b15bce` / SUCCEEDED |
| Client review | `9084a24d-ac44-412e-a653-52512b014c03` / submitted + published |
| Freelancer review | `d62dc196-0384-4321-9047-6999c1b6581a` / submitted + published |

Review invitation được scheduler tạo tự nhiên. Hai review được gửi qua UI/API thật, sau đó public profile/reputation cập nhật theo server truth.

Đây là bằng chứng runtime QA local đã được chấp nhận, không phải giao dịch production hay đánh giá năng lực người thật. Rating/comment QA là dữ liệu thử nghiệm; không có DB state được tạo thủ công.

## 7. Các bề mặt nghiệp vụ đã hoàn thiện

### Client

- Overview
- Work list
- Job authoring
- Applicants
- Job Detail / Contract Lifecycle
- Funding
- Submission review
- Finance
- Tax
- Activity
- Account/Profile
- Public partner profile
- Review Freelancer

### Freelancer

- Overview
- Explore
- Applications
- My Work
- Job Detail / Contract Lifecycle
- Submission
- Finance
- Tax
- Activity
- Account/Profile
- Portfolio
- Public partner profile
- Review Client

Các bề mặt này đã được đóng băng trong P06; không nên mở lại chỉ để polish chủ quan.

## 8. Business truth phải giữ nguyên

- Job completion không chứng minh off-ramp/tax/on-chain đã hoàn tất.
- Review không phải payment gate.
- Activity là notification ledger, không phải audit log hoàn chỉnh.
- Profile reputation chỉ dùng dữ liệu server thật; không tạo “trust score” tự suy luận.
- Finance không được bịa aggregate khi API không cung cấp.
- Public profile không được lộ email session, account UUID hoặc dữ liệu ngân hàng/tax private.
- Simulation/local demo phải luôn được mô tả đúng là mô phỏng/local, không phải chuyển tiền thật.

## 9. Trạng thái cuối hiện tại

- P06 visual/product UI stream: **COMPLETE / FROZEN**.
- Test evidence accepted: **793/793 PASS**, 22 files.
- Real contract-backed E2E: **PASS**.
- Desktop/laptop: 1440 và 1024 **PASS**.
- Mobile optimization: **deferred**.
- Solana/on-chain ở final QA: **NOT FULLY RECONCILED**.
- Observed on-chain: **UNKNOWN / ON_RAMP_AWAITING_RECONCILIATION**.
- Off-ramp: **NOT_STARTED**.
- Tax downstream cho QA Job: **NOT_STARTED**.

Baseline là **LOCAL MVP / DEMO**: không chứng minh blockchain payment end-to-end, fiat payout thật, khai thuế production hay Solana production deployment. Review bàn giao là quyết định nghiệm thu; rating/review hai chiều là phản hồi sau hoàn tất, không phải payment gate.

## 10. Source paths liên quan

### Frontend nghiệp vụ

- `frontend/src/Jobs.tsx`
- `frontend/src/JobEditor.tsx`
- `frontend/src/Workflow.tsx`
- `frontend/src/ContractLifecycle.tsx`
- `frontend/src/Funding.tsx`
- `frontend/src/ContractReviews.tsx`
- `frontend/src/Finance.tsx`
- `frontend/src/Activity.tsx`
- `frontend/src/Account.tsx`
- `frontend/src/Profile.tsx`
- `frontend/src/Portfolio.tsx`
- `frontend/src/PublicProfile.tsx`

### Marketplace nghiệp vụ

- `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/JobServiceImpl.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/FundingService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractSubmissionService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/SettlementService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/service/ContractReviewService.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/scheduler/SettlementScheduler.java`
- `marketplace-backend/src/main/java/com/marketplace/backend/scheduler/ContractReviewScheduler.java`

### Authority docs

- `README.md`
- `docs/mvp-functional-spec.md`
- `docs/ui/FREELAX_FINAL_FREEZE_HANDOFF_20261008.md`
- `docs/ui/UI_VISUAL_POLISH_HANDOFF_20261006.md`
- `docs/ui/UI_VISUAL_POLISH_SESSION_LOG_20261006.md`
- `docs/ui/UI_IMPLEMENTATION_PROGRESS.md`
- `solana-stablecoin-payout/docs/bao-cao-tich-hop-freelax-solana.md`
