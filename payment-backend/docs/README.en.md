# Payment Backend

Spring Boot service (`:9190`, schema `payment` by default (`DB_URL`), Flyway `V1__baseline_schema.sql`) that plays the external money partner for `marketplace-backend`. Browsers never call it; Marketplace does, with an internal API key. Status labels: IMPLEMENTED unless noted.

## Modules

| Module | Base path | Purpose | Used by |
| --- | --- | --- | --- |
| Unified USD order mock | `/internal/unified-mock/usd-orders` | Simulated USD funding: create order, `submit`, read order and `statement` | `UNIFIED_USDC_PAYOUT` flow |
| Unified fiat exit mock | `/internal/unified-mock/fiat-exits` | Simulated VND payout after fee and tax: create, read, `statement` | `UNIFIED_USDC_PAYOUT` flow |
| Partner escrow mock | `/internal/partner-mock/escrows` | Simulated partner escrow: create, read, `freeze`, `release`, `refund`, `statement` | `PARTNER_ESCROW_MOCK` rail |
| Checkout order | `/internal/BofA/checkout/orders` | Create, `capture`, read by id or by idempotency key against the PayPal Orders API | Legacy job-level payment |
| Checkout refund | `/internal/BofA/refunds` | Refund a captured order, read by `by-key` | Legacy job-level payment |
| Payout release | `/internal/BofA/payout/releases` | Release a payout after capture, read by `by-key` or `by-checkout/{id}` | Legacy job-level payment |
| Account balance | `/internal/BofA/balances/{bankAccountNumber}` | Read a simulated USD account balance (initial balance 10000 USD) | Mock partner |
| Auth | `/api/v1/auth/*` | Register, sign-in, refresh, sign-out, forgot-password, `me` (JWT) | Demo and tests |

The `BofA` name is historical: the checkout, refund and payout clients call PayPal (`BOFA_BASE_URL`, default `https://api-m.sandbox.paypal.com` in profile `dev` and `https://api-m.paypal.com` in profile `prod`). Everything under `unified-mock` and `partner-mock` is simulation only.

## Authentication

- `/internal/**` requires the `X-Internal-Api-Key` header (`InternalApiKeyFilter`).
- `/api/v1/auth/**` uses the service's own JWT secret and Redis.

## Not present any more

The former `paypal-backend` modules (Payee/Transaction fee simulator, Payee Status, `/paypal/payees/*`, `/internal/paypal/*`) no longer exist. Their documentation and Postman collection are kept under [`archive/`](archive/) for history only and do not match the current API.

## Related

- Business rules: [`docs/business/README.md`](../../docs/business/README.md)
- Money flow: [`docs/business/PAYMENT_FLOW.md`](../../docs/business/PAYMENT_FLOW.md)
