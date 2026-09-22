# Marketplace Backend

Backend service (`marketplace-backend`) that owns job posting, the job payment lifecycle, and user accounts for this service. It has no PayPal logic of its own — every real money movement is delegated to `paypal-backend` (a separate repository/service) over its three internal, API-key-authenticated endpoints. This service's job is to sequence those calls correctly, keep a job's state in sync with what happened on the payment side, and gate everything behind its own login.

## Relationship to paypal-backend

| This service calls | On paypal-backend | When |
|---|---|---|
| `GET /internal/paypal/users/{userId}/payee-status` | Payee Status module | Before creating a checkout order — confirms the freelancer has an active PayPal payee profile |
| `POST /internal/paypal/checkout/orders` | Checkout Order module | When the client pays for a job (`POST /jobs/{jobId}/pay`) |
| `POST /internal/paypal/checkout/orders/{id}/capture` | Checkout Order module | When the client confirms they approved payment (`POST /jobs/{jobId}/confirm-payment`) |
| `GET /internal/paypal/checkout/orders/{id}` | Checkout Order module | Read-only status check (`GET /jobs/{jobId}/payment-status`) |
| `POST /internal/paypal/payouts` | Payout Release module | When the client approves the finished job (`POST /jobs/{jobId}/approve`) |
| `GET /internal/paypal/payouts/{id}` | Payout Release module | Read-only status check (`GET /jobs/{jobId}/payment-status`) |

All six calls go through `PaypalBackendClient`, authenticated with the `X-Internal-Api-Key` header (`paypal-backend.internal-api-key` — must match `internal.api.key` / `INTERNAL_API_KEY` on the paypal-backend side). See `docs/MARKETPLACE_MODULE_REFERENCE.md` for exactly what each Job endpoint does and what can go wrong.

Note the direction: this service's own `internal.api.key` / `InternalApiKeyFilter` exist and are wired, but nothing here currently exposes an `/internal/**` endpoint for anyone to call — they're ready for future use, not in use today. paypal-backend is the one being called, not the one calling in.

## Run it

```bash
cp .env.example .env
mvn spring-boot:run
```

Needs MySQL reachable at the URL in `application.yml` (database `marketplace`) and Redis (now actually used, by the OTP flow below — not just wired-and-idle like before). No `docker-compose.yml` is included here — copy and adapt the one from `paypal-backend` (same MySQL/Redis shape, different database name and ports) if you want one.

Environment variables to set:

- `DB_USERNAME` / `DB_PASSWORD` / `DB_URL` — this service's own database, separate from paypal-backend's.
- `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` — backs the forgot-password OTP store (see "Auth" below).
- `JWT_SECRET` — **do not use the dev default in `application-dev.yml`.** Independent from paypal-backend's own `JWT_SECRET` — a token issued by one service is not valid on the other, since each has its own `User` table and its own secret.
- `JWT_EXPIRATION_MS` — access token lifetime, default 24h.
- `PAYPAL_BACKEND_BASE_URL` — where paypal-backend is reachable, e.g. `http://localhost:9192`.
- `PAYPAL_BACKEND_INTERNAL_API_KEY` — must equal paypal-backend's `internal.api.key`.
- `INTERNAL_API_KEY` — this service's own internal key, for the day something exposes `/internal/**` here. Set it anyway so `${internal.api.key}` resolves and the app starts.

## Auth

Full email/password auth, mirroring paypal-backend's own Auth module endpoint-for-endpoint:

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Create a user, assigned `ROLE_USER` (seeded by `DataInitializer` on first boot). |
| `POST` | `/api/v1/auth/sign-in` | Returns an access token in the body and sets a `refreshToken` cookie (`HttpOnly`, `Secure`). |
| `POST` | `/api/v1/auth/refresh-token` | Reads the `refreshToken` cookie, returns a new access token. |
| `POST` | `/api/v1/auth/sign-out` | Blacklists the current access token in Redis for its remaining lifetime, clears the stored refresh token. |
| `POST` | `/api/v1/auth/forgot-password/send-otp` | Generates a 6-digit OTP, stores it in Redis for 5 minutes. |
| `POST` | `/api/v1/auth/forgot-password/verify-otp` | Checks the OTP, returns a short-lived reset token. |
| `POST` | `/api/v1/auth/forgot-password/reset` | Consumes the reset token, sets a new password. |
| `GET` | `/api/v1/auth/me` | Returns the caller's own profile. |

Every `Job` endpoint now requires a valid `Bearer` access token. `clientUserId` is read from the token (`@AuthenticationPrincipal UserPrincipal`) rather than the request body — a job's creator is whoever is logged in, not whoever the request claims to be. See "Ownership" in `docs/MARKETPLACE_MODULE_REFERENCE.md` for exactly which Job endpoints check for the client, the freelancer, or either.

### What's reconstructed, not given

The 18 files you sent (`AuthenticationService(Impl)`, `JwtService(Impl)`, `AuthController`, `RedisService(Impl)`, `UserRepository`, `RoleRepository`, and the 10 request/response DTOs) were repackaged as-is — same logic, only the package changed. Everything they depend on that wasn't sent had to be reconstructed here, with varying confidence:

- **`User` entity, `AuthProvider` enum** — reconstructed from field usage across `AuthenticationServiceImpl`/`JwtServiceImpl` (`email`, `password`, `displayName`, `authProvider`, `enabled`, `roles`, `refreshToken`). Reasonably confident — every field is used somewhere, nothing was guessed beyond that.
- **`UserPrincipal`** — reconstructed as a `UserDetails` implementation with `getId()`/`getEmail()`/`getRoles()` added on top, since `JwtServiceImpl.buildRoles()` calls `userPrincipal.getRoles()` directly rather than `getAuthorities()`.
- **`UserDetailsServiceCustomizer`** — reconstructed as a plain `UserDetailsService` implementation (`loadUserByUsername` → `UserPrincipal.create(user)`).
- **`JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint`, `JwtAccessDenied`** — these were never sent, only referenced by the `SecurityConfiguration` you sent for paypal-backend earlier in this conversation. Rebuilt here to match that same wiring (the filter is instantiated via `new JwtAuthenticationFilter()` inside a `@Bean` method, not `@Component`-scanned, so it uses field `@Autowired` rather than constructor injection — this matches how paypal-backend's own version has to work, given how its `SecurityConfiguration` constructs it).
- **`OTPService` / `OTPServiceImpl`** — never sent at all. Implemented here against `RedisService` (which *was* sent) — generates a 6-digit code, stores it in Redis for 5 minutes, and **logs it instead of emailing it**. There's no `spring-boot-starter-mail` dependency and no mail sender bean in this project — if paypal-backend's real OTP flow sends actual email, send that implementation (and the mail config it depends on) to replace this stub.
- **`SecurityConfiguration`, `SecurityConstants`** — adapted directly from paypal-backend's own copies (already seen in full), with `com.marketplace.backend` packages and the same `InternalApiKeyFilter` wiring already in place from before.

Everything above compiles and runs as given — nothing here is a placeholder pending more files, except the real-email part of OTP.

## Job module

### Status machine

`OPEN` → `AWAITING_PAYMENT` → `IN_PROGRESS` → `COMPLETED`, with `OPEN` → `CANCELLED` as the only other transition. No state moves backward.

### Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/v1/marketplace/jobs` | Create a job: `{freelancerUserId, title, description, budgetUsd}`. `clientUserId` comes from the caller's token. Status starts at `OPEN`. |
| `GET` | `/api/v1/marketplace/jobs` | List every job where the caller is the client or the freelancer. |
| `GET` | `/api/v1/marketplace/jobs/{jobId}` | Read one job — caller must be a participant (client or freelancer). |
| `POST` | `/api/v1/marketplace/jobs/{jobId}/pay` | Client pays: looks up the freelancer's payee status, creates a Checkout Order on paypal-backend, stores its id, moves the job to `AWAITING_PAYMENT`. Returns `approvalUrl` for the client to approve in a browser. |
| `POST` | `/api/v1/marketplace/jobs/{jobId}/confirm-payment` | Call after the client approves on PayPal's side: captures the Checkout Order. Moves the job to `IN_PROGRESS` only if PayPal actually reports `CAPTURED`. |
| `POST` | `/api/v1/marketplace/jobs/{jobId}/approve` | Client approves the finished job: releases the payout on paypal-backend, stores the release id, moves the job to `COMPLETED`. |
| `POST` | `/api/v1/marketplace/jobs/{jobId}/cancel` | Cancel a job that hasn't been paid for yet (`OPEN` only). |
| `GET` | `/api/v1/marketplace/jobs/{jobId}/payment-status` | Live status: re-fetches the Checkout Order (and Payout Release, if one exists) from paypal-backend rather than trusting this service's own stored status. Caller must be a participant. |

See `docs/MARKETPLACE_MODULE_REFERENCE.md` for the exact business rules, ownership checks, and error codes behind each of these.

## Known limitations

- **No webhook or retry handling.** If `pay` succeeds on paypal-backend but this service crashes before saving the job, the Checkout Order exists on paypal-backend with nothing pointing to it from here. Same risk at `approve`/payout release. Fine for a demo, not for production.
- **OTP is logged, not emailed** — see "What's reconstructed" above.
- **`pom.xml` is still a best-effort reconstruction** — now with `spring-boot-starter-security` and `nimbus-jose-jwt` added to match what the Auth files need. The rest of it (parent POM, other dependency versions) was never confirmed against a real `pom.xml`.
- **No `docker-compose.yml`** — copy and adapt `paypal-backend`'s (different database name/ports).
- No pagination on `GET /api/v1/marketplace/jobs` — returns every matching row in one response.
