# FreelaX — Hệ thống và luồng nghiệp vụ tổng thể

> **Lưu trữ theo mốc ngày.** Xem [tổng quan hiện tại](../README.md) và [trạng thái kiểm chứng](../VERIFICATION.md) trước khi dùng các kết luận bên dưới.

> Phần 1–10 bên dưới ghi lại **baseline P06 tại thời điểm freeze**. Để đọc trạng thái nhánh hiện tại và luồng USD→VND đã chốt về nghiệp vụ, xem phần 0 ngay dưới đây. Một số ca escrow token local đã qua E2E; toàn bộ gate và devnet chưa hoàn tất. Xem [báo cáo E2E](SOLANA_ESCROW_LOCAL_E2E_20261008.md).

**Ngày chốt:** 2026-10-08

**Trạng thái baseline:** BUSINESS HANDOFF / SOURCE-BACKED

**Final product/source authority:** `7fc31555b9cd3f50a23872401968ee67c5275f30`

**Final documentation authority trước business addendum:** `519e77c5d689319009ddcd288de932d617fe78d0`

**Branch của baseline P06:** `feat/ui-visual-polish-20261006`

## 0. Bản đồ nghiệp vụ tại thời điểm cập nhật

| Luồng | Tiền/chứng từ nằm ở đâu | Điều kiện bắt đầu làm và kết thúc | Trạng thái |
| --- | --- | --- | --- |
| P06 Payment simulation | Ledger mô phỏng của Payment Backend; không có USD ngân hàng được xác minh | Funding `SUCCEEDED` mới mở việc; primary settlement mô phỏng `SUCCEEDED` mới hoàn tất Job | Đã có và E2E P06; không phải ký quỹ tiền thật. |
| `SOLANA_ESCROW` | Mock token trong vault PDA riêng của một Milestone; không phải USD ở ngân hàng | Vault được xác minh đã nhận token mới mở việc; chỉ hoàn tất sau chuyển token release được xác minh | Có code và một số ca E2E local đã qua; còn gate mở. |
| `PARTNER_ESCROW_MOCK` | Payment Backend giữ sao kê **mock** riêng; về sau cần đối tác có phạm vi hoạt động phù hợp để giữ USD và chi/hoàn | Đối tác mock xác nhận đủ USD mới mở việc; xác nhận chi VND/hoàn USD mới tất toán | API E2E chi/hoàn và browser E2E seed account đã qua trên local; fiat thật chưa có. Xem [tài liệu đối tác](PARTNER_ESCROW_BUSINESS_GAP_20261008.md). |

Luồng nghiệp vụ mục tiêu qua đối tác, không dùng token Solana cho cùng một khoản tiền:

```text
Client và Freelancer chốt đầu ra, tiêu chí, hạn, tối đa 2 vòng sửa và giá USD
    → Client nộp đủ USD cho đối tác mock
    → xác nhận + đối soát đủ tiền → Job “Đã ký quỹ”, Freelancer bắt đầu
    → Freelancer bàn giao → Client duyệt / yêu cầu sửa / tranh chấp
    → duyệt hợp lệ → đối tác chốt tỷ giá và nhận lệnh chi
    → đối tác xác nhận chi VND cho Freelancer → Job hoàn tất, FreelaX ghi phí 3%
    ↘ nếu hủy/hoàn trước giải ngân → đối tác hoàn đủ USD cho Client, phí FreelaX 0
```

**Ranh giới trạng thái:** `Đã ký quỹ` đòi hỏi bằng chứng nhận tiền; `Đã duyệt` chỉ cho phép tạo lệnh chi; `Đang giải ngân` chưa phải `Đã thanh toán`. Tiền chờ đối soát hoặc đang tranh chấp không được giải ngân/hoàn theo một kết quả phỏng đoán. Số dư đối tác mock phải đối soát với tổng nghĩa vụ chưa quyết toán theo USD; không cộng USD và VND làm một số dư. Mọi màn hình demo đối tác ghi rõ **mô phỏng**.

Rail đối tác mock thu **3% giá Job, do Freelancer chịu, chỉ ghi nhận sau giải ngân thành công**. Tỷ giá khóa lúc tạo lệnh chi; mock không tính phí đối tác; hoàn trước giải ngân trả đủ USD và không tính phí FreelaX. Rail này tự duyệt sau 3 ngày làm việc, nhắc 2 lần; tranh chấp đóng băng tiền, dành 3 ngày làm việc tự thương lượng, sau đó Admin có mốc 5 ngày làm việc để quyết định. Một Job hiện chỉ có một Milestone; trả theo nhiều giai đoạn là hướng sau MVP.

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

## 4. Luồng nghiệp vụ end-to-end đã được kiểm chứng ở baseline P06

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

## 9. Trạng thái cuối của baseline P06

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
