# Báo cáo triển khai Solana module — Stablecoin Payout

Tài liệu này mô tả **code đang tồn tại trong repository**, IDL được sinh bởi
`anchor build` và kết quả test thực tế trên local validator. Đây không phải bản
mô tả ý tưởng tương lai. Những phần chưa có code được ghi rõ là chưa triển khai.

## 1. Kết quả cuối cùng

Solana module hiện hỗ trợ các nghiệp vụ sau:

1. Khởi tạo và cập nhật cấu hình toàn cục.
2. Tạo Invoice yêu cầu Client thanh toán Mock USDC.
3. Client chuyển Mock USDC cho Freelancer và đánh dấu Invoice là `Paid` trong
   cùng transaction.
4. Freelancer hủy Invoice còn `Pending`.
5. Freelancer đóng Invoice `Paid` hoặc `Cancelled` và nhận lại rent.
6. Rate Authority công bố snapshot USDC/USD và USD/VND; program tự tính
   USDC/VND.
7. Freelancer yêu cầu off-ramp bằng một RateSnapshot cụ thể; Mock USDC được
   chuyển vào Treasury ATA và số VNĐ được program tự tính.
8. Settlement Oracle xác nhận off-ramp đã hoàn tất ngoài blockchain hoặc đưa
   lỗi không chắc chắn vào `FailedPendingReview`; Admin resolve thủ công với
   hash bằng chứng được lưu và emit event.

| Thành phần | Số lượng thực tế |
|---|---:|
| Instruction trong IDL | 11 |
| Anchor state/account PDA | 4 |
| Event | 8 |
| Custom error | 37 |
| Test passing ở lần chạy cuối | 60 |

Bốn state/PDA là `Config`, `Invoice`, `RateSnapshot` và `WithdrawalRecord`.
Tám event là `InvoiceCreated`, `InvoicePaid`, `InvoiceCancelled`,
`RatePublished`, `OfframpRequested`, `OfframpCompleted`,
`OfframpFailedPendingReview` và `OfframpResolved`.

### Hardening Phase 10 hoàn thành ngày 27/09/2026

- `Invoice` khóa `mint`, `rate_snapshot` và `expires_at`. `create_invoice` chỉ
  nhận snapshot còn fresh và expiry không vượt snapshot; `pay_invoice` từ chối
  Invoice hết hạn.
- `pay_invoice` xác minh Mint bằng `invoice.mint`, không bằng
  `config.accepted_mint`; Invoice cũ vì vậy vẫn trả được sau Config mint
  rotation trong thời hạn đã khóa.
- `WithdrawalStatus` có thêm `FailedPendingReview`. Oracle gọi
  `mark_offramp_failed(failure_hash)`; Admin mới được gọi
  `resolve_offramp(resolution_hash)`. Record giữ failure/resolution hashes,
  timestamps và `resolved_by`; không auto-refund token khỏi Treasury.
- `initialize_config` nhận chính Program và ProgramData, chỉ chấp nhận Admin là
  upgrade authority hiện tại. Test validator được cấu hình `upgradeable=true`
  và có negative test front-run bằng signer giả.
- Đây là account-layout và IDL breaking change. Deployment cũ phải dùng Program
  ID/config mới hoặc có migration/reallocation được audit; không upgrade rồi
  deserialize trực tiếp account Invoice/Withdrawal layout cũ.

Các phần chưa hoàn tất:

- Chưa deploy devnet.
- Deployment keypair hiện resolve thành `2Tx2fa...69oqb`, khác `declare_id!` và
  `[programs.localnet]` là `CwuaAP...AnhEf`. Không được deploy cho tới khi chọn
  canonical Program ID và đồng bộ key/config/manifest có chủ đích.
- Chưa có backend, database, indexer hoặc cơ chế backfill event.
- Chưa có Rate Collector lấy giá thật.
- Chưa có Web Dashboard hoặc Flutter checkout.
- Chưa có tích hợp ngân hàng/on-ramp/off-ramp thật.
- Chưa có instruction đóng WithdrawalRecord.

Lần kiểm tra cuối đã pass `cargo fmt --check`, `anchor build`,
`yarn tsc --noEmit` và `anchor test --validator legacy`.

## 2. Cấu trúc thư mục

```text
programs/invoice_payments/src/
├── constants.rs
├── error.rs
├── events.rs
├── instructions.rs
├── lib.rs
├── state.rs
├── instructions/
│   ├── cancel_invoice.rs
│   ├── close_invoice.rs
│   ├── create_invoice.rs
│   ├── initialize_config.rs
│   ├── mark_offramp_failed.rs
│   ├── pay_invoice.rs
│   ├── publish_rate.rs
│   ├── record_offramp.rs
│   ├── request_offramp.rs
│   ├── resolve_offramp.rs
│   └── update_config.rs
└── state/
    ├── config.rs
    ├── invoice.rs
    ├── rate_snapshot.rs
    └── withdrawal_record.rs

tests/
├── config/
│   └── config.spec.ts
├── helpers/
│   ├── invoice.ts
│   ├── mock-usdc.ts
│   ├── rate.ts
│   └── test-environment.ts
├── invoice/
│   ├── cancel-invoice.spec.ts
│   ├── close-invoice.spec.ts
│   ├── create-invoice.spec.ts
│   └── pay-invoice.spec.ts
├── offramp/
│   ├── record-offramp.spec.ts
│   └── request-offramp.spec.ts
└── rates/
    └── rate-snapshot.spec.ts
```

### Trách nhiệm từng file Rust

| File | Trách nhiệm |
|---|---|
| `src/lib.rs` | Khai báo Program ID và 11 entrypoint xuất ra IDL. |
| `src/constants.rs` | Seeds, decimals, `RATE_SCALE`, giới hạn lệch thời gian và mẫu số tính VNĐ. |
| `src/error.rs` | 37 custom error của program. |
| `src/events.rs` | Tám event phục vụ quan sát/indexing sau này. |
| `src/instructions.rs` | Đăng ký và re-export các module instruction. |
| `src/state.rs` | Đăng ký và re-export các module state. |
| `state/config.rs` | Cấu hình authority, mint, tuổi rate và paused. |
| `state/invoice.rs` | `InvoiceStatus` và dữ liệu Invoice. |
| `state/rate_snapshot.rs` | Snapshot tỷ giá bất biến. |
| `state/withdrawal_record.rs` | `WithdrawalStatus` và dữ liệu off-ramp. |
| `instructions/*.rs` | Mỗi file chứa accounts validation và handler cho một instruction cùng tên. |

### Trách nhiệm từng file test/helper

| File | Trách nhiệm |
|---|---|
| `tests/helpers/test-environment.ts` | Tạo provider, program client, Mock USDC và Config dùng chung. |
| `tests/helpers/mock-usdc.ts` | Tạo mint 6 decimals, Client ATA và mint 1.000 Mock USDC. |
| `tests/helpers/invoice.ts` | Derive Invoice PDA, tạo Invoice fixture và helper kiểm tra transaction bị từ chối. |
| `tests/helpers/rate.ts` | Derive/publish RateSnapshot dùng lại trong off-ramp tests. |
| `tests/config/config.spec.ts` | Test Mock USDC và vòng đời Config. |
| `tests/invoice/create-invoice.spec.ts` | Test tạo Invoice. |
| `tests/invoice/pay-invoice.spec.ts` | Happy path và negative tests thanh toán. |
| `tests/invoice/cancel-invoice.spec.ts` | Test hủy Invoice. |
| `tests/invoice/close-invoice.spec.ts` | Test đóng Invoice và hoàn rent. |
| `tests/rates/rate-snapshot.spec.ts` | Test công bố và kiểm tra RateSnapshot. |
| `tests/offramp/request-offramp.spec.ts` | Test chuyển token vào Treasury và tạo WithdrawalRecord. |
| `tests/offramp/record-offramp.spec.ts` | Test Settlement Oracle hoàn tất WithdrawalRecord. |

## 3. Giải thích từng instruction

Danh sách dưới đây lấy từ `target/idl/invoice_payments.json`; không có
instruction nào khác ngoài 11 instruction này.

### 3.1 `initialize_config`

- **Nghiệp vụ:** tạo cấu hình toàn cục lần đầu.
- **Người gọi/ký:** Program upgrade authority; signer này trở thành Admin và
  trả rent.
- **Accounts:** `admin` để xác thực/payer; `config` để tạo PDA; `accepted_mint`
  để kiểm tra mint có 6 decimals; chính executable Program và ProgramData để
  đối chiếu upgrade authority; `system_program` để tạo account.
- **Đọc/thay đổi:** đọc Mint; tạo và ghi Config.
- **PDA:** tạo `[b"config"]`, lưu bump.
- **Token/CPI:** không chuyển token. `init` khiến Anchor gọi System Program;
  handler không tự tạo `CpiContext`. Không dùng `new_with_signer`.
- **State:** trước đó Config chưa tồn tại; sau đó lưu Admin, accepted mint,
  Treasury/Rate/Oracle Authority, max rate age, `paused=false` và bump.
- **Thất bại:** signer không phải upgrade authority, ProgramData không thuộc
  đúng program, PDA đã tồn tại, mint không phải 6 decimals, authority là
  default, Rate Authority trùng Oracle, hoặc max age không dương.
- **Code/test:** `src/instructions/initialize_config.rs`;
  `tests/config/config.spec.ts`.

**Safe initialization plan cho devnet/mainnet:**

1. Giữ program ở trạng thái upgradeable; deployer/upgrade-authority key nên là
   multisig hoặc deployment signer được bảo vệ, không phải hot wallet backend.
2. Ngay sau deploy, derive và kiểm tra ProgramData, rồi chính upgrade authority
   ký `initialize_config`; không chuyển/revoke authority trước bước này.
3. Fetch Config ở commitment `finalized`, đối chiếu Admin, mint, Treasury/Rate/
   Oracle authorities, max age và Config PDA; lưu signature + IDL checksum vào
   deployment manifest.
4. Sau xác minh mới chuyển upgrade authority sang governance/multisig hoặc set
   immutable theo policy. Program immutable nhưng Config chưa init sẽ không thể
   khởi tạo bằng instruction này và cần redeploy Program ID mới.
5. Không gửi private key qua backend/API; runbook cần two-person review cho
   public keys và cluster trước khi ký.

### 3.2 `update_config`

- **Nghiệp vụ:** đổi accepted mint, các authority, max rate age hoặc paused.
- **Người gọi/ký:** Admin đã lưu trong Config.
- **Accounts:** `admin` để `has_one`; `config` để đọc/ghi; `accepted_mint` để
  kiểm tra 6 decimals và ghi làm mint mới.
- **Đọc/thay đổi:** đọc Mint và Admin; thay đổi Config, không thay `admin`/bump.
- **PDA:** xác minh `[b"config"]` và stored bump.
- **Token/CPI:** không chuyển token, không CPI thủ công.
- **State:** Config cũ chuyển sang các giá trị cấu hình mới.
- **Thất bại:** signer không phải Admin, mint sai decimals, authority default,
  Rate/Oracle trùng nhau hoặc max age không dương.
- **Code/test:** `src/instructions/update_config.rs`;
  `tests/config/config.spec.ts`.

### 3.3 `create_invoice`

- **Nghiệp vụ:** Freelancer tạo yêu cầu Client trả một lượng token.
- **Người gọi/ký:** Freelancer; signer này cũng trả rent.
- **Accounts:** `freelancer`; Config để đọc paused/mint/max age; RateSnapshot
  để khóa quote; Invoice để tạo state; System Program để tạo account.
- **Đọc/thay đổi:** đọc Config/RateSnapshot/Clock; tạo Invoice.
- **PDA:** `[b"invoice", freelancer, invoice_id.to_le_bytes()]`.
- **Token/CPI:** chưa chuyển token. Anchor `init` gọi System Program; không có
  `CpiContext` thủ công.
- **State:** chưa có Invoice; sau đó là `Pending`, `paid_at=None`, khóa mint từ
  Config, RateSnapshot PDA và `expires_at` không vượt snapshot expiry.
- **Thất bại:** paused, amount bằng 0, Client default, snapshot stale/hết hạn/sai
  publisher, Invoice expiry không hợp lệ hoặc PDA trùng.
- **Code/test:** `src/instructions/create_invoice.rs`;
  `tests/invoice/create-invoice.spec.ts`.

### 3.4 `pay_invoice`

- **Nghiệp vụ:** Client trả đúng amount đã lưu cho Freelancer.
- **Người gọi/ký:** Client được lưu trong Invoice.
- **Accounts:** Client signer; Config; Invoice mutable; Freelancer nhận tiền;
  accepted Mint; Client ATA nguồn; Freelancer ATA đích; Legacy Token Program.
- **Đọc/thay đổi:** đọc Config/Mint/Freelancer; giảm Client ATA, tăng Freelancer
  ATA; đổi Invoice sang `Paid` và ghi `paid_at`.
- **PDA:** xác minh Config và Invoice seeds/bump.
- **Token/CPI:** có `transfer_checked` CPI sang Legacy Token Program, dùng
  `CpiContext::new` vì Client ký trực tiếp; không dùng PDA signer hay
  `new_with_signer`.
- **State:** Invoice phải `Pending`; sau thành công là `Paid`.
- **Thất bại:** paused, Invoice hết hạn, Client sai, Invoice không Pending, mint
  không khớp `invoice.mint`, Freelancer sai, ATA sai owner/mint hoặc thiếu token.
- **Code/test:** `src/instructions/pay_invoice.rs`;
  `tests/invoice/pay-invoice.spec.ts`.

### 3.5 `publish_rate`

- **Nghiệp vụ:** công bố một snapshot tỷ giá mới, không sửa snapshot cũ.
- **Người gọi/ký:** Rate Authority hiện tại; signer này trả rent.
- **Accounts:** Rate Authority; Config; RateSnapshot mới; System Program.
- **Đọc/thay đổi:** đọc Config/Clock; tạo RateSnapshot.
- **PDA:** `[b"rate", rate_id.to_le_bytes()]`.
- **Token/CPI:** không chuyển token. Anchor `init` gọi System Program; không CPI
  thủ công và không `new_with_signer`.
- **State:** snapshot chưa tồn tại; sau đó lưu hai rate đầu vào, USDC/VND do
  program tính, timestamps, source hash, publisher và bump.
- **Thất bại:** paused, authority/signer sai, rate bằng 0, observed time quá xa
  tương lai, expiry không sau observation, lifetime quá dài, ID trùng hoặc kết
  quả không vừa `u64`.
- **Code/test:** `src/instructions/publish_rate.rs`;
  `tests/rates/rate-snapshot.spec.ts`.

### 3.6 `cancel_invoice`

- **Nghiệp vụ:** Freelancer hủy yêu cầu chưa được trả.
- **Người gọi/ký:** đúng Freelancer lưu trong Invoice.
- **Accounts:** Freelancer signer; Config để kiểm tra paused; Invoice mutable.
- **Đọc/thay đổi:** đọc Config; đổi `Invoice.status`.
- **PDA:** xác minh Config và Invoice seeds/bump; `has_one=freelancer`.
- **Token/CPI:** không chuyển token, không CPI.
- **State:** `Pending → Cancelled`; thời gian hủy nằm trong event, không có
  trường `cancelled_at` trong Invoice.
- **Thất bại:** paused, Freelancer sai, Invoice Paid hoặc đã Cancelled.
- **Code/test:** `src/instructions/cancel_invoice.rs`;
  `tests/invoice/cancel-invoice.spec.ts`.

### 3.7 `close_invoice`

- **Nghiệp vụ:** xóa Invoice đã kết thúc và hoàn rent.
- **Người gọi/ký:** đúng Freelancer trong Invoice.
- **Accounts:** Freelancer mutable để nhận lamports; Invoice mutable và `close`.
- **Đọc/thay đổi:** đọc trạng thái/authority; đóng Invoice, chuyển toàn bộ
  lamports của account cho Freelancer.
- **PDA:** xác minh Invoice seeds/bump và `has_one=freelancer`.
- **Token/CPI:** không chuyển SPL token; không CPI token. Việc close do Anchor
  thực hiện khi instruction kết thúc.
- **State:** chỉ `Paid` hoặc `Cancelled` được đóng; sau đó account không còn.
- **Thất bại:** Invoice `Pending`, Freelancer sai, seed/bump sai.
- **Code/test:** `src/instructions/close_invoice.rs`;
  `tests/invoice/close-invoice.spec.ts`.

### 3.8 `request_offramp`

- **Nghiệp vụ:** Freelancer khóa/chuyển Mock USDC vào Treasury ATA để yêu cầu
  nhận VNĐ ngoài blockchain.
- **Người gọi/ký:** Freelancer; signer này trả rent cho WithdrawalRecord.
- **Accounts:** Freelancer; Config; RateSnapshot; accepted Mint; Freelancer ATA;
  Treasury Authority; Treasury ATA; WithdrawalRecord mới; Token Program; System
  Program.
- **Đọc/thay đổi:** đọc Config, RateSnapshot, Mint và authority; giảm Freelancer
  ATA, tăng Treasury ATA; tạo WithdrawalRecord.
- **PDA:** xác minh Config và RateSnapshot; tạo
  `[b"withdrawal", freelancer, withdrawal_id.to_le_bytes()]`.
- **Token/CPI:** `transfer_checked` sang Legacy Token Program bằng
  `CpiContext::new` vì Freelancer ký. Không dùng `new_with_signer`. Anchor còn
  dùng System Program cho `init`.
- **State:** trước đó chưa có record; sau đó record `Pending`, lưu snapshot,
  token amount, Treasury ATA, fiat amount, timestamps và bump.
- **Thất bại:** paused, thiếu signer, token amount 0, snapshot hết hạn/quá cũ/sai
  publisher, mint hoặc ATA sai, Treasury sai, thiếu token, kết quả overflow,
  kết quả dưới 1 VND hoặc PDA trùng. Transaction thất bại rollback cả account
  init lẫn CPI token.
- **Code/test:** `src/instructions/request_offramp.rs`;
  `tests/offramp/request-offramp.spec.ts`.

### 3.9 `record_offramp`

- **Nghiệp vụ:** ghi nhận Settlement Oracle đã xác nhận việc trả fiat ngoài
  blockchain.
- **Người gọi/ký:** Oracle Authority hiện tại trong Config.
- **Accounts:** Oracle signer; Config; WithdrawalRecord mutable. IDL không chứa
  Mint, ATA hay Token Program.
- **Đọc/thay đổi:** đọc Config/Clock; đổi WithdrawalRecord.
- **PDA:** xác minh Config và WithdrawalRecord seeds/bump.
- **Token/CPI:** không chuyển token, không CPI.
- **State:** `Pending → Completed`, `completed_at: None → Some(timestamp)`.
- **Thất bại:** Oracle sai/thiếu chữ ký, paused, record không Pending hoặc PDA
  không hợp lệ.
- **Code/test:** `src/instructions/record_offramp.rs`;
  `tests/offramp/record-offramp.spec.ts`.

### 3.10 `mark_offramp_failed`

- **Nghiệp vụ:** Oracle ghi nhận settlement lỗi/timeout chưa chắc chắn và đưa
  request vào hàng đợi review, không hoàn token tự động.
- **Người gọi/ký:** Oracle Authority hiện tại.
- **State:** `Pending → FailedPendingReview`; lưu `failure_hash` khác zero và
  `failed_at`; emit `OfframpFailedPendingReview`.
- **Thất bại:** sai/thiếu Oracle, paused, hash zero hoặc record không Pending.

### 3.11 `resolve_offramp`

- **Nghiệp vụ:** Admin resolve thủ công sau đối soát có bằng chứng fiat đã
  settlement.
- **Người gọi/ký:** Admin hiện tại, tách khỏi Oracle transition ban đầu.
- **State:** `FailedPendingReview → Completed`; lưu `resolution_hash`,
  `resolved_at`, `resolved_by`, đồng thời set `completed_at`; emit
  `OfframpResolved`.
- **Thất bại:** sai/thiếu Admin, paused, hash zero hoặc record không ở review.

## 4. Giải thích từng state/account

| Account | Tạo và rent | Owner | Dữ liệu/quyền sửa | Seeds, bump, đóng | Có token balance? |
|---|---|---|---|---|---|
| Config PDA | Admin tạo/trả rent qua `initialize_config` | Invoice Program | Lưu Admin, mint, ba authority, max rate age, paused; chỉ Admin update | `[b"config"]`, stored bump; chưa có close instruction | Không |
| Invoice PDA | Freelancer tạo/trả rent | Invoice Program | Lưu id, hai bên, amount, locked mint/rate/expiry, status, timestamps; Client pay, Freelancer cancel/close | `[b"invoice", freelancer, id_le]`; close khi Paid/Cancelled | Không |
| RateSnapshot PDA | Rate Authority tạo/trả rent | Invoice Program | Lưu ba rate, timestamps, hash, publisher; không có instruction sửa/đóng | `[b"rate", rate_id_le]`; bất biến | Không |
| WithdrawalRecord PDA | Freelancer tạo/trả rent | Invoice Program | Lưu request, failure/resolution audit; Oracle complete/fail, Admin resolve review | `[b"withdrawal", freelancer, id_le]`; chưa có close | Không |
| Mint Account | Test tạo bằng SPL helper, test payer trả rent | Legacy Token Program | Decimals, supply, mint authority; fixture dùng mint authority để phát Mock USDC | Không phải PDA của Invoice Program; không đóng trong suite | Không giữ balance người dùng; giữ metadata/supply |
| Client ATA | SPL helper tạo, test payer trả rent | Legacy Token Program | Token Program đổi balance khi Client trả Invoice | ATA suy ra từ Client + mint + Token Program; không đóng | Có |
| Freelancer ATA | SPL helper tạo, thường test payer trả rent | Legacy Token Program | Nhận payment; bị trừ khi request off-ramp | ATA suy ra từ Freelancer + mint + Token Program; không đóng | Có |
| Treasury ATA | SPL helper tạo, test payer trả rent | Legacy Token Program | Nhận token đúng một lần ở `request_offramp` | ATA suy ra từ Treasury Authority + mint + Token Program; không đóng | Có |

Điểm quan trọng: wallet address, Config, Invoice, RateSnapshot và
WithdrawalRecord không chứa SPL token balance. Balance nằm trong ATA do Legacy
Token Program sở hữu.

## 5. Toàn bộ luồng nghiệp vụ

### 5.1 Mock USDC setup

1. **Trong test, ngoài nghiệp vụ production:** tạo Mint 6 decimals.
2. **Trên Solana:** Legacy Token Program lưu Mint Account.
3. **Trong test:** tạo Client keypair và Client ATA.
4. **Trên Solana:** mint 1.000 Mock USDC vào Client ATA.
5. **Không có backend/frontend:** fixture gọi RPC trực tiếp.

### 5.2 Freelancer tạo Invoice

1. **Frontend tương lai:** thu Client pubkey, invoice ID và amount; hiện test làm việc này.
2. **Trên Solana:** Freelancer ký `create_invoice`.
3. **Trên Solana:** program đọc accepted mint/paused từ Config.
4. **Trên Solana:** tạo Invoice PDA `Pending`; chưa chuyển token.

### 5.3 Client thanh toán Invoice

1. **Backend tương lai:** Client-Mock có thể được backend điều phối; hiện test ký trực tiếp.
2. **Trên Solana:** Client ký `pay_invoice`.
3. **Trên Solana:** program xác minh Client, Invoice, mint và hai ATA.
4. **Trên Solana:** CPI `transfer_checked` chuyển đúng amount từ Client ATA sang Freelancer ATA.
5. **Trên Solana:** chỉ sau CPI thành công, Invoice thành `Paid` và phát `InvoicePaid`.

### 5.4 Công bố tỷ giá

1. **Ngoài blockchain, chưa triển khai:** nguồn thật/Rate Collector lấy USDC/USD và USD/VND.
2. **Trong test:** test cung cấp rate fixed-point và source hash.
3. **Trên Solana:** Rate Authority ký `publish_rate`.
4. **Trên Solana:** program kiểm tra thời gian/giới hạn và tự tính USDC/VND.
5. **Trên Solana:** tạo RateSnapshot mới, không sửa snapshot cũ.

### 5.5 Freelancer yêu cầu off-ramp

1. **Frontend/backend tương lai:** chọn snapshot và token amount; hiện test làm trực tiếp.
2. **Trên Solana:** Freelancer ký `request_offramp`.
3. **Trên Solana:** program kiểm tra snapshot còn fresh và publisher hợp lệ.
4. **Trên Solana:** program tự tính VNĐ, không nhận fiat amount từ caller.
5. **Trên Solana:** chuyển Mock USDC từ Freelancer ATA vào Treasury ATA.
6. **Trên Solana:** tạo WithdrawalRecord `Pending` và phát event.
7. **Ngoài blockchain, chưa triển khai:** bên vận hành thực hiện/mô phỏng chuyển VNĐ.

### 5.6 Oracle xác nhận off-ramp

1. **Ngoài blockchain:** hệ thống vận hành xác định settlement đã hoàn tất; phần này chưa có backend.
2. **Trên Solana:** Oracle ký `record_offramp`.
3. **Trên Solana:** record thành `Completed`, ghi `completed_at` và phát event.
4. **Trên Solana:** không chuyển token lần nữa.

### 5.7 Đóng Invoice và hoàn rent

1. **Trên Solana:** Invoice phải `Paid` hoặc `Cancelled`.
2. **Trên Solana:** đúng Freelancer ký `close_invoice`.
3. **Trên Solana:** account Invoice bị đóng và toàn bộ lamports của account được trả cho Freelancer.

## 6. Module tỷ giá

USDC thường bám USD nhưng không nên giả định luôn đúng chính xác 1 USD. Vì vậy
USDC/USD biểu diễn giá thực tế của stablecoin. USD/VND cho biết một USD đổi được
bao nhiêu VNĐ. Kết hợp hai tỷ giá mới suy ra được USDC/VND dùng cho off-ramp.

`RATE_SCALE = 1_000_000` nghĩa là mọi giá có sáu chữ số thập phân cố định:

- USDC/USD = 0,9998 được lưu là `999_800`.
- USD/VND = 25.000 được lưu là `25_000_000_000`.

Program tính:

```text
usdc_vnd_e6 = usdc_usd_e6 × usd_vnd_e6 ÷ 1_000_000
```

Ví dụ:

1. `999_800 × 25_000_000_000 = 24_995_000_000_000_000`.
2. Chia `1_000_000` được `24_995_000_000`.
3. Đây là 24.995 VND/USDC ở scale `10^6`.
4. Off-ramp 10 USDC là `10_000_000` base units.
5. `10_000_000 × 24_995_000_000 ÷ 1_000_000_000_000 = 249_950 VND`.

Không dùng `f32/f64` vì số thực nhị phân có làm tròn và không thích hợp cho kết
quả tài chính đồng thuận. Phép nhân dùng `u128` để hai số `u64` không overflow
trước khi chia; kết quả được kiểm tra rồi mới đổi về `u64`.

- **Rate Authority:** ký và công bố dữ liệu giá.
- **Settlement Oracle:** chỉ xác nhận settlement fiat ngoài blockchain; không
  được công bố giá.

RateSnapshot lưu `rate_id`, ba rate, `observed_at`, `expires_at`, `source_hash`,
publisher và bump. “Stale” nghĩa là snapshot đã quá `max_rate_age_seconds` so
với observation hoặc đã hết hạn. `request_offramp` nhận account RateSnapshot,
không nhận rate tự do. Account phải do program sở hữu, đúng PDA, publisher phải
khớp Config và còn fresh. Fiat amount cũng không phải argument instruction, nên
frontend không thể thay rate hoặc tự khai số VNĐ.

## 7. Bảo mật và invariant

| Quy tắc | Cơ chế bảo vệ | Nếu thiếu | Test chứng minh |
|---|---|---|---|
| Chỉ Admin update Config | `has_one=admin`, `Signer` | Chiếm quyền cấu hình | `rejects an update signed by a fake admin` |
| Đúng Client mới pay | `has_one=client`, `Signer` | Người khác kích hoạt/giả payment | `rejects a client signer...` |
| Đúng Freelancer cancel/close/request | `has_one`, seeds, `Signer` | Hủy/đóng/rút token trái phép | cancel/close signer tests; request thiếu chữ ký |
| Đúng Rate Authority | `address=config.rate_authority`, `Signer` | Giá giả | hai signer tests trong rate spec |
| Đúng Settlement Oracle | `address=config.oracle_authority`, `Signer` | Hoàn tất withdrawal giả | hai Oracle negative tests |
| Accepted mint | `address=config.accepted_mint`, Mint 6 decimals | Token giả/sai decimals | Config mint fixture; wrong mint payment/off-ramp |
| ATA đúng owner và mint | `associated_token::authority/mint/token_program` | Chuyển sai nguồn/đích | Client/Freelancer/Treasury ATA tests |
| Invoice status hợp lệ | so sánh enum trước pay/cancel/close | double pay, cancel paid, close pending | payment twice, cancel Paid/twice, close Pending |
| Withdrawal chỉ complete một lần | `status == Pending` | settlement lặp | `rejects completing...twice` |
| Không trùng PDA | `init` với deterministic seeds | ghi đè lịch sử | duplicate Invoice/Rate/Withdrawal tests |
| Paused chặn nghiệp vụ | constraint trên Config | hoạt động khi khẩn cấp | create/pay/cancel/rate/request/record paused tests |
| Checked arithmetic | `checked_mul`, `checked_div`, `try_from` | wrap số tiền | rate/fiat overflow tests |
| Snapshot còn hạn | `current < expires_at` và max age | quote cũ | expired/stale snapshot tests |
| Không double payment | Invoice phải Pending | trừ Client hai lần | `rejects paying the same invoice twice` |
| Atomic rollback | Solana transaction atomic + assertions | PDA rác hoặc mất token | zero/insufficient off-ramp; payment balance assertions |

## 8. Giải thích tests

### `tests/config/config.spec.ts` — 6 tests

| Loại | Test | Chuẩn bị/gọi | Assertion chính |
|---|---|---|---|
| Happy | creates the Mock USDC fixture | Tạo Mint, Client ATA, mint token | decimals, authority, owner, mint, balance đúng |
| Happy | initializes Config PDA... | Dùng shared environment | mọi field và bump đúng |
| Negative | rejects duplicate Config initialization | Gọi initialize lần hai | transaction thất bại |
| Happy | allows configured admin to update | Admin gọi update rồi restore | các field đổi đúng |
| Negative | rejects invalid rate configuration... | default key, authority trùng, max age 0 | đều fail, Config không đổi |
| Negative | rejects update signed by fake admin | fake signer | fail, mọi field Config giữ nguyên |

### `tests/invoice/create-invoice.spec.ts` — 6 tests

| Loại | Test | Assertion chính |
|---|---|---|
| Happy | allows a freelancer to create an invoice | PDA/fields/status Pending/timestamps/bump đúng |
| Negative | amount zero | fail và Invoice PDA không tồn tại |
| Negative | default Client | fail và không để lại PDA |
| Negative | duplicate invoice ID | fail, Invoice cũ không đổi |
| Negative | expiry sau RateSnapshot | fail, không tạo PDA |
| Negative | Config paused | fail, không tạo PDA; Config được restore |

### `tests/invoice/pay-invoice.spec.ts` — 10 tests

| Loại | Test | Assertion chính |
|---|---|---|
| Happy | transfers Mock USDC... | Client giảm, Freelancer tăng, supply giữ nguyên, Invoice Paid |
| Negative | paying same invoice twice | lần hai fail; hai balance và state không đổi |
| Negative | Invoice hết hạn | fail, Invoice vẫn Pending |
| Negative | wrong Client signer | `UnauthorizedClient`; state/balance giữ nguyên |
| Negative | wrong mint | `InvalidInvoiceMint`; state/balance giữ nguyên |
| Happy/security | Config rotate mint | Invoice cũ vẫn trả bằng locked mint |
| Negative | Client ATA wrong owner | fail; state/balance giữ nguyên |
| Negative | Freelancer ATA wrong owner | fail; state/balance giữ nguyên |
| Negative | insufficient Client tokens | CPI fail; Invoice Pending, balance giữ nguyên |
| Negative | Config paused | fail; Invoice Pending, balance giữ nguyên |

### `tests/invoice/cancel-invoice.spec.ts` — 5 tests

| Loại | Test | Assertion chính |
|---|---|---|
| Happy | assigned Freelancer cancels Pending | status Cancelled, đúng một event |
| Negative | signer khác Freelancer | fail, status vẫn Pending |
| Negative | cancel Paid | fail, status vẫn Paid |
| Negative | cancel twice | lần hai fail |
| Negative | Config paused | fail, status vẫn Pending |

### `tests/invoice/close-invoice.spec.ts` — 4 tests

| Loại | Test | Assertion chính |
|---|---|---|
| Happy | close Cancelled và hoàn rent | account biến mất; balance Freelancer tăng đúng rent |
| Happy | close Paid | Invoice account biến mất |
| Negative | close Pending | fail; account vẫn tồn tại |
| Negative | người khác close | fail; account vẫn tồn tại |

### `tests/rates/rate-snapshot.spec.ts` — 10 tests

| Loại | Test | Assertion chính |
|---|---|---|
| Happy | configured Rate Authority publishes | mọi field, phép tính, bump và một event đúng |
| Negative | wrong Rate Authority | fail |
| Negative | đúng pubkey thiếu chữ ký | fail |
| Negative | một rate bằng 0 | cả hai biến thể fail |
| Negative | expiry không sau observation | fail |
| Negative | observation quá xa tương lai | fail |
| Negative | lifetime quá max | fail |
| Negative | Config paused | fail |
| Negative | duplicate rate ID | fail, snapshot bất biến |
| Negative | combined rate không vừa u64 | fail |

### `tests/offramp/request-offramp.spec.ts` — 12 tests

| Loại | Test | Assertion chính |
|---|---|---|
| Happy | transfer và tạo Pending record | record đủ field; token chuyển đúng; supply giữ nguyên; event đúng |
| Negative | token amount 0 | fail, không tạo record |
| Negative | expired snapshot | fail |
| Negative | Freelancer thiếu chữ ký | fail, không tạo record |
| Negative | Treasury Authority sai | fail |
| Negative | Config paused | fail; record không tạo; hai balance giữ nguyên |
| Negative | snapshot quá max age | fail |
| Negative | publisher không còn được cấu hình | fail |
| Negative | wrong mint/ATA ownership | ba biến thể đều fail |
| Negative | Freelancer thiếu token | CPI fail; record không tạo; Treasury balance giữ nguyên |
| Negative | overflow/dưới 1 VND | hai phép tính không hợp lệ đều fail |
| Negative | duplicate WithdrawalRecord | lần hai fail |

### `tests/offramp/record-offramp.spec.ts` — 7 tests

| Loại | Test | Assertion chính |
|---|---|---|
| Happy | Settlement Oracle complete Pending | Completed, có timestamp/event; token balance không đổi |
| Negative | sai Oracle | fail, record vẫn Pending |
| Negative | đúng pubkey thiếu chữ ký | fail, record vẫn Pending |
| Negative | record lần hai | fail, timestamp lần đầu giữ nguyên |
| Happy/security | failure rồi manual resolve | lưu đủ hai hash/timestamp/resolver và emit hai event audit |
| Negative | hash/signer/transition sai | fail, record giữ `FailedPendingReview` |
| Negative | Config paused | fail, status Pending và completed_at vẫn null |

Tổng cộng: `6 + 6 + 10 + 5 + 4 + 10 + 12 + 7 = 60` test. Fixture
Config còn chủ động thử signer giả trước init hợp lệ để chứng minh chống
front-run; assertion này chạy trong `before` dùng chung nên không tính thêm một
Mocha test.

## 9. Những lỗi đã gặp và cách sửa

Chỉ ghi nhận sự cố thực sự quan sát được; không coi negative test dự kiến là lỗi
triển khai.

| Nhóm | Điều thực tế xảy ra | Nguyên nhân gốc | Cách sửa/phòng tránh |
|---|---|---|---|
| Compile Rust | Không có compile failure trong các checkpoint cuối | Accounts/state được thêm đồng bộ vào registries | Chạy `cargo fmt` và `anchor build` ngay sau mỗi checkpoint |
| TypeScript account resolution | Không có lỗi TypeScript thực tế | Luôn build IDL/types trước khi typecheck test mới | Dùng `accountsStrict` và `anchor build` trước `tsc` |
| IDL | IDL trước Phase 10 có 9 instruction; sau build có 11 | IDL là artefact sinh từ Rust, không tự đổi nếu chưa build | Không sửa IDL tay; chạy `anchor build` sau thay đổi entrypoint/account |
| Numeric error ABI | Các lỗi rate từng được chèn trước lỗi Invoice cũ, làm dịch error code | Thứ tự enum quyết định mã Anchor | Giữ lỗi cũ nguyên thứ tự, nối lỗi mới phía sau |
| Event test fixture | Test cancel từng đọc transaction và nhận `null` | RPC chưa xác nhận signature trước `getTransaction` | Gọi `confirmTransaction(..., "confirmed")` trước parse logs |
| Signer/authority | Không có bug ngoài ý muốn; các lỗi signer là negative test chủ đích | Solana cần cả đúng pubkey lẫn chữ ký | Luôn dùng `Signer`, `address`/`has_one` và test thiếu chữ ký riêng |
| Account constraint | Không có lỗi triển khai ngoài ý muốn | Constraint được derive trong IDL và kiểm thử bằng account sai | Dùng `seeds`, stored bump, `has_one`, `address`, ATA constraints |
| Test fixture | Shared Config có thể bị kẹt paused nếu test fail giữa chừng | Test thay đổi global state | Restore Config trong `finally` |
| Upgrade authority test | `initialize_config` hợp lệ từng fail vì program được legacy validator preload immutable/default authority | `--bpf-program` không mô phỏng ProgramData có upgrade authority | Thêm `[test] upgradeable = true`; validator dùng `--upgradeable-program` với provider wallet làm authority |
| Rate fixture | Default Invoice rate từng trùng rate ID `1` của rate test | Fixture dùng chung namespace PDA | Đổi fixture rate ID sang `9_000_000` |
| Program identity | `anchor build` cảnh báo deploy keypair `2Tx2...` khác source `Cwua...` | Artifact keypair hiện tại không thuộc Program ID đã khai báo | Chưa tự ý `anchor keys sync`; phải chốt canonical ID trước devnet rồi cập nhật source/config/docs cùng một lần |
| CPI | Không có CPI bug ngoài ý muốn; insufficient funds được test chủ đích | Token Program từ chối nguồn thiếu balance | Chỉ đổi state sau CPI và kiểm tra rollback |

Node phát cảnh báo `MODULE_TYPELESS_PACKAGE_JSON`, nhưng đây không phải lỗi build,
typecheck hoặc test và chưa được sửa vì không thuộc contract Solana.

## 10. Bản đồ file thay đổi

| Đường dẫn | Loại | Trách nhiệm/thay đổi chính | Liên quan |
|---|---|---|---|
| `src/lib.rs` | Sửa | Xuất 11 entrypoint | toàn bộ instruction |
| `src/constants.rs` | Sửa | Withdrawal seed, fiat calculation scale | request off-ramp |
| `src/error.rs` | Sửa | Lỗi authority/state/rate/off-ramp; giữ mã cũ ổn định | toàn module |
| `src/events.rs` | Sửa | Event cancel và off-ramp | cancel/request/record |
| `src/instructions.rs` | Sửa | Đăng ký instruction module | 11 instruction |
| `src/state.rs` | Sửa | Đăng ký WithdrawalRecord | off-ramp |
| `src/instructions/cancel_invoice.rs` | Mới | Pending → Cancelled | Invoice |
| `src/instructions/close_invoice.rs` | Mới | Close Paid/Cancelled, hoàn rent | Invoice |
| `src/instructions/request_offramp.rs` | Mới | Rate validation, tính fiat, token CPI, tạo record | off-ramp |
| `src/instructions/record_offramp.rs` | Mới | Oracle hoàn tất record | off-ramp |
| `src/instructions/mark_offramp_failed.rs` | Mới | Oracle đưa failure vào review, lưu audit hash | off-ramp |
| `src/instructions/resolve_offramp.rs` | Mới | Admin resolve review có audit | off-ramp |
| `src/state/withdrawal_record.rs` | Mới | WithdrawalStatus/WithdrawalRecord | off-ramp |
| `tests/helpers/invoice.ts` | Mới | Invoice fixture/rejection helper | invoice tests |
| `tests/helpers/rate.ts` | Mới | Rate fixture | off-ramp tests |
| `tests/invoice/pay-invoice.spec.ts` | Sửa | Hoàn tất negative tests và atomic assertions | pay |
| `tests/invoice/cancel-invoice.spec.ts` | Mới | 5 cancel tests | cancel |
| `tests/invoice/close-invoice.spec.ts` | Mới | 4 close/rent tests | close |
| `tests/offramp/request-offramp.spec.ts` | Mới | 12 request tests | request |
| `tests/offramp/record-offramp.spec.ts` | Sửa | 7 completion/failure/resolution tests | off-ramp lifecycle |
| `docs/bao-cao-trien-khai-solana-module.md` | Sửa | Ghi Phase 10, 11 instruction, 60 test và safe-init runbook | tài liệu triển khai |
| `target/idl/invoice_payments.json`, `target/types/*` | Sinh lại | IDL/type mới từ `anchor build` | client contract |

Các đường dẫn `src/...` trong bảng thuộc
`programs/invoice_payments/src/...`.

## 11. Cách tự kiểm tra dự án

Chạy từ root repository:

```bash
cargo fmt --check
anchor build
node -e 'const i=require("./target/idl/invoice_payments.json"); console.log(i.instructions.map(x=>x.name))'
yarn tsc --noEmit
anchor test --validator legacy
```

Kết quả thực tế lần cuối:

- Format check: exit code 0.
- Anchor build: exit code 0.
- IDL: đủ 11 instruction được liệt kê ở mục 3.
- TypeScript typecheck: exit code 0.
- Anchor test với legacy validator/upgradable program: exit code 0,
  **60 passing**.

## 12. Tiến độ và phần còn lại

### Đã hoàn thành

- [x] 11 instruction on-chain.
- [x] 4 PDA/state chính.
- [x] Legacy SPL Token CPI cho payment và request off-ramp.
- [x] Rate fixed-point và checked arithmetic.
- [x] Invoice lifecycle: create, pay/cancel, close.
- [x] Withdrawal lifecycle: request, completion, failed pending review và
  audited manual resolution.
- [x] Invoice locked RateSnapshot/expiry và rotation-safe mint payment.
- [x] Config initialization chỉ bởi program upgrade authority.
- [x] Authority, signer, mint, ATA, status, paused và rollback tests.
- [x] IDL/types mới và 60 test local passing.

### Đang dở

- [ ] Chưa có công việc on-chain nào đang ở trạng thái code dở trong phạm vi đã
  yêu cầu; devnet chưa bắt đầu.

### Chưa thực hiện

- [ ] Deploy devnet và initialize Config devnet.
- [ ] Backend transaction builder/REST API.
- [ ] Database và indexer idempotent/backfill.
- [ ] Rate Collector và nguồn giá thật.
- [ ] Dashboard, Flutter checkout và admin settlement UI.
- [ ] E2E từ UI qua backend đến devnet.

### Thứ tự nên làm tiếp

1. Review key management và cấu hình authority cho devnet.
2. Thêm `[programs.devnet]`, cấp SOL cho deployer và deploy khi được cho phép.
3. Initialize Config devnet và lưu các public key cấu hình an toàn.
4. Xây indexer/database trước để có nguồn đọc trạng thái ổn định.
5. Xây Rate Collector và API transaction builder.
6. Tích hợp Web/Flutter, sau đó chạy E2E và security review devnet.

## Sai khác so với kiến trúc ban đầu

- File kiến trúc ban đầu ghi RateSnapshot và WithdrawalRecord “chưa triển khai”;
  code hiện tại đã có cả hai và phần tiến độ trong file đó đã được cập nhật.
- `WithdrawalRecord.treasury` trong code lưu **Treasury ATA**, không phải public
  key Treasury Authority. Authority vẫn nằm trong Config.
- Invoice không lưu `cancelled_at`; timestamp chỉ có trong `InvoiceCancelled`.
- Kiến trúc yêu cầu event được index đúng một lần, nhưng indexer chưa tồn tại.
  Test hiện chỉ chứng minh transaction phát đúng một event, không chứng minh
  database idempotency.
- Code cho phép close đúng hai trạng thái `Paid` và `Cancelled`; tài liệu kiến
  trúc cũ chỉ nói chung là “trạng thái cho phép”.
- `record_offramp` chỉ là xác nhận của Oracle; không có bằng chứng on-chain rằng
  ngân hàng đã chuyển VNĐ.

## 13. Tự kiểm tra kiến thức

### Câu hỏi

1. Vì sao Invoice PDA không chứa balance Mock USDC?
2. Khác biệt giữa signer và authority được lưu dưới dạng Pubkey là gì?
3. Seeds nào làm một Invoice ID chỉ duy nhất trong phạm vi một Freelancer?
4. Vì sao `pay_invoice` không nhận amount làm argument?
5. `transfer_checked` kiểm tra thêm điều gì so với việc chỉ thay đổi state?
6. Vì sao payment dùng `CpiContext::new`, không dùng `new_with_signer`?
7. ATA của Client và Freelancer được program xác minh owner/mint thế nào?
8. Vì sao một RateSnapshot không được cập nhật lại?
9. Rate Authority khác Settlement Oracle ở trách nhiệm nào?
10. Snapshot stale bị chặn bởi hai điều kiện thời gian nào?
11. Vì sao công thức tỷ giá dùng `u128` trung gian?
12. Điều gì đảm bảo frontend không thể tự khai `fiat_amount_vnd`?
13. Vì sao `record_offramp` không chuyển token lần hai?
14. Khi `request_offramp` CPI thất bại vì thiếu token, WithdrawalRecord ra sao?
15. Tại sao close Invoice trả rent đúng cho Freelancer mà không cho người khác?

### Đáp án

1. SPL token balance nằm trong Token Account/ATA do Token Program sở hữu; Invoice
   chỉ là business state của Invoice Program.
2. Pubkey chỉ là dữ liệu/address; `Signer<'info>` còn chứng minh private key
   tương ứng đã ký transaction.
3. `[b"invoice", freelancer_pubkey, invoice_id_le]`.
4. Amount được lấy từ Invoice đã lưu, tránh Client truyền số nhỏ hơn.
5. Token Program kiểm tra authority, mint, balance và decimals rồi mới chuyển.
6. Client là signer thật của transaction; program không ký thay bằng PDA seeds.
7. Anchor dùng `associated_token::authority`, `associated_token::mint` và
   `associated_token::token_program`.
8. Mỗi rate ID tạo PDA mới bằng `init`; lịch sử cần bất biến để kiểm toán.
9. Rate Authority công bố giá; Settlement Oracle xác nhận sự kiện trả fiat.
10. `current_time < expires_at` và `observed_at` không cũ hơn
    `max_rate_age_seconds`.
11. Tích hai `u64` có thể vượt `u64`; `u128` giữ kết quả trung gian trước khi
    chia và kiểm tra chuyển ngược.
12. Instruction chỉ nhận withdrawal ID và token amount; program đọc rate từ PDA
    và tự tính fiat amount.
13. Token đã chuyển vào Treasury trong `request_offramp`; `record_offramp` chỉ có
    Config, Oracle và WithdrawalRecord trong IDL.
14. Toàn transaction rollback: PDA không được tạo và balance Treasury không đổi.
15. Invoice có `has_one=freelancer`, Freelancer phải ký, và `close=freelancer`
    chỉ định chính account đó nhận lamports.
