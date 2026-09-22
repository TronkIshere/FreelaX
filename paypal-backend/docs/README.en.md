# PayPal Backend

Backend service (`paypal-backend`) that owns everything related to PayPal money movement for this project. It has four independent sub-modules with different purposes and different auth models — read the table below before touching any endpoint.

| Module | Purpose | Calls real PayPal? | Auth |
|---|---|---|---|
| Payee / Transaction (mock) | Simulates the "old way" a freelancer gets paid via PayPal (fee breakdown + VND conversion), persisted for side-by-side comparison against the USDC + off-ramp + MISA flow in `misa-backend` | No | User JWT |
| Payee Status (internal) | Lets a caller check whether a given user has an active PayPal payee profile, and get that payee's id | No | Internal API key |
| Checkout Order (internal) | Creates and captures a real PayPal order so a payer (client) can pay into this platform's PayPal account for a marketplace job | Yes (`/v2/checkout/orders`) | Internal API key |
| Payout Release (internal) | Releases a real PayPal payout to a freelancer's PayPal email after their checkout order has been captured | Yes (`/v1/payments/payouts`) | Internal API key |

The three internal modules are **not called directly by end users**. They exist to be called by `marketplace-backend` (a separate repository/service) as it moves a job's payment through: look up the freelancer's payee → create and capture a checkout order → release the payout. See "Auth models" below.

## Run it

```bash
cp .env.example .env
docker compose up -d --build
```

The backend runs at `http://localhost:9192`. Swagger UI: `http://localhost:9192/swagger-ui.html`.

`docker-compose.yml` provisions MySQL (database `paypal`, user `root`, password read from `.env` via `DB_PASSWORD`, host port `3307` → container port `3306`) and Redis (host port `6380` → container port `6379`).

`.env.example` should ship with placeholders for the following — add them if not already present, and replace every one before any real deployment:

- `JWT_SECRET` — do not use the sample value.
- `INTERNAL_API_KEY` — shared secret for the three internal modules; generate a real random value, do not leave it blank or default.
- `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` — PayPal sandbox or live REST app credentials. Without these, Checkout Order and Payout Release fail at PayPal's OAuth step, not before. Only required if you're exercising those two modules — the mock Payee/Transaction and Payee Status modules work without them.

Running without Docker (requires MySQL at `localhost:3307`, Redis at `localhost:6379`, and the env vars above set yourself):

```bash
mvn spring-boot:run
```

## Auth models

This service has two unrelated authentication mechanisms — don't mix them up:

- **User JWT** — every endpoint under `/api/v1/**` (auth, payee, transaction). Resolved via `@AuthenticationPrincipal UserPrincipal`; every payee/transaction lookup is scoped to the caller's own data.
- **Internal API key** — every endpoint under `/internal/**` (payee status, checkout order, payout release). No user identity involved; enforced by `InternalApiKeyFilter`, which compares the `X-Internal-Api-Key` header against `internal.api.key`. `/internal/**` is whitelisted from the JWT filter for this reason — it is a different trust boundary, meant for service-to-service calls from `marketplace-backend`, not browser clients.

### Internal module setup checklist

The three `/internal/**` modules need this wiring to actually run, not just compile — confirm all three before demoing:

- A `RestTemplate` bean exists (`RestTemplateConfiguration`) — both `PaypalCheckoutClient` and `PaypalPayoutClient` are constructor-injected with it and fail to start otherwise. If a `RestTemplate` bean already existed elsewhere in the project, check for a duplicate-bean conflict.
- `InternalApiKeyFilter` is registered in `SecurityConfiguration` via `addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)`, and `/internal/**` is in `SecurityConstants.WHITE_LIST`. Without both, either the JWT filter rejects internal calls before they reach the key check, or the key check never runs at all.
- `INTERNAL_API_KEY` is set to the same value on both sides — this service and `marketplace-backend`.

## Suggested API flow for a demo (mock Payee/Transaction module)

1. `POST /api/v1/auth/register`, `POST /api/v1/auth/sign-in` — get an access token.
2. `POST /api/v1/paypal/payees` — freelancer registers a PayPal receiving profile.
3. `POST /api/v1/paypal/payees/{payeeId}/transactions` — record a payment received (input `grossAmountUsd`, `midMarketRate`). The system computes the fees (`commercialFeeUsd`, `fxSpreadCostUsd`) and `netVnd` using the mock formula, and persists the full breakdown.
4. `GET /api/v1/paypal/payees/{payeeId}/transactions/{transactionId}` — read back the stored transaction; use this figure to compare side by side with the `misa-backend` response for the same amount when demoing.
5. `GET /api/v1/paypal/payees/{payeeId}/transactions` — list every transaction for that payee.
6. `POST /api/v1/paypal/payees/{payeeId}/transactions/{transactionId}/withdraw` — transitions the transaction status from `RECEIVED` to `WITHDRAWN`.

All calls above must use the same logged-in user's access token — every one enforces that `payeeId` (and, where applicable, `transactionId`) belongs to the caller. Calling any of them with someone else's `payeeId`/`transactionId` returns a not-found-style error rather than that user's data.

See "Ownership / access control" in `PAYPAL_MODULE_REFERENCE.md` for exactly what this enforces.

## Internal API: Payee Status

Lets `marketplace-backend` check whether a freelancer has a PayPal payee profile, and get the `payeeId` needed to create a Checkout Order — this is the lookup step a marketplace flow does before it can pay a freelancer at all.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/internal/paypal/users/{userId}/payee-status` | Returns `{payeeId, registered, active}` for the given user id. If the user has never registered a payee, responds `200` with `registered: false` and `payeeId: null` — this is a status check, not an ownership-gated lookup, so a "not found" case is not treated as an error. |

## Internal API: Checkout Order

Real PayPal Orders API integration. A payer (client) pays into the platform's PayPal account for a specific job; the platform later releases that money to the freelancer via the Payout Release module below.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/internal/paypal/checkout/orders` | Create a PayPal order for `{payeeId, payerUserId, jobId, amountUsd}`. Validates the payee exists and is active, then calls PayPal `/v2/checkout/orders`. Returns `approvalUrl` for the payer to approve. |
| `POST` | `/internal/paypal/checkout/orders/{orderId}/capture` | Captures the order after the payer approves. Calls PayPal `/v2/checkout/orders/{id}/capture`. Order must currently be `CREATED`; any non-`COMPLETED` PayPal response marks the order `FAILED`. |
| `GET` | `/internal/paypal/checkout/orders/{orderId}` | Reads back a stored order — status, PayPal order/capture id, amounts. |

Status machine: `CREATED` → `CAPTURED` or `FAILED`.

`payerUserId` and `jobId` are trusted values supplied by the caller (`marketplace-backend`) for audit/linking purposes only — this service does not independently verify who the payer is or that the job exists, since it doesn't own that data.

## Internal API: Payout Release

Real PayPal Payouts API integration. Moves money already captured by a Checkout Order out to the freelancer's PayPal account.

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/internal/paypal/payouts` | Release the payout for `{checkoutOrderId}`. Requires the checkout order to exist and be `CAPTURED`, and that it hasn't already been released. Looks up the payee's PayPal email and calls PayPal `/v1/payments/payouts`. |
| `GET` | `/internal/paypal/payouts/{id}` | Reads back a stored payout release — status, PayPal batch id, amounts. |

Status machine: `PENDING`, `SUCCESS`, `FAILED`. PayPal's Payouts API is asynchronous — a newly created batch is almost always `PENDING`. This service does not currently poll PayPal or handle payout webhooks, so a release can remain `PENDING` here even after PayPal finishes processing it on their side. `paypalPayoutItemId` is reserved in the schema but never populated yet — getting it requires a follow-up `GET` call to PayPal that isn't implemented.

## Known limitations

- **Mock Payee/Transaction module**: all fee rates (`paypal.fee.*`) are illustrative placeholder values, not verified against PayPal's official fee schedule — see the full disclaimer in `PAYPAL_MODULE_REFERENCE.md`. It never calls the real PayPal API; `senderReference` and `platformPayoutId` are manually entered, simulated data. There is intentionally no tax-withholding step (this is the point of comparison against `misa-backend`), and no complex state machine (`RECEIVED`/`WITHDRAWN` only).
- **Checkout Order / Payout Release modules**: both call the real PayPal API and require valid sandbox or live credentials — don't confuse them with the mock module above when demoing.
- **Payee Status is a plain lookup, not an ownership check**: any internal caller can query any `userId`. That's intentional here (there's no browser session on the other end to own anything), but it means this endpoint must never be exposed outside the internal trust boundary.
- **Payout Release has no async status sync**: no webhook handler or polling job updates a `PENDING` release once PayPal actually finishes it. Add one before relying on this status for anything beyond "was a payout attempted."
- **No hiring/escrow/marketplace logic lives in this repository.** Job posting, listing, and the overall marketplace flow live in a separate `marketplace-backend` service, which calls the three internal modules above over HTTP. This repo has no visibility into job or user data beyond the `payeeId`/`jobId`/`payerUserId` values it's given.
- `PaypalPayoutTransactionRepository.findByPayeeId(UUID)` was added without the original file in hand (reconstructed from how `PaypalPayoutTransactionServiceImpl` calls it) — diff it against the real file before trusting it blindly, in case the original had other methods not visible from usage alone.