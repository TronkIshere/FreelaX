# Đặc tả Solana API Gateway cho Spring Boot

Tài liệu này ánh xạ trực tiếp Program `invoice_payments` sang REST API cho
backend Spring Boot.

- Program ID: `CwuaAPrxYLK6avPUbMRBerBYt1apdNU829TDZmoAnhEf`
- Legacy Token Program: `TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA`
- Associated Token Program: `ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL`
- Số lượng instruction trong IDL hiện tại: 13
- Mọi `u64` được nhận/trả qua REST dưới dạng chuỗi thập phân.
- Mọi public key được nhận/trả dưới dạng Base58.
- Mọi hash `[u8; 32]` được nhận/trả dưới dạng chuỗi hex 64 ký tự.

## PHẦN 1: Bảng ánh xạ RPC và API Gateway

| # | Tên API Endpoint | Mục đích | Solana RPC Method / Program Instruction | Tham số đầu vào (Payload) | Quyền ký (Signer/PDA) | Dữ liệu trả về (Output) |
|---:|---|---|---|---|---|---|
| 1 | `GET /v1/solana/config` | Đọc cấu hình hiện tại | `getAccountInfo` và deserialize `Config` | Không có | Config PDA: `["config"]` | `ConfigResponse` |
| 2 | `GET /v1/solana/invoices/{freelancer}/{invoiceId}` | Đọc Invoice | `getAccountInfo` và deserialize `Invoice` | `freelancer`, `invoiceId` | Invoice PDA: `["invoice", freelancer, invoiceId_u64_le]` | `InvoiceResponse` |
| 3 | `GET /v1/solana/rates/{rateId}` | Đọc RateSnapshot | `getAccountInfo` và deserialize `RateSnapshot` | `rateId` | Rate PDA: `["rate", rateId_u64_le]` | `RateSnapshotResponse` |
| 4 | `GET /v1/solana/withdrawals/{freelancer}/{withdrawalId}` | Đọc WithdrawalRecord | `getAccountInfo` và deserialize `WithdrawalRecord` | `freelancer`, `withdrawalId` | Withdrawal PDA: `["withdrawal", freelancer, withdrawalId_u64_le]` | `WithdrawalResponse` |
| 5 | `GET /v1/solana/onramp-receipts/{client}/{purchaseId}` | Kiểm tra purchase đã được cấp token chưa | `getAccountInfo` và deserialize `MockOnrampReceipt` | `client`, `purchaseId` | Receipt PDA: `["mock_onramp", client, purchaseId_u64_le]` | `MockOnrampReceiptResponse` |
| 6 | `GET /v1/solana/token-balances/{owner}/{mint}` | Đọc balance ATA | Derive ATA, `getAccountInfo`, `getTokenAccountBalance` | `owner`, `mint` | ATA seeds: `[owner, TOKEN_PROGRAM_ID, mint]` dưới Associated Token Program | `TokenBalanceResponse` |
| 7 | `GET /v1/solana/transactions/{signature}` | Kiểm tra trạng thái giao dịch | `getSignatureStatuses`; tùy chọn `getTransaction` | `signature`, query `includeTransaction` | Không signer/PDA | `TransactionStatusResponse` |
| 8 | `POST /v1/solana/config/initialize` | Khởi tạo Config lần đầu | `initialize_config` | `admin`, `acceptedMint`, `treasuryAuthority`, `rateAuthority`, `oracleAuthority`, `maxRateAgeSeconds` | Signer/payer: `admin`, đồng thời phải là Program upgrade authority. Config PDA: `["config"]`. ProgramData PDA của Upgradeable Loader. Accounts: `admin`, `config`, `accepted_mint`, `program`, `program_data`, System Program | `TransactionOperationResponse`; derived `config`, `programData` |
| 9 | `PUT /v1/solana/config` | Cập nhật mint, authorities, rate age và paused | `update_config` | `admin`, `acceptedMint`, `treasuryAuthority`, `rateAuthority`, `oracleAuthority`, `maxRateAgeSeconds`, `paused` | Signer: Config `admin`. Config PDA: `["config"]`. Accounts: `admin`, `config`, `accepted_mint` | `TransactionOperationResponse` |
| 10 | `PUT /v1/solana/config/mock-onramp` | Cấu hình mock on-ramp | `configure_mock_onramp` | `admin`, `authority`, `maxAmount`, `enabled` | Signer: Config `admin`. Config PDA: `["config"]`. Accounts: `admin`, `config` | `TransactionOperationResponse`; event `MockOnrampConfigured` |
| 11 | `POST /v1/solana/mock-onramp/purchases` | Cấp Mock USDC cho Client sau khi backend demo xác nhận USD | `mock_onramp` | `onrampAuthority`, `client`, `purchaseId`, `usdAmountE6` | Signer/payer: Config `mock_onramp_authority`. Treasury authority PDA `["mock_onramp_treasury"]` ký CPI. Receipt PDA `["mock_onramp", client, purchaseId_u64_le]`. Derive treasury ATA và Client ATA. Accounts: authority, config, mint, treasury authority/ATA, client/ATA, receipt, Associated Token, Token và System Program | `TransactionOperationResponse`; derived receipt/ATAs; event `MockOnrampCompleted` |
| 12 | `POST /v1/solana/rates` | Công bố snapshot tỷ giá | `publish_rate` | `rateAuthority`, `rateId`, `usdcUsdE6`, `usdVndE6`, `observedAt`, `expiresAt`, `sourceHash` | Signer/payer: Config `rate_authority`. Rate PDA `["rate", rateId_u64_le]`. Accounts: authority, config, snapshot, System Program | `TransactionOperationResponse`; derived `rateSnapshot`; event `RatePublished` |
| 13 | `POST /v1/solana/invoices` | Tạo Invoice | `create_invoice` | `freelancer`, `invoiceId`, `client`, `amount`, `rateId`, `expiresAt` | Signer/payer: `freelancer`. Config PDA, Rate PDA và Invoice PDA `["invoice", freelancer, invoiceId_u64_le]`. Accounts: freelancer, config, rate snapshot, invoice, System Program | `TransactionOperationResponse`; event `InvoiceCreated` |
| 14 | `POST /v1/solana/invoices/{freelancer}/{invoiceId}/pay` | Thanh toán Invoice | `pay_invoice` | `client`, path `freelancer`, `invoiceId` | Signer: Invoice `client`. Mint lấy từ Invoice đã khóa, không lấy từ Config. Derive Client ATA và Freelancer ATA. Accounts: client, config, invoice, freelancer, locked mint, hai ATA, Token Program | `TransactionOperationResponse`; event `InvoicePaid` |
| 15 | `POST /v1/solana/invoices/{freelancer}/{invoiceId}/cancel` | Hủy Invoice Pending | `cancel_invoice` | path `freelancer`, `invoiceId` | Signer: Invoice `freelancer`. Accounts: freelancer, Config PDA, Invoice PDA | `TransactionOperationResponse`; event `InvoiceCancelled` |
| 16 | `DELETE /v1/solana/invoices/{freelancer}/{invoiceId}` | Đóng Invoice Paid/Cancelled và hoàn rent | `close_invoice` | path `freelancer`, `invoiceId` | Signer và rent recipient: Invoice `freelancer`. Accounts: freelancer, Invoice PDA | `TransactionOperationResponse` |
| 17 | `POST /v1/solana/withdrawals` | Chuyển token vào Treasury và tạo withdrawal | `request_offramp` | `freelancer`, `withdrawalId`, `rateId`, `tokenAmount` | Signer/payer: `freelancer`. Config, Rate và Withdrawal PDAs. Mint/Treasury Authority lấy từ Config. Derive Freelancer ATA và Treasury ATA. Accounts: freelancer, config, rate, mint, hai ATA/authorities, withdrawal, Token và System Program | `TransactionOperationResponse`; event `OfframpRequested` |
| 18 | `POST /v1/solana/withdrawals/{freelancer}/{withdrawalId}/complete` | Oracle xác nhận settlement thành công | `record_offramp` | `oracleAuthority`, path `freelancer`, `withdrawalId` | Signer: Config `oracle_authority`. Accounts: oracle, Config PDA, Withdrawal PDA | `TransactionOperationResponse`; event `OfframpCompleted` |
| 19 | `POST /v1/solana/withdrawals/{freelancer}/{withdrawalId}/fail` | Đưa withdrawal sang `FailedPendingReview` | `mark_offramp_failed` | `oracleAuthority`, `failureHash`, path `freelancer`, `withdrawalId` | Signer: Config `oracle_authority`. Accounts: oracle, Config PDA, Withdrawal PDA | `TransactionOperationResponse`; event `OfframpFailedPendingReview` |
| 20 | `POST /v1/solana/withdrawals/{freelancer}/{withdrawalId}/resolve` | Admin resolve withdrawal sau review | `resolve_offramp` | `admin`, `resolutionHash`, path `freelancer`, `withdrawalId` | Signer: Config `admin`. Accounts: admin, Config PDA, Withdrawal PDA | `TransactionOperationResponse`; event `OfframpResolved` |
| 21 | `POST /v1/solana/transactions/submit` | Gửi transaction đã được ví người dùng ký | `sendTransaction` | `transactionBase64`, `skipPreflight`, `preflightCommitment` | Transaction phải chứa đủ chữ ký trong `requiredSigners`. Gateway không ký thay Client/Freelancer | `SubmitTransactionResponse` |

## PHẦN 2: Interface Request/Response và cách dùng trong Spring Boot

### 2.1 Interface TypeScript trong đặc tả có ý nghĩa gì?

Các interface TypeScript bên dưới chỉ mô tả **hình dạng JSON** giữa frontend,
backend và Solana Gateway. Backend Spring Boot không cần chạy TypeScript hoặc
Node.js.

Ví dụ contract:

```typescript
interface CreateInvoiceRequest {
  freelancer: string;
  invoiceId: string;
  client: string;
  amount: string;
  rateId: string;
  expiresAt: string;
}
```

Spring Boot nhận cùng JSON bằng một Java DTO:

```java
public record CreateInvoiceRequest(
    String freelancer,
    String invoiceId,
    String client,
    String amount,
    String rateId,
    String expiresAt
) {}
```

### 2.2 Quy tắc ánh xạ TypeScript sang Java

| Contract | JSON | Java DTO nên dùng | Xử lý nội bộ |
|---|---|---|---|
| `PublicKeyString` | Chuỗi Base58 | `String` | Parse/validate thành public key của Solana SDK |
| `U64String` | Chuỗi số không âm | `String` | Parse bằng `BigInteger`; kiểm tra `0 <= n <= 2^64-1`; encode 8-byte little-endian |
| `I64String` | Chuỗi số có dấu | `String` hoặc `long` | Nếu dùng `String`, parse bằng `Long.parseLong`; encode 8-byte little-endian signed |
| `Hash32Hex` | Chuỗi hex 64 ký tự | `String` | Decode thành `byte[32]`; từ chối all-zero khi instruction yêu cầu non-zero |
| `boolean` | JSON boolean | `boolean`/`Boolean` | Không nhận chuỗi `"true"` |
| `TransactionSignature` | Chuỗi Base58 | `String` | Dùng để gọi `getSignatureStatuses`/`getTransaction` |
| `null` | JSON null | nullable field | Có thể dùng `Optional` ở service; DTO thường để nullable cho Jackson |

Không dùng Java `double`, `float` hoặc JSON number cho amount/rate. Ví dụ 100
USDC phải gửi là `"100000000"`, không gửi `100.0`.

### 2.3 Hai luồng ký transaction backend phải tách riêng

#### A. Backend có quyền giữ signer

Áp dụng cho các signer vận hành được lưu trong KMS/HSM hoặc secret manager:

- Program upgrade authority/Admin: `initialize_config`.
- Admin: `update_config`, `configure_mock_onramp`, `resolve_offramp`.
- Mock On-ramp Authority: `mock_onramp`.
- Rate Authority: `publish_rate`.
- Settlement Oracle: `record_offramp`, `mark_offramp_failed`.

Luồng xử lý:

```text
REST request
  → validate payload và Config on-chain
  → derive PDA/ATA
  → build instruction/transaction
  → KMS/HSM ký bằng authority tương ứng
  → sendTransaction
  → trả signature
```

Với nhóm này, `mode = "send"` phù hợp nếu backend thực sự quản lý signer.

#### B. Ví người dùng phải ký

Áp dụng cho:

- Freelancer: `create_invoice`, `cancel_invoice`, `close_invoice`,
  `request_offramp`.
- Client: `pay_invoice`.

Luồng xử lý:

```text
REST request
  → backend derive PDA/ATA và build transaction
  → trả transactionBase64 + requiredSigners
  → frontend/wallet deserialize và ký
  → gửi transactionBase64 đã ký tới /transactions/submit
  → backend gọi sendTransaction
```

Backend không được dùng private key của Client/Freelancer để ký thay. Với nhóm
này, `mode = "build"` là lựa chọn mặc định.

### 2.4 TypeScript contract đầy đủ

```typescript
export type PublicKeyString = string;
export type TransactionSignature = string;
export type U64String = string;
export type I64String = string;
export type Hash32Hex = string;

export type Commitment = "processed" | "confirmed" | "finalized";
export type TransactionExecutionMode = "build" | "send";

export interface TransactionOptions {
  mode: TransactionExecutionMode;
  commitment?: Commitment;
  skipPreflight?: boolean;
}

export interface GetConfigRequest {
  commitment?: Commitment;
}

export interface GetInvoiceRequest {
  freelancer: PublicKeyString;
  invoiceId: U64String;
  commitment?: Commitment;
}

export interface GetRateSnapshotRequest {
  rateId: U64String;
  commitment?: Commitment;
}

export interface GetWithdrawalRequest {
  freelancer: PublicKeyString;
  withdrawalId: U64String;
  commitment?: Commitment;
}

export interface GetMockOnrampReceiptRequest {
  client: PublicKeyString;
  purchaseId: U64String;
  commitment?: Commitment;
}

export interface GetTokenBalanceRequest {
  owner: PublicKeyString;
  mint: PublicKeyString;
  commitment?: Commitment;
}

export interface GetTransactionStatusRequest {
  signature: TransactionSignature;
  includeTransaction?: boolean;
  commitment?: Commitment;
}

export interface DerivedAccounts {
  config?: PublicKeyString;
  programData?: PublicKeyString;
  invoice?: PublicKeyString;
  rateSnapshot?: PublicKeyString;
  withdrawalRecord?: PublicKeyString;
  mockOnrampReceipt?: PublicKeyString;
  mockOnrampTreasuryAuthority?: PublicKeyString;
  mockOnrampTreasuryAta?: PublicKeyString;
  clientAta?: PublicKeyString;
  freelancerAta?: PublicKeyString;
  treasuryAta?: PublicKeyString;
}

export interface TransactionBuildResponse {
  status: "requires_signature";
  instruction: string;
  transactionBase64: string;
  feePayer: PublicKeyString;
  recentBlockhash: string;
  lastValidBlockHeight: number;
  requiredSigners: PublicKeyString[];
  derivedAccounts: DerivedAccounts;
}

export interface TransactionSubmittedResponse {
  status: "submitted";
  instruction: string;
  signature: TransactionSignature;
  derivedAccounts: DerivedAccounts;
}

export type TransactionOperationResponse =
  | TransactionBuildResponse
  | TransactionSubmittedResponse;

export interface InitializeConfigRequest extends TransactionOptions {
  admin: PublicKeyString;
  acceptedMint: PublicKeyString;
  treasuryAuthority: PublicKeyString;
  rateAuthority: PublicKeyString;
  oracleAuthority: PublicKeyString;
  maxRateAgeSeconds: I64String;
}

export interface UpdateConfigRequest extends TransactionOptions {
  admin: PublicKeyString;
  acceptedMint: PublicKeyString;
  treasuryAuthority: PublicKeyString;
  rateAuthority: PublicKeyString;
  oracleAuthority: PublicKeyString;
  maxRateAgeSeconds: I64String;
  paused: boolean;
}

export interface ConfigureMockOnrampRequest extends TransactionOptions {
  admin: PublicKeyString;
  authority: PublicKeyString;
  maxAmount: U64String;
  enabled: boolean;
}

export interface MockOnrampRequest extends TransactionOptions {
  onrampAuthority: PublicKeyString;
  client: PublicKeyString;
  purchaseId: U64String;
  usdAmountE6: U64String;
}

export interface PublishRateRequest extends TransactionOptions {
  rateAuthority: PublicKeyString;
  rateId: U64String;
  usdcUsdE6: U64String;
  usdVndE6: U64String;
  observedAt: I64String;
  expiresAt: I64String;
  sourceHash: Hash32Hex;
}

export interface CreateInvoiceRequest extends TransactionOptions {
  freelancer: PublicKeyString;
  invoiceId: U64String;
  client: PublicKeyString;
  amount: U64String;
  rateId: U64String;
  expiresAt: I64String;
}

export interface PayInvoiceRequest extends TransactionOptions {
  client: PublicKeyString;
  freelancer: PublicKeyString;
  invoiceId: U64String;
}

export interface CancelInvoiceRequest extends TransactionOptions {
  freelancer: PublicKeyString;
  invoiceId: U64String;
}

export interface CloseInvoiceRequest extends TransactionOptions {
  freelancer: PublicKeyString;
  invoiceId: U64String;
}

export interface RequestOfframpRequest extends TransactionOptions {
  freelancer: PublicKeyString;
  withdrawalId: U64String;
  rateId: U64String;
  tokenAmount: U64String;
}

export interface CompleteOfframpRequest extends TransactionOptions {
  oracleAuthority: PublicKeyString;
  freelancer: PublicKeyString;
  withdrawalId: U64String;
}

export interface MarkOfframpFailedRequest extends TransactionOptions {
  oracleAuthority: PublicKeyString;
  freelancer: PublicKeyString;
  withdrawalId: U64String;
  failureHash: Hash32Hex;
}

export interface ResolveOfframpRequest extends TransactionOptions {
  admin: PublicKeyString;
  freelancer: PublicKeyString;
  withdrawalId: U64String;
  resolutionHash: Hash32Hex;
}

export interface SubmitSignedTransactionRequest {
  transactionBase64: string;
  skipPreflight?: boolean;
  preflightCommitment?: Commitment;
}

export interface ConfigDto {
  address: PublicKeyString;
  admin: PublicKeyString;
  acceptedMint: PublicKeyString;
  treasuryAuthority: PublicKeyString;
  rateAuthority: PublicKeyString;
  oracleAuthority: PublicKeyString;
  maxRateAgeSeconds: I64String;
  mockOnrampAuthority: PublicKeyString;
  maxMockOnrampAmount: U64String;
  mockOnrampEnabled: boolean;
  paused: boolean;
  bump: number;
}

export type InvoiceStatus = "Pending" | "Paid" | "Cancelled";

export interface InvoiceDto {
  address: PublicKeyString;
  invoiceId: U64String;
  freelancer: PublicKeyString;
  client: PublicKeyString;
  amount: U64String;
  mint: PublicKeyString;
  rateSnapshot: PublicKeyString;
  expiresAt: I64String;
  status: InvoiceStatus;
  createdAt: I64String;
  paidAt: I64String | null;
  bump: number;
}

export interface RateSnapshotDto {
  address: PublicKeyString;
  rateId: U64String;
  usdcUsdE6: U64String;
  usdVndE6: U64String;
  usdcVndE6: U64String;
  observedAt: I64String;
  expiresAt: I64String;
  sourceHash: Hash32Hex;
  publisher: PublicKeyString;
  bump: number;
}

export type WithdrawalStatus =
  | "Pending"
  | "FailedPendingReview"
  | "Completed";

export interface WithdrawalDto {
  address: PublicKeyString;
  withdrawalId: U64String;
  freelancer: PublicKeyString;
  tokenAmount: U64String;
  mint: PublicKeyString;
  treasury: PublicKeyString;
  rateSnapshot: PublicKeyString;
  fiatAmountVnd: U64String;
  status: WithdrawalStatus;
  requestedAt: I64String;
  completedAt: I64String | null;
  failureHash: Hash32Hex | null;
  failedAt: I64String | null;
  resolutionHash: Hash32Hex | null;
  resolvedAt: I64String | null;
  resolvedBy: PublicKeyString | null;
  bump: number;
}

export interface MockOnrampReceiptDto {
  address: PublicKeyString;
  purchaseId: U64String;
  client: PublicKeyString;
  clientAta: PublicKeyString;
  mint: PublicKeyString;
  treasury: PublicKeyString;
  usdAmountE6: U64String;
  tokenAmount: U64String;
  authority: PublicKeyString;
  completedAt: I64String;
  bump: number;
}

export interface AccountResponse<T> {
  exists: boolean;
  data: T | null;
}

export type ConfigResponse = AccountResponse<ConfigDto>;
export type InvoiceResponse = AccountResponse<InvoiceDto>;
export type RateSnapshotResponse = AccountResponse<RateSnapshotDto>;
export type WithdrawalResponse = AccountResponse<WithdrawalDto>;
export type MockOnrampReceiptResponse =
  AccountResponse<MockOnrampReceiptDto>;

export interface TokenBalanceResponse {
  exists: boolean;
  ata: PublicKeyString;
  owner: PublicKeyString;
  mint: PublicKeyString;
  amount: U64String;
  decimals: number;
  uiAmountString: string;
}

export interface TransactionErrorDto {
  raw: unknown;
  anchorCode?: number;
  anchorName?: string;
  message?: string;
}

export interface TransactionStatusResponse {
  signature: TransactionSignature;
  found: boolean;
  slot: U64String | null;
  confirmations: number | null;
  confirmationStatus: Commitment | null;
  error: TransactionErrorDto | null;
  blockTime: I64String | null;
  logs?: string[];
}

export interface SubmitTransactionResponse {
  signature: TransactionSignature;
}
```

### 2.5 Java DTO khuyến nghị cho Spring Boot

Các record sau là ánh xạ trực tiếp của contract chính. Có thể đổi `record`
thành class nếu phiên bản Java hoặc convention dự án yêu cầu.

```java
import java.util.List;
import java.util.Map;

public enum Commitment {
    processed, confirmed, finalized
}

public enum TransactionExecutionMode {
    build, send
}

public record TransactionOptions(
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record InitializeConfigRequest(
    String admin,
    String acceptedMint,
    String treasuryAuthority,
    String rateAuthority,
    String oracleAuthority,
    String maxRateAgeSeconds,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record UpdateConfigRequest(
    String admin,
    String acceptedMint,
    String treasuryAuthority,
    String rateAuthority,
    String oracleAuthority,
    String maxRateAgeSeconds,
    boolean paused,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record ConfigureMockOnrampRequest(
    String admin,
    String authority,
    String maxAmount,
    boolean enabled,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record MockOnrampRequest(
    String onrampAuthority,
    String client,
    String purchaseId,
    String usdAmountE6,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record PublishRateRequest(
    String rateAuthority,
    String rateId,
    String usdcUsdE6,
    String usdVndE6,
    String observedAt,
    String expiresAt,
    String sourceHash,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record CreateInvoiceRequest(
    String freelancer,
    String invoiceId,
    String client,
    String amount,
    String rateId,
    String expiresAt,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record PayInvoiceRequest(
    String client,
    String freelancer,
    String invoiceId,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record InvoiceActionRequest(
    String freelancer,
    String invoiceId,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record RequestOfframpRequest(
    String freelancer,
    String withdrawalId,
    String rateId,
    String tokenAmount,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record CompleteOfframpRequest(
    String oracleAuthority,
    String freelancer,
    String withdrawalId,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record MarkOfframpFailedRequest(
    String oracleAuthority,
    String freelancer,
    String withdrawalId,
    String failureHash,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record ResolveOfframpRequest(
    String admin,
    String freelancer,
    String withdrawalId,
    String resolutionHash,
    TransactionExecutionMode mode,
    Commitment commitment,
    Boolean skipPreflight
) {}

public record SubmitSignedTransactionRequest(
    String transactionBase64,
    Boolean skipPreflight,
    Commitment preflightCommitment
) {}

public record DerivedAccounts(
    String config,
    String programData,
    String invoice,
    String rateSnapshot,
    String withdrawalRecord,
    String mockOnrampReceipt,
    String mockOnrampTreasuryAuthority,
    String mockOnrampTreasuryAta,
    String clientAta,
    String freelancerAta,
    String treasuryAta
) {}

public record TransactionBuildResponse(
    String status,
    String instruction,
    String transactionBase64,
    String feePayer,
    String recentBlockhash,
    long lastValidBlockHeight,
    List<String> requiredSigners,
    DerivedAccounts derivedAccounts
) {}

public record TransactionSubmittedResponse(
    String status,
    String instruction,
    String signature,
    DerivedAccounts derivedAccounts
) {}

public record AccountResponse<T>(
    boolean exists,
    T data
) {}

public record ConfigDto(
    String address,
    String admin,
    String acceptedMint,
    String treasuryAuthority,
    String rateAuthority,
    String oracleAuthority,
    String maxRateAgeSeconds,
    String mockOnrampAuthority,
    String maxMockOnrampAmount,
    boolean mockOnrampEnabled,
    boolean paused,
    int bump
) {}

public record InvoiceDto(
    String address,
    String invoiceId,
    String freelancer,
    String client,
    String amount,
    String mint,
    String rateSnapshot,
    String expiresAt,
    String status,
    String createdAt,
    String paidAt,
    int bump
) {}

public record RateSnapshotDto(
    String address,
    String rateId,
    String usdcUsdE6,
    String usdVndE6,
    String usdcVndE6,
    String observedAt,
    String expiresAt,
    String sourceHash,
    String publisher,
    int bump
) {}

public record WithdrawalDto(
    String address,
    String withdrawalId,
    String freelancer,
    String tokenAmount,
    String mint,
    String treasury,
    String rateSnapshot,
    String fiatAmountVnd,
    String status,
    String requestedAt,
    String completedAt,
    String failureHash,
    String failedAt,
    String resolutionHash,
    String resolvedAt,
    String resolvedBy,
    int bump
) {}

public record MockOnrampReceiptDto(
    String address,
    String purchaseId,
    String client,
    String clientAta,
    String mint,
    String treasury,
    String usdAmountE6,
    String tokenAmount,
    String authority,
    String completedAt,
    int bump
) {}

public record TokenBalanceResponse(
    boolean exists,
    String ata,
    String owner,
    String mint,
    String amount,
    int decimals,
    String uiAmountString
) {}

public record TransactionStatusResponse(
    String signature,
    boolean found,
    String slot,
    Integer confirmations,
    String confirmationStatus,
    Map<String, Object> error,
    String blockTime,
    List<String> logs
) {}

public record SubmitTransactionResponse(String signature) {}
```

`TransactionOperationResponse` là union type trong TypeScript. Java không có
union tương đương trực tiếp; Spring Boot có thể trả một trong hai DTO sau:

- `TransactionBuildResponse` khi `status = "requires_signature"`.
- `TransactionSubmittedResponse` khi `status = "submitted"`.

Controller có thể khai báo `ResponseEntity<?>`, hoặc tạo sealed interface nếu
dự án dùng Java 17+.

### 2.6 Ví dụ JSON quan trọng

#### Backend mock on-ramp tự ký

Request:

```json
{
  "onrampAuthority": "ConfiguredOnrampAuthorityBase58",
  "client": "ClientWalletBase58",
  "purchaseId": "10001",
  "usdAmountE6": "100000000",
  "mode": "send",
  "commitment": "confirmed",
  "skipPreflight": false
}
```

Response:

```json
{
  "status": "submitted",
  "instruction": "mock_onramp",
  "signature": "TransactionSignatureBase58",
  "derivedAccounts": {
    "config": "ConfigPdaBase58",
    "mockOnrampReceipt": "ReceiptPdaBase58",
    "mockOnrampTreasuryAuthority": "TreasuryAuthorityPdaBase58",
    "mockOnrampTreasuryAta": "TreasuryAtaBase58",
    "clientAta": "ClientAtaBase58"
  }
}
```

#### Backend build `pay_invoice` để Client ký

Request:

```json
{
  "client": "ClientWalletBase58",
  "freelancer": "FreelancerWalletBase58",
  "invoiceId": "42",
  "mode": "build",
  "commitment": "confirmed",
  "skipPreflight": false
}
```

Response:

```json
{
  "status": "requires_signature",
  "instruction": "pay_invoice",
  "transactionBase64": "BASE64_SERIALIZED_TRANSACTION",
  "feePayer": "ClientWalletBase58",
  "recentBlockhash": "RecentBlockhashBase58",
  "lastValidBlockHeight": 123456,
  "requiredSigners": ["ClientWalletBase58"],
  "derivedAccounts": {
    "config": "ConfigPdaBase58",
    "invoice": "InvoicePdaBase58",
    "clientAta": "ClientAtaBase58",
    "freelancerAta": "FreelancerAtaBase58"
  }
}
```

Sau khi ví ký, frontend gửi transaction đã ký:

```json
{
  "transactionBase64": "BASE64_SIGNED_TRANSACTION",
  "skipPreflight": false,
  "preflightCommitment": "confirmed"
}
```

### 2.7 Quy tắc backend cần enforce trước khi gọi RPC

1. Không nhận amount/rate dưới dạng số thực.
2. Không nhận private key từ request REST.
3. Public key signer trong payload phải khớp signer được yêu cầu bởi Config
   hoặc account on-chain.
4. Với `pay_invoice`, lấy mint từ `Invoice.mint`; không lấy mint hiện tại từ
   Config vì Invoice đã khóa mint khi tạo.
5. Với `mock_onramp`, dùng `purchaseId` duy nhất trong phạm vi một Client. Nếu
   receipt đã tồn tại, trả kết quả idempotent hoặc HTTP `409`; không build giao
   dịch mới.
6. Với `sourceHash`, `failureHash`, `resolutionHash`, validate đúng 32 byte sau
   khi decode.
7. Không coi HTTP thành công là settlement hoàn tất. Lưu signature rồi kiểm tra
   `getSignatureStatuses`/`getTransaction` tới commitment yêu cầu.
8. Không tự derive account bằng số JavaScript/Java bị overflow; `u64` phải đi
   qua `BigInteger` rồi encode little-endian đúng 8 byte.
9. Transaction build cho ví người dùng phải có blockhash expiry; nếu quá
   `lastValidBlockHeight`, build lại transaction, không tái sử dụng payload cũ.
10. Không log private key, raw KMS signature material hoặc signed transaction
    nếu log có thể được truy cập ngoài nhóm vận hành.
