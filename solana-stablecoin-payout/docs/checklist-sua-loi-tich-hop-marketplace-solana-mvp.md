# Checklist sửa lỗi tích hợp Marketplace - Solana cho MVP

## 1. Phạm vi và kết luận

Tài liệu này tổng hợp các điểm cần sửa khi nối thay đổi tại commit
`91e0ffd540ed7393c3e86659ce28d3d1f11ed510` vào module
`solana-integration` hiện tại.

Mục tiêu MVP được chấp nhận:

- Không xử lý tiền pháp định hoặc crypto có giá trị thật.
- USD -> Mock USDC chạy trên local validator hoặc Devnet.
- USDC -> VND chỉ là phép tính mô phỏng off-ramp.
- Có thể dùng custodial wallet chung cho demo.
- Có thể dùng local private key qua biến môi trường trong môi trường development.
- Kết quả phải được ghi rõ là mock/simulated, không phải giao dịch tài chính thật.

Kết luận: kiến trúc `marketplace-backend -> solana-integration -> Solana RPC`
đúng hướng, nhưng code trong commit chưa chạy được end-to-end do contract HTTP,
response mapping, signer và Docker wiring chưa khớp. Các mục P0 dưới đây phải sửa
ngay cả khi hệ thống chỉ là demo.

## 2. Luồng MVP sau khi sửa

```text
Client capture khoản USD giả lập
  -> marketplace-backend tính phí on-ramp
  -> gọi solana-integration:9193
  -> gateway build, ký và gửi instruction mock_onramp
  -> gateway gọi Solana JSON-RPC
  -> marketplace chờ transaction confirmed/finalized
  -> marketplace đọc và xác minh MockOnrampReceipt
  -> tính số VND mô phỏng sau phí off-ramp
  -> lưu payout ledger và xuất chứng từ demo
```

## 3. P0 - Bắt buộc để demo chạy

### P0.1. Sửa đường dẫn API

Commit đang gọi sai route:

```text
POST /api/v1/demo/onramp/purchases
GET  /api/v1/transactions/{signature}
```

Route thực tế của `solana-integration`:

```text
POST /api/v1/solana/mock-onramp/purchases
GET  /api/v1/solana/transactions/{signature}
GET  /api/v1/solana/onramp-receipts/{client}/{purchaseId}
```

File cần sửa:

- `marketplace-backend/.../client/SolanaCprClient.java`

### P0.2. Đồng bộ request DTO

Request hiện tại của marketplace:

```json
{
  "clientPublicKey": "...",
  "purchaseId": "...",
  "usdAmountE6": "...",
  "idempotencyKey": "onramp-job-..."
}
```

Gateway thực tế yêu cầu:

```json
{
  "onrampAuthority": "...",
  "client": "...",
  "purchaseId": "...",
  "usdAmountE6": "...",
  "mode": "send",
  "commitment": "confirmed",
  "skipPreflight": false
}
```

Việc cần làm:

- Đổi `clientPublicKey` thành `client`, hoặc dùng `@JsonProperty("client")`.
- Bổ sung `onrampAuthority`.
- Có thể gửi rõ `mode=send` và `commitment=confirmed` dù gateway đã có default.
- Bỏ `idempotencyKey` khỏi request hiện tại, hoặc bổ sung contract idempotency ở
  gateway nếu muốn giữ field này.

File cần sửa:

- `marketplace-backend/.../dto/request/solana/MockOnrampPurchaseRequest.java`
- `marketplace-backend/.../provider/onchain/SolanaOnRampProvider.java`

### P0.3. Đồng bộ response DTO của lệnh on-ramp

Gateway trả response submit theo dạng:

```json
{
  "status": "submitted",
  "instruction": "mock_onramp",
  "signature": "...",
  "derivedAccounts": {
    "config": "...",
    "mockOnrampReceipt": "...",
    "mockOnrampTreasuryAuthority": "...",
    "mockOnrampTreasuryAta": "...",
    "clientAta": "..."
  }
}
```

Marketplace hiện đọc các field top-level không tồn tại:

- `clientUsdcAta`
- `receiptPda`
- `tokenAmountBaseUnits`
- `purchaseId`
- `slot`

Việc cần làm:

- Tạo DTO `DerivedAccountsResult`.
- Map `derivedAccounts.clientAta` thành client USDC ATA.
- Map `derivedAccounts.mockOnrampReceipt` thành receipt PDA.
- Không suy ra confirmation hoặc token amount chỉ từ response submit.
- Token amount phải lấy từ receipt sau khi transaction được xác nhận, hoặc dùng
  `amountUsdNet` trong demo nhưng vẫn phải đối chiếu receipt.

File cần sửa:

- `marketplace-backend/.../dto/response/solana/MockOnrampPurchaseResult.java`
- Tạo DTO response cho `derivedAccounts` và receipt.

### P0.4. Sửa transaction status mapping

Marketplace hiện mong đợi:

```json
{
  "status": "CONFIRMED",
  "error": "..."
}
```

Gateway thực tế trả:

```json
{
  "signature": "...",
  "found": true,
  "slot": "...",
  "confirmations": 1,
  "confirmationStatus": "confirmed",
  "error": null
}
```

Việc cần làm:

- Thay field `status` bằng `confirmationStatus`.
- Thêm field `found`.
- Đổi `error` từ `String` thành `Map<String, Object>` hoặc `JsonNode`.
- Chỉ coi thành công khi:
  - `found == true`;
  - `confirmationStatus` là `confirmed` hoặc `finalized`;
  - `error == null`.
- `submitted`, `processed` hoặc `found == false` là trạng thái đang chờ, không
  phải thất bại cuối cùng.

File cần sửa:

- `marketplace-backend/.../dto/response/solana/SolanaTransactionStatusResult.java`
- `marketplace-backend/.../provider/onchain/SolanaOnRampProvider.java`

### P0.5. Thêm confirmation polling tối thiểu

Không gọi transaction status đúng một lần ngay sau `sendTransaction`.

Mức tối thiểu cho demo:

- Poll 5-10 lần.
- Khoảng nghỉ 500 ms-1 giây.
- Có tổng timeout rõ ràng.
- Nếu hết timeout, lưu `PENDING` hoặc trả trạng thái đang xử lý; không tự kết luận
  transaction đã thất bại.
- Nếu RPC trả `error`, đánh dấu failed và lưu lỗi.

Nếu không muốn block request HTTP, tách bước confirmation sang scheduled worker.
Với MVP nhỏ, polling đồng bộ có timeout ngắn vẫn chấp nhận được.

### P0.6. Cấu hình đúng on-ramp authority và signer

`custodialClientPublicKey` là địa chỉ nhận Mock USDC, không phải authority ký
instruction.

Cần hai cấu hình riêng:

```yaml
solana-cpr:
  custodial-client-public-key: ${SOLANA_CPR_CUSTODIAL_CLIENT_PUBLIC_KEY:}
  onramp-authority-public-key: ${SOLANA_CPR_ONRAMP_AUTHORITY_PUBLIC_KEY:}
```

Gateway phải có private key tương ứng với `onrampAuthority` trong development:

```text
SOLANA_LOCAL_PRIVATE_KEYS=<base58-64-byte-secret-key>
```

Các điều kiện phải đúng:

- Public key của secret key phải bằng `onrampAuthority` trong request.
- Public key này phải bằng `Config.mockOnrampAuthority` trên chain.
- Không commit private key thật vào Git.
- Production sau này phải thay bằng KMS/HSM; chưa bắt buộc trong MVP.

### P0.7. Nối Docker Compose

Trong container, `http://localhost:9193` trỏ vào chính container marketplace,
không trỏ tới gateway.

Thêm vào service `marketplace-backend`:

```yaml
depends_on:
  solana-integration:
    condition: service_started

environment:
  SOLANA_CPR_BASE_URL: http://solana-integration:9193
  SOLANA_CPR_INTERNAL_API_KEY: ${SOLANA_INTERNAL_API_KEY:-change-me}
  SOLANA_CPR_CUSTODIAL_CLIENT_PUBLIC_KEY: ${SOLANA_CUSTODIAL_CLIENT_PUBLIC_KEY:-}
  SOLANA_CPR_ONRAMP_AUTHORITY_PUBLIC_KEY: ${SOLANA_ONRAMP_AUTHORITY_PUBLIC_KEY:-}
```

Đảm bảo:

- `SOLANA_CPR_INTERNAL_API_KEY` phía marketplace bằng
  `SOLANA_INTERNAL_API_KEY` phía gateway.
- Không để default `change-me` trong môi trường được chia sẻ/public.
- Có thể bổ sung healthcheck cho gateway sau khi luồng cơ bản chạy.

File cần sửa:

- `docker-compose.yml`
- `marketplace-backend/src/main/resources/application.yml`
- Các file `.env.example` liên quan.

### P0.8. Bootstrap môi trường Solana

RPC hoạt động không đồng nghĩa instruction sẽ chạy được. Trước khi test cần:

- Deploy program đúng `SOLANA_PROGRAM_ID`.
- Initialize Config PDA.
- Accepted mint đã tồn tại.
- Mint dùng cho demo nên có 6 decimals để khớp `usdAmountE6`.
- Cấu hình đúng `mockOnrampAuthority`.
- Bật `mockOnrampEnabled`.
- `maxMockOnrampAmount` đủ lớn.
- Mock treasury ATA tồn tại và có đủ Mock USDC.
- Authority/system fee payer có đủ SOL trả transaction fee và rent.
- `SOLANA_RPC_HTTP_URL` trỏ đúng local validator hoặc Devnet RPC.

## 4. P1 - Cần sửa để demo ổn định và retry được

### P1.1. Idempotency và recovery

`idempotencyKey` trong commit hiện không được gateway sử dụng. Receipt PDA đã
ngăn chuyển token hai lần, nhưng retry sau khi mất HTTP response sẽ nhận lỗi
duplicate dù transaction lần đầu đã thành công.

Giải pháp tối thiểu cho MVP:

1. Dùng cùng `purchaseId` cho cùng một job.
2. Trước khi submit, đọc:
   `GET /api/v1/solana/onramp-receipts/{client}/{purchaseId}`.
3. Nếu receipt tồn tại và client/amount/mint đúng, trả lại kết quả thành công cũ.
4. Nếu receipt tồn tại nhưng dữ liệu khác, trả conflict và không gửi transaction.
5. Nếu chưa tồn tại, mới submit `mock_onramp`.

Không dùng `reference.getMostSignificantBits() & Long.MAX_VALUE` vì làm mất một
bit của UUID. Tối thiểu dùng:

```java
Long.toUnsignedString(reference.getMostSignificantBits())
```

Nếu cần tránh collision tốt hơn, lưu mapping `jobId -> purchaseId` trong DB.

### P1.2. Xác minh receipt sau confirmation

Sau khi transaction confirmed/finalized, đọc receipt và kiểm tra:

- `purchaseId` đúng.
- `client` đúng custodial wallet.
- `clientAta` đúng ATA đã derive.
- `mint` đúng accepted mint.
- `usdAmountE6` đúng số gửi lên.
- `tokenAmount` đúng số Mock USDC mong đợi.

Chỉ sau bước này mới lưu `FreelancerPayoutRecord` là thành công.

### P1.3. Tách trạng thái nghiệp vụ

Không dùng `taxExportStatus=FAILED` để biểu diễn cả payout failure.

Nên có tối thiểu:

```text
onRampStatus:  NOT_STARTED | SUBMITTED | CONFIRMED | FAILED
offRampStatus: NOT_STARTED | SIMULATED | FAILED
taxExportStatus: NOT_ATTEMPTED | SUCCESS | FAILED
```

Nếu chưa muốn thêm nhiều cột, tối thiểu thêm `payoutStatus` và giữ
`taxExportStatus` đúng mục đích.

### P1.4. Sửa nội dung notification/UI

Không thông báo "Đã nhận được tiền" khi off-ramp mới chỉ tính toán.

Nội dung phù hợp hơn:

> Đã hoàn tất mô phỏng payout. Số tiền VND dự kiến nhận sau phí là ...

UI/API cũng nên hiển thị:

- `simulation: true`;
- `network: localnet` hoặc `devnet`;
- `rateSource`;
- transaction signature;
- explorer URL nếu chạy Devnet.

### P1.5. Sửa contract MISA về đúng đơn vị

Commit đang gửi USD gốc vào field `amountUsdc` để MISA tính thu nhập chịu thuế:

```java
"amountUsdc", amountUsd
```

Phép tính có thể cho kết quả mong muốn nhưng sai ngữ nghĩa dữ liệu.

Giải pháp ưu tiên:

```text
sourceCurrency = USD
sourceAmount = <amountUsd>
exchangeRatePair = USD/VND
exchangeRate = <usdToVndRate>
```

Nếu giữ contract cũ tạm thời cho demo:

- Ghi rõ đây là compatibility workaround.
- Ghi currency gốc trong description.
- Không sử dụng dữ liệu này như chứng từ production.

### P1.6. Không coi tỷ giá fallback là tỷ giá live

MVP có thể dùng `25000` khi API tỷ giá lỗi, nhưng phải:

- Lưu `rateSource=FALLBACK_PLACEHOLDER`.
- Hiển thị rõ là tỷ giá giả lập.
- Không ghi nội dung "live rate" trong UI/notification khi dùng fallback.
- Production sau này phải dừng hoặc retry thay vì phát hành chứng từ bằng
  placeholder.

### P1.7. Cấu hình HTTP timeout

`RestTemplate` hiện không có connect/read timeout. Cần đặt timeout cho:

- `SolanaCprClient`.
- MISA client.
- Exchange-rate provider.

MVP có thể dùng connect timeout 2-3 giây và read timeout 5-10 giây.

### P1.8. Database migration

Nếu luôn tạo database demo mới, có thể tạm dùng `ddl-auto: update`.

Nếu giữ database cũ, cần Flyway migration cho các thay đổi của
`freelancer_payout_records`, đặc biệt:

- Cột mới `NOT NULL`.
- Rename các cột amount/rate.
- Unique constraint trên `job_id`.
- Backfill dữ liệu cũ.

## 5. Những phần được phép giữ giả lập trong MVP

Các điểm sau không phải bug nếu được ghi rõ trong tài liệu và UI:

- Mock on-ramp thay vì provider mua USDC thật.
- Dùng Mock USDC trên localnet/Devnet.
- Một custodial client wallet dùng chung.
- Phí on-ramp/off-ramp cố định.
- Off-ramp chỉ tính số VND, không chuyển khoản ngân hàng.
- `offRampReference` là reference nội bộ giả lập.
- Private key trong environment ở local development.
- Polling đồng bộ ngắn thay vì worker/event listener.
- Tỷ giá fallback cho màn demo, với nhãn `FALLBACK_PLACEHOLDER`.
- Hibernate `ddl-auto: update` nếu database được tạo mới cho mỗi lần demo.

## 6. Những tuyên bố không nên dùng trong MVP hiện tại

Không mô tả hệ thống là:

- Đã chuyển VND cho freelancer.
- Đã hoàn tất off-ramp thật.
- Đã mua USDC bằng USD thật.
- Đã sẵn sàng production/mainnet.
- Chứng từ hiện tại là chứng từ tài chính/thuế production.

Nên mô tả là:

- Mock on-ramp on-chain.
- Simulated off-ramp.
- Estimated VND payout.
- Demo tax record/certificate.

## 7. Test case tối thiểu trước khi demo

### Happy path

- [ ] Marketplace gọi đúng endpoint gateway.
- [ ] Header internal API key hợp lệ.
- [ ] Gateway tìm thấy Config PDA.
- [ ] Authority signer khả dụng.
- [ ] Treasury có đủ Mock USDC.
- [ ] Transaction được submit.
- [ ] Transaction chuyển sang confirmed/finalized.
- [ ] Receipt tồn tại và có đúng client, mint, amount.
- [ ] Client ATA tăng đúng Mock USDC.
- [ ] Payout record lưu signature, ATA và receipt PDA.
- [ ] Số VND mô phỏng được tính và hiển thị đúng nhãn.

### Error path

- [ ] API key sai trả 401.
- [ ] Thiếu signer trả lỗi rõ ràng.
- [ ] Treasury thiếu token không tạo payout record thành công.
- [ ] RPC unavailable không bị báo nhầm là transaction failed vĩnh viễn.
- [ ] Transaction pending không bị coi là failed ngay.
- [ ] Transaction có `error` được ghi nhận.
- [ ] Retry sau khi response bị mất đọc lại receipt và không cấp token lần hai.
- [ ] Receipt có amount/client khác trả conflict.
- [ ] Tỷ giá fallback được gắn nhãn placeholder.

## 8. Điều kiện hoàn thành MVP

MVP được xem là đủ ổn để demo khi:

- Toàn bộ mục P0 đã hoàn thành.
- Happy path chạy được từ `approve job` đến receipt on-chain và payout ledger.
- Retry không cấp Mock USDC hai lần.
- UI/notification không tuyên bố đã chuyển tiền thật.
- Transaction signature có thể kiểm tra qua RPC hoặc explorer của đúng network.
- Các config/key demo không được commit vào repository.

## 9. Ghi chú về lịch sử Git

Commit `91e0ffd` không phải patch độc lập trên HEAD `4c7869a`; nó có các commit
payout tiền đề. Không nên cherry-pick riêng commit này vào nhánh `solana` hiện
tại rồi sửa conflict thủ công mà không kiểm tra chuỗi lịch sử.

Khi tích hợp nên:

1. Fetch đầy đủ branch chứa commit.
2. Xem toàn bộ các commit nằm giữa `4c7869a` và `91e0ffd`.
3. Merge/rebase chuỗi thay đổi cần thiết.
4. Sau đó thực hiện checklist P0/P1 trong tài liệu này.

