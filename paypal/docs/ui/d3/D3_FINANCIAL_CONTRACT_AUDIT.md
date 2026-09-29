# D3.1 — Financial lifecycle contract audit

Source baseline: `origin/master` `2bd28397244725744fc5056aa691705705ff4389`. Read-only audit for a desktop design study; it does not authorize API or Flutter changes.

## A. Files inspected

All paths below are under `marketplace-backend/src/main/java/com/marketplace/backend/`:

- `controller/JobController.java`, `controller/TaxCertificateController.java`
- `service/impl/JobServiceImpl.java`, `PayoutServiceImpl.java`, `ClientPaymentServiceImpl.java`, `OnChainOffRampServiceImpl.java`, `VndPayoutServiceImpl.java`, `TaxCertificateServiceImpl.java`
- `dto/response/job/JobPaymentStatusResponse.java`, `dto/response/tax/TaxCertificateResponse.java`
- `entity/FreelancerPayoutRecord.java`, `OnRampStatus.java`, `ClientPaymentStatus.java`, `OnChainOffRampStatus.java`, `OffRampStatus.java`, `TaxExportStatus.java`, `TaxCertificateStatus.java`, `ExchangeRateSource.java`

## B. Approval and checkout boundary

`JobServiceImpl.approve` requires the Client owner, `SUBMITTED_FOR_REVIEW`, a checkout order, and a latest `SUBMITTED` submission. It calls payment-backend checkout capture and requires the returned checkout status **`CAPTURED`** before setting `JobStatus.COMPLETED` and the latest submission to `APPROVED`. It then calls `payoutService.settle(job)`. A completed job is the work handoff, not proof of finished payout, bank transfer, or certificate. `checkoutOrderStatus` is retrieved from the payment backend for the participant payment-status response; it is not `ClientPaymentStatus`.

## C. Payout record lifecycle and four tracks

`PayoutServiceImpl.settle` is idempotent by job ID when a record exists. It creates `FreelancerPayoutRecord` with `simulated=true`, source USD/fee/net amounts, network, on-ramp identifiers, separate tax conversion, and initial stage statuses. A missing wallet/configuration or provider failure can leave later stages absent or failed while checkout capture and work approval remain valid. Reconciliation advances stage records. Tax export can occur at simulated payout **or on some payout failure paths**; never imply it proves payout success.

| Track | Source field/object | Meaning |
| --- | --- | --- |
| Checkout payment | `checkoutOrderStatus` from payment backend | Client checkout capture at approval |
| Payout pipeline | `FreelancerPayoutRecord`, `onRampStatus`, `offRampStatus` | Post-approval simulated settlement progression |
| On-chain evidence | `clientPaymentStatus`, `onChainOffRampStatus`, stage signatures/IDs | Mock on-ramp, rate, invoice, payment, withdrawal, completion records; separate events |
| Tax/certificate | job `taxExportStatus`, `TaxCertificateResponse.status` | Job-level export result and independent certificate lifecycle |

## D–G. Exact payout enums

| Field | Exact values | Interpretation |
| --- | --- | --- |
| `OnRampStatus` | `NOT_STARTED`, `SUBMITTED`, `CONFIRMED`, `FAILED` | Mock on-ramp; `CONFIRMED` can expose Mock USDC, not fiat purchase |
| `ClientPaymentStatus` | `NOT_STARTED`, `RATE_SUBMITTED`, `RATE_CONFIRMED`, `INVOICE_SUBMITTED`, `INVOICE_CREATED`, `PAYMENT_SUBMITTED`, `CONFIRMED`, `FAILED` | Internal/on-chain Client payment evidence, **not checkout capture** |
| `OnChainOffRampStatus` | `NOT_STARTED`, `REQUEST_SUBMITTED`, `CONFIRMED`, `FAILED` | Withdrawal/off-ramp request record |
| `OffRampStatus` | `NOT_STARTED`, `SIMULATED`, `COMPLETION_SUBMITTED`, `COMPLETED`, `FAILED` | VND simulation and on-chain completion evidence; even `COMPLETED` does **not** certify a real bank transfer |

`ClientPaymentServiceImpl` publishes a rate, creates an invoice, and pays it as separate steps. `OnChainOffRampServiceImpl` requests and confirms a withdrawal record. `VndPayoutServiceImpl` first calculates an estimated VND result and stores a bank destination, then advances `SIMULATED → COMPLETION_SUBMITTED → COMPLETED`. The payout notification explicitly says there is no real bank transfer. Stage errors (`clientPaymentError`, `onChainOffRampError`, `offRampError`) remain stage-specific.

## H. Participant payment response as evidence levels

`GET /api/v1/marketplace/jobs/{jobId}/payment-status` is a participant read in `JobController`/`JobServiceImpl`; it requires a checkout order and fetches its current status. No payout record means payout fields can be null, not zero or success.

| Level | Fields to present |
| --- | --- |
| 1 — human summary | job/title context from the job read, `checkoutOrderStatus`, `simulation`, `onRampStatus`, `clientPaymentStatus`, `onChainOffRampStatus`, `offRampStatus`, `estimatedAmountVnd`, `taxExportStatus`, certificate `statusLabel` when available |
| 2 — supporting evidence | `network`, `amountUsdcReceived`, `amountVndBeforeOffRampFee`, `offRampFeeVnd`, masked `payoutBankCode` / `payoutBankAccountNumber` / holder, `simulatedPayoutAt`, submission/confirmation timestamps, `usdcToVndRateSource`, `taxableAmountVnd`, `taxRateSource`, stage errors |
| 3 — technical proof | `checkoutOrderId`, `onRampPurchaseId`, `onRampTransactionSignature`, `onRampReceiptPda`, `rateId`, `rateSnapshotPda`, `rateTransactionSignature`, `invoiceId`, `invoicePda`, `invoiceTransactionSignature`, `paymentTransactionSignature`, `withdrawalId`, `withdrawalPda`, treasury keys, `withdrawalTransactionSignature`, `offRampCompletionSignature`, stage explorer URLs |

The backend masks `payoutBankAccountNumber` before returning it. Explorer URLs are generated for signatures only when `network=devnet`. A URL is technical evidence of a stage, not proof of bank settlement. The on-ramp, rate, invoice, payment, withdrawal, and completion signatures are **not one transaction chain**.

## I–J. Tax export and certificate state

`TaxExportStatus` is job-level: `NOT_ATTEMPTED`, `SUCCESS`, `FAILED`. It is not certificate status. Exact `TaxCertificateStatus` values and source Vietnamese labels:

| Status | Source label |
| --- | --- |
| `PENDING_EXPORT` | Đang chờ lập chứng từ |
| `EXPORT_FAILED` | Lập chứng từ thất bại |
| `DRAFT` | Đã lập chứng từ, chưa phát hành |
| `SIGNED` | Đã ký số |
| `SUBMITTING` | Đã phát hành, đang gửi cơ quan thuế |
| `SUBMITTED` | Đã gửi cơ quan thuế |
| `ACCEPTED` | Cơ quan thuế đã chấp nhận |
| `REJECTED` | Cơ quan thuế từ chối |
| `CORRECTION_REQUIRED` | Cần điều chỉnh |
| `REPLACED` | Đã được thay thế |
| `CANCELLED` | Đã hủy |

`TaxCertificateResponse` carries job/party IDs, `amountUsd`, `usdToVndRate`, `rateSource`, `taxableIncomeVnd`, nullable `taxWithheldVnd`, status/label, certificate and MISA identifiers, lookup and authority references, and timestamps. Taxable income is `job.budgetUsd × USD/VND tax rate`, separately from the USDC/VND payout estimate. Withheld tax is returned from MISA when present; the UI must not calculate a statutory rate. `ExchangeRateSource`: `LIVE_OPEN_ER_API`, `LIVE_COINGECKO`, `FALLBACK_PLACEHOLDER`; the latter must be visibly identified as a placeholder.

## K–M. Tax API, access, and actions

All paths below are under `/api/v1/marketplace/tax-records` and require the authenticated participant (Client or Freelancer):

- `GET /` paged participant list; `GET /{taxRecordId}` detail; `GET /jobs/{jobId}` job lookup.
- `POST /{taxRecordId}/sync` requires an existing `misaCertificateId`.
- `POST /{taxRecordId}/retry-export` requires `EXPORT_FAILED`.
- `GET /{taxRecordId}/pdf` and `/xml` require a MISA certificate ID and participant access; an upstream file error can still occur.

The job payment-status endpoint exposes no generic user payout/on-ramp/withdrawal retry. A read-only refresh is possible. No manual certificate create, edit, issue, approve, delete, or tax-rate edit endpoint is present. A missing tax record is absence, not certificate failure or tax exemption.

## N–O. Simulation and unsupported concepts

`simulated=true`, Mock USDC, `DEVNET`, `SIMULATED`, and **Estimated VND / VND dự kiến** must appear by the relevant outcome. `COMPLETED` off-ramp remains completion of simulated/on-chain records. No real bank transfer, withdrawable or available balance, token trading, wallet transfer controls, manual payout, or cash receipt can be asserted from these fields. See [D3_SIMULATION_EVIDENCE_RULES.md](D3_SIMULATION_EVIDENCE_RULES.md).
