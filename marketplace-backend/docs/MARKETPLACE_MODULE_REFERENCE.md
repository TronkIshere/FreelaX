# Marketplace Module Reference — job lifecycle, Auth & PayPal integration

Detailed technical reference for `marketplace-backend`. For run instructions and a high-level overview, see `README.md` in this same folder.

This service has two business modules (Auth, Job) and one integration client (`PaypalBackendClient`, calling `paypal-backend`'s three internal modules). There is no PayPal logic here — every rule about fees, PayPal statuses, or PayPal error codes lives in `paypal-backend`'s own `PAYPAL_MODULE_REFERENCE.md`, not here.

## Auth module

### Entities

`User`: `email` (unique), `password` (BCrypt-hashed), `displayName`, `authProvider` (`LOCAL` only so far), `enabled`, `refreshToken` (nullable — cleared on sign-out), `roles` (many-to-many via `user_roles`). `Role`: just `name` (`ROLE_USER` / `ROLE_ADMIN`, seeded once by `DataInitializer` if the table is empty).

### Token model

- **Access token**: HS512-signed, subject = email, includes a `roles` claim, expires after `security.jwt.expiration-ms` (default 24h). Sent as `Authorization: Bearer <token>`.
- **Refresh token**: HS256-signed, 14-day expiry, stored both as an `HttpOnly`/`Secure` cookie **and** on the `User` row itself (`refreshToken` column) — `refreshToken()` checks the incoming cookie against the stored column before issuing a new access token, so a token that's been superseded by a newer sign-in (which overwrites the column) or cleared by sign-out no longer works even if it hasn't technically expired.
- **Reset token**: HS512-signed, 5-minute expiry, carries a `type: RESET_PASSWORD` claim so `validateResetToken()` can reject a token that's structurally valid but was never meant for password resets.
- **Blacklisting**: `signOut()` stores the access token's JWT ID in Redis for exactly its remaining lifetime (`extractTokenExpired()`), so a signed-out token becomes unusable immediately rather than staying valid until natural expiry — but only `JwtServiceImpl.verificationToken()` (used during refresh) actually checks the blacklist; `validateToken()` (used by the request filter on every call) does not, so a blacklisted access token is still accepted by `JwtAuthenticationFilter` on ordinary requests until it naturally expires. This matches exactly what the given `JwtServiceImpl` does — it wasn't changed here, just repackaged.

### Business rules worth knowing

- `registerUser()` requires a `ROLE_USER` row to already exist — this is why `DataInitializer` (seeding `ROLE_USER`/`ROLE_ADMIN` on an empty `roles` table) has to run before the first registration, not after.
- `signIn()` checks the email exists *before* calling `AuthenticationManager.authenticate()`, but both paths throw the same `INVALID_CREDENTIALS` — so a nonexistent email and a wrong password are indistinguishable to the caller, which is the intended behavior (don't reveal which emails are registered).
- `sendResetPasswordOTP()` does the opposite tradeoff deliberately: it throws a distinct `EMAIL_NOT_FOUND` rather than a generic error, so this endpoint **does** reveal whether an email is registered. That's the given code's behavior, carried over as-is, not something changed here.

### What NOT to say during a demo

- Do not say a blacklisted access token is immediately rejected everywhere — see the blacklist caveat above; it's only checked during token refresh, not on every authenticated request.
- Do not say forgot-password sends a real email — see "What's reconstructed" in `README.md`. It logs the OTP server-side.

## Job module

### Entity

`Job`: `title`, `description`, `budgetUsd`, `clientUserId`, `freelancerUserId`, `status`, `checkoutOrderId` (nullable — set once payment starts), `payoutReleaseId` (nullable — set once the payout is released). `id`/`createdAt`/`createdBy`/`updatedAt`/`updatedBy` come from `AbstractEntity`.

### Status machine

```
OPEN ──pay──> AWAITING_PAYMENT ──confirm-payment (CAPTURED)──> IN_PROGRESS ──approve──> COMPLETED
  └──cancel──> CANCELLED
```

There is no way back from any state to an earlier one, and no state skips a step — `approve` cannot be called on a job that never reached `IN_PROGRESS`, `confirm-payment` cannot be called before `pay`.

### Ownership

Every `Job` endpoint requires a valid access token (`@AuthenticationPrincipal UserPrincipal`). Two different checks are used, matching which side of the job is allowed to act:

- **Client-only** (`getOwnedByClientOrThrow`) — `pay`, `confirmPayment`, `approve`, `cancel`. Only the job's `clientUserId` may call these; a mismatch throws the same `JOB_NOT_FOUND` as a nonexistent job, hiding whether the job exists at all from a non-owner — the same "don't leak existence" pattern paypal-backend's own ownership checks use.
- **Either participant** (`getParticipantOrThrow`) — `getById`, `getPaymentStatus`. Either `clientUserId` or `freelancerUserId` may read these; anyone else gets `JOB_NOT_FOUND`.
- `list()` never takes a target user id from the caller — it always queries by the caller's own id (`findByClientUserIdOrFreelancerUserId(userId, userId)`), so there's no way to list another user's jobs by passing a different id in.
- There is no freelancer-only action anywhere in this API — the freelancer is never the one calling `pay`/`confirmPayment`/`approve`/`cancel`. If a "freelancer marks job as delivered" step is ever needed, it would need its own endpoint and its own ownership check (freelancer-only, mirroring the client-only ones above) — nothing here currently gates on `freelancerUserId` for a mutating action.

### Business rules (`JobServiceImpl`)

- `create()`: no validation beyond the request DTO's own constraints (non-blank title, positive `budgetUsd`). Does not check that `freelancerUserId` has a PayPal payee yet, or that it's even a real user id — this service has no way to verify a `UUID` belongs to an actual account unless that account happens to exist in this same service's own `User` table, which a freelancer's id has no reason to.
- `pay()`: requires `OPEN` (`INVALID_JOB_STATUS` otherwise). Calls Payee Status first — if the freelancer isn't `registered` or isn't `active`, fails with `FREELANCER_NOT_LINKED_TO_PAYPAL` **before** ever calling Checkout Order create, so no PayPal order is created for a freelancer who can't receive it. On success, stores `checkoutOrderId` and moves to `AWAITING_PAYMENT`.
- `confirmPayment()`: requires `AWAITING_PAYMENT` with a `checkoutOrderId` already set. Calls Checkout Order capture. **Only** advances to `IN_PROGRESS` if paypal-backend's response status is exactly `"CAPTURED"` — any other value leaves the job sitting at `AWAITING_PAYMENT` with no distinct error surfaced.
- `approve()`: requires `IN_PROGRESS` with a `checkoutOrderId` set. Calls Payout Release. Stores whatever `payoutReleaseId` comes back and moves straight to `COMPLETED` **regardless of the release's own status** — see paypal-backend's own known limitations on why `PENDING` is the near-universal result here. Use `getPaymentStatus()` to check the real, current status.
- `cancel()`: requires `OPEN` — a job that has already moved to `AWAITING_PAYMENT` or beyond cannot be cancelled through this endpoint.
- `getPaymentStatus()`: requires `checkoutOrderId` to be set (`JOB_NOT_PAID` otherwise). Always re-fetches the Checkout Order's live status from paypal-backend rather than trusting whatever this service last stored.

### Error codes (`ErrorCode`)

| Code | Meaning |
|---|---|
| `JOB_NOT_FOUND` (4000) | No job with that id, or the caller isn't allowed to see/act on it (ownership hidden behind the same code) |
| `INVALID_JOB_STATUS` (4001) | The requested transition doesn't apply to the job's current status |
| `FREELANCER_NOT_LINKED_TO_PAYPAL` (4002) | Payee Status returned `registered: false` or `active: false` |
| `JOB_NOT_PAID` (4003) | `getPaymentStatus()` called before `checkoutOrderId` exists |
| `PAYPAL_BACKEND_CALL_FAILED` (4004) | Any call to paypal-backend threw (network error, non-2xx response, timeout) |

The `2000`-series Auth codes (`UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `TOKEN_EXPIRED`, etc.) are the exact same codes and numbers used in paypal-backend's own `ErrorCode` — copied over rather than reinvented, so error handling looks identical from either service if a client ever has to deal with both.

## PaypalBackendClient

A thin RestTemplate wrapper, one method per internal endpoint on paypal-backend (see the table in `README.md`). Every call sets `X-Internal-Api-Key` from `PaypalBackendProperties` (`paypal-backend.internal-api-key`) and deserializes the response through this service's own `ResponseAPI<T>` wrapper — which works because paypal-backend's `ResponseAPI` has the identical `{code, message, data}` shape.

The `PayeeStatusResult` / `CheckoutOrderResult` / `PayoutReleaseResult` DTOs here are **hand-reconstructed copies** of paypal-backend's actual response DTOs, matched field-for-field from having written those classes directly. If paypal-backend's response shape ever changes, these three files have to be updated by hand — there is no shared library between the two services.

## Known limitations

- No retry or compensation logic anywhere in `JobServiceImpl` — a crash between a successful paypal-backend call and the following `jobRepository.save()` leaves the two services out of sync.
- `PaypalBackendClient.exchange()` collapses every failure mode into the same `PAYPAL_BACKEND_CALL_FAILED` — see the same limitation noted in the previous version of this doc, unchanged by adding Auth.
- OTP is logged, not emailed (see `README.md`). Blacklist is only checked on refresh, not on every request (see Auth module above).
