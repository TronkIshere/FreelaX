# WP-G0.2 — Refresh Token Transport Contract Decision

STATUS: CONTRACT_DECISION_RECORDED; IMPLEMENTATION_PENDING
MODE: GOVERNANCE_ONLY
IMPLEMENTATION_AUTHORIZED: NO

This document records the checked-in contract and recommends one mobile transport. It authorizes no Flutter or backend source change. The actual route is `POST /api/v1/auth/refresh-token` (the shorter `/auth/refresh` in the option description is conceptual).

## Verified current behavior

| Check | Result | Evidence |
| --- | --- | --- |
| LOGIN: backend returns refresh token in response body | YES | `SignInResponse.refreshToken`; `AuthenticationServiceImpl.generateTokenResponse` |
| LOGIN: backend sets refresh token cookie | YES | `generateTokenResponse` calls `response.addCookie` |
| REFRESH: backend accepts refresh token from cookie | YES | `AuthController.refreshToken` requires `@CookieValue(name = "refreshToken")` |
| REFRESH: backend accepts refresh token from JSON body | NO | No `@RequestBody` or refresh request DTO on this endpoint |
| FLUTTER: stores refresh token in secure storage | YES | `AuthUser.toJson` includes it; `AuthService` persists the user JSON using `FlutterSecureStorage` |
| FLUTTER: automatically persists backend cookies across restart | NOT IMPLEMENTED | `RemoteAuthRepository` uses a plain `http.Client`; no cookie jar, `Set-Cookie` handling, cookie storage, or `Cookie` request header is present |

The cookie is named `refreshToken`, with `HttpOnly=true`, `Secure=true`, path `/`, and a 14-day `Max-Age`. This helper does not set `SameSite`. The backend stores the issued token on `User.refreshToken`. Refresh rejects a blank token, extracts its subject, loads the user, compares the presented token with the stored token, and verifies the JWT. It returns `accessToken` and `userId`; it does not rotate or return a new refresh token. There is no refresh request DTO.

Backend sign-out takes `LogoutRequest.accessToken`, blacklists that access token for its remaining lifetime, clears `User.refreshToken`, and emits an expired refresh cookie. Flutter sign-out sends the access token, but `RemoteAuthRepository.logout` swallows request errors; `AuthService.logout` then deletes its locally stored user. A failed network sign-out therefore does not prove server-side revocation.

Flutter `RemoteAuthRepository.refreshAccessToken` posts `{"refreshToken":"<securely stored token>"}` as JSON to `/api/v1/auth/refresh-token`, with no cookie header. `AuthService.refreshAccessToken` keeps the stored refresh token and replaces the access token. `ApiClient` retries a request once after a 401 if refresh succeeds; an auth 401/403 from refresh triggers logout, while other refresh errors are swallowed and the original response is parsed. On restart, `SplashScreen` only loads the stored user and checks that it exists; it does not validate or refresh the session. `AppConfigService` persists the server URL in `SharedPreferences`, but startup does not call `AppConfigService.load` before session work. These are future P0.1 frontend tasks, not behavior already implemented.

## Options

### A. Cookie-based refresh

Contract: `POST /api/v1/auth/refresh-token` with `Cookie: refreshToken=<token>`; no refresh token in the JSON request. This matches the current backend endpoint. A mobile client would need a cookie jar that receives `Set-Cookie` at login, sends the matching cookie on refresh, persists it securely across process restart, and removes it on logout. It must respect the cookie's `HttpOnly` and `Secure` attributes rather than read the token into Dart and synthesize a `Cookie` header. The `Secure` cookie requires HTTPS. Cookie scope must follow the configured server host/path; changing the saved server URL must not silently reuse credentials for another host. Startup must load the URL, restore the jar, then validate the session. The 401 retry must use that same jar and replay at most once. Logout must clear the jar locally and request server revocation.

Flutter currently has no cookie jar or persistence implementation. **DEPENDENCY REQUIRED** for a maintained, persistent cookie-jar approach; alternatively, custom cookie management would be new security-sensitive frontend code and is not an approved shortcut. The backend endpoint needs no transport change, though cookie policy and any web cross-site requirements would need explicit review. The current login response also exposes the refresh token in JSON, so choosing a cookie-only security model would require a separate decision about removing that exposure and migrating Flutter storage.

### B. Body-based mobile refresh

Contract: `POST /api/v1/auth/refresh-token`, `Content-Type: application/json`, body `{"refreshToken":"<token>"}`. This fits the current sign-in JSON and Flutter secure storage; the existing 401 retry already calls this request shape. The backend must explicitly accept and validate a required refresh-token request DTO at this endpoint (or an explicitly versioned mobile endpoint). It must preserve the present stored-token comparison, JWT verification, expiry, and revocation checks. A body token is available to application code and must be kept out of logs, analytics, crash reports, and URLs. HTTPS is required for sign-in, refresh, and sign-out. No new Flutter dependency is required.

Flutter still needs P0.1 work: load the saved server URL before auth, validate/refresh the restored session and fetch trusted `/me` role before entering a role shell, clear local credentials when refresh is rejected, and make 401 replay and logout behavior explicit. The existing local clear on logout cannot guarantee server revocation if the sign-out call fails; the backend owner must confirm the logout contract for expired access tokens and failed network sign-out. Do not treat a stored token or an unavailable backend as proof of a valid session.

### Dual transport

Cookie **or** request body could retain a cookie-based web caller while supporting mobile, but no web caller of this endpoint was established in this review. Accepting both creates a precedence rule when both values are present, can mask a misconfigured client, expands the endpoint's credential surface, and complicates tests and logout/cookie cleanup. It is only suitable with an identified web compatibility requirement, explicit precedence and rejection of conflicting credentials, identical validation for either source, and one revocation rule. **Do not select dual transport for convenience.**

## Decision matrix

| Criterion | Cookie | Body |
| --- | --- | --- |
| Matches current backend | YES | NO; endpoint change required |
| Matches current Flutter | NO | YES; current request shape and storage |
| Requires new Flutter dependency | YES for maintained persistent jar | NO |
| Works after app restart | Only after jar persistence and startup restore are built | Stored token survives; startup validation still required |
| Requires HTTPS | YES; `Secure` cookie | YES; credential in request body |
| Compatible with secure storage | Requires secure persistent cookie storage and migration from current token record | YES; current token record already uses it |
| Easy to test | Needs cookie receipt, scope, persistence, and replay tests | Explicit JSON request and response tests |
| Risk of hidden state | Higher: jar, host, path, and persistence | Lower: explicit token field, still sensitive |
| Backend change required | No endpoint transport change; cookie policy review remains | YES; accept validated JSON DTO while preserving validation |
| Frontend change required | Cookie jar, secure persistence, URL handling, startup, retry, logout | Startup/session hardening, retry/logout handling; request shape already exists |

## Recommended contract for mobile

**TRANSPORT:** BODY, pending backend owner acceptance and implementation. Until the backend accepts the body, refresh remains blocked; the current Flutter request cannot be considered working.

`SCREEN_ARCHITECTURE.md` currently describes the cookie endpoint. This recommendation records a proposed mobile contract change; that screen text remains the description of the checked-in backend until the backend owner accepts and implements the change. This task does not edit the screen architecture.

**LOGIN RESPONSE:** Keep the existing `ResponseAPI.data` fields `status`, `accessToken`, `refreshToken`, `userId`, and `email` for the mobile client. Treat `refreshToken` as a credential. The existing `Set-Cookie` is not the mobile credential path under this decision; its future web use requires a separate compatibility decision.

**REFRESH REQUEST:** `POST /api/v1/auth/refresh-token` with JSON `{"refreshToken":"<token>"}`. The backend must require a nonblank token and apply the same stored-token equality and JWT validation currently used for the cookie value. Do not accept an unauthenticated role or user ID supplied by the client.

**REFRESH RESPONSE:** Keep `ResponseAPI.data.accessToken` and `userId`. Current refresh does not rotate the refresh token; Flutter retains the existing stored token. Any future rotation requires an explicit new response/storage contract.

**TOKEN STORAGE:** Flutter secure storage for access and refresh tokens, scoped to the configured server identity. Never put tokens in `SharedPreferences`, URLs, or logs. The server URL remains a configuration value in `SharedPreferences`.

**STARTUP BEHAVIOR:** Load the saved server URL before auth work. Restore credentials from secure storage, validate or refresh them with the backend, then fetch authenticated `/api/v1/auth/me` for the trusted `CLIENT`/`FREELANCER` role before opening a role-aware shell. An absent, invalid, or unverified role does not become a default role or authenticated session.

**401 RETRY:** Refresh once with the stored token, persist the returned access token, and replay the original request once. If refresh is rejected, clear local credentials and return to login; avoid a retry loop. Handle transient network failure separately from invalid credentials without treating it as a verified session.

**LOGOUT:** Send the existing sign-out request to revoke the server-stored refresh token and blacklist the access token when reachable; clear local secure storage even if the request fails. Do not claim remote revocation after a failed call. The backend owner must settle how sign-out revokes a session when its access token is expired; do not use that gap to keep a stale local session.

**HTTPS REQUIREMENT:** Require HTTPS for login, refresh, `/me`, and sign-out when transmitting real credentials. The current cookie's `Secure` flag also requires HTTPS for cookie transport. No HTTP fallback for production credentials is authorized here.

## Gates and ownership

- `fix/auth-me-user-type` (`bdebb2d99b2132c9988a882d61f5c6aaa11155ac`) is **not integrated into `origin/master`** at this review. It must be integrated into the shared backend baseline before P0.1 is fully unblocked. This document does not merge it.
- The refresh transport decision still requires backend owner acceptance and an authorized backend endpoint change. This frontend branch must not modify `marketplace-backend/**` or `payment-backend/**`.
- `paypal/docs/WORKPACK_P0_FOUNDATION.md` remains `IMPLEMENTATION_AUTHORIZED: NO`. No P0.1 implementation is authorized by this decision record.
- **OPEN-job budget editing remains disabled.** The separate payment contract is unchanged.

Evidence reviewed: `marketplace-backend/src/main/java/com/marketplace/backend/controller/AuthController.java`; `marketplace-backend/src/main/java/com/marketplace/backend/service/impl/AuthenticationServiceImpl.java`; auth request/response DTOs; `paypal/lib/core/data/remote_auth_repository.dart`; `paypal/lib/core/services/auth_service.dart`; `paypal/lib/core/services/api_client.dart`; `paypal/lib/core/models/auth_user.dart`; `paypal/lib/core/services/app_config_service.dart`; `paypal/lib/features/splash/presentation/screens/splash_screen.dart`; `paypal/lib/main.dart`; `paypal/pubspec.yaml`.
