# Báo cáo đánh giá tích hợp FreelaX với Solana Stablecoin Payout

> **Cập nhật 28/09/2026:** phân tích riêng ba commit mới nhất của `master`
> (`3352168`, `7e73cee`, `26c44d3`) được ghi tại
> [bao-cao-3-commit-moi-nhat-master-2026-09-28.md](./bao-cao-3-commit-moi-nhat-master-2026-09-28.md).
> Các phần bên dưới là baseline ngày 27/09/2026 tại `557dc90` và cần được đọc
> cùng bản cập nhật mới, không còn đại diện đầy đủ cho HEAD hiện tại.

Ngày cập nhật: 27/09/2026

Nguồn FreelaX: `https://github.com/TronkIshere/FreelaX`

FreelaX được phân tích tại commit mới nhất của nhánh `master`: `557dc905a80b57ad4dc306679a9b9202de72383d`

Commit dùng trong lần cập nhật trước: `62246a906126e97c89a4a2f6460d7c523b5c18b9`

Commit gốc của báo cáo: `115fdaf384325282ecefb91e1f0e66b7af5e51d7`
Solana Program ID: `CwuaAPrxYLK6avPUbMRBerBYt1apdNU829TDZmoAnhEf`

## Phạm vi và phương pháp kiểm tra

Đây là kết quả đọc code, IDL và chạy test thực tế, không phải kết luận từ README.

- Đã lập inventory toàn bộ 472 file được Git theo dõi trong FreelaX, bao gồm cả các thư mục platform của Flutter.
- Đã đối chiếu commit `557dc90` với `62246a9`: 26 file thay đổi, 343 dòng thêm và 241 dòng xóa. Commit mới thay PayPal checkout bằng sổ cái BofA mô phỏng, chuyển thời điểm debit sang lúc Client approve và bổ sung flow Freelancer ứng tuyển; không có code tích hợp Solana mới.
- Đã đọc sâu critical path sau đổi tên trong `marketplace-backend`, `payment-backend`, `misa-backend`, `paypal/lib`, các build file, cấu hình runtime, entity/repository/service/controller/client và schema khởi tạo.
- Đã đọc sâu toàn bộ source Rust, IDL sinh ra và các test/helper của module Solana hiện tại.
- Đã đọc các tài liệu module và inventory endpoint/request trong ba Postman collection để đối chiếu với code. Nhiều phần tài liệu đã cũ; mọi kết luận trong báo cáo ưu tiên code thực tế.
- Không đọc nội dung nhị phân của icon PNG/ICO; không đọc từng dòng boilerplate sinh tự động cho Android/iOS/macOS/Windows; không đọc chi tiết từng DTO auth lặp lại vì chúng không nằm trên payment critical path. Tên và vị trí của chúng vẫn đã được inventory.
- FreelaX không có file test Java/Dart nào trong tree. Môi trường phân tích cũng không có `java`, `mvn`, `flutter` hay `dart`, và repo không có Maven Wrapper, nên chưa thể xác nhận build FreelaX. Đây là giới hạn xác minh, không phải kết luận rằng FreelaX build lỗi.

Mức độ tin cậy: **cao đối với contract và critical path payment hiện tại; trung bình-cao đối với khả năng chạy toàn bộ FreelaX**, do không thể build/run ba backend và Flutter trong môi trường này.

Các file không được đọc từng dòng là các binary asset dưới `paypal/**/Assets.xcassets/**`, `paypal/**/mipmap-*/**`, `paypal/web/icons/**`, boilerplate platform dưới `paypal/android`, `paypal/ios`, `paypal/macos`, `paypal/windows`, các metadata IDE `.idea/**`, và các DTO auth/common lặp lại không tham gia payment. Không có file Java/Dart/Rust nào trên critical path được cố ý bỏ qua.

## Tóm tắt điều hành

**Kết luận chính xác:** module Solana có thể tích hợp vào FreelaX để tạo một phương thức thanh toán USDC thứ hai chạy song song với payment rail hiện tại. Rail BofA hiện chỉ là **mô phỏng nội bộ**: order được tạo trong MySQL, mỗi số tài khoản mới mặc định có 10.000 USD và balance bị trừ khi Client approve; runtime critical path không còn gọi PayPal hay BofA thật. Repository hiện tại **chưa thể chạy end-to-end** với Solana. Program on-chain đã hoạt động và có 60 test passing từ lần xác minh Phase 10 ngày 27/09/2026, nhưng FreelaX chưa có wallet identity, adapter RPC/Anchor, schema payment Solana, API transaction preparation/verification, indexer/reconciliation, rate collector, UI ký giao dịch hay settlement worker.

Có một xung đột nghiệp vụ phải quyết định trước khi triển khai:

- FreelaX mới tạo BofA order `CREATED` ngay khi tạo Job, nhưng chưa debit. Freelancer ứng tuyển; Client gán Freelancer làm Job chuyển `OPEN -> IN_PROGRESS`. Chỉ khi Client `approve`, backend mới debit balance mô phỏng, capture order và chuyển Job sang `COMPLETED`.
- `pay_invoice` hiện chuyển USDC **thẳng vào ATA của Freelancer ngay khi thanh toán**. Program không escrow, không có `release` hoặc `refund`.

Thiết kế đã chốt là **direct payment tại bước approve**: Client ký `pay_invoice`, USDC chuyển thẳng sang Freelancer, backend chỉ đánh dấu Job `COMPLETED` sau khi xác minh finalized `InvoicePaid`. Freelancer không có bảo đảm tiền trước khi làm; đây là trade-off được chấp nhận cho MVP. Program không triển khai escrow, refund hoặc dispute on-chain.

Khuyến nghị cho FreelaX hiện tại: giữ `payment-backend` như rail demo, thêm `SOLANA_USDC` như payment method thứ hai, và tách payment khỏi `checkoutOrderId` trong `Job`. Backend triển khai duy nhất flow direct pay-at-approve; không xây các API fund/release/refund/dispute của escrow.

### Quyết định kiến trúc đã chốt

| Concern | Quyết định | Hệ quả kỹ thuật |
|---|---|---|
| Payment | Direct payment tại `approve` | `pay_invoice` chuyển Client ATA → Freelancer ATA; `InvoicePaid` là bằng chứng thanh toán |
| Income recognition | Tại finalized `InvoicePaid` | MISA handoff chỉ chạy sau verifier/indexer xác nhận event; không chờ off-ramp |
| Refund/dispute | P2P/thỏa thuận dân sự off-chain | Không có vault/refund/dispute instruction; giao dịch SPL Token đã finalized không được đảo ngược bởi program |
| Mint/rate rotation | Lock theo từng Invoice và có `expires_at` | Invoice phải lưu `mint`, `rate_snapshot` và expiration; config rotation chỉ áp dụng cho invoice tạo sau đó |
| Off-ramp failure | `FAILED_PENDING_REVIEW` | Không tự hoàn USDC; operator xử lý manual/semi-manual và lưu đầy đủ audit trail |

Các quyết định này loại bỏ nhánh escrow khỏi phạm vi MVP, nhưng tạo hai nghĩa vụ UX rõ ràng: Freelancer phải biết chưa có tiền bảo đảm trước khi làm, và Client phải được cảnh báo direct payment là không thể hoàn tác on-chain.

Quy ước kỹ thuật của báo cáo: RateSnapshot khóa trong Invoice là **tỷ giá ghi nhận thu nhập**. Off-ramp diễn ra sau đó dùng một snapshot execution còn hiệu lực tại thời điểm rút và không sửa lại income record. Nếu bắt buộc off-ramp dùng đúng rate cũ của Invoice thì treasury phải chịu rủi ro tỷ giá và snapshot expiry sẽ mâu thuẫn với việc rút muộn. Với mint rotation, mint cũ phải còn được off-ramp trong một grace/settlement window sau invoice expiry; chỉ thay một `accepted_mint` toàn cục sẽ làm USDC cũ của Freelancer bị kẹt.

---

## PHASE 1 — Repo discovery

### 1.1 Cấu trúc thực tế của FreelaX

```text
FreelaX/
├── pom.xml                         # Maven parent, Java 17, Spring Boot 3.5.6
├── docker-compose.yml              # MySQL, Redis và ba backend
├── init-db.sql                     # Chỉ tạo 3 database
├── marketplace-backend/            # Job, auth, orchestration payment/MISA
├── payment-backend/                # Sổ cái BofA mô phỏng, không gọi ngân hàng thật
├── misa-backend/                   # Payout record, taxpayer, chứng từ thuế
└── paypal/                         # Flutter client, tên thư mục dễ gây nhầm
```

| Module | Stack/build | Trách nhiệm thực tế | Nơi sở hữu logic chính |
|---|---|---|---|
| `marketplace-backend` | Java 17, Spring Boot 3.5.6, Maven, JPA, MySQL, Redis, JWT | User, Job lifecycle, gọi payment backend và MISA, notification | `JobServiceImpl`, `AuthenticationServiceImpl` |
| `payment-backend` | Java 17, Spring Boot, Maven, JPA, MySQL | Tạo/capture BofA order mô phỏng; balance theo số tài khoản, mặc định 10.000 USD, debit khi capture | `BofaCheckoutOrderServiceImpl`, `BofaAccountBalanceServiceImpl` |
| `misa-backend` | Java 17, Spring Boot, Maven, JPA, MySQL | Taxpayer, payout income, withholding certificate; provider hiện là mock | `PayoutTransactionServiceImpl`, `WithholdingCertificateServiceImpl` |
| `paypal` | Flutter/Dart, Riverpod, `http` | Mobile/web UI, auth client, mock Job, PayPal demo | `JobService`, repositories và screens |

### 1.2 Build, database và config

- Root `pom.xml` aggregate ba backend. Không có Solana Java SDK hoặc Anchor client dependency.
- `init-db.sql` đã đổi database `paypal` thành `payment`; vẫn chỉ tạo ba database, không tạo bảng, index hay migration.
- Cả ba backend dùng `spring.jpa.hibernate.ddl-auto: update`, kể cả profile production.
- `docker-compose.yml` khai báo ba service backend, MySQL và Redis.
- Flutter `pubspec.yaml` không có Solana wallet adapter, Solana RPC SDK, deep-link wallet package hoặc base58 package.
- Không có `.env.example`; runtime config nằm trong `application*.yml` và Compose.

### 1.3 Chủ sở hữu theo concern

| Concern | Owner hiện tại | Bằng chứng code |
|---|---|---|
| Authentication | Mỗi backend tự có auth/JWT riêng | Ba bộ `AuthController`, `AuthenticationServiceImpl`, `User` |
| Job/business lifecycle | Marketplace | `marketplace-backend/.../JobServiceImpl.java` |
| Payment orchestration | Marketplace | `JobServiceImpl.create/approve`, `PaymentBackendClient` |
| BofA simulation ledger | Payment backend | `BofaCheckoutOrderServiceImpl`, `BofaAccountBalanceServiceImpl`; không có external bank call trên critical path |
| Job application | Marketplace | `JobController.apply/listApplications/assignFreelancer`, `JobApplicationRepository` |
| Tax/MISA handoff | Marketplace gọi MISA backend | `JobServiceImpl.exportTaxRecordSafely`, `MisaBackendClient` |
| Database | Mỗi backend có schema MySQL riêng | `application.yml`, `init-db.sql` |
| Frontend | Flutter module `paypal` | `paypal/lib/main.dart` và feature repositories |
| Blockchain/Solana | Chưa tồn tại trong FreelaX | Search source chỉ thấy chuỗi `blockchain = "solana"` ở MISA model |

---

## PHASE 2 — Critical-path analysis của FreelaX

### 2.1 Entry point tới payment rail hiện tại

1. Client gọi `POST /api/v1/marketplace/jobs` với thông tin Job và `payerBankCode`, `payerBankAccountNumber`, `payerBankAccountHolderName`.
2. `JobServiceImpl.create` lưu Job `OPEN`, rồi `PaymentBackendClient.createCheckoutOrder` gọi `POST /internal/BofA/checkout/orders` bằng internal API key.
3. Payment backend tự sinh `bofaOrderId`, lưu `BofaCheckoutOrder(CREATED)` trong MySQL; không gọi PayPal hoặc BofA bên ngoài. Marketplace lưu `checkoutOrderId`.
4. Nếu request tạo Job đã truyền `freelancerId`, Job được chuyển thẳng sang `IN_PROGRESS`; nhánh này bỏ qua flow ứng tuyển.
5. Nếu chưa gán, Freelancer gọi `POST /jobs/{jobId}/apply`; marketplace tạo `JobApplication(PENDING)`.
6. Client xem applications rồi gọi `PATCH /jobs/{jobId}/assign-freelancer`; application được accept, các application pending khác bị reject và Job chuyển `OPEN -> IN_PROGRESS`.
7. Client gọi `POST /jobs/{jobId}/approve`. Marketplace capture checkout order.
8. Payment backend lấy balance theo đúng chuỗi `payerBankAccountNumber`; nếu chưa tồn tại thì tự tạo với 10.000 USD, sau đó debit `amountUsd` và chuyển order sang `CAPTURED`.
9. Marketplace chuyển Job sang `COMPLETED`, gửi notification và thử export payout/tax record sang MISA. Không có credit/chuyển tiền cho Freelancer trong payment backend.

### 2.2 State machine hiện tại

`JobStatus` trong `marketplace-backend/.../entity/JobStatus.java`:

```text
OPEN --freelancer applies--> OPEN
OPEN --assign freelancer--> IN_PROGRESS
OPEN --cancel--> CANCELLED
IN_PROGRESS --approve/CAPTURED--> COMPLETED
```

Nhánh tạo Job có sẵn `freelancerId` đi trực tiếp `OPEN -> IN_PROGRESS`. Enum vẫn còn `AWAITING_PAYMENT` nhưng critical path mới không set trạng thái này. Không có refund, dispute, payment failed, payment expired hoặc reconciliation state trong `JobStatus`.

BofA-labelled checkout order có `CREATED`, `CAPTURED`, `FAILED`. Solana Invoice có `Pending`, `Paid`, `Cancelled`. Withdrawal có `Pending`, `Completed`.

### 2.3 Model/ID hiện được lưu

`Job.java` lưu `UUID id`, `budgetUsd`, `clientUserId`, `freelancerId`, `status`, `checkoutOrderId`, ba field payer bank, `misaPayoutTransactionId`, `misaCertificateId`, `taxExportStatus`. `JobApplication` mới lưu `(jobId, freelancerId, PENDING/ACCEPTED/REJECTED)` với unique constraint. Không có payment method, wallet, mint, amount base units, Invoice PDA, transaction signature, slot hoặc confirmation status.

`User.java` lưu identity, loại user, thuế và tài khoản ngân hàng. Không có Solana public key hoặc bằng chứng user sở hữu wallet.

`PayoutTransaction.java` lưu `platformPayoutId` dạng string, `blockchain`, `transactionHash`, `amountUsdc`, `exchangeRate`, `amountVndGross`, nhưng service nhận toàn bộ các giá trị này từ HTTP body và không verify blockchain.

### 2.4 Authorization hiện tại

- Các Job mutation dùng authenticated principal; `getOwnedByClientOrThrow` chỉ cho Client sở hữu Job gọi update, assign, approve và cancel.
- Freelancer đã có API `apply`, nhưng không có endpoint liệt kê Job `OPEN` công khai và `getByIdForParticipant` không cho Freelancer chưa được gán xem chi tiết. Flow ứng tuyển vì vậy chỉ dùng được nếu Freelancer biết trước `jobId`.
- Payment internal API dựa trên `X-Internal-Api-Key`.
- MISA `registerForExternal` yêu cầu role `PLATFORM_INTEGRATION`, nhưng payout/certificate endpoints chỉ yêu cầu “đã đăng nhập”, không giới hạn role/platform account và không kiểm tra taxpayer ownership.

---

## PHASE 3 — Module Solana hiện tại

### 3.1 Responsibility và interface

IDL `target/idl/invoice_payments.json` có đúng 11 instruction:

1. `initialize_config`
2. `update_config`
3. `create_invoice`
4. `pay_invoice`
5. `publish_rate`
6. `cancel_invoice`
7. `close_invoice`
8. `request_offramp`
9. `record_offramp`
10. `mark_offramp_failed`
11. `resolve_offramp`

Program dùng Anchor `1.2.0`, Legacy SPL Token Program, accepted mint 6 decimals. Không dùng Token-2022.

### 3.2 PDA/state

| State | Seeds | Dữ liệu liên quan integration |
|---|---|---|
| `Config` | `["config"]` | admin, accepted mint, treasury/rate/oracle authorities, max age, paused |
| `Invoice` | `["invoice", freelancer_pubkey, invoice_id_u64_le]` | client/freelancer wallet, token amount, locked mint/rate/expiry, status, timestamps |
| `RateSnapshot` | `["rate", rate_id_u64_le]` | USDC/USD, USD/VND, USDC/VND e6, times, source hash, publisher |
| `WithdrawalRecord` | `["withdrawal", freelancer_pubkey, withdrawal_id_u64_le]` | amount, mint, treasury ATA, rate PDA, VND amount, status, failure/resolution audit |

Phase 10 đã bổ sung `Invoice.rate_snapshot`, `Invoice.expires_at`,
`WithdrawalStatus::FailedPendingReview` và audit fields. Đây là breaking account
layout/IDL change; account cũ không được deserialize như layout mới nếu chưa có
migration/reallocation được audit.

### 3.3 Token, signer và transaction semantics

- `pay_invoice`: Client wallet ký; chuyển đúng `Invoice.amount` từ Client ATA sang Freelancer ATA bằng `transfer_checked`; Invoice chỉ thành `Paid` nếu CPI thành công.
- `request_offramp`: Freelancer ký; dùng snapshot còn hiệu lực; chuyển USDC từ Freelancer ATA vào Treasury ATA; tạo withdrawal `Pending`.
- `record_offramp`: Settlement Oracle ký và đổi `Pending -> Completed`; nhánh lỗi dùng `mark_offramp_failed`, sau đó Admin dùng `resolve_offramp` với hash audit.
- `publish_rate`: Rate Authority ký; tạo snapshot immutable, fixed point `10^6`, checked `u128` trung gian.

### 3.4 Dependencies và assumptions

Program giả định:

- Có một accepted mint 6 decimals đã được Config chọn.
- Client/Freelancer đã có ví, SOL trả fee/rent và ATA đúng mint.
- Treasury ATA đã tồn tại.
- Caller tự cấp `invoice_id`, `rate_id`, `withdrawal_id` không trùng.
- Một dịch vụ ngoài chain sở hữu Rate Authority và Oracle Authority.
- Một đối tác ngoài chain thực sự gửi VND trước khi Oracle gọi `record_offramp`.
- Backend/indexer tự đọc event và reconcile; program không cung cấp việc đó.

### 3.5 Kết quả xác minh thực tế

| Lệnh | Kết quả ngày 26/09/2026 |
|---|---|
| `cargo fmt --check` | PASS |
| `anchor build` | PASS |
| `yarn tsc --noEmit` | PASS |
| `anchor test --validator legacy` | PASS — **60 passing (1m)** |

---

## PHASE 4 — Compatibility mapping

| FreelaX | Solana module | Mức khớp | Ghi chú bắt buộc |
|---|---|---|---|
| `Job.id: UUID` | `Invoice.invoice_id: u64` + Invoice PDA | Không trực tiếp | Cần cột `solanaInvoiceId` riêng; không ép/cắt UUID xuống u64 |
| `User.id: UUID` của Client | `Invoice.client: Pubkey` | Cần mapping | Cần wallet binding đã verify chữ ký |
| `User.id: UUID` của Freelancer | `Invoice.freelancer: Pubkey` | Cần mapping | Freelancer signer tạo/cancel/close invoice |
| `Job.budgetUsd: BigDecimal(19,2)` | `Invoice.amount: u64` base units | Cần conversion | 1 USDC = 1,000,000 base units; phải chốt rounding và quote |
| `JobStatus.OPEN` | Chưa có invoice hoặc `Invoice.Pending` | Một phần | Job có thể đã có BofA order `CREATED`; Job và payment không cùng state machine |
| `AWAITING_PAYMENT` | `Invoice.Pending` | Không còn active | Enum còn tồn tại nhưng flow mới không set trạng thái này |
| `IN_PROGRESS` | Invoice chưa paid | Đã chốt | Freelancer làm việc trước khi tiền được bảo đảm; UI phải công bố trade-off này |
| `COMPLETED` | finalized `Invoice.Paid` | Mapping đích | Backend chỉ transition sau khi verify đúng transaction/account/event |
| `CANCELLED` | `Invoice.Cancelled` | Một phần | FreelaX Client cancel Job; on-chain chỉ Freelancer cancel Invoice |
| `checkoutOrderId` | Invoice PDA | Sai abstraction nếu dùng chung | Nên có `PaymentAttempt` thay vì nhét PDA vào cột checkout-provider cụ thể |
| BofA order `CAPTURED` | Invoice `Paid` | Gần tương đương | Cùng thời điểm approve; backend vẫn phải verify on-chain, không tin frontend |
| `PayoutTransaction.platformPayoutId` | Withdrawal PDA hoặc Invoice PDA | Cần quy ước | Nên dùng canonical address/string và unique constraint |
| `PayoutTransaction.transactionHash` | Solana transaction signature | Trực tiếp | Hiện FreelaX gửi chuỗi giả `internal:{jobId}` |
| `amountUsdc` | `token_amount / 10^6` | Trực tiếp nếu đúng mint | Không dùng `double`; dùng integer/BigDecimal |
| `exchangeRate` | `RateSnapshot.usdc_vnd_e6 / 10^6` | Trực tiếp | Phải lấy từ account verified, không nhận từ frontend |
| `TaxExportStatus` | Không có on-chain | Ngoài chain | Giữ ở marketplace/MISA |
| Bank account | Treasury/off-ramp provider | Không trực tiếp | Cần settlement service/provider adapter |

### Quyết định đối với payment rail hiện tại

**Solana nên chạy song song như payment method thứ hai**, không thay toàn bộ rail hiện tại ngay.

Bằng chứng:

- `JobServiceImpl` và `Job` đang bị gắn với `checkoutOrderId`, nhưng phần Job/auth/notification/MISA vẫn dùng lại được.
- `payment-backend` là service độc lập nhưng chỉ mô phỏng BofA bằng MySQL; không gọi provider bên ngoài. Có thể giữ làm rail demo, không được mô tả là bank integration production.
- Flutter chưa được đổi theo backend, vẫn có model/repository/màn hình PayPal và màn hình so sánh phí. Hỗ trợ hai rail vẫn phù hợp, nhưng contract Flutter ↔ backend cần đồng bộ.
- Solana cần chữ ký thật của Client tại approve. UX phải yêu cầu ký và chỉ báo `COMPLETED` sau finalized `InvoicePaid`; không có escrow/refund/dispute on-chain trong phạm vi đã chốt.

---

## PHASE 5 — Integration assessment

| Phân loại | File hiện tại | Vấn đề và lý do | Thay đổi cần thiết |
|---|---|---|---|
| `READY` | `programs/invoice_payments/src/**` | 11 instruction có trong IDL, build/typecheck/60 test pass | Đóng gói IDL/artifact theo version và deploy cluster mục tiêu |
| `READY` | `marketplace-backend/.../JobController.java` | Có authenticated Job entry points và ownership check | Giữ controller làm public façade |
| `NEEDS_ADAPTER` | Chưa có | Không có client gọi Solana RPC/deserialize Anchor account | Thêm `SolanaGateway`/`AnchorProgramClient` trong marketplace hoặc service riêng |
| `NEEDS_MODEL_CHANGE` | `entity/Job.java` | Chỉ có `checkoutOrderId`; không biểu diễn nhiều payment methods/attempts | Thêm `PaymentAttempt` entity và quan hệ Job 1-N |
| `NEEDS_MODEL_CHANGE` | `CreateJobRequest.java` | Ba payer-bank fields đang bắt buộc cho mọi Job, kể cả khi chọn Solana | Tách `paymentMethod` và provider-specific request; không buộc dữ liệu BofA cho Solana |
| `NEEDS_MODEL_CHANGE` | `entity/User.java` | Không có wallet binding | Thêm wallet table với public key, chain, proof status, timestamps |
| `NEEDS_NEW_SERVICE` | `JobServiceImpl.java` | Job orchestration gọi thẳng `PaymentBackendClient` và hardcode rate | Tách `PaymentOrchestrator`, adapter cho rail hiện tại và `SolanaPaymentAdapter` |
| `NEEDS_NEW_SERVICE` | Chưa có | Không verify signature/account/event on-chain | Thêm confirmation verifier + indexer + reconciliation worker |
| `NEEDS_NEW_SERVICE` | Chưa có | Không có rate collector/publisher | Thêm provider adapters, aggregation policy và publisher signer |
| `NEEDS_DATABASE_CHANGE` | `init-db.sql`, JPA entities | Không có Solana invoice/rate/withdrawal/event/cursor tables | Dùng Flyway/Liquibase và unique indexes |
| `NEEDS_CONFIG` | `application.yml`, Compose | Không có RPC, cluster, program ID, mint, commitment, IDL version | Thêm typed `SolanaProperties` và secrets ngoài source |
| `NEEDS_FRONTEND_CHANGE` | `paypal/pubspec.yaml`, `HireFreelancerScreen` | Không có wallet adapter; UI chỉ PayPal và contract cũ | Thêm connect/sign/submit/status UI cho Solana |
| `DONE_PHASE_10` | `Invoice`, `create_invoice`, `pay_invoice` | Đã lock mint/rate/expiry; pay dùng Invoice mint và chặn expiry | Backend phải dùng IDL mới và verify locked fields |
| `NEEDS_PROGRAM_CHANGE` | `Config`/mint policy | Một `accepted_mint` toàn cục không đủ cho safe rotation và off-ramp token cũ | Thêm versioned mint registry/policy với create/pay/off-ramp cutoffs |
| `DONE_PHASE_10` | `WithdrawalRecord`, off-ramp instructions | Có `FailedPendingReview`, failure hash và Admin resolution audit | Backend/indexer cần consume hai event mới |
| `BLOCKER` | `JobRepository.java` | Có derived query dùng field không tồn tại: `freelancerUserId` | Xóa/sửa thành `freelancerId`; nếu không Spring Data có thể fail khi tạo repository bean |
| `BLOCKER` | `MisaBackendClient.java` | Client deserialize trực tiếp DTO, controller trả `ResponseAPI<T>` | Deserialize wrapper hoặc đổi contract thống nhất |
| `BLOCKER` | `docker-compose.yml`, ba `Dockerfile.maven` | Compose trỏ `Dockerfile.maven` ở root nhưng file không tồn tại; Dockerfiles trong module vẫn `COPY paypal-backend/pom.xml` | Tạo/sửa root Dockerfile hoặc đổi context/path; thay mọi đường dẫn stale thành `payment-backend` |
| `NEEDS_FRONTEND_CHANGE` | `RemoteJobRepository.java`, checkout UI | List kỳ vọng `List`, backend trả `PageResponse`; UI vẫn dùng PayPal endpoints/model và chưa gửi bank fields/application flow mới | Đồng bộ contract với `JobController` và bỏ flow checkout trực tiếp cũ |
| `BUG` | Job discovery/application flow | Có `apply`, nhưng list/get chỉ trả Job mà user đã là participant | Thêm endpoint browse/search Job `OPEN` với dữ liệu công khai phù hợp |

---

## PHASE 6 — Missing pieces để chạy end-to-end

### 6.1 Identity và wallet

1. `UserWallet` model: `id`, `userId`, `chain`, `publicKey`, `isPrimary`, `verifiedAt`, `revokedAt`, nonce/challenge metadata; unique `(chain, publicKey)`.
2. API tạo challenge và verify signed message. Không được chỉ cho user POST một public key rồi tin rằng họ sở hữu ví.
3. Client/Freelancer wallet phải khớp `Invoice.client/freelancer` khi backend xác minh.
4. Chính sách đổi/revoke wallet khi còn Invoice/Withdrawal pending.

### 6.2 Payment API và adapter

Nên thêm dưới marketplace, vì `JobController` đang là façade của business flow:

- `POST /api/v1/marketplace/jobs/{jobId}/payments/solana/invoice/prepare` — Freelancer tạo unsigned `create_invoice` chứa mint, RateSnapshot và `expires_at` đã lock sau authorization.
- `POST /.../invoice/confirm` — nhận signature, nhưng server tự RPC verify rồi mới lưu payment attempt `INVOICE_CREATED`; không dùng `JobStatus.AWAITING_PAYMENT` đã bị bỏ khỏi flow mới.
- `POST /.../approve/prepare` — dựng `pay_invoice` từ Invoice còn hạn; Client xác nhận mint, rate snapshot và amount rồi ký.
- `POST /.../approve/confirm` — chỉ đổi Job sang `COMPLETED` và kích hoạt income/MISA workflow sau khi verify finalized `InvoicePaid`.
- `GET /.../payment-status` — trả normalized status từ DB kèm chain confirmation, không chỉ echo frontend.

Không tạo API `fund`, `release`, `refund` hoặc `dispute` on-chain. Backend chỉ có thể lưu case/reference/note cho quy trình P2P off-chain; không được thể hiện rằng có thể đảo ngược giao dịch Solana.

Backend không giữ private key của user. Backend dựng transaction; wallet người dùng ký. Chỉ Rate Authority/Settlement Oracle là service key, phải nằm trong secret manager/HSM/KMS hoặc signer service, không nằm trong source/database plaintext.

### 6.3 Database tối thiểu

`payment_attempts`:

- `id`, `job_id`, `method` (tên provider đã chốt, ví dụ `PAYPAL` hoặc `BOFA`, và `SOLANA_USDC`), normalized `status`.
- `amount_usd`, `token_amount_base_units`, `mint`, `token_decimals`, `rate_snapshot_pda`, `invoice_expires_at`.
- `payer_wallet`, `payee_wallet`, `invoice_id_u64`, `invoice_pda`.
- `create_invoice_signature`, `pay_signature`, `slot`, `commitment`, `last_verified_at`.
- `provider_reference`, `failure_code`, timestamps, version.
- Unique: `(job_id, method, active-attempt policy)`, `invoice_pda`, transaction signatures.

Các bảng khác:

- `user_wallets` cho identity mapping.
- `solana_rate_snapshots` cho PDA, component rates, combined rate, observed/expires/source hash/publisher/signature/slot.
- `solana_withdrawals` cho Withdrawal PDA, user, amount, rate, treasury, request/complete signatures, provider/bank settlement reference.
- `solana_events` unique `(signature, instruction_index, event_index)` để chống duplicate.
- `solana_sync_cursor` để backfill/reconciliation.

### 6.4 Backend verification on-chain bắt buộc

Khi frontend gửi signature, backend phải:

1. Gọi RPC lấy signature status và yêu cầu commitment đã chọn, nên là `finalized` cho business settlement.
2. Lấy transaction; kiểm tra `meta.err == null`.
3. Kiểm tra instruction gọi đúng Program ID `Cwua...AnhEf`, không chỉ có một SPL transfer bất kỳ.
4. Derive lại PDA bằng seeds chuẩn và so với account trong transaction.
5. Fetch/deserialise Invoice hoặc Withdrawal account từ IDL đúng version.
6. Cross-check Job mapping, payer/payee wallet, locked mint, locked RateSnapshot, `expires_at`, amount base units và state.
7. Kiểm tra event tương ứng (`InvoiceCreated`, `InvoicePaid`, `OfframpRequested`, `OfframpFailedPendingReview`, `OfframpCompleted`) và slot/signature.
8. Chỉ sau đó mới transition DB bằng compare-and-set/idempotent transaction.
9. Một reconciliation worker quét lại signature/PDA/event nếu callback bị mất hoặc RPC tạm lỗi.

`payment successful` do frontend gửi lên **không bao giờ là bằng chứng đủ**.

### 6.5 Rate, on-ramp và off-ramp

- Rate collector cho USDC/USD và USD/VND; định nghĩa source, fallback, freshness và circuit breaker.
- Durable allocator cho `rate_id`; publisher tạo `source_hash` có thể audit.
- `create_invoice` phải nhận/verify RateSnapshot còn hiệu lực, lưu PDA snapshot và `expires_at`; `invoice.expires_at` không được vượt `rate_snapshot.expires_at`. `pay_invoice` từ chối sau expiration.
- Mint rotation chỉ ảnh hưởng invoice mới. Invoice cũ dùng `invoice.mint` đã lock cho tới khi hết hạn; phải bỏ constraint `invoice.mint == config.accepted_mint` trong `pay_invoice` và validate mint account bằng `invoice.mint`.
- Không nên chỉ lưu một mint toàn cục. Cần versioned mint policy/registry với tối thiểu `accept_new_invoices_until`, `pay_existing_until` và `offramp_until`; `offramp_until` phải đủ dài để token mint cũ đã trả cho Freelancer không bị kẹt sau rotation.
- RateSnapshot của Invoice dùng cho accounting tại `InvoicePaid`. `request_offramp` dùng snapshot execution mới còn hạn; chênh lệch rate không được hồi tố income record.
- On-ramp USD→USDC hiện hoàn toàn chưa có. Cần provider hoặc demo faucet rõ ràng; không được gọi Mock USDC là tiền thật.
- Settlement worker theo dõi `OfframpRequested`, gọi licensed off-ramp/bank provider, chỉ dùng Oracle signer gọi `record_offramp` sau khi provider xác nhận.
- Nếu provider lỗi/timeout không xác định, Oracle chuyển Withdrawal sang `FAILED_PENDING_REVIEW`; USDC vẫn nằm ở treasury và không tự refund. Operator retry/đối soát thủ công, sau đó mới resolve sang `Completed` khi có bằng chứng VND settlement.
- DB phải giữ provider reference, failure code/hash, operator action, timestamps và signature. On-chain nên emit failure/resolution event nhưng không chứa PII ngân hàng.

### 6.6 MISA/tax handoff

- Thay `transactionHash = "internal:" + jobId` bằng signature verified thật.
- Thay rate placeholder bằng `RateSnapshot` verified.
- Sửa wrapper contract của `MisaBackendClient`.
- Chỉ tạo record từ một event đã canonical/finalized và dùng `platformPayoutId` idempotent.
- Thu nhập được ghi nhận tại finalized `InvoicePaid`, dùng amount/mint/RateSnapshot đã lock trong Invoice. Off-ramp chỉ là bước chuyển đổi/settlement sau đó, không thay đổi thời điểm ghi nhận thu nhập.
- Có retry/dead-letter/manual replay cho `TaxExportStatus.FAILED`.

### 6.7 Config/env cần thêm

```text
SOLANA_CLUSTER
SOLANA_RPC_HTTP_URL
SOLANA_RPC_WS_URL
SOLANA_COMMITMENT
SOLANA_PROGRAM_ID
SOLANA_ACCEPTED_MINT
SOLANA_TOKEN_DECIMALS=6
SOLANA_IDL_PATH hoặc SOLANA_IDL_VERSION
SOLANA_TREASURY_AUTHORITY
SOLANA_RATE_AUTHORITY_SIGNER_REF
SOLANA_ORACLE_AUTHORITY_SIGNER_REF
SOLANA_CONFIRMATION_TIMEOUT_MS
SOLANA_RECONCILIATION_FROM_SLOT
```

Signer ref phải trỏ tới secret manager/signer service; không phải private key literal.

### 6.8 Tests và deployment còn thiếu

- Unit tests adapter/parser/PDA derivation/amount conversion/idempotency.
- Integration tests marketplace ↔ local validator ↔ MySQL.
- Contract tests marketplace ↔ MISA/payment-backend wrappers.
- E2E Flutter wallet ký transaction trên devnet.
- Failure tests cho RPC timeout, finalized fork handling, duplicate signature/event, retry, stale DB state.
- Devnet deployment manifest: Program ID, IDL checksum, mint, Config PDA, authorities, treasury ATA, explorer links.

---

## PHASE 7 — Wrong design, conflict và bug có bằng chứng

### 7.1 Facts cần sửa trước integration

| Mức | Vấn đề | Bằng chứng | Hậu quả |
|---|---|---|---|
| `BLOCKER` | Derived query sai property | `JobRepository.java` khai báo `findByFreelancerUserId` trong khi entity chỉ có `freelancerId` | Spring Data có thể fail startup khi tạo repository bean |
| `BLOCKER` | MISA response contract mismatch | `MisaBackendClient` đọc `MisaPayoutTransactionResult`, controller trả `ResponseAPI<PayoutTransactionResponse>` | `id` deserialize không đúng/null; tax export hỏng |
| `BLOCKER` | Docker build path stale | Compose dùng root `Dockerfile.maven` không tồn tại; cả ba module Dockerfile vẫn `COPY paypal-backend/pom.xml` sau khi thư mục đã đổi thành `payment-backend` | `docker compose build` không thể dùng config hiện tại |
| `BUG` | MISA URL trong container thiếu | Compose không set `MISA_BACKEND_BASE_URL`; default là `localhost:9192` | Marketplace container gọi chính nó thay vì `misa-backend` |
| `BUG` | Flutter Job API contract sai | `RemoteJobRepository.listOpenJobs` mong `List`, backend trả `PageResponse`; `/checkout-order` không tồn tại | Chuyển sang remote repository vẫn không chạy |
| `BUG` | Flutter checkout contract chưa đổi theo backend | UI gọi `/api/v1/paypal/...`, dùng model `paypalOrderId` và body `payeeId/referenceId`; backend tạo BofA order nội bộ ngay trong `POST /jobs` và yêu cầu ba payer-bank fields | 404/schema mismatch; UI không thể chạy flow mới |
| `BUG` | Application flow không có discovery | Có `POST /jobs/{id}/apply`, nhưng list/get Job chỉ cho Client hoặc Freelancer đã được gán | Freelancer chưa được gán không thể tìm/xem Job `OPEN` qua API, trừ khi biết `jobId` từ nguồn ngoài |
| `CONSISTENCY` | Payer-bank fields trong `Job` không được set ở create path | `Job` có ba field payer bank và request bắt buộc gửi chúng, nhưng `JobServiceImpl.create` chỉ forward sang payment backend; chỉ seed path set các field này | Cùng một entity có dữ liệu khác nhau giữa demo seed và API thật; nên xóa field khỏi Job hoặc persist nhất quán |
| `BUG` | CORS thiếu PATCH | Marketplace có PATCH endpoints nhưng `CORS_ALLOWED_METHODS` không có PATCH | Flutter Web/browser không gọi assign/update/read được cross-origin |
| `SECURITY` | MISA authorization quá rộng | Payout/certificate controllers không có `@PreAuthorize`/ownership check | User đăng nhập có thể ghi/đọc tài nguyên taxpayer khác nếu biết UUID |
| `SECURITY` | MISA tin dữ liệu blockchain do caller gửi | `PayoutTransactionServiceImpl.record` lưu hash, chain, amount, rate trực tiếp | Có thể ghi payout giả nếu API credential bị dùng sai/quyền quá rộng |
| `RESOLVED_PHASE_10` | Config initialization | Chỉ ProgramData upgrade authority được init; test signer giả bị từ chối | Deploy → init/verify finalized → mới transfer/revoke authority |
| `RESOLVED_PHASE_10` | Invoice mint/rate/expiry lock | Payment dùng locked mint qua Config rotation và chặn invoice hết hạn | IDL/layout breaking change đã ghi rõ |
| `RESOLVED_PHASE_10` | Off-ramp failure review | Có failure evidence, review state và Admin resolution event/audit | Không auto-refund; backend vẫn cần queue/SLA/maker-checker vận hành |
| `CONSISTENCY` | Job completion không đồng nghĩa Freelancer được trả | `approve` chỉ debit balance của payer và set `COMPLETED`; payment backend không credit payee hoặc chuyển ngân hàng | UI/status có thể nói thanh toán thành công khi Freelancer chưa nhận giá trị |
| `CONSISTENCY` | BofA chỉ là ledger mô phỏng | `getOrCreate(bankAccountNumber)` tự cấp 10.000 USD cho mọi số tài khoản mới; capture chỉ trừ balance MySQL | Không có xác minh sở hữu tài khoản, reserve, clearing hay bank settlement; tuyệt đối không dùng như tiền thật |
| `SECURITY` | Dữ liệu tài khoản ngân hàng chưa có protection policy | Full account number/holder name được caller gửi, lưu trong `BofaCheckoutOrder` và trả lại trong internal response | Cần masking, encryption-at-rest, access control, retention và log-redaction trước production |
| `DEAD_CODE` | Client/config PayPal cũ còn trong payment backend | `BofaCheckoutClient`, `BofaPayoutClient`, `PaypalCheckoutProperties`, DTO/package `com.paypal.*` vẫn tồn tại nhưng không được critical path mới sử dụng | Gây hiểu nhầm provider, tăng maintenance/config surface; nên xóa hoặc cô lập rõ |
| `CONSISTENCY` | Source of truth kép | `Job.status`, checkout status và Invoice status độc lập, chưa có reconciler | DB dễ stale so với provider/blockchain |
| `CONFIG` | Credential demo hardcoded | Marketplace/MISA application config và DataInitializer chứa platform password; initializers chứa user passwords | Không phù hợp production; dễ tái sử dụng nhầm secret |
| `DATABASE` | Không có migrations | `init-db.sql` chỉ tạo DB; prod dùng `ddl-auto:update` | Schema khó review/rollback/reproduce |
| `DOCUMENTATION` | Docs/Postman và metadata lệch code hiện tại | Tài liệu trong `payment-backend/docs` vẫn mang tên/nội dung PayPal; root/module descriptions và Flutter vẫn nói PayPal; marketplace docs còn contract cũ; source còn DTO/config package `com.paypal.*` | Người tích hợp không xác định được provider thật và dễ gọi endpoint/field không tồn tại; phải chốt provider rồi sinh lại docs từ code/OpenAPI |
| `RELIABILITY` | Cross-service create/capture chưa idempotent | Marketplace gọi payment backend bên trong DB transaction; capture lần hai bị reject và không có idempotency key | Remote call thành công nhưng response/transaction marketplace lỗi có thể tạo orphan order hoặc Job kẹt không retry được |
| `RELIABILITY` | Tax failure không tự retry | `exportTaxRecordSafely` set `FAILED`, chỉ log/manual | Mất handoff MISA lâu dài |
| `OBSERVABILITY` | Không có signature/slot/indexer cursor | `Job` không có các field này | Không audit và reconcile Solana được |

### 7.2 RISK — đã chấp nhận hoặc cần operational control

- **ACCEPTED RISK: Client không trả tại approve.** Direct pay-at-approve không reserve USDC; Freelancer làm việc trước khi có bảo đảm thanh toán. Backend không được đánh dấu `COMPLETED` nếu Client không ký hoặc transaction fail.
- **ACCEPTED RISK: giao dịch không thể đảo ngược.** Refund/dispute là P2P/off-chain; UI và Terms phải nói rõ program không hoàn hoặc thu hồi USDC sau finalized `InvoicePaid`.
- **OPERATIONAL RISK: off-ramp failed giữ token ở treasury.** `FAILED_PENDING_REVIEW` không tự refund; cần SLA, queue, maker-checker approval, audit log và cảnh báo số tiền/ticket tồn đọng.
- **RISK: `invoice_id`/`withdrawal_id` allocator.** Program cho caller chọn u64 là hợp lệ, nhưng backend cần cơ chế chống collision ổn định.
- **RISK: Rate Authority rotation.** Invoice/snapshot cũ đã lock phải còn verify được tới expiry; không được kiểm tra publisher chỉ bằng authority hiện tại nếu việc rotate làm vô hiệu snapshot hợp lệ. Cần versioning/revocation policy và runbook.
- **RISK: không lưu settlement reference on-chain.** DB có thể đủ cho MVP, nhưng audit story yếu hơn khi trình diễn.
- **RISK: `double` trong Flutter cho USD.** Hiện budget đi qua `double`; khi tạo token amount phải chuyển sang decimal string/integer, không nhân tiền bằng binary floating point.

---

## PHASE 8 — Kiến trúc

### 8.1 Kiến trúc hiện tại

```text
Flutter (Auth remote, Job mock, PayPal UI/contract cũ)
   |
   +--> Marketplace Backend --> Marketplace MySQL
               |
               +--> Payment Backend --> BofA ledger mô phỏng trong Payment MySQL
               |
               +--> MISA Backend --> Mock MISA Provider + MISA MySQL

Solana Anchor module (đứng riêng, chưa có kết nối tới FreelaX)
   +--> Legacy SPL Token Program
   +--> Local validator trong test
```

### 8.2 Kiến trúc đích được khuyến nghị

```text
Flutter
   |  JWT + wallet signature + signed Solana transaction
   v
Marketplace Backend
   |-- JobService / PaymentOrchestrator
   |      |-- BofaSimulationAdapter --> payment-backend --> MySQL demo ledger
   |      `-- SolanaPaymentAdapter --> Solana RPC --> Anchor Program --> SPL Token
   |
   |-- Solana Confirmation Verifier / Indexer / Reconciler
   |-- Rate Collector --> Rate Publisher signer --> publish_rate
   |-- Settlement Worker --> licensed off-ramp/bank --> Oracle signer --> record_offramp
   `-- MisaBackendClient --> misa-backend --> MISA provider

Marketplace MySQL
   +-- users/jobs
   +-- user_wallets/payment_attempts
   +-- rate_snapshots/withdrawals/events/sync_cursor
```

### 8.3 Flow end-to-end đề xuất cho pay-at-approve MVP

1. **Frontend:** User đăng nhập FreelaX và connect wallet.
2. **Backend:** tạo nonce; frontend ký message; backend verify và bind Pubkey với User.
3. **Backend:** Client tạo Job và chọn `SOLANA_USDC`; DTO không được bắt buộc payer-bank fields của BofA.
4. **Frontend Freelancer:** tìm Job `OPEN`, ứng tuyển; Client accept để Job sang `IN_PROGRESS`.
5. **Freelancer:** thực hiện công việc. UI phải nói rõ chưa có USDC được reserve và payment phụ thuộc chữ ký Client lúc approve.
6. **Freelancer yêu cầu thanh toán:** backend authorize đúng Job/Freelancer, chọn Config mint và RateSnapshot còn hiệu lực, cấp `invoice_id`, tính amount và đặt `expires_at <= rate_snapshot.expires_at`.
7. **Frontend Freelancer:** ký `create_invoice`; backend verify finalized Invoice PDA và lưu payment attempt `INVOICE_CREATED`. Invoice khóa mint/rate cho tới expiry; chưa có token chuyển.
8. **Client approve:** backend dựng `pay_invoice`; Client kiểm tra amount/mint/rate/expiry/network rồi ký và gửi.
9. **Anchor/SPL Token:** nếu Invoice còn hạn, chuyển đúng USDC Client ATA → Freelancer ATA và đổi Invoice thành `Paid` atomically.
10. **Backend:** verify finalized signature/instruction/PDA/event/account, ghi nhận thu nhập tại `InvoicePaid`, rồi compare-and-set Job `IN_PROGRESS -> COMPLETED`. Nếu Client không ký, thiếu USDC hoặc Invoice hết hạn, Job không được đánh dấu completed/payment-success; invoice mới phải dùng ID/rate/mint quote mới.
11. **MISA handoff:** gửi signature thật và locked rate/amount bằng reference idempotent; lưu chứng từ và retry nếu lỗi.
12. **Freelancer:** ký `request_offramp`; token chuyển vào Treasury ATA, Withdrawal `Pending`.
13. **Settlement Worker:** gọi đối tác off-ramp/bank bằng idempotency key Withdrawal PDA.
14. **Thành công:** Oracle xác nhận `Completed`. **Lỗi/timeout không chắc chắn:** Oracle ghi `FAILED_PENDING_REVIEW`; operator đối soát/retry thủ công rồi resolve, không có auto-refund USDC.

Refund/dispute sau `InvoicePaid` nằm ngoài program và được xử lý bằng thỏa thuận P2P/off-chain. Backend có thể lưu case/audit note nhưng không được tạo kỳ vọng rằng smart contract có thể đảo ngược SPL transfer.

---

## PHASE 9 — File-level implementation plan

Không có code ứng dụng nào được sửa trong lần đánh giá này. Bảng dưới là kế hoạch theo thứ tự triển khai.

| Thứ tự | Repo/file | Hành động | Kết quả mong đợi |
|---:|---|---|---|
| 1 | `marketplace-backend/.../repository/JobRepository.java` | Sửa/xóa hai derived query dùng `freelancerUserId` | Marketplace có thể khởi tạo repository |
| 2 | `docker-compose.yml` và Dockerfiles | Sửa root Dockerfile/context, đổi `paypal-backend` stale thành `payment-backend`; thêm `MISA_BACKEND_BASE_URL=http://misa-backend:9192`, dependency/healthcheck | Stack khởi động đúng network |
| 3 | `marketplace-backend/.../client/MisaBackendClient.java` | Dùng `ResponseAPI<T>` thống nhất | Taxpayer/payout/certificate deserialize đúng |
| 4 | Product ADR mới trong `docs/` | Ghi nhận direct pay-at-approve, income tại `InvoicePaid`, dispute off-chain, quote lock/expiry và manual off-ramp review | Quyết định đã chốt trở thành contract chung cho program/backend/frontend |
| 5 | MISA controllers/security | Giới hạn payout/certificate mutation cho platform integration/admin; kiểm tra taxpayer path | Đóng lỗ hổng authorization |
| 6 | `PaymentBackendClient.java`, BofA simulation và Flutter checkout repository | Giữ rail demo nhưng ghi nhãn simulation; xóa client/config/DTO PayPal không dùng; thêm idempotency và contract tests | Contract demo rõ ràng, không bị hiểu là bank integration thật |
| 7 | DB migrations mới | Thêm Flyway/Liquibase, `user_wallets`, `payment_attempts`, rates, withdrawals, events, cursor | Durable integration state |
| 8 | `User.java`, `Job.java` + entity mới | Thêm wallet mapping và generic payment relation; migrate `checkoutOrderId` | Existing rail/Solana chạy song song |
| 9 | `configuration/SolanaProperties.java` + YAML/Compose | Typed config RPC/program/mint/commitment; signer references | Config theo môi trường, không hardcode key |
| 10 | `client/solana/SolanaGateway.java` | RPC send/read/confirm/get transaction/account | Low-level adapter có thể test |
| 11 | `service/payment/SolanaPaymentAdapter.java` | Derive PDA, build unsigned ix/tx, deserialize account/event | Anchor-specific boundary |
| 12 | `service/payment/PaymentOrchestrator.java` | Tách routing provider/`SOLANA_USDC` khỏi `JobServiceImpl` | Business layer không phụ thuộc provider |
| 13 | `controller/JobPaymentController.java` + DTOs | Thêm invoice/approve prepare-confirm-status; tách payer-bank fields khỏi `CreateJobRequest`; thêm idempotency key | Public contract direct pay-at-approve an toàn cho Solana |
| 14 | indexer/reconciliation package mới | Persist finalized events, backfill slot, dedupe | DB không stale khi callback mất |
| 15 | rate collector/publisher package mới | Thu thập hai rate, policy freshness, publish snapshot | Không còn `PLACEHOLDER_USDC_TO_VND_RATE` |
| 16 | offramp settlement package mới | Consume withdrawal, provider call, Oracle completion/failure, manual review queue và audited resolution | Off-ramp có failure path vận hành được |
| 17 | `MisaBackendClient.java`, `PayoutTransaction` contract | Gửi signature/rate verified, retry/idempotency | Tax handoff audit được |
| 18 | `paypal/pubspec.yaml` | Thêm wallet adapter/deep-link phù hợp platform | Wallet connect/sign |
| 19 | Flutter repositories/models | Đồng bộ PageResponse và Job APIs; thêm Solana payment repository | Không còn contract giả/cũ |
| 20 | `HireFreelancerScreen` hoặc screen payment mới | Chọn payment method, connect/sign, trạng thái finalized | UX hai rail rõ ràng |
| 21 | tests mới ở ba backend và Flutter | Unit, contract, integration, E2E local validator/devnet | Definition of Done có bằng chứng |
| 22 | Anchor program | Thêm Invoice rate/expiry lock; sửa mint rotation; thêm `FAILED_PENDING_REVIEW` và resolution events/instructions; tests/audit lại | Program khớp các quyết định MVP đã chốt |

---

## PHASE 10 — Completion checklist

### On-chain module

- [x] 11 instruction có trong IDL.
- [x] Invoice direct payment bằng Legacy SPL Token.
- [x] RateSnapshot fixed-point và freshness checks.
- [x] Request/record off-ramp state machine.
- [x] Authority, signer, ATA, mint, overflow, rollback tests.
- [x] 60 tests local validator passing.
- [ ] Deploy devnet và ghi lại artifact/IDL checksum.
- [x] `initialize_config` ràng buộc đúng ProgramData upgrade authority; safe initialization plan đã ghi trong báo cáo triển khai.
- [x] Invoice lưu RateSnapshot/`expires_at`; pay dùng locked mint an toàn qua Config rotation.
- [x] Withdrawal có `FAILED_PENDING_REVIEW`, failure evidence và Admin manual resolution audit transition.
- [x] Không triển khai escrow/refund/dispute on-chain theo phạm vi MVP đã chốt.

### FreelaX backend/database

- [x] Authenticated Job façade và Client ownership check.
- [~] BofA simulation adapter/service tồn tại và route đã khớp; không có external bank settlement, client/config PayPal cũ vẫn còn dưới dạng dead code.
- [~] MISA domain tồn tại nhưng provider là mock, client wrapper mismatch và authorization thiếu.
- [ ] Wallet identity/proof mapping.
- [ ] Generic payment model hỗ trợ rail hiện tại + Solana.
- [!] `CreateJobRequest` đang bắt buộc dữ liệu tài khoản BofA cho mọi Job; chưa thể chọn Solana sạch sẽ.
- [ ] Solana RPC/Anchor adapter.
- [ ] Transaction builder và on-chain verifier.
- [ ] Event indexer, backfill, dedupe, reconciliation.
- [ ] Rate collector/publisher.
- [ ] Off-ramp provider/settlement worker, failure review queue và operator resolution audit.
- [ ] Database migrations và unique constraints.
- [ ] Retry/idempotency/dead-letter flows.
- [!] `JobRepository` có query property sai.
- [!] Job application đã có nhưng chưa có API browse/search Job `OPEN` cho Freelancer.
- [!] Docker Compose hiện không build/routing MISA đúng; Dockerfiles còn đường dẫn `paypal-backend` đã bị xóa.

### Frontend

- [~] Có UI Job/PayPal demo và remote auth; UI chưa đổi theo BofA-labelled backend.
- [!] Job đang dùng `MockJobRepository` trong `main.dart`.
- [!] Remote Job và checkout contracts không khớp backend.
- [ ] Wallet connect và ownership proof.
- [ ] Solana transaction signing.
- [ ] ATA/on-ramp UX và network/mint guard.
- [ ] Finalized/status/retry UI.
- [ ] Hiển thị invoice expiry/re-quote và cảnh báo direct payment không thể hoàn tác on-chain.
- [ ] Off-ramp UI và history.

### Test/deployment

- [!] FreelaX không có Java/Dart test files trong commit đã kiểm tra.
- [ ] Backend unit/contract/integration tests.
- [ ] Flutter tests.
- [ ] Clean-environment Docker test.
- [ ] Local validator cross-repo E2E.
- [ ] Devnet E2E với explorer links.
- [ ] Secrets/KMS, monitoring, alerting và runbook authority rotation.

---

## A. Integration verdict

**Có thể tích hợp**, theo mô hình Solana USDC là payment method thứ hai chạy song song với BofA simulation rail hiện tại.

**Chưa thể xem là tích hợp hoàn chỉnh.** Các blocker trước khi có E2E là:

1. Marketplace có repository query sai property.
2. Marketplace ↔ MISA response contract không khớp.
3. `CreateJobRequest` bắt buộc payer-bank fields, chưa có generic payment-method contract cho Solana.
4. Docker Compose trỏ sai Dockerfile, Dockerfiles còn path cũ và thiếu MISA service URL.
5. Không có wallet binding, Solana adapter, verifier/indexer, schema hay frontend signing.

Direct transfer core trong `pay_invoice` được giữ lại, nhưng phải hoàn thiện quote lock/expiry và rotation-safe mint validation trước khi gọi vertical slice là đúng contract. Escrow/refund/dispute không thuộc phạm vi MVP; backend và UI chỉ hỗ trợ case management off-chain.

## B. Exact integration points

| Điểm tích hợp | File/class/API chính xác |
|---|---|
| Public Job façade | `marketplace-backend/.../controller/JobController.java` |
| Business orchestration cần tách | `marketplace-backend/.../service/impl/JobServiceImpl.java` |
| Payment abstraction cần thay | `marketplace-backend/.../entity/Job.java`, trường `checkoutOrderId` |
| Provider-specific create contract | `marketplace-backend/.../dto/request/job/CreateJobRequest.java` |
| User ↔ wallet mapping | `marketplace-backend/.../entity/User.java` + entity `UserWallet` mới |
| BofA simulation path | `PaymentBackendClient.java`, `BofaCheckoutOrderController.java`, `BofaCheckoutOrderServiceImpl.java`, `BofaAccountBalanceServiceImpl.java` |
| Job application/discovery | `JobController.java`, `JobApplication.java`, `JobApplicationRepository.java` + browse endpoint mới |
| MISA handoff cần sửa | `MisaBackendClient.java`, `PayoutTransactionController.java`, `PayoutTransactionServiceImpl.java` |
| Flutter repository wiring | `paypal/lib/main.dart`, `RemoteJobRepository`, `JobService` |
| Flutter payment UI | `paypal/lib/features/marketplace/presentation/screens/hire_freelancer_screen.dart` |
| Anchor ABI | `target/idl/invoice_payments.json` |
| Invoice payment/quote lock | `state/invoice.rs`, `instructions/create_invoice.rs`, `pay_invoice.rs`, `events.rs` |
| Rate | `publish_rate.rs`, `state/rate_snapshot.rs` |
| Off-ramp failure review | `request_offramp.rs`, `record_offramp.rs`, `state/withdrawal_record.rs`, `events.rs`, `error.rs` |

## C. Missing pieces

Thiếu chính xác: verified wallet identity; UUID↔u64/PDA allocation; payment method/attempt model; RPC/IDL adapter; unsigned transaction APIs; on-chain finalized verification; event indexer/backfill/dedupe; DB migrations; rate collector/publisher; on-ramp; treasury settlement worker/review queue; provider reference; Oracle signing service; `InvoicePaid`-driven MISA handoff; retry/idempotency; Flutter wallet/signing/expiry/status/off-ramp UI; backend/Flutter/cross-system tests; devnet deployment/config/monitoring.

## D. Existing problems

Ba blocker on-chain Phase 10 (Invoice rate/expiry + rotation-safe pay, withdrawal
failure review, safe Config initialization) đã được xử lý. Các vấn đề cần ưu
tiên còn lại là versioned mint policy cho off-ramp mint cũ, query
`freelancerUserId` không tồn tại, MISA wrapper mismatch, payer-bank fields bị
gắn cứng vào tạo Job, application flow thiếu Job discovery, BofA chỉ debit
ledger mô phỏng mà không trả Freelancer, cross-service call thiếu idempotency,
Dockerfile path cũ/MISA URL trong Compose, Flutter contract cũ, MISA
authorization thiếu, tỷ giá placeholder, transaction hash giả, hardcoded demo
credentials, `ddl-auto:update` và không retry tax/reconciliation.

## E. Definition of Done

Integration chỉ được xem là hoàn tất khi toàn bộ điều sau đúng:

- [x] Đã chốt direct pay-at-approve, income tại `InvoicePaid`, dispute off-chain, quote lock/expiry và manual off-ramp review.
- [ ] FreelaX build/test pass trong clean environment; Compose khởi động đủ ba backend và dependencies.
- [ ] User bind wallet bằng challenge signature; không thể bind wallet người khác.
- [ ] Job có generic payment attempt; regression tests của payment rail hiện tại vẫn pass.
- [ ] Backend derive đúng PDA, dựng transaction nhưng không giữ user private key.
- [ ] Frontend ký được `create_invoice`, `pay_invoice`, `request_offramp` trên devnet.
- [ ] Backend chỉ transition sau khi verify finalized transaction, đúng Program ID/PDA/wallet/locked mint/rate/expiry/amount/state/event.
- [ ] Indexer backfill được sau downtime, duplicate event không tạo duplicate payment/tax record.
- [ ] Rate collector publish snapshot có nguồn/audit/freshness; không còn rate placeholder trong production path.
- [ ] Settlement worker dùng Withdrawal PDA làm idempotency key; lỗi vào `FAILED_PENDING_REVIEW`, operator action được audit và không auto-refund.
- [ ] MISA nhận signature/rate/amount đã verify từ finalized `InvoicePaid`; wrapper và authorization đã sửa; lỗi được retry.
- [ ] Secrets không nằm trong source; Rate/Oracle keys ở signer service/KMS; authority rotation có runbook.
- [ ] Unit, contract, integration và E2E tests bao phủ happy path, duplicate, timeout, stale rate, wrong signer/mint/ATA và reconciliation.
- [ ] Devnet manifest ghi Program ID, IDL checksum/version, accepted mint, Config PDA, authority Pubkeys, treasury ATA và explorer transactions.
- [ ] Demo chứng minh từ Job → apply/assign → locked invoice → Client approve/sign → direct USDC transfer → finalized `InvoicePaid` → income/MISA record → off-ramp success hoặc manual-review failure, không dùng “success” do frontend tự khai.

Khi chưa đạt các checkbox trên, cách mô tả chính xác là **Anchor module đã sẵn sàng làm blockchain core, nhưng FreelaX integration chưa hoàn tất**.
