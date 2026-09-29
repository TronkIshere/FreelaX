# WP-G0 — Marketplace UI Contract Unblock

STATUS: CONTRACT_UNBLOCK_PENDING
MODE: GOVERNANCE_ONLY
IMPLEMENTATION_AUTHORIZED: NO

This workpack records verified P0 contract blockers and the gates for later frontend work. It does not authorize Flutter or backend implementation, change the P0 foundation workpack, or grant permission to edit a backend branch.

## G0.1 — Trusted role contract

**Status:** `BLOCKED_BACKEND_CONTRACT`

Verified current facts:

- Backend `User` stores `userType` as `CLIENT` or `FREELANCER`.
- Registration returns `UserResponse` with `userType` populated.
- `SignInResponse` has no `userType` field.
- `UserResponse` declares `userType`, but `AuthController.getCurrentUser` does not populate it for `GET /api/v1/auth/me`.
- Flutter `AuthUser` has no marketplace role. Login and session restore therefore cannot establish a trusted CLIENT/FREELANCER role.

Preferred authenticated response contract:

```text
GET /api/v1/auth/me
→ id
→ email
→ displayName
→ userType
```

Allowed `userType` values: `CLIENT`, `FREELANCER`. The response must provide the authenticated user's actual role. Flutter must not infer, hardcode, or default a role from registration choice, email, stored UI state, or an absent field. Role-aware navigation remains gated until this contract is available and verified after login and restore.

Evidence: `marketplace-backend/src/main/java/com/marketplace/backend/entity/User.java`; `marketplace-backend/src/main/java/com/marketplace/backend/dto/response/auth/SignInResponse.java`; `marketplace-backend/src/main/java/com/marketplace/backend/dto/response/auth/UserResponse.java`; `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/AuthenticationServiceImpl.java`; `marketplace-backend/src/main/java/com/marketplace/backend/controller/AuthController.java`; `paypal/lib/core/models/auth_user.dart`.

## G0.2 — Refresh/session contract

**Status:** `CONTRACT_DECISION_REQUIRED`

Exact current mismatch:

```text
Flutter refresh request:
POST /api/v1/auth/refresh-token
Content-Type: application/json
Body: {"refreshToken": "<stored refresh token>"}

Backend expected refresh transport:
POST /api/v1/auth/refresh-token
Cookie: refreshToken=<token>
AuthController reads @CookieValue(name = "refreshToken").
```

At sign-in, the backend sets a `refreshToken` cookie with `HttpOnly` and `Secure` flags and also includes `refreshToken` in `SignInResponse`. Flutter stores the JSON token in secure storage; its refresh request does not send the cookie expected by the controller. The frontend and backend owners must agree on a supported mobile refresh transport, cookie/session handling across app restarts, and secure transport before P0.1 relies on refresh. Do not add a frontend workaround that weakens authentication or treats a failed refresh as a valid session.

Evidence: `paypal/lib/core/data/remote_auth_repository.dart` (`refreshAccessToken`); `paypal/lib/core/services/auth_service.dart` (`refreshAccessToken`); `marketplace-backend/src/main/java/com/marketplace/backend/controller/AuthController.java` (`refreshToken`); `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/AuthenticationServiceImpl.java` (`generateTokenResponse`, `buildRefreshTokenCookie`).

## G0.3 — Saved server URL bootstrap

**Classification:** `FRONTEND_P0.1`

`AppConfigService.setBaseUrl` persists the server URL in `SharedPreferences`, and `AppConfigService.load` can restore it. Current `main.dart` and `SplashScreen` do not call that load method before auth/session work. The saved URL may therefore remain unavailable in memory after restart. P0.1 must load configuration before making authenticated requests. This is a frontend task, not a backend blocker once the auth contract is resolved.

Evidence: `paypal/lib/core/services/app_config_service.dart`; `paypal/lib/main.dart`; `paypal/lib/features/splash/presentation/screens/splash_screen.dart`.

## G0.4 — OPEN Job budget editing

**Status:** `BLOCKED_BACKEND_PAYMENT_CONTRACT`

Verified behavior:

```text
Job create
→ creates a checkout order using the initial budgetUsd.

Job update
→ can change job.budgetUsd while the Job is OPEN.

PaymentBackendClient
→ exposes checkout create/get/capture, with no checkout amount update operation.
```

Changing the job budget can therefore leave `job.budgetUsd` different from the checkout amount. **Frontend budget editing = DISABLED** until the backend/payment contract resolves this mismatch. This explicit gate qualifies the OPEN edit action in `SCREEN_ARCHITECTURE.md`. OPEN-job title and description editing may remain separately eligible, subject to normal authorization and state checks. Do not simulate checkout synchronization in Flutter.

Evidence: `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/JobServiceImpl.java` (`create`, `update`); `marketplace-backend/src/main/java/com/marketplace/backend/client/PaymentBackendClient.java`; `marketplace-backend/src/main/java/com/marketplace/backend/dto/request/job/UpdateJobRequest.java`.

## G0.5 — Implementation gates

- **WP-P0.1 Auth role:** May start only after the trusted `userType` contract is resolved. Its refresh/session decision must be made before refresh is relied on; the saved server URL bootstrap belongs to this frontend phase.
- **WP-P0.2 Registration:** May follow P0.1 interface stabilization.
- **WP-P0.3 Job API:** May begin for non-budget operations after auth is stable. Budget update remains disabled until G0.4 is resolved.
- **WP-P0.4 Role-aware shell:** Requires the trusted role from P0.1. No role is inferred or hardcoded.

Each later implementation workpack still requires its own exact approved file manifest and explicit authorization. `paypal/docs/WORKPACK_P0_FOUNDATION.md` remains `IMPLEMENTATION_AUTHORIZED: NO`.

## G0.6 — Ownership boundary

This frontend branch must **not** modify:

```text
marketplace-backend/**
payment-backend/**
```

Backend contract fixes belong to the backend owner or a separately authorized backend branch. This document records requirements and blockers only; it is not permission to change backend code here.
