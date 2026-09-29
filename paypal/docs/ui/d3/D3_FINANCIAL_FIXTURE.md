# D3.1 — Fixed financial evidence fixture

> **DESIGN FIXTURE ONLY · SIMULATED · DEVNET · NOT PRODUCTION FINANCIAL DATA**

This is one deterministic study of a possible post-approval snapshot. Values, timestamps, IDs, parties, and statuses are illustrative rather than a live job or a promise that every deployment reaches each stage. Reuse these values unchanged in every D3.1 artifact. No fixture signature or identifier is a real secret, real transaction, or live explorer proof.

## Work and approval boundary

| Field | Fixed value |
| --- | --- |
| Job | **Build Solana Payment Infrastructure**; `jobId=FIXTURE-JOB-001` |
| Client | North Studio (`CLIENT`) |
| Freelancer | Assigned Freelancer (`FREELANCER`; display label only) |
| Budget / checkout source | **500.00 USD** |
| Latest work | `APPROVED` submission V2 |
| Job state | `COMPLETED` |
| Payment-backend checkout | `checkoutOrderStatus=CAPTURED`; `checkoutOrderId=FIXTURE-CHECKOUT-001` |
| Financial handoff | `payoutService.settle(job)` after successful capture and work approval |

Checkout `CAPTURED` and the separate `ClientPaymentStatus.CONFIRMED` below refer to different systems. Work completion is not bank receipt.

## Payout and VND study arithmetic

| Field | Fixed value | Meaning |
| --- | --- | --- |
| `simulation` | `true` | Simulation banner stays visible |
| `network` | `devnet` | DEVNET evidence, not production settlement |
| `amountUsd` | 500.00 USD | Source job amount |
| `onRampFeeUsd` | 5.00 USD | Design fixture fee |
| `amountUsdNet` | 495.00 USD | 500.00 − 5.00 |
| `amountUsdcReceived` | 495.00 Mock USDC | Illustrative mock on-ramp result |
| USDC/VND study rate | 25,000 VND per USDC | Illustrative quote; response exposes source but not the numeric rate in `JobPaymentStatusResponse` |
| `usdcToVndRateSource` | `LIVE_COINGECKO` | Fixture source selection; not a guaranteed live quote |
| `amountVndBeforeOffRampFee` | 12,375,000 VND | 495 × 25,000 |
| `offRampFeeVnd` | 75,000 VND | Design fixture fee |
| `estimatedAmountVnd` | **12,300,000 VND dự kiến** | 12,375,000 − 75,000; no bank receipt |
| Bank destination | `TECHCOMBANK` · `******6789` · `ASSIGNED FREELANCER` | Masked account response example; no raw account number |
| `offRampReference` | `FIXTURE-OFFRAMP-001` | Study reference only |

The numeric USDC/VND rate is a fixture calculation aid, not a declared `JobPaymentStatusResponse` field. The service record stores the rate; the public response listed in this audit exposes the gross, fee, estimate, and rate source. Do not invent a live rate readback.

## Separate tax conversion

| Field | Fixed value | Meaning |
| --- | --- | --- |
| USD/VND tax study rate | 25,200 VND per USD | Separate from the USDC/VND payout quote |
| `taxRateSource` / certificate `rateSource` | `LIVE_OPEN_ER_API` | Fixture happy-path source |
| `taxableAmountVnd` / `taxableIncomeVnd` | **12,600,000 VND** | 500 × 25,200 |
| `taxWithheldVnd` | **null / not supplied** | No inferred statutory percentage or zero-withholding claim |
| Job `taxExportStatus` | `SUCCESS` | Job-level export result only |
| Certificate `status` / `statusLabel` | `DRAFT` / **Đã lập chứng từ, chưa phát hành** | Record exists, not issued |
| Certificate number, symbol, lookup code | Not available in this DRAFT fixture | Do not fabricate issued-document proof |

The job-level export can be `SUCCESS` while the certificate remains `DRAFT`. A later `SIGNED`, `SUBMITTED`, or `ACCEPTED` state requires its own response.

## Eight-stage status sequence and final snapshot

| Stage | Fixture status at that stage | What remains valid |
| --- | --- | --- |
| 01 Work approval / checkout | `JobStatus.COMPLETED`, V2 `APPROVED`, checkout `CAPTURED` | Work/checkout confirmed |
| 02 Payout initiated | payout record created; `simulated=true`; stage statuses `NOT_STARTED` | Checkout captured, payout not yet advanced |
| 03 Mock on-ramp | `OnRampStatus.NOT_STARTED → SUBMITTED → CONFIRMED` | 495 Mock USDC is fixture evidence |
| 04 Client payment evidence | `ClientPaymentStatus.NOT_STARTED → RATE_SUBMITTED → RATE_CONFIRMED → INVOICE_SUBMITTED → INVOICE_CREATED → PAYMENT_SUBMITTED → CONFIRMED` | Separate rate, invoice, payment records |
| 05 On-chain off-ramp | `OnChainOffRampStatus.NOT_STARTED → REQUEST_SUBMITTED → CONFIRMED` | Withdrawal request evidence only |
| 06 Simulated VND payout | `OffRampStatus.NOT_STARTED → SIMULATED` | 12,300,000 VND estimated; bank transfer **not real** |
| 07 Completion evidence | `OffRampStatus.SIMULATED → COMPLETION_SUBMITTED → COMPLETED` | On-chain/simulated record complete, not bank receipt |
| 08 Tax/certificate | `TaxExportStatus.SUCCESS`; `TaxCertificateStatus.DRAFT` | Taxable amount recorded; certificate not issued |

At the final snapshot: on-ramp `CONFIRMED`, Client payment `CONFIRMED`, on-chain off-ramp `CONFIRMED`, off-ramp `COMPLETED`, job tax export `SUCCESS`, certificate `DRAFT`. These independent values must never be replaced by a single “payment complete” state.

## Design fixture evidence identifiers

All values in this table are **DESIGN FIXTURE EVIDENCE**, deliberately non-live. The actual response may omit any unavailable value. Do not construct a live Explorer link from these placeholders.

| Event | Study identifier / proof |
| --- | --- |
| Mock on-ramp | `onRampPurchaseId=FIXTURE-PURCHASE-001`; `onRampTransactionSignature=FIXTURE-SIG-ONRAMP-001`; `onRampReceiptPda=FIXTURE-PDA-RECEIPT-001` |
| Rate snapshot | `rateId=FIXTURE-RATE-001`; `rateTransactionSignature=FIXTURE-SIG-RATE-001`; `rateSnapshotPda=FIXTURE-PDA-RATE-001` |
| Invoice | `invoiceId=FIXTURE-INVOICE-001`; `invoiceTransactionSignature=FIXTURE-SIG-INVOICE-001`; `invoicePda=FIXTURE-PDA-INVOICE-001` |
| Client payment | `paymentTransactionSignature=FIXTURE-SIG-PAYMENT-001` |
| Withdrawal request | `withdrawalId=FIXTURE-WITHDRAWAL-001`; `withdrawalTransactionSignature=FIXTURE-SIG-WITHDRAWAL-001`; `withdrawalPda=FIXTURE-PDA-WITHDRAWAL-001` |
| Completion record | `offRampCompletionSignature=FIXTURE-SIG-COMPLETION-001` |
| Tax record | `taxRecordId=FIXTURE-TAX-001`; certificate `DRAFT`; no issued certificate number |

These are separate event proofs. A real DEVNET explorer URL, when returned by the backend for a real signature, belongs in technical details and never implies a fiat bank transfer.
