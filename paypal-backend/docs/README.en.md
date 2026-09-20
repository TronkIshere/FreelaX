# PayPal Backend

Backend that simulates the "old way" a Vietnamese freelancer gets paid: receive
money via PayPal, pay fees (commercial transaction fee + FX spread), then
withdraw VND to a bank account. Used to produce a real, persisted comparison
(not just an on-the-fly calculation) against the USDC + off-ramp + MISA flow in
`misa-backend`. See the full fee model and disclaimers in
`docs/PAYPAL_MODULE_REFERENCE.md`.

## Run it

```bash
cp .env.example .env
docker compose up -d --build
```

The backend runs at `http://localhost:9192`. Swagger UI:
`http://localhost:9192/swagger-ui.html`.

`docker-compose.yml` provisions MySQL (database `paypal`, user `root`, password
read from `.env` via `DB_PASSWORD`, host port `3307` → container port `3306`)
and Redis (host port `6380` → container port `6379`). `.env.example` ships with
a `JWT_SECRET` placeholder — **replace it with a real value before any real
deployment**, do not use the sample value.

Running without Docker (requires MySQL at `localhost:3307`, Redis at
`localhost:6379`, and `JWT_SECRET` set yourself):

```bash
mvn spring-boot:run
```

## Suggested API flow for a demo

1. `POST /api/v1/auth/register`, `POST /api/v1/auth/sign-in` — get an access
   token.
2. `POST /api/v1/paypal/payees` — freelancer registers a PayPal receiving
   profile.
3. `POST /api/v1/paypal/payees/{payeeId}/transactions` — record a payment
   received (input `grossAmountUsd`, `midMarketRate`). The system computes the
   fees (`commercialFeeUsd`, `fxSpreadCostUsd`) and `netVnd` using the mock
   formula, and persists the full breakdown.
4. `GET /api/v1/paypal/payees/{payeeId}/transactions/{transactionId}` — read
   back the stored transaction; use this figure to compare side by side with
   the `misa-backend` response for the same amount when demoing.
5. `POST /api/v1/paypal/payees/{payeeId}/transactions/{transactionId}/withdraw`
   — transitions the transaction status from `RECEIVED` to `WITHDRAWN`.

All five calls above must use the same logged-in user's access token — steps
2–5 now enforce that `payeeId` (and, for steps 4–5, `transactionId`) belong to
the caller. Calling any of them with someone else's `payeeId`/`transactionId`
returns a not-found-style error rather than that user's data. See "Ownership
/ access control" in `docs/PAYPAL_MODULE_REFERENCE.md` for exactly what
changed.

## Marketplace module

Job posting and listing — `POST/GET /api/v1/marketplace/jobs`, `GET /api/v1/marketplace/jobs/{jobId}`, plus `POST /{jobId}/checkout-order` to record which real PayPal checkout order (see below) is paying for a job. See `docs/MARKETPLACE_MODULE_REFERENCE.md`. No hiring, escrow, or payout logic exists yet — that was drafted once and dropped pending real product docs.

## PayPal checkout

Real PayPal Orders API integration (`/v2/checkout/orders` create + capture), not the mock fee model described above — `POST /api/v1/paypal/checkout/orders` `{payeeId, amountUsd, referenceId}` then `POST /{id}/capture`. `payeeId` is validated against an existing `PaypalPayee` before a real order is created. Requires real sandbox or live PayPal credentials in `application.yml` (`paypal.checkout.*`) — without them, order creation fails at PayPal's OAuth step, not before.

## Known limitations

- All fee rates (`paypal.fee.*` in `application.yml`) are **mock placeholder
  values**, not yet verified against PayPal's official fee schedule — see the
  full disclaimer in `docs/PAYPAL_MODULE_REFERENCE.md`.
- Does not connect to the real PayPal API — `senderReference` and
  `platformPayoutId` are manually entered data used to simulate a transaction,
  not an actual PayPal transaction. **This applies only to the payee/
  transaction module above.** The separate PayPal checkout module (see
  "PayPal checkout" below) does call the real PayPal Orders API — don't
  confuse the two when demoing.
- There is no "tax withholding" step in this flow at all — this correctly
  reflects the nature of the old PayPal path (the freelancer is responsible for
  self-declaring taxes). This is precisely the differentiator to emphasize when
  comparing against `misa-backend` (which issues a formal withholding
  certificate).
- `PaypalTransactionStatus` only has 2 states (`RECEIVED`/`WITHDRAWN`), with no
  complex state machine — this correctly reflects that the old flow has no
  certificate/tax-compliance process attached to it.
- `GET /api/v1/paypal/payees/{payeeId}/transactions` (list) now exists,
  ownership-checked like the other three — see
  `docs/PAYPAL_MODULE_REFERENCE.md`. Requires a `findByPayeeId` method on
  `PaypalPayoutTransactionRepository`, which needs adding by hand (that file
  itself was never shared).