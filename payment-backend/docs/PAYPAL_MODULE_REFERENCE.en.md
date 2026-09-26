# PayPal Module Reference — money-movement logic & disclaimers

This document is the detailed technical reference for every money-related calculation and business rule in `paypal-backend`. For run instructions and a high-level endpoint overview, see `README.md`.

Four independent sub-modules are documented here:

1. Payee / Transaction (mock fee simulator) — user-facing, JWT-authenticated
2. Payee Status (real payee lookup) — internal, API-key authenticated
3. Checkout Order (real PayPal Orders API) — internal, API-key authenticated
4. Payout Release (real PayPal Payouts API) — internal, API-key authenticated

Don't conflate them. Only (3) and (4) call the real PayPal API.

## 1. Payee / Transaction module (mock fee simulator)

### Purpose

This module **does not connect to the real PayPal API**. It records and computes a PayPal payment-received transaction using a mock fee model, persists it to the database, and is used to produce real comparison data (not just a one-off calculation) against the USDC + off-ramp + MISA flow in `misa-backend`.

### Data disclaimer

The fee rates in `application.yml` (`paypal.fee.commercial-fee-rate`, `paypal.fee.fixed-fee-usd`, `paypal.fee.fx-spread-rate`) are **illustrative placeholder values**, taken from an earlier discussion referencing PayPal Vietnam's published fee schedule, and **have not been independently verified**. Before presenting to judges or stakeholders, look up PayPal's official fee page yourself to confirm current figures.

### Calculation formula (`PaypalFeeCalculatorServiceImpl`)

```
commercialFeeUsd          = grossAmountUsd * commercialFeeRate + fixedFeeUsd
netUsdAfterTransactionFee = grossAmountUsd - commercialFeeUsd
fxSpreadCostUsd           = netUsdAfterTransactionFee * fxSpreadRate
netUsdAfterFees           = netUsdAfterTransactionFee - fxSpreadCostUsd
netVnd                    = netUsdAfterFees * midMarketRate
effectiveFeeRatePercent   = (1 - netVnd / (grossAmountUsd * midMarketRate)) * 100
```

The full breakdown is persisted directly onto `PaypalPayoutTransaction` (not recomputed on every read) — the same way `misa-backend` persists `taxWithheld` onto `WithholdingCertificate` — so the two systems can be compared using stored data rather than two throwaway calculations.

### Core differences vs. `misa-backend` (talking points for a demo)

| | PayPal (old, this module) | USDC + MISA (new) |
|---|---|---|
| Transaction states | 2 (`RECEIVED`, `WITHDRAWN`) | 9 (`CertificateStatus`, full certificate lifecycle) |
| Tax document | None — freelancer self-declares | Yes — automatic withholding tax certificate (Form 03/TNCN) |
| Who computes the fee | The payment platform (PayPal), not transparent line-by-line | Off-ramp partner + platform, transparent per-line fees |
| Data persistence | Yes (this module), but no tax fields | Yes, including full tax data (`taxableIncome`, `taxWithheld`) |

### What NOT to say during a demo

- Do not say this module is a "real PayPal integration" — it is a simulated record using a configurable fee formula; it does not call the PayPal API. (The Checkout Order and Payout Release modules below *do* call the real API — keep that distinction clear.)
- Do not present `effectiveFeeRatePercent` computed from these mock values as an authoritative number unless you've re-verified it against PayPal's real fee schedule.
- Do not claim this module "replaces" PayPal — it only simulates the old flow to serve as a comparison baseline.

### Ownership / access control

`PaypalPayeeController` and `PaypalPayoutTransactionController` resolve the caller's identity via `@AuthenticationPrincipal UserPrincipal` and enforce ownership before returning or mutating any record:

- `GET /api/v1/paypal/payees/{id}` — `getByIdForOwner(userId, payeeId)`; returns the same `PAYEE_NOT_FOUND` whether the payee doesn't exist or simply isn't the caller's (existence is not leaked).
- `POST /api/v1/paypal/payees/{payeeId}/transactions` — the `payeeId` path segment is resolved against the caller's own payee before a transaction can be recorded under it.
- `GET .../transactions/{transactionId}`, `POST .../transactions/{transactionId}/withdraw`, and `GET .../transactions` (list) — all go through an ownership check that verifies the transaction's payee matches the path's `payeeId` **and** that this payee belongs to the caller. Any mismatch throws the same not-found error as a nonexistent record, for the same "don't leak existence" reason.

This ownership model is specific to this module — contrast with Payee Status below, which is a plain internal lookup with no ownership concept at all.

### Known limitations

- All fee rates (see Data disclaimer above) remain mock placeholder values, independent of anything else in this document.
- Ownership checks assume `PaypalPayee.getUserId()` is reliably set at payee creation and never null for a persisted payee — holds for the current single registration path (`register()` in `PaypalPayeeServiceImpl`), would need re-checking if payees are ever created any other way (e.g. an admin/import tool).
- `PaypalPayoutTransactionRepository.findByPayeeId(UUID)` was added without seeing the original repository file — reconstructed purely from how `PaypalPayoutTransactionServiceImpl.list()` calls it. Diff against the real file before trusting it, in case other methods existed that aren't visible from usage alone.

## 2. Payee Status module (real payee lookup)

### Purpose

Answers a single question for `marketplace-backend`: does this user have a PayPal payee profile, and if so, what's its id? This is the lookup step that has to happen before a Checkout Order can be created — a Checkout Order needs a `payeeId`, and the caller has no other way to get one.

### Business rules (`PaypalPayeeStatusServiceImpl`)

- Looks up `PaypalPayeeRepository.findByUserId(userId)` directly — no JWT, no ownership check. The caller supplies whatever `userId` it wants to check; this service trusts it, the same way it trusts `payerUserId`/`jobId` on Checkout Order.
- If no payee exists for that user, the response is `200 OK` with `registered: false`, `payeeId: null` — **not** a `404`. Ownership-checked endpoints elsewhere in this service return not-found errors specifically to hide whether a record exists (see module 1's ownership section); this endpoint has the opposite goal — the caller needs a definite yes/no to branch on, and there's no owner to protect here.
- If a payee exists but is `active: false`, the response still reports `registered: true` with `active: false` — the caller (`marketplace-backend`) decides what to do with an inactive payee (e.g. block job creation), this service doesn't make that call.

### What NOT to say during a demo

- Do not describe this as an authentication or authorization check — it isn't one. It's a data lookup with no access control beyond the internal API key gate shared by all `/internal/**` endpoints.

## 3. Checkout Order module (real PayPal Orders API)

### Purpose

Lets a payer (client) pay real money into this platform's PayPal account for a specific marketplace job. Called only by `marketplace-backend` over the internal API — never by an end-user JWT session. See "Auth models" in `README.md`.

### Entity & status

`PaypalCheckoutOrder`: `payeeId`, `payerUserId`, `jobId`, `amountUsd`, `paypalOrderId`, `paypalCaptureId`, `status`, `createdAt`, `capturedAt`.

Status machine: `CREATED` → `CAPTURED` (success) or `FAILED` (PayPal capture didn't return `COMPLETED`). There is no path back from `FAILED` or `CAPTURED` — a failed order is not retried automatically.

### Business rules (`PaypalCheckoutOrderServiceImpl`)

- `create()`: the referenced `payeeId` must exist and be `active`, or the call fails with `PAYEE_NOT_FOUND` / `PAYEE_NOT_ACTIVE` before any call to PayPal is made. In practice, the caller should have already confirmed this via Payee Status (module 2) before ever reaching this call.
- `capture()`: the order must currently be `CREATED` (`INVALID_CHECKOUT_ORDER_STATUS` otherwise) — this prevents capturing the same order twice.
- `payerUserId` and `jobId` are **not validated** against real user/job records — this service trusts whatever `marketplace-backend` sends, since it has no direct access to that data. If `marketplace-backend` ever needs stronger guarantees here, that validation has to happen on its side before calling this API.

### What NOT to say during a demo

- Do not describe this module as "escrow" — captured funds land in the platform's own PayPal account with no automatic hold/release tied to job completion. The actual hold/release timing is a business decision made by whatever calls this API (see Payout Release below for the release half).

## 4. Payout Release module (real PayPal Payouts API)

### Purpose

Moves money already captured by a Checkout Order out to the freelancer's PayPal account, once `marketplace-backend` decides the job is complete and approved.

### Entity & status

`PaypalPayoutRelease`: `checkoutOrderId` (unique — one release per checkout order), `payeeId`, `jobId`, `amountUsd`, `paypalPayoutBatchId`, `paypalPayoutItemId` (currently always null, see below), `status`, `createdAt`, `releasedAt`.

Status machine: `PENDING`, `SUCCESS`, `FAILED`, driven by PayPal's `batch_status` on the create-batch response (`SUCCESS` → `SUCCESS`, `DENIED` → `FAILED`, anything else → `PENDING`).

### Business rules (`PaypalPayoutReleaseServiceImpl`)

- The referenced `checkoutOrderId` must exist and currently be `CAPTURED` (`CHECKOUT_ORDER_NOT_FOUND` / `INVALID_CHECKOUT_ORDER_STATUS` otherwise) — you cannot release a payout for money that hasn't actually been captured.
- Exactly one release is allowed per checkout order (`existsByCheckoutOrderId`) — a second attempt fails with `PAYOUT_ALREADY_RELEASED` rather than sending a duplicate payout.
- The payee must still be `active` at release time (`PAYEE_NOT_ACTIVE` otherwise) — this is re-checked here even though it was already checked at order-creation time, in case the payee was deactivated in between.
- The payout is sent to `payee.getPaypalEmail()` as an `EMAIL`-type recipient — there's no fallback to a merchant/payee id.

### Known limitations

- **Asynchronous by nature, not polled**: PayPal's Payouts API almost always returns `batch_status: PENDING` on creation. This service has no webhook handler or scheduled job to later reconcile that to `SUCCESS`/`FAILED` — a release can sit at `PENDING` here indefinitely even after PayPal finishes it on their side. Treat `PENDING` as "submitted," not "confirmed."
- **`paypalPayoutItemId` is never populated.** PayPal's create-batch response only returns a `payout_batch_id`; the per-item id requires a separate `GET /v1/payments/payouts/{batchId}` call, which isn't implemented.
- **OAuth token fetching is duplicated** between `PaypalCheckoutClient` and `PaypalPayoutClient` rather than shared through a common client — both call the same `/v1/oauth2/token` endpoint independently using the same `PaypalCheckoutProperties`. Not a correctness problem, but worth consolidating if a third module ever needs a PayPal access token.
- Both this module and Checkout Order share PayPal app credentials via `PaypalCheckoutProperties` (`paypal.checkout.*`) — there's no separate credential set for Payouts specifically, since PayPal issues one client id/secret per app, not per API.