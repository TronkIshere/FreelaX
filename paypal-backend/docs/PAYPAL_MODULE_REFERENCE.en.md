# PayPal Module Reference — fee model & disclaimers

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

## Known limitations

- `PaypalPayoutTransactionController` does not check whether the `payeeId` in
  the path belongs to the currently authenticated user — this is a utility
  endpoint for demo purposes; ownership validation should be added before real
  use.
- There is no endpoint to list all transactions for a payee (only
  get-by-id exists) — if a full history view is needed on the UI, add
  `GET /api/v1/paypal/payees/{payeeId}/transactions` returning a list.
