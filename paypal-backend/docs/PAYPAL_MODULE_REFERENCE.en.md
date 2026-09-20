# PayPal Module Reference — fee model & disclaimers

**Scope note**: this document covers the mock payee/transaction module only
(the fee calculator described below). The same backend also has a separate,
**real** PayPal checkout integration (`/api/v1/paypal/checkout/orders`,
actual PayPal Orders API) — see `README.md`'s "PayPal checkout" section.
Don't conflate the two: this module never calls PayPal; checkout does.

## Purpose

This module **does not connect to the real PayPal API**. It records and
computes a PayPal payment-received transaction using a mock fee model, persists
it to the database, and is used to produce real comparison data (not just a
one-off calculation) against the USDC + off-ramp + MISA flow in
`misa-backend`. This is the persisted extension of the original stateless
calculator design (see the previously delivered `PAYPAL_MOCK_REFERENCE.md` /
`PAYPAL_SIMULATION_MODULE.md`).

## Data disclaimer

The fee rates in `application.yml` (`paypal.fee.commercial-fee-rate`,
`paypal.fee.fixed-fee-usd`, `paypal.fee.fx-spread-rate`) are **illustrative
placeholder values**, taken from an earlier discussion referencing PayPal
Vietnam's published 2026 fee schedule, and **have not been independently
verified**. Before presenting to judges, look up PayPal's official fee page
yourself to confirm current figures.

## Calculation formula (implemented in `PaypalFeeCalculatorServiceImpl`)

```
commercialFeeUsd          = grossAmountUsd * commercialFeeRate + fixedFeeUsd
netUsdAfterTransactionFee = grossAmountUsd - commercialFeeUsd
fxSpreadCostUsd           = netUsdAfterTransactionFee * fxSpreadRate
netUsdAfterFees           = netUsdAfterTransactionFee - fxSpreadCostUsd
netVnd                    = netUsdAfterFees * midMarketRate
effectiveFeeRatePercent   = (1 - netVnd / (grossAmountUsd * midMarketRate)) * 100
```

This full breakdown is persisted directly onto `PaypalPayoutTransaction` (not
recomputed on every read) — the same way `misa-backend` persists `taxWithheld`
onto `WithholdingCertificate` — so the two systems can be compared using stored
data rather than two throwaway calculations.

## Core differences vs. `misa-backend` (talking points for the demo)

| | PayPal (old) | USDC + MISA (new) |
|---|---|---|
| Transaction states | 2 (`RECEIVED`, `WITHDRAWN`) | 9 (`CertificateStatus`, full certificate lifecycle) |
| Tax document | None — freelancer self-declares | Yes — automatic withholding tax certificate (Form 03/TNCN) |
| Who computes the fee | The payment platform (PayPal), not transparent line-by-line | Off-ramp partner + platform, transparent per-line fees |
| Data persistence | Yes (this module), but no tax fields | Yes, including full tax data (taxableIncome, taxWithheld) |

## What NOT to say during a demo

- Do not say this is a "real PayPal integration" — this is a simulated record
  using a configurable fee formula; it does not call the PayPal API.
- Do not present the `effectiveFeeRatePercent` figure computed from these mock
  values as an authoritative number unless you have re-verified it against
  PayPal's real fee schedule.
- Do not claim this module "replaces" PayPal — it only simulates the old flow
  to serve as a comparison baseline, not an actual payment gateway.

## Ownership / access control (fixed)

Both `PaypalPayeeController` and `PaypalPayoutTransactionController` now
resolve the caller's identity via `@AuthenticationPrincipal UserPrincipal` and
enforce ownership before returning or mutating any record:

- `GET /api/v1/paypal/payees/{id}` — now `getByIdForOwner(userId, payeeId)`;
  returns the same `PAYEE_NOT_FOUND` whether the payee doesn't exist or simply
  isn't the caller's (existence is not leaked).
- `POST /api/v1/paypal/payees/{payeeId}/transactions` — the `payeeId` path
  segment is now resolved against the caller's own payee
  (`paypalPayeeRepository.findById(payeeId).filter(p -> p.getUserId().equals(userId))`)
  before a transaction can be recorded under it.
- `GET .../transactions/{transactionId}` and
  `POST .../transactions/{transactionId}/withdraw` — both now go through
  `getOwnedOrThrow(userId, payeeId, transactionId)`, which checks that the
  transaction's payee matches the path's `payeeId` **and** that this payee
  belongs to the caller. Either mismatch throws the same `PAYOUT_NOT_FOUND` as
  a nonexistent transaction, for the same "don't reveal existence" reason.

Previously, the transaction endpoints accepted `payeeId` from the URL but
never validated it — `getById`/`withdraw` didn't even pass it to the service
layer, so any authenticated user could read or (for `withdraw`) mutate any
other user's transaction by `transactionId` alone; `record` let any
authenticated user attach a fabricated transaction to any other user's payee.
Both are closed now.

## New: list transactions for a payee

`GET /api/v1/paypal/payees/{payeeId}/transactions` — returns every transaction for that payee, ownership-checked the same way as `record`/`getById`/`withdraw` (same `PAYEE_NOT_FOUND` on mismatch, not a 403). Requires a `findByPayeeId(UUID)` method on `PaypalPayoutTransactionRepository` — not included here since the actual repository file was never shared; add it or `PaypalPayoutTransactionServiceImpl.list()` won't compile.

## Known limitations (remaining)

- All fee rates (see Data disclaimer above) remain mock placeholder values,
  independent of the ownership fix.
- Ownership checks assume `PaypalPayee.getUserId()` is reliably set at payee
  creation (`register()` in `PaypalPayeeServiceImpl`) and never null for a
  persisted payee — this holds for the current single registration path, but
  would need re-checking if payees are ever created any other way (e.g. an
  admin/import tool).