# Hướng dẫn tích hợp Solana API Gateway cho Backend

## Phạm vi và các địa chỉ cố định

Tài liệu này mô tả contract giữa Frontend, Backend Spring Boot, Solana RPC và
provider thanh toán cho Program `invoice_payments` hiện tại.

| Thành phần | Giá trị/vai trò |
|---|---|
| Invoice Program | `4Wd6umju26vej2ftzwR6J55pjkUqDQsxfVkt46UqDb1b` |
| Token Program | Legacy SPL Token: `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA` |
| Associated Token Program | `ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL` |
| Token thanh toán | `Config.acceptedMint`, bắt buộc 6 decimals |
| `systemFeePayer` | Ví SOL do Backend/KMS giữ; trả network fee và tài trợ rent theo cơ chế bên dưới |
| User signer | Client hoặc Freelancer; vẫn phải ký instruction yêu cầu quyền sở hữu token/nghiệp vụ |
| Operational signer | Admin, Rate Authority, Oracle Authority, Mock On-ramp Authority do KMS/HSM quản lý |

`systemFeePayer` chỉ tài trợ SOL. Fee payer không thay thế chữ ký bắt buộc của
Client, Freelancer, Admin, Rate Authority hoặc Oracle Authority.

**Ràng buộc code hiện tại:** `create_invoice` và `request_offramp` khai báo
`payer = freelancer`; vì vậy chỉ đặt Backend làm transaction fee payer chưa đủ
để Freelancer có 0 SOL. Có hai cách xử lý:

1. Giải pháp tương thích code hiện tại: Backend prepend System Program transfer
   để nạp đúng lượng lamports cần tạo PDA cho Freelancer trong cùng transaction.
   Transaction atomic nên khoản tài trợ rollback nếu instruction chính fail.
2. Giải pháp production khuyến nghị: sửa Program để nhận riêng
   `system_fee_payer: Signer` và dùng account này làm `payer` khi `init` Invoice/
   WithdrawalRecord. Cần đồng thời quyết định rõ ai nhận rent khi close.

Tài liệu này mô tả flow tương thích hiện tại bằng sponsored rent top-up. Không
được coi `feePayer` và Anchor `payer = ...` là cùng một vai trò.

Quy ước dữ liệu REST:

- Public key và transaction signature: chuỗi Base58.
- `u64`, token amount và rate: chuỗi số nguyên thập phân.
- Timestamp `i64`: Unix seconds dưới dạng chuỗi.
- Hash `[u8; 32]`: chuỗi hex 64 ký tự.
- 100 USDC với mint 6 decimals: `"100000000"` base units.

---

## 1. Danh mục API Gateway của hệ thống

### 1.1 API cho Frontend/Client

| Method | Path | Mục đích nghiệp vụ | Solana/Provider tương ứng |
|---|---|---|---|
| `GET` | `/api/v1/config` | Đọc mint và trạng thái hệ thống | RPC `getAccountInfo(Config PDA)` |
| `GET` | `/api/v1/rates/{rateId}` | Đọc snapshot tỷ giá | RPC `getAccountInfo(RateSnapshot PDA)` |
| `GET` | `/api/v1/wallets/{ownerPublicKey}/balances/{mintPublicKey}` | Đọc balance ATA | RPC `getTokenAccountBalance` |
| `POST` | `/api/v1/invoices` | Backend cấp ID, lưu metadata và build giao dịch tạo Invoice | `create_invoice` |
| `GET` | `/api/v1/invoices/{freelancerPublicKey}/{invoiceId}` | Đọc Invoice theo PDA | RPC `getAccountInfo(Invoice PDA)` |
| `POST` | `/api/v1/invoices/{freelancerPublicKey}/{invoiceId}/pay/build` | Build giao dịch để Client ký thanh toán | `pay_invoice`; SPL `transfer_checked` trong Program |
| `POST` | `/api/v1/invoices/{freelancerPublicKey}/{invoiceId}/cancel/build` | Build giao dịch để Freelancer ký hủy | `cancel_invoice` |
| `POST` | `/api/v1/invoices/{freelancerPublicKey}/{invoiceId}/close/build` | Build giao dịch đóng Invoice và hoàn rent | `close_invoice` |
| `POST` | `/api/v1/onramp/orders` | Tạo đơn mua USDC production | API on-ramp provider; không gọi Solana Program để đổi USD |
| `GET` | `/api/v1/onramp/orders/{orderId}` | Đọc quote/payment/on-chain status | Database và provider status; RPC khi đã có signature |
| `POST` | `/api/v1/demo/onramp/purchases` | Demo cấp Mock USDC từ PDA Treasury | `mock_onramp`; Backend Mock On-ramp Authority ký |
| `POST` | `/api/v1/withdrawals` | Tạo payout record và build giao dịch off-ramp | `request_offramp` |
| `GET` | `/api/v1/withdrawals/{freelancerPublicKey}/{withdrawalId}` | Đọc WithdrawalRecord | RPC `getAccountInfo(WithdrawalRecord PDA)` |
| `POST` | `/api/v1/transactions/submit` | Gửi transaction đã có đủ chữ ký | RPC `sendRawTransaction` |
| `GET` | `/api/v1/transactions/{signature}` | Đọc trạng thái transaction | RPC `getSignatureStatuses`, `getTransaction` |

### 1.2 API nội bộ/Provider webhook

Các endpoint này không public cho Frontend.

| Method | Path | Mục đích nghiệp vụ | Solana/Provider tương ứng |
|---|---|---|---|
| `POST` | `/api/v1/internal/rates` | Rate service công bố tỷ giá | `publish_rate`; Rate Authority ký |
| `POST` | `/api/v1/internal/onramp/webhooks/{provider}` | Nhận kết quả thanh toán USD và transfer USDC | Verify webhook; RPC kiểm tra provider transaction |
| `POST` | `/api/v1/internal/offramp/webhooks/{provider}` | Nhận kết quả payout VND | Verify webhook; chọn complete/fail/review |
| `POST` | `/api/v1/internal/withdrawals/{freelancerPublicKey}/{withdrawalId}/complete` | Xác nhận payout đã hoàn tất | `record_offramp`; Oracle Authority ký |
| `POST` | `/api/v1/internal/withdrawals/{freelancerPublicKey}/{withdrawalId}/fail` | Đưa lỗi không chắc chắn vào review | `mark_offramp_failed`; Oracle Authority ký |
| `POST` | `/api/v1/internal/withdrawals/{freelancerPublicKey}/{withdrawalId}/resolve` | Admin resolve sau đối soát thủ công | `resolve_offramp`; Admin ký |
| `POST` | `/api/v1/internal/config/initialize` | Khởi tạo Config lần đầu | `initialize_config`; Program upgrade authority ký |
| `PUT` | `/api/v1/internal/config` | Cập nhật mint/authorities/pause | `update_config`; Admin ký |
| `PUT` | `/api/v1/internal/config/mock-onramp` | Bật/tắt và đặt hạn mức demo | `configure_mock_onramp`; Admin ký |

---

## 2. Checklist Backend nối với Solana từ A đến Z

### 2.1 Cấu hình bắt buộc

- [ ] Cấu hình `rpcEndpoint`, cluster và Program ID theo từng environment.
- [ ] Load đúng IDL của Program đang deploy; kiểm tra IDL checksum khi release.
- [ ] Cấu hình `commitment = confirmed` cho build, simulate, send và confirm.
- [ ] Cấu hình `systemFeePayerPublicKey` và signer trong KMS/HSM; không lưu raw
  private key trong source code, database hoặc log.
- [ ] Duy trì đủ SOL cho fee payer và cảnh báo khi xuống dưới ngưỡng vận hành.
- [ ] Cấu hình riêng Admin, Rate Authority, Oracle Authority và Mock On-ramp
  Authority; không dùng chung một key cho mọi vai trò.
- [ ] Đọc Config PDA trước khi build để lấy `acceptedMint`, authorities,
  `paused`, hạn mức và trạng thái mock on-ramp.

### 2.2 Derive account và chuẩn hóa số

- [ ] Config PDA: `["config"]`.
- [ ] Invoice PDA: `["invoice", freelancerPublicKey, invoiceId_u64_le]`.
- [ ] RateSnapshot PDA: `["rate", rateId_u64_le]`.
- [ ] WithdrawalRecord PDA:
  `["withdrawal", freelancerPublicKey, withdrawalId_u64_le]`.
- [ ] MockOnrampReceipt PDA:
  `["mock_onramp", clientPublicKey, purchaseId_u64_le]`.
- [ ] Mock treasury authority PDA: `["mock_onramp_treasury"]`.
- [ ] ATA được derive từ `owner + Token Program + mint` dưới Associated Token
  Program.
- [ ] Parse `u64` bằng `BigInteger`; kiểm tra range và encode đúng 8-byte
  little-endian. Không dùng `double`/`float` cho tiền hoặc tỷ giá.

### 2.3 Build transaction với Backend làm Fee Payer

1. Backend validate payload, quyền signer và trạng thái account on-chain.
2. Backend gọi `getLatestBlockhash("confirmed")` để lấy:
   `blockhash` và `lastValidBlockHeight`.
3. Backend build instruction đúng accounts/args theo IDL.
4. Đặt `feePayer = systemFeePayerPublicKey`.
5. Với instruction đang dùng `payer = freelancer`, tính rent cần thiết và
   prepend System Program transfer từ `systemFeePayer` sang Freelancer nếu ví
   không đủ lamports. Chỉ tài trợ transaction do Backend build cố định.
6. Nếu ATA đích cần được tạo và instruction cho phép, prepend Associated Token
   Account instruction; `systemFeePayer` trả rent.
7. Fee payer ký message bằng KMS/HSM.
8. Nếu còn chữ ký Client/Freelancer, trả transaction đã partial-sign dưới dạng
   Base64. Frontend chỉ được thêm chữ ký, không được sửa message.
9. Lưu `buildSessionId`, message hash, instruction, required signers,
   blockhash, `lastValidBlockHeight`, idempotency key và thời điểm hết hạn.

Không nhận một transaction tùy ý từ Frontend rồi ký tài trợ. Backend chỉ tài
trợ transaction do chính Backend build và có message hash khớp build session.

Payer tạo account theo code hiện tại:

| Instruction | Anchor `payer` hiện tại | Backend cần làm |
|---|---|---|
| `initialize_config` | Admin | Admin là operational key; duy trì SOL hoặc sponsor top-up |
| `publish_rate` | Rate Authority | Duy trì SOL hoặc sponsor top-up từ system fee payer |
| `mock_onramp` | Mock On-ramp Authority | Trả rent Client ATA và Receipt; duy trì SOL hoặc sponsor top-up |
| `create_invoice` | Freelancer | Prepend sponsored rent top-up nếu muốn Freelancer có 0 SOL |
| `request_offramp` | Freelancer | Prepend sponsored rent top-up nếu muốn Freelancer có 0 SOL |

Khi `close_invoice`, rent trong Invoice PDA được trả cho Freelancer theo code
hiện tại. Nếu Backend đã tài trợ rent lúc tạo, khoản tài trợ đó trở thành số dư
SOL của Freelancer khi close; cần được tính vào chính sách chi phí hoặc thay đổi
Program trước production. WithdrawalRecord và MockOnrampReceipt hiện chưa có
close instruction.

### 2.4 Submit và xác nhận

1. `/transactions/submit` deserialize transaction Base64.
2. Kiểm tra message hash khớp build session và fee payer đúng ví hệ thống.
3. Kiểm tra đủ chữ ký bắt buộc và blockhash chưa hết hạn.
4. Gọi `simulateTransaction` hoặc preflight; không mặc định bỏ qua preflight.
5. Gọi `sendRawTransaction` với bytes transaction đã ký.
6. Lưu signature ngay với trạng thái `SUBMITTED`.
7. Gọi `confirmTransaction({signature, blockhash, lastValidBlockHeight},
   "confirmed")`.
8. Gọi `getTransaction` để lưu slot, error, log và event liên quan.
9. Khi cần tính bất biến cho báo cáo/settlement, theo dõi tiếp tới `finalized`.
10. Retry theo idempotency key; không build một Invoice/Withdrawal/Purchase ID
    mới cho cùng yêu cầu nghiệp vụ.

### 2.5 Trạng thái tối thiểu cần lưu trong database

| Nhóm | Trường tối thiểu |
|---|---|
| Transaction build | `buildSessionId`, message hash, blockhash, last valid block height, fee payer, required signers, expiresAt |
| Solana transaction | signature, instruction, cluster, submittedAt, confirmation status, slot, error/log |
| Invoice | internal ID, on-chain `invoiceId`, Invoice PDA, Freelancer, Client, amount, mint, rate PDA, status |
| On-ramp order | order ID, provider order ID, Client, Client ATA, quote, fee, fiat amount/currency, token amount/mint, provider transaction signature, status |
| Off-ramp order | withdrawal ID/PDA, Freelancer, bank beneficiary encrypted reference, token amount, fiat amount, provider payout ID, webhook status, Solana settlement signature |

---

## 3. Quy trình nghiệp vụ chi tiết từ A đến Z

## A. Tạo hóa đơn — `create_invoice`

### A.1 Vai trò dữ liệu

| Trường | Đại diện cho ai/cái gì |
|---|---|
| `freelancerPublicKey` | Ví Freelancer tạo hóa đơn và nhận USDC |
| `clientPublicKey` | Ví Client được chỉ định phải thanh toán |
| `systemFeePayerPublicKey` | Ví SOL do Backend/KMS giữ; trả network fee và tài trợ rent top-up cho Invoice PDA |
| `invoiceId` | ID `u64` Backend cấp, duy nhất trong phạm vi một Freelancer |
| `amountBaseUnits` | Số USDC cần trả theo base units; 100 USDC = `100000000` |
| `rateId` | RateSnapshot Backend chọn và khóa vào Invoice |
| `expiresAt` | Unix seconds; không được vượt `RateSnapshot.expiresAt` |
| `freelancerUsdcAta` | ATA nhận tiền; derive từ Freelancer và mint đã khóa |
| `qrPayload` | Deep link của ứng dụng chứa khóa tra cứu Invoice; không phải account nhận token trực tiếp |

### A.2 Luồng xử lý

1. Freelancer nhập Client wallet, số USDC, mô tả và hạn thanh toán trên
   Frontend.
2. Frontend gọi `POST /api/v1/invoices` với
   `freelancerPublicKey` là ví đang kết nối.
3. Backend xác thực session phải sở hữu `freelancerPublicKey` bằng wallet
   challenge/signature ngoài transaction.
4. Backend đọc Config và RateSnapshot; từ chối nếu paused, snapshot hết hạn
   hoặc mint không hợp lệ.
5. Backend sinh `invoiceId` `u64`, lưu metadata ngoài chain và derive:
   Invoice PDA, RateSnapshot PDA, Freelancer USDC ATA.
6. Nếu Freelancer ATA chưa tồn tại, Backend có thể tạo ATA trong transaction
   tài trợ riêng hoặc prepend ATA instruction; `systemFeePayer` trả rent.
7. Do Program hiện dùng `payer = freelancer`, Backend tính rent-exempt balance
   của Invoice và prepend SOL top-up từ `systemFeePayer` nếu Freelancer thiếu
   lamports. Không cấp SOL bằng một transaction rời.
8. Backend build `create_invoice(invoiceId, client, amount, expiresAt)`:
   - `freelancer`: writable signer.
   - `config`: Config PDA.
   - `rate_snapshot`: RateSnapshot PDA.
   - `invoice`: Invoice PDA mới.
   - `system_program`: System Program.
9. Backend đặt `systemFeePayerPublicKey` làm fee payer và ký phần fee payer.
10. Backend trả transaction Base64 để ví Freelancer ký.
11. Frontend gửi transaction đã ký tới `/api/v1/transactions/submit`.
12. Backend send/confirm, đọc event `InvoiceCreated`, cập nhật database và trả
    Invoice/QR cho Frontend.

`create_invoice` chưa chuyển token. Địa chỉ nhận token thực tế khi thanh toán là
`freelancerUsdcAta`, không phải Invoice PDA.

### A.3 Request tạo Invoice

```json
{
  "freelancerPublicKey": "FreelancerWalletBase58",
  "clientPublicKey": "ClientWalletBase58",
  "amountBaseUnits": "100000000",
  "rateId": "9000001",
  "requestedExpiresAt": "1790000000",
  "description": "Thanh toan milestone 1",
  "idempotencyKey": "invoice-order-8dc4f0"
}
```

| Trường | Bắt buộc | Ý nghĩa |
|---|---:|---|
| `freelancerPublicKey` | Có | Ví Freelancer sẽ ký `create_invoice` và nhận USDC |
| `clientPublicKey` | Có | Ví Client duy nhất được phép gọi `pay_invoice` |
| `amountBaseUnits` | Có | Số token theo 6 decimals; không phải USD/VND dạng số thực |
| `rateId` | Có | Snapshot tỷ giá dùng để ghi nhận giá trị Invoice |
| `requestedExpiresAt` | Có | Hạn Client được phép thanh toán |
| `description` | Không | Metadata chỉ lưu database, không nằm trong Invoice account |
| `idempotencyKey` | Có | Chống tạo nhiều `invoiceId` khi Frontend retry |

### A.4 Response cần Freelancer ký

```json
{
  "status": "REQUIRES_FREELANCER_SIGNATURE",
  "buildSessionId": "txbuild_01JABC",
  "invoiceId": "42",
  "invoicePda": "InvoicePdaBase58",
  "freelancerUsdcAta": "FreelancerAtaBase58",
  "acceptedMint": "MockOrUsdcMintBase58",
  "systemFeePayerPublicKey": "BackendFeePayerBase58",
  "transactionBase64": "BASE64_PARTIALLY_SIGNED_TRANSACTION",
  "requiredSignerPublicKeys": ["FreelancerWalletBase58"],
  "lastValidBlockHeight": 123456,
  "qrPayload": "freelax://invoice?freelancer=FreelancerWalletBase58&invoiceId=42"
}
```

| Trường | Ý nghĩa |
|---|---|
| `buildSessionId` | ID server-side dùng để xác minh transaction khi submit |
| `invoicePda` | Account Invoice sẽ được tạo, không giữ token |
| `freelancerUsdcAta` | Account nhận USDC khi Client thanh toán |
| `acceptedMint` | Mint bị khóa vào Invoice tại thời điểm tạo |
| `systemFeePayerPublicKey` | Ví Backend đã ký, trả network fee và tài trợ rent top-up nếu cần |
| `requiredSignerPublicKeys` | Ví Freelancer còn phải ký |
| `transactionBase64` | Transaction do Backend build; Frontend không được sửa message |
| `qrPayload` | Dữ liệu QR/deep link để Client mở màn hình thanh toán |

### A.5 Response sau khi submit thành công

```json
{
  "status": "CONFIRMED",
  "signature": "CreateInvoiceTransactionSignatureBase58",
  "invoiceId": "42",
  "invoicePda": "InvoicePdaBase58",
  "onChainStatus": "Pending",
  "slot": "301234567"
}
```

---

## B. Thanh toán hóa đơn — `pay_invoice`

### B.1 Phạm vi thanh toán

Program hiện tại chỉ thanh toán bằng SPL token tại `Invoice.mint`. Program
không hỗ trợ trả Invoice bằng native SOL.

- USDC/Mock USDC được trừ từ `clientUsdcAta`.
- USDC/Mock USDC được cộng vào `freelancerUsdcAta`.
- SOL chỉ bị trừ từ `systemFeePayerPublicKey` để trả network fee/rent ATA nếu
  Backend cần tạo ATA đích.

### B.2 Vai trò dữ liệu

| Trường | Đại diện cho ai/cái gì |
|---|---|
| `clientPublicKey` | Ví Client đã lưu trong Invoice; phải ký để cho phép trừ token |
| `freelancerPublicKey` | Ví Freelancer đã lưu trong Invoice; chủ ATA nhận token |
| `systemFeePayerPublicKey` | Ví Backend trả SOL network fee |
| `clientUsdcAta` | ATA nguồn thuộc Client và đúng `Invoice.mint` |
| `freelancerUsdcAta` | ATA đích thuộc Freelancer và đúng `Invoice.mint` |
| `invoicePda` | Invoice đang `Pending`; chứa amount, Client, Freelancer và locked mint |

### B.3 Luồng xử lý

1. Client mở QR/deep link và Frontend đọc Invoice qua Backend.
2. Frontend hiển thị đúng amount, mint, Freelancer và expiry từ on-chain state.
3. Client bấm Thanh toán; Frontend gọi endpoint `/pay/build`.
4. Backend đọc Invoice PDA và kiểm tra:
   `Pending`, chưa hết hạn, `clientPublicKey` khớp Invoice và Config không
   paused.
5. Backend lấy `acceptedMint` từ `Invoice.mint`, không lấy mint hiện tại từ
   Config; quy tắc này giữ Invoice an toàn khi Config rotate mint.
6. Backend derive Client ATA và Freelancer ATA. Nếu Freelancer ATA chưa tồn
   tại, prepend instruction tạo ATA với Backend làm payer.
7. Backend build `pay_invoice` với accounts:
   Client signer, Config PDA, Invoice PDA, Freelancer, locked mint, Client ATA,
   Freelancer ATA và Token Program.
8. Backend đặt fee payer là `systemFeePayerPublicKey`, ký phần fee payer và trả
   transaction Base64.
9. Ví Client ký transaction. Chữ ký Client là quyền trừ token; chữ ký fee payer
   không thay thế chữ ký này.
10. Frontend gửi transaction đã ký tới `/transactions/submit`.
11. Backend gọi `sendRawTransaction`, confirm ở `confirmed`, đọc event
    `InvoicePaid` và cập nhật database.
12. Nếu transaction hết blockhash, Backend build lại. Không gửi lại transaction
    cũ với blockhash đã hết hạn.

### B.4 Request build thanh toán

```json
{
  "clientPublicKey": "ClientWalletBase58",
  "idempotencyKey": "pay-invoice-42-client-01"
}
```

Path:

```text
POST /api/v1/invoices/FreelancerWalletBase58/42/pay/build
```

| Trường | Ý nghĩa |
|---|---|
| Path `freelancerPublicKey` | Freelancer sở hữu namespace của Invoice PDA và nhận token |
| Path `invoiceId` | ID on-chain của Invoice trong namespace Freelancer |
| `clientPublicKey` | Ví Client sẽ ký và bị trừ USDC |
| `idempotencyKey` | Chống tạo nhiều build session cho một lần bấm thanh toán |

### B.5 Response build thanh toán

```json
{
  "status": "REQUIRES_CLIENT_SIGNATURE",
  "buildSessionId": "txbuild_01JPAY",
  "invoicePda": "InvoicePdaBase58",
  "amountBaseUnits": "100000000",
  "mint": "LockedInvoiceMintBase58",
  "clientUsdcAta": "ClientAtaBase58",
  "freelancerUsdcAta": "FreelancerAtaBase58",
  "systemFeePayerPublicKey": "BackendFeePayerBase58",
  "transactionBase64": "BASE64_PARTIALLY_SIGNED_TRANSACTION",
  "requiredSignerPublicKeys": ["ClientWalletBase58"],
  "lastValidBlockHeight": 123500
}
```

### B.6 Submit transaction đã ký

Request:

```json
{
  "buildSessionId": "txbuild_01JPAY",
  "transactionBase64": "BASE64_FULLY_SIGNED_TRANSACTION"
}
```

Response:

```json
{
  "status": "CONFIRMED",
  "signature": "PayInvoiceTransactionSignatureBase58",
  "slot": "301234890",
  "instruction": "pay_invoice",
  "invoicePda": "InvoicePdaBase58",
  "onChainStatus": "Paid"
}
```

### B.7 Các lỗi Backend phải trả rõ ràng

| HTTP | Mã ứng dụng | Trường hợp |
|---:|---|---|
| `400` | `INVALID_AMOUNT_OR_ADDRESS` | Public key/payload sai định dạng |
| `403` | `CLIENT_NOT_ASSIGNED` | Client request không khớp `Invoice.client` |
| `409` | `INVOICE_NOT_PENDING` | Invoice đã Paid/Cancelled |
| `410` | `INVOICE_EXPIRED` | `expiresAt` đã qua |
| `422` | `INSUFFICIENT_USDC` | Client ATA không đủ token |
| `422` | `ATA_NOT_FOUND` | ATA bắt buộc không tồn tại và Backend không tạo được |
| `503` | `SOLANA_RPC_UNAVAILABLE` | RPC timeout/unavailable; không tự kết luận transaction thất bại nếu đã có signature |

---

## C. Rút tiền/đổi tiền — `request_offramp`

### C.1 Luồng tiền chính xác

Luồng tổng thể của hệ thống có thể bắt đầu từ USD, nhưng `request_offramp` chỉ
xử lý đoạn USDC → VND:

```text
USD của Client
  → On-ramp provider nhận USD ngoài blockchain
  → Provider chuyển USDC vào Client ATA trên Solana
  → Client trả Invoice bằng USDC
  → Freelancer nhận USDC trong Freelancer ATA
  → Freelancer ký request_offramp
  → Program chuyển USDC vào Treasury ATA
  → Backend gọi payout provider
  → Provider chuyển VND vào tài khoản ngân hàng Freelancer
  → Oracle cập nhật WithdrawalRecord
```

Solana không nhận USD/VND và không tự chuyển đổi fiat. Trong Program hiện tại,
USDC được **thu vào Treasury ATA**, không burn. Nếu production provider yêu cầu
burn hoặc chuyển tới ví provider khác, cần thiết kế/migrate instruction riêng;
không mô tả `request_offramp` hiện tại là burn.

### C.2 Vai trò của Provider

| Giai đoạn | Provider | Backend | Solana |
|---|---|---|---|
| On-ramp | Quote USD, nhận USD, chuyển USDC | Tạo order, verify webhook và transaction | Ghi nhận SPL transfer vào Client ATA |
| Off-ramp | Nhận payout request, chuyển VND | Theo dõi Withdrawal Pending, gửi payout API, verify webhook | Thu USDC vào Treasury và lưu WithdrawalRecord |
| Settlement | Trả provider payout status/reference | Oracle/Admin ký transition phù hợp | `Pending → Completed` hoặc `FailedPendingReview → Completed` |

Thông tin tài khoản ngân hàng không ghi lên Solana. Backend lưu beneficiary đã
mã hóa và chỉ gửi dữ liệu cần thiết cho payout provider.

### C.3 State machine off-ramp

```text
request_offramp
  → Pending
      ├─ provider xác nhận payout thành công
      │    → record_offramp
      │    → Completed
      └─ timeout/kết quả không chắc chắn
           → mark_offramp_failed
           → FailedPendingReview
                └─ Admin đối soát thấy payout thực sự thành công
                     → resolve_offramp
                     → Completed
```

Program hiện tại không tự refund USDC khi payout lỗi. Không gọi
`resolve_offramp` nếu kết quả đối soát cho thấy VND chưa được trả.

### C.4 Luồng chi tiết `request_offramp`

1. Freelancer chọn amount USDC và tài khoản ngân hàng đã xác minh.
2. Frontend gọi `POST /api/v1/withdrawals`.
3. Backend xác thực wallet session, beneficiary ownership/KYC và idempotency.
4. Backend cấp `withdrawalId` `u64`, chọn RateSnapshot còn fresh và lưu bank
   beneficiary reference trong database.
5. Backend đọc Config để lấy mint và `treasuryAuthority`; derive:
   Freelancer ATA, Treasury ATA và WithdrawalRecord PDA.
6. Do Program hiện dùng `payer = freelancer`, Backend tính rent-exempt balance
   của WithdrawalRecord và prepend SOL top-up nếu Freelancer không đủ lamports.
7. Backend build `request_offramp(withdrawalId, tokenAmount)`:
   - `freelancer`: writable signer/payer nghiệp vụ.
   - `config`: Config PDA.
   - `rate_snapshot`: RateSnapshot PDA.
   - `accepted_mint`: Config mint.
   - `freelancer_ata`: nguồn token.
   - `treasury_authority` và `treasury_ata`: đích token.
   - `withdrawal_record`: PDA mới.
   - Token Program và System Program.
8. Backend đặt `systemFeePayerPublicKey` làm transaction fee payer, ký phần fee
   payer rồi trả transaction cho Freelancer ký.
9. Frontend submit transaction đã ký. Backend confirm và deserialize
   WithdrawalRecord `Pending`.
10. Chỉ sau khi transaction đã confirmed và token thực sự ở Treasury, Backend
   gọi payout provider với `fiatAmountVnd` do Program tính.
11. Provider trả `providerPayoutId`, trạng thái ban đầu và sau đó gửi webhook.
12. Backend verify webhook signature, provider payout ID, amount, beneficiary
    và chống replay.
13. Nếu payout thành công, Oracle Authority gọi `record_offramp`.
14. Nếu timeout/kết quả không chắc chắn, Oracle gọi `mark_offramp_failed` với
    `failureHash`; Admin đối soát và chỉ resolve khi có bằng chứng phù hợp.

### C.5 Request tạo withdrawal và build transaction

```json
{
  "freelancerPublicKey": "FreelancerWalletBase58",
  "tokenAmountBaseUnits": "250000000",
  "rateId": "9000010",
  "bankBeneficiaryId": "beneficiary_db_8fa2",
  "idempotencyKey": "withdrawal-order-a91c"
}
```

| Trường | Ý nghĩa |
|---|---|
| `freelancerPublicKey` | Ví Freelancer sở hữu USDC và phải ký token transfer |
| `tokenAmountBaseUnits` | Số USDC thu vào Treasury, ví dụ 250 USDC = `250000000` |
| `rateId` | Snapshot dùng để Program tính `fiatAmountVnd` |
| `bankBeneficiaryId` | ID nội bộ trỏ tới thông tin ngân hàng đã mã hóa; không đưa lên chain |
| `idempotencyKey` | Chống tạo trùng withdrawal/provider payout |

Response:

```json
{
  "status": "REQUIRES_FREELANCER_SIGNATURE",
  "buildSessionId": "txbuild_01JWITHDRAW",
  "withdrawalId": "77",
  "withdrawalRecordPda": "WithdrawalRecordPdaBase58",
  "freelancerUsdcAta": "FreelancerAtaBase58",
  "treasuryUsdcAta": "TreasuryAtaBase58",
  "tokenAmountBaseUnits": "250000000",
  "estimatedFiatAmountVnd": "6248750",
  "systemFeePayerPublicKey": "BackendFeePayerBase58",
  "transactionBase64": "BASE64_PARTIALLY_SIGNED_TRANSACTION",
  "requiredSignerPublicKeys": ["FreelancerWalletBase58"],
  "lastValidBlockHeight": 123900
}
```

`estimatedFiatAmountVnd` để hiển thị trước khi ký. Giá trị settlement chính
thức phải đọc lại từ `WithdrawalRecord.fiatAmountVnd` sau khi transaction được
confirmed.

### C.6 Backend gọi payout provider

Đây là schema chuẩn hóa nội bộ; adapter của từng provider chuyển sang format
thực tế của provider đó.

```json
{
  "merchantReference": "withdrawal-77",
  "amount": "6248750",
  "currency": "VND",
  "beneficiary": {
    "bankCode": "VCB",
    "accountNumber": "ENCRYPTED_OR_TOKENIZED_ACCOUNT_REFERENCE",
    "accountName": "NGUYEN VAN A"
  },
  "callbackUrl": "https://backend.example.com/api/v1/internal/offramp/webhooks/provider-a",
  "metadata": {
    "withdrawalId": "77",
    "withdrawalRecordPda": "WithdrawalRecordPdaBase58",
    "solanaRequestSignature": "RequestOfframpSignatureBase58"
  }
}
```

| Trường | Đại diện cho ai/cái gì |
|---|---|
| `merchantReference` | ID idempotent do Backend cấp cho payout provider |
| `amount` | Số VND từ WithdrawalRecord, không do Frontend tự khai |
| `beneficiary` | Tài khoản ngân hàng của Freelancer đã qua xác minh |
| `callbackUrl` | Endpoint Backend nhận webhook; không phải Solana address |
| `withdrawalRecordPda` | Account audit on-chain liên kết payout |
| `solanaRequestSignature` | Bằng chứng transaction đã thu USDC vào Treasury |

Provider response chuẩn hóa:

```json
{
  "providerPayoutId": "provider-payout-99881",
  "merchantReference": "withdrawal-77",
  "status": "PROCESSING",
  "acceptedAmount": "6248750",
  "currency": "VND",
  "createdAt": "2026-09-27T12:00:00Z"
}
```

### C.7 Provider webhook và cập nhật on-chain

Webhook thành công sau khi verify chữ ký provider:

```json
{
  "eventId": "provider-event-551",
  "providerPayoutId": "provider-payout-99881",
  "merchantReference": "withdrawal-77",
  "status": "COMPLETED",
  "amount": "6248750",
  "currency": "VND",
  "bankReference": "BANK-REF-123",
  "completedAt": "2026-09-27T12:03:00Z"
}
```

Backend xử lý:

1. Verify webhook signature/timestamp và deduplicate `eventId`.
2. Đối chiếu payout ID, withdrawal ID, beneficiary, amount và currency.
3. Tạo evidence hash trong database.
4. Với `COMPLETED`, Oracle Authority ký `record_offramp`; instruction này
   không nhận args.
5. Confirm transaction, đọc WithdrawalRecord phải là `Completed`, lưu Solana
   settlement signature.

Response nội bộ sau settlement:

```json
{
  "status": "COMPLETED",
  "withdrawalId": "77",
  "withdrawalRecordPda": "WithdrawalRecordPdaBase58",
  "providerPayoutId": "provider-payout-99881",
  "providerStatus": "COMPLETED",
  "solanaSettlementSignature": "RecordOfframpSignatureBase58",
  "onChainStatus": "Completed"
}
```

### C.8 Webhook lỗi hoặc không chắc chắn

Nếu provider trả `FAILED`, timeout hoặc trạng thái không đủ chắc chắn để refund,
Backend không tự chuyển token khỏi Treasury. Oracle gọi:

```text
mark_offramp_failed(failure_hash: [u8; 32])
```

Request nội bộ:

```json
{
  "oracleAuthorityPublicKey": "OracleAuthorityBase58",
  "failureHash": "64_HEX_CHARACTERS",
  "providerPayoutId": "provider-payout-99881",
  "reasonCode": "PROVIDER_TIMEOUT",
  "idempotencyKey": "fail-provider-event-552"
}
```

`oracleAuthorityPublicKey` là public key của Oracle signer do Backend/KMS giữ,
không phải public key của Freelancer hoặc provider.

Sau đối soát, Admin chỉ gọi `resolve_offramp(resolution_hash)` khi có bằng chứng
payout thực sự đã hoàn tất:

```json
{
  "adminPublicKey": "ConfiguredAdminBase58",
  "resolutionHash": "64_HEX_CHARACTERS",
  "resolutionNote": "Bank confirmed payout after provider timeout",
  "idempotencyKey": "resolve-withdrawal-77"
}
```

`resolutionNote` chỉ lưu database; on-chain chỉ lưu `resolutionHash`, timestamp
và `resolvedBy`.

---

## 4. On-ramp production và demo

### 4.1 Production: USD → USDC

1. Client yêu cầu mua một lượng USDC.
2. Backend gọi provider để lấy quote gồm fiat amount, fee, expiry và payment
   instructions.
3. Client trả USD trực tiếp cho provider.
4. Provider webhook Backend sau khi nhận USD.
5. Provider chuyển USDC từ ví provider vào Client ATA.
6. Backend xác minh transaction qua RPC: success, mint, destination ATA,
   amount, provider source và commitment.
7. Backend cập nhật order `COMPLETED`.

Solana không chuyển đổi USD. Production flow không gọi `mock_onramp`.

Request Frontend tạo order:

```json
{
  "clientPublicKey": "ClientWalletBase58",
  "requestedTokenAmountBaseUnits": "100000000",
  "tokenMint": "ProductionUsdcMintBase58",
  "fiatCurrency": "USD",
  "idempotencyKey": "onramp-order-client-100"
}
```

Response quote chuẩn hóa:

```json
{
  "orderId": "onramp_01JONRAMP",
  "providerOrderId": "provider-order-123",
  "clientPublicKey": "ClientWalletBase58",
  "clientUsdcAta": "ClientAtaBase58",
  "tokenAmountBaseUnits": "100000000",
  "fiatAmount": "10125",
  "fiatAmountScale": 2,
  "fiatCurrency": "USD",
  "providerFee": "125",
  "providerFeeScale": 2,
  "status": "AWAITING_FIAT_PAYMENT",
  "checkoutUrl": "https://provider.example/checkout/provider-order-123",
  "quoteExpiresAt": "2026-09-27T12:10:00Z"
}
```

`fiatAmount = "10125"` với `fiatAmountScale = 2` nghĩa là 101,25 USD; không
dùng JSON floating point.

### 4.2 Demo: Mock USD → Mock USDC

Demo không gọi provider và không nhận USD thật:

1. Backend giả lập payment thành công.
2. Mock On-ramp Authority gọi
   `mock_onramp(purchaseId, usdAmountE6)`.
3. Program chuyển Mock USDC từ PDA Treasury ATA vào Client ATA.
4. Program tạo MockOnrampReceipt để chống cấp hai lần.
5. Backend confirm signature và cập nhật demo order.

Tên `usdAmountE6` chỉ là số tiền USD giả lập với tỷ lệ demo 1:1; đây không phải
fiat conversion trên Solana.

---

## 5. Ma trận signer và người trả phí

| Instruction | Signer nghiệp vụ bắt buộc | Fee payer đề xuất | Ai giữ key |
|---|---|---|---|
| `initialize_config` | Program upgrade authority/Admin | `systemFeePayer` hoặc cùng Admin | Backend deployment KMS/multisig |
| `update_config` | Admin | `systemFeePayer` | Backend Admin KMS/multisig |
| `configure_mock_onramp` | Admin | `systemFeePayer` | Backend Admin KMS/multisig |
| `mock_onramp` | Mock On-ramp Authority | `systemFeePayer` | Backend demo KMS |
| `publish_rate` | Rate Authority | `systemFeePayer` | Backend rate-service KMS |
| `create_invoice` | Freelancer | `systemFeePayer` | Ví Freelancer; Backend chỉ giữ fee payer |
| `pay_invoice` | Client | `systemFeePayer` | Ví Client; Backend chỉ giữ fee payer |
| `cancel_invoice` | Freelancer | `systemFeePayer` | Ví Freelancer |
| `close_invoice` | Freelancer | `systemFeePayer` | Ví Freelancer |
| `request_offramp` | Freelancer | `systemFeePayer` | Ví Freelancer |
| `record_offramp` | Oracle Authority | `systemFeePayer` | Backend settlement KMS |
| `mark_offramp_failed` | Oracle Authority | `systemFeePayer` | Backend settlement KMS |
| `resolve_offramp` | Admin | `systemFeePayer` | Backend Admin KMS/multisig |

---

## 6. Tiêu chí hoàn tất tích hợp

- Backend không yêu cầu Client có SOL; với Freelancer, Backend phải thực hiện
  sponsored rent top-up cho `create_invoice`/`request_offramp` hoặc deploy thay
  đổi Program dùng `system_fee_payer` làm Anchor init payer.
- Backend không giữ private key người dùng trong flow wallet thông thường.
- Mọi transaction do Backend build có build session, message hash và expiry.
- Invoice dùng mint đã khóa trong Invoice khi thanh toán.
- On-ramp production do provider nhận USD và chuyển USDC; Solana không convert
  fiat.
- Off-ramp chỉ gọi provider sau khi `request_offramp` confirmed và
  WithdrawalRecord là `Pending`.
- Provider webhook được verify, chống replay và đối chiếu amount/order.
- Backend lưu Solana signature cho mọi state transition.
- Retry không tạo trùng Invoice, Withdrawal, provider order hoặc payout.
- Trạng thái database có thể reconcile lại từ account state, transaction logs
  và provider API.
