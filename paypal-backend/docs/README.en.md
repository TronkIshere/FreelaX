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

## Known limitations

- All fee rates (`paypal.fee.*` in `application.yml`) are **mock placeholder
  values**, not yet verified against PayPal's official fee schedule — see the
  full disclaimer in `docs/PAYPAL_MODULE_REFERENCE.md`.
- Does not connect to the real PayPal API — `senderReference` and
  `platformPayoutId` are manually entered data used to simulate a transaction,
  not an actual PayPal transaction.
- There is no "tax withholding" step in this flow at all — this correctly
  reflects the nature of the old PayPal path (the freelancer is responsible for
  self-declaring taxes). This is precisely the differentiator to emphasize when
  comparing against `misa-backend` (which issues a formal withholding
  certificate).
- `PaypalTransactionStatus` only has 2 states (`RECEIVED`/`WITHDRAWN`), with no
  complex state machine — this correctly reflects that the old flow has no
  certificate/tax-compliance process attached to it.
